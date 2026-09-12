-- TANI maintenance v1 — authoritative cart quote
-- Run after phase3_commerce.sql.

create or replace function public.quote_cart(p_items jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_item jsonb;
  v_product record;
  v_product_id uuid;
  v_qty int;
  v_subtotal numeric := 0;
  v_delivery_total numeric := 0;
  v_delivery_fee numeric;
  v_items jsonb := '[]'::jsonb;
  v_warnings jsonb := '[]'::jsonb;
  v_seen_sellers uuid[] := array[]::uuid[];
begin
  if v_uid is null then
    raise exception 'Authentication required';
  end if;

  if not exists (
    select 1 from public.profiles p
    where p.id = v_uid and p.is_active = true and p.deleted_at is null
  ) then
    raise exception 'Account is not active';
  end if;

  if jsonb_typeof(coalesce(p_items, '[]'::jsonb)) <> 'array' then
    raise exception 'Invalid cart';
  end if;

  if jsonb_array_length(coalesce(p_items, '[]'::jsonb)) = 0 then
    raise exception 'Cart is empty';
  end if;

  if jsonb_array_length(p_items) > 100 then
    raise exception 'Cart has too many items';
  end if;

  for v_item in
    select jsonb_build_object(
      'product_id', product_id::text,
      'quantity', sum(quantity)::int
    )
    from (
      select
        (x.value ->> 'product_id')::uuid as product_id,
        greatest(1, least(99, coalesce((x.value ->> 'quantity')::int, 1))) as quantity
      from jsonb_array_elements(p_items) x(value)
      where x.value ? 'product_id'
    ) q
    group by product_id
    order by product_id
  loop
    v_product_id := (v_item ->> 'product_id')::uuid;
    v_qty := (v_item ->> 'quantity')::int;

    select
      p.id,
      p.seller_id,
      p.name,
      p.price,
      p.stock,
      p.is_active,
      s.verification_status
    into v_product
    from public.products p
    join public.sellers s on s.id = p.seller_id
    where p.id = v_product_id;

    if not found then
      v_warnings := v_warnings || jsonb_build_array('A product no longer exists');
      continue;
    end if;

    if not v_product.is_active or v_product.verification_status <> 'approved' then
      v_warnings := v_warnings || jsonb_build_array(
        format('%s is not available', v_product.name)
      );
    elsif v_product.stock < v_qty then
      v_warnings := v_warnings || jsonb_build_array(
        format('Insufficient stock for %s', v_product.name)
      );
    end if;

    v_items := v_items || jsonb_build_array(
      jsonb_build_object(
        'product_id', v_product.id,
        'seller_id', v_product.seller_id,
        'name', v_product.name,
        'unit_price', v_product.price,
        'quantity', v_qty,
        'stock', v_product.stock,
        'available', (
          v_product.is_active
          and v_product.verification_status = 'approved'
          and v_product.stock >= v_qty
        ),
        'line_total', v_product.price * v_qty
      )
    );

    v_subtotal := v_subtotal + (v_product.price * v_qty);

    if not (v_product.seller_id = any(v_seen_sellers)) then
      select ds.base_fee
      into v_delivery_fee
      from public.delivery_settings ds
      where ds.seller_id = v_product.seller_id
        and ds.is_active = true
      limit 1;

      if not found then
        v_warnings := v_warnings || jsonb_build_array(
          format('Delivery settings are incomplete for seller %s', v_product.seller_id)
        );
        v_delivery_fee := 0;
      end if;

      v_delivery_total := v_delivery_total + coalesce(v_delivery_fee, 0);
      v_seen_sellers := array_append(v_seen_sellers, v_product.seller_id);
    end if;
  end loop;

  return jsonb_build_object(
    'subtotal', v_subtotal,
    'delivery_total', v_delivery_total,
    'discount_total', 0,
    'grand_total', v_subtotal + v_delivery_total,
    'items', v_items,
    'warnings', v_warnings
  );
end;
$$;

revoke all on function public.quote_cart(jsonb) from public, anon;
grant execute on function public.quote_cart(jsonb) to authenticated;
