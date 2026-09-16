-- TANI — zone-aware checkout for merchants with per-area delivery pricing.
-- Customers select a delivery zone per merchant. The server validates the
-- selected zone belongs to that seller and reads the current fee at checkout.

create or replace function public.checkout_create_order_group_v2(
  p_address_id uuid,
  p_phone text,
  p_notes text,
  p_items jsonb,
  p_idempotency_key text,
  p_delivery_zones jsonb default '{}'::jsonb
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_existing uuid;
  v_group_id uuid;
  v_order_id uuid;
  v_item jsonb;
  v_line jsonb;
  v_lines jsonb := '[]'::jsonb;
  v_product record;
  v_seller record;
  v_address record;
  v_profile record;
  v_product_id uuid;
  v_qty int;
  v_subtotal numeric := 0;
  v_delivery_total numeric := 0;
  v_delivery_fee numeric;
  v_delivery_area text;
  v_selected_zone_id text;
  v_address_text text;
begin
  if v_uid is null then raise exception 'Authentication required'; end if;
  if jsonb_typeof(coalesce(p_delivery_zones, '{}'::jsonb)) <> 'object' then
    raise exception 'Invalid delivery zone selections';
  end if;

  select p.id, p.name into v_profile
  from public.profiles p
  where p.id = v_uid and p.is_active = true and p.deleted_at is null;
  if not found then raise exception 'Account is not active'; end if;

  if coalesce(char_length(trim(p_idempotency_key)), 0) < 16
     or char_length(trim(p_idempotency_key)) > 100 then
    raise exception 'Invalid checkout key';
  end if;

  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended(v_uid::text || ':' || trim(p_idempotency_key), 0)
  );
  select og.id into v_existing
  from public.order_groups og
  where og.customer_id = v_uid and og.idempotency_key = trim(p_idempotency_key)
  limit 1;
  if found then return v_existing; end if;

  select a.* into v_address
  from public.addresses a
  where a.id = p_address_id and a.user_id = v_uid;
  if not found then raise exception 'Address not found'; end if;

  if coalesce(char_length(trim(p_phone)), 0) < 7 or char_length(trim(p_phone)) > 30 then
    raise exception 'Invalid phone number';
  end if;
  if char_length(coalesce(p_notes, '')) > 1000 then raise exception 'Order notes are too long'; end if;
  if jsonb_typeof(coalesce(p_items, '[]'::jsonb)) <> 'array'
     or jsonb_array_length(coalesce(p_items, '[]'::jsonb)) = 0 then
    raise exception 'Cart is empty';
  end if;
  if jsonb_array_length(p_items) > 100 then raise exception 'Cart has too many items'; end if;

  v_address_text := concat_ws(
    ' - ',
    nullif(trim(v_address.label), ''),
    nullif(trim(v_address.description), ''),
    nullif(trim(coalesce(v_address.area, '')), ''),
    nullif(trim(coalesce(v_address.landmark, '')), '')
  );

  for v_item in
    select jsonb_build_object('product_id', product_id::text, 'quantity', sum(quantity)::int)
    from (
      select (x.value ->> 'product_id')::uuid as product_id,
             greatest(1, least(99, coalesce((x.value ->> 'quantity')::int, 1))) as quantity
      from jsonb_array_elements(p_items) x(value)
      where x.value ? 'product_id'
    ) q
    group by product_id
    order by product_id
  loop
    v_product_id := (v_item ->> 'product_id')::uuid;
    v_qty := (v_item ->> 'quantity')::int;

    select p.id, p.seller_id, p.name, p.price, p.stock,
           coalesce(st.name, s.store_name) as store_name
    into v_product
    from public.products p
    join public.sellers s on s.id = p.seller_id
    left join public.stores st on st.seller_id = p.seller_id and st.is_active = true
    where p.id = v_product_id
      and p.is_active = true
      and s.verification_status = 'approved'
    for update of p;

    if not found then raise exception 'A product in your cart is no longer available'; end if;
    if v_product.stock < v_qty then raise exception 'Insufficient stock for %', v_product.name; end if;

    v_lines := v_lines || jsonb_build_array(jsonb_build_object(
      'product_id', v_product.id::text,
      'seller_id', v_product.seller_id::text,
      'product_name', v_product.name,
      'store_name', v_product.store_name,
      'unit_price', v_product.price,
      'quantity', v_qty,
      'line_total', v_product.price * v_qty
    ));
    v_subtotal := v_subtotal + (v_product.price * v_qty);
  end loop;
  if jsonb_array_length(v_lines) = 0 then raise exception 'Cart is empty'; end if;

  insert into public.order_groups(
    customer_id, subtotal, delivery_total, discount_total, grand_total,
    payment_method, payment_status, status, idempotency_key,
    address_id, address_snapshot, customer_note, created_at, updated_at
  ) values (
    v_uid, v_subtotal, 0, 0, v_subtotal,
    'cod', 'pending', 'pending', trim(p_idempotency_key), p_address_id,
    jsonb_build_object(
      'label', v_address.label,
      'description', v_address.description,
      'area', v_address.area,
      'landmark', v_address.landmark,
      'phone', coalesce(nullif(trim(p_phone), ''), v_address.phone),
      'delivery_notes', v_address.delivery_notes,
      'delivery_zones', coalesce(p_delivery_zones, '{}'::jsonb)
    ),
    nullif(trim(coalesce(p_notes, '')), ''), now(), now()
  ) returning id into v_group_id;

  for v_seller in
    select seller_id::uuid as seller_id,
           max(store_name) as store_name,
           sum(line_total)::numeric as subtotal
    from jsonb_to_recordset(v_lines)
      as x(seller_id text, store_name text, line_total numeric)
    group by seller_id
    order by seller_id
  loop
    v_delivery_area := null;
    v_delivery_fee := null;

    if exists (
      select 1 from public.delivery_zones z
      where z.seller_id = v_seller.seller_id and z.is_active = true
    ) then
      v_selected_zone_id := nullif(trim(coalesce(p_delivery_zones ->> v_seller.seller_id::text, '')), '');
      if v_selected_zone_id is null then
        raise exception 'Choose a delivery area for %', v_seller.store_name;
      end if;

      select z.fee, z.area_name
      into v_delivery_fee, v_delivery_area
      from public.delivery_zones z
      where z.id::text = v_selected_zone_id
        and z.seller_id = v_seller.seller_id
        and z.is_active = true
      limit 1;

      if not found then
        raise exception 'Selected delivery area is not available for %', v_seller.store_name;
      end if;
    else
      select d.base_fee, d.delivery_area
      into v_delivery_fee, v_delivery_area
      from public.delivery_settings d
      where d.seller_id = v_seller.seller_id and d.is_active = true
      limit 1;

      if not found then
        raise exception 'Delivery settings are incomplete for %', v_seller.store_name;
      end if;
    end if;

    v_delivery_fee := coalesce(v_delivery_fee, 0);

    insert into public.orders(
      customer_id, order_group_id, seller_id,
      subtotal, delivery_fee, discount, total,
      status, payment_method, payment_status,
      address, phone, customer_name_snapshot, store_name_snapshot,
      address_snapshot, customer_note, created_at, updated_at
    ) values (
      v_uid, v_group_id, v_seller.seller_id,
      v_seller.subtotal, v_delivery_fee, 0, v_seller.subtotal + v_delivery_fee,
      'pending', 'cod', 'pending',
      v_address_text, trim(p_phone), v_profile.name, v_seller.store_name,
      jsonb_build_object(
        'label', v_address.label,
        'description', v_address.description,
        'area', v_address.area,
        'landmark', v_address.landmark,
        'phone', trim(p_phone),
        'delivery_notes', v_address.delivery_notes,
        'delivery_zone', v_delivery_area
      ),
      nullif(trim(coalesce(p_notes, '')), ''), now(), now()
    ) returning id into v_order_id;

    for v_line in
      select value
      from jsonb_array_elements(v_lines)
      where (value ->> 'seller_id')::uuid = v_seller.seller_id
      order by (value ->> 'product_id')::uuid
    loop
      insert into public.order_items(
        order_id, product_id, seller_id, quantity, unit_price,
        product_name_snapshot, discount_snapshot, line_total, created_at
      ) values (
        v_order_id,
        (v_line ->> 'product_id')::uuid,
        v_seller.seller_id,
        (v_line ->> 'quantity')::int,
        (v_line ->> 'unit_price')::numeric,
        v_line ->> 'product_name',
        0,
        (v_line ->> 'line_total')::numeric,
        now()
      );

      update public.products
      set stock = stock - (v_line ->> 'quantity')::int,
          is_active = (stock - (v_line ->> 'quantity')::int) > 0,
          updated_at = now()
      where id = (v_line ->> 'product_id')::uuid;
    end loop;

    insert into public.order_status_history(
      order_id, from_status, to_status, changed_by, note, created_at
    ) values (v_order_id, null, 'pending', v_uid, 'Order created', now());

    v_delivery_total := v_delivery_total + v_delivery_fee;
  end loop;

  update public.order_groups
  set delivery_total = v_delivery_total,
      grand_total = v_subtotal + v_delivery_total,
      updated_at = now()
  where id = v_group_id;

  delete from public.cart_items
  where cart_id in (select c.id from public.carts c where c.user_id = v_uid);

  return v_group_id;
end;
$$;

revoke all on function public.checkout_create_order_group_v2(uuid,text,text,jsonb,text,jsonb) from public, anon;
grant execute on function public.checkout_create_order_group_v2(uuid,text,text,jsonb,text,jsonb) to authenticated;
