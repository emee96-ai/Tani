-- TANI Phase 3 — Commerce
-- Cart, multi-merchant checkout, addresses, COD, order integrity/state machine.
-- Designed to run after the base schema + Phase 1/2 migrations.

alter table public.order_groups
  add column if not exists idempotency_key text,
  add column if not exists address_id uuid null references public.addresses(id) on delete set null,
  add column if not exists address_snapshot jsonb,
  add column if not exists customer_note text;

create unique index if not exists order_groups_customer_idempotency_uidx
  on public.order_groups(customer_id, idempotency_key)
  where idempotency_key is not null;
create index if not exists order_groups_customer_created_idx
  on public.order_groups(customer_id, created_at desc);

alter table public.orders
  add column if not exists customer_name_snapshot text,
  add column if not exists store_name_snapshot text,
  add column if not exists address_snapshot jsonb,
  add column if not exists customer_note text,
  add column if not exists stock_restored_at timestamptz;

create index if not exists orders_group_created_idx on public.orders(order_group_id, created_at);
create index if not exists orders_customer_created_idx on public.orders(customer_id, created_at desc);
create index if not exists orders_seller_status_idx on public.orders(seller_id, status, created_at desc);
create index if not exists order_status_history_order_created_idx on public.order_status_history(order_id, created_at);
create index if not exists cart_items_product_id_idx on public.cart_items(product_id);
create index if not exists cart_items_variant_id_idx on public.cart_items(variant_id);

alter table public.orders drop constraint if exists orders_status_check;
alter table public.orders add constraint orders_status_check
  check (status = any(array[
    'pending'::text,'accepted'::text,'preparing'::text,'ready'::text,
    'out_for_delivery'::text,'delivered'::text,'cancelled'::text,
    'rejected'::text,'failed'::text
  ]));

alter table public.order_groups drop constraint if exists order_groups_status_check;
alter table public.order_groups add constraint order_groups_status_check
  check (status = any(array[
    'pending'::text,'confirmed'::text,'processing'::text,
    'out_for_delivery'::text,'delivered'::text,'cancelled'::text
  ]));

alter table public.cart_items drop constraint if exists cart_items_quantity_max_check;
alter table public.cart_items add constraint cart_items_quantity_max_check check (quantity <= 99);
alter table public.order_items drop constraint if exists order_items_quantity_max_check;
alter table public.order_items add constraint order_items_quantity_max_check check (quantity <= 99);

with ranked as (
  select id, row_number() over (partition by user_id order by updated_at desc, created_at desc, id) as rn
  from public.addresses where is_default = true
)
update public.addresses a
set is_default = false, updated_at = now()
from ranked r where a.id = r.id and r.rn > 1;

create unique index if not exists addresses_one_default_per_user_uidx
  on public.addresses(user_id) where is_default = true;

create or replace function private.normalize_default_address()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if new.is_default then
    update public.addresses
    set is_default = false, updated_at = now()
    where user_id = new.user_id and id <> new.id and is_default = true;
  end if;
  return new;
end;
$$;
revoke all on function private.normalize_default_address() from public;
drop trigger if exists trg_normalize_default_address on public.addresses;
create trigger trg_normalize_default_address
before insert or update of is_default on public.addresses
for each row execute function private.normalize_default_address();

create or replace function private.refresh_order_group_status(p_group_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_total int; v_pending int; v_delivered int; v_cancelled int;
  v_out int; v_working int; v_accepted int; v_unpaid int; v_status text;
begin
  select
    count(*)::int,
    count(*) filter (where status = 'pending')::int,
    count(*) filter (where status = 'delivered')::int,
    count(*) filter (where status in ('cancelled','rejected','failed'))::int,
    count(*) filter (where status = 'out_for_delivery')::int,
    count(*) filter (where status in ('preparing','ready'))::int,
    count(*) filter (where status = 'accepted')::int,
    count(*) filter (where payment_status <> 'paid')::int
  into v_total, v_pending, v_delivered, v_cancelled, v_out, v_working, v_accepted, v_unpaid
  from public.orders where order_group_id = p_group_id;

  if v_total = 0 then return;
  elsif v_delivered + v_cancelled = v_total then
    v_status := case when v_delivered > 0 then 'delivered' else 'cancelled' end;
  elsif v_out > 0 then v_status := 'out_for_delivery';
  elsif v_working > 0 or v_delivered > 0 then v_status := 'processing';
  elsif v_pending > 0 then v_status := 'pending';
  elsif v_accepted > 0 then v_status := 'confirmed';
  else v_status := 'pending';
  end if;

  update public.order_groups
  set status = v_status,
      payment_status = case when v_status = 'delivered' and v_unpaid = 0 then 'paid' else payment_status end,
      updated_at = now()
  where id = p_group_id;
end;
$$;
revoke all on function private.refresh_order_group_status(uuid) from public;

create or replace function private.on_order_status_changed()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if new.status is distinct from old.status and new.order_group_id is not null then
    perform private.refresh_order_group_status(new.order_group_id);
  end if;
  return new;
end;
$$;
revoke all on function private.on_order_status_changed() from public;
drop trigger if exists trg_refresh_order_group_status on public.orders;
create trigger trg_refresh_order_group_status
after update of status on public.orders
for each row execute function private.on_order_status_changed();

-- Only the owner, seller involved, admin/support may read status history.
drop policy if exists tani_order_status_owner_read on public.order_status_history;
create policy tani_order_status_owner_read
on public.order_status_history for select to authenticated
using (
  exists (
    select 1 from public.orders o
    where o.id = order_status_history.order_id
      and (
        o.customer_id = (select auth.uid())
        or exists (select 1 from public.sellers s where s.id = o.seller_id and s.user_id = (select auth.uid()))
        or public.current_user_role() = any(array['admin'::text,'support'::text])
      )
  )
);

-- Sensitive order writes must go through RPCs, not direct table writes.
revoke all on public.addresses, public.carts, public.cart_items,
  public.order_groups, public.orders, public.order_items, public.order_status_history from anon;
revoke insert, update, delete, truncate on public.carts, public.cart_items,
  public.order_groups, public.orders, public.order_items, public.order_status_history from authenticated;
grant select, insert, update, delete on public.addresses to authenticated;
grant select on public.carts, public.cart_items, public.order_groups,
  public.orders, public.order_items, public.order_status_history to authenticated;
revoke all on function public.place_order(text,text,jsonb) from public, anon, authenticated;

create or replace view public.my_cart_items
with (security_invoker = true)
as
select
  ci.id as cart_item_id,
  ci.cart_id,
  ci.product_id,
  ci.variant_id,
  ci.quantity,
  p.seller_id,
  p.category_id,
  p.name,
  p.description,
  p.price,
  p.stock,
  coalesce(p.image, pi.storage_path) as image,
  coalesce(st.name, s.store_name) as store_name,
  coalesce(ds.base_fee, 0::numeric) as delivery_fee,
  ds.delivery_area,
  ds.estimated_minutes,
  ci.updated_at
from public.cart_items ci
join public.carts c on c.id = ci.cart_id
join public.products p on p.id = ci.product_id
join public.sellers s on s.id = p.seller_id
left join public.stores st on st.seller_id = p.seller_id and st.is_active = true
left join lateral (
  select pimg.storage_path from public.product_images pimg
  where pimg.product_id = p.id
  order by pimg.is_primary desc, pimg.sort_order asc, pimg.created_at asc limit 1
) pi on true
left join public.delivery_settings ds on ds.seller_id = p.seller_id and ds.is_active = true
where c.user_id = (select auth.uid())
  and p.is_active = true
  and s.verification_status = 'approved';
grant select on public.my_cart_items to authenticated;

create or replace function public.sync_my_cart(p_items jsonb)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid(); v_cart_id uuid; v_item jsonb; v_product record;
  v_qty int; v_product_id uuid;
begin
  if v_uid is null then raise exception 'Authentication required'; end if;
  if jsonb_typeof(coalesce(p_items, '[]'::jsonb)) <> 'array' then raise exception 'Invalid cart'; end if;
  if jsonb_array_length(coalesce(p_items, '[]'::jsonb)) > 100 then raise exception 'Cart has too many items'; end if;

  insert into public.carts(user_id, created_at, updated_at)
  values(v_uid, now(), now())
  on conflict (user_id) do update set updated_at = now()
  returning id into v_cart_id;

  delete from public.cart_items where cart_id = v_cart_id;

  for v_item in
    select jsonb_build_object('product_id', product_id::text, 'quantity', sum(quantity)::int)
    from (
      select (x.value ->> 'product_id')::uuid as product_id,
             greatest(1, least(99, coalesce((x.value ->> 'quantity')::int, 1))) as quantity
      from jsonb_array_elements(coalesce(p_items, '[]'::jsonb)) x(value)
      where x.value ? 'product_id'
    ) q
    group by product_id order by product_id
  loop
    v_product_id := (v_item ->> 'product_id')::uuid;
    v_qty := (v_item ->> 'quantity')::int;
    select p.id, p.stock into v_product
    from public.products p join public.sellers s on s.id = p.seller_id
    where p.id = v_product_id and p.is_active = true and s.verification_status = 'approved';
    if not found then raise exception 'Product is not available'; end if;
    if v_product.stock < v_qty then raise exception 'Requested quantity is not available'; end if;
    insert into public.cart_items(cart_id, product_id, quantity, created_at, updated_at)
    values(v_cart_id, v_product_id, v_qty, now(), now());
  end loop;
  return v_cart_id;
end;
$$;
revoke all on function public.sync_my_cart(jsonb) from public, anon;
grant execute on function public.sync_my_cart(jsonb) to authenticated;

create or replace function public.checkout_create_order_group(
  p_address_id uuid,
  p_phone text,
  p_notes text,
  p_items jsonb,
  p_idempotency_key text
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid(); v_existing uuid; v_group_id uuid; v_order_id uuid;
  v_item jsonb; v_line jsonb; v_lines jsonb := '[]'::jsonb;
  v_product record; v_seller record; v_address record; v_profile record;
  v_product_id uuid; v_qty int; v_subtotal numeric := 0; v_delivery_total numeric := 0;
  v_delivery_fee numeric; v_address_text text;
begin
  if v_uid is null then raise exception 'Authentication required'; end if;
  select p.id, p.name into v_profile from public.profiles p
  where p.id = v_uid and p.is_active = true and p.deleted_at is null;
  if not found then raise exception 'Account is not active'; end if;
  if coalesce(char_length(trim(p_idempotency_key)),0) < 16 or char_length(trim(p_idempotency_key)) > 100 then
    raise exception 'Invalid checkout key';
  end if;

  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended(v_uid::text || ':' || trim(p_idempotency_key), 0)
  );
  select og.id into v_existing from public.order_groups og
  where og.customer_id = v_uid and og.idempotency_key = trim(p_idempotency_key) limit 1;
  if found then return v_existing; end if;

  select a.* into v_address from public.addresses a where a.id = p_address_id and a.user_id = v_uid;
  if not found then raise exception 'Address not found'; end if;
  if coalesce(char_length(trim(p_phone)),0) < 7 or char_length(trim(p_phone)) > 30 then raise exception 'Invalid phone number'; end if;
  if char_length(coalesce(p_notes,'')) > 1000 then raise exception 'Order notes are too long'; end if;
  if jsonb_typeof(coalesce(p_items,'[]'::jsonb)) <> 'array' or jsonb_array_length(coalesce(p_items,'[]'::jsonb)) = 0 then
    raise exception 'Cart is empty';
  end if;
  if jsonb_array_length(p_items) > 100 then raise exception 'Cart has too many items'; end if;

  v_address_text := concat_ws(' - ', nullif(trim(v_address.label),''), nullif(trim(v_address.description),''),
    nullif(trim(coalesce(v_address.area,'')),''), nullif(trim(coalesce(v_address.landmark,'')),''));

  for v_item in
    select jsonb_build_object('product_id', product_id::text, 'quantity', sum(quantity)::int)
    from (
      select (x.value ->> 'product_id')::uuid as product_id,
             greatest(1, least(99, coalesce((x.value ->> 'quantity')::int,1))) as quantity
      from jsonb_array_elements(p_items) x(value) where x.value ? 'product_id'
    ) q
    group by product_id order by product_id
  loop
    v_product_id := (v_item ->> 'product_id')::uuid;
    v_qty := (v_item ->> 'quantity')::int;
    select p.id,p.seller_id,p.name,p.price,p.stock,coalesce(st.name,s.store_name) as store_name
    into v_product
    from public.products p
    join public.sellers s on s.id = p.seller_id
    left join public.stores st on st.seller_id = p.seller_id and st.is_active = true
    where p.id = v_product_id and p.is_active = true and s.verification_status = 'approved'
    for update of p;
    if not found then raise exception 'A product in your cart is no longer available'; end if;
    if v_product.stock < v_qty then raise exception 'Insufficient stock for %', v_product.name; end if;

    v_lines := v_lines || jsonb_build_array(jsonb_build_object(
      'product_id',v_product.id::text,'seller_id',v_product.seller_id::text,
      'product_name',v_product.name,'store_name',v_product.store_name,
      'unit_price',v_product.price,'quantity',v_qty,'line_total',v_product.price*v_qty
    ));
    v_subtotal := v_subtotal + (v_product.price*v_qty);
  end loop;
  if jsonb_array_length(v_lines)=0 then raise exception 'Cart is empty'; end if;

  insert into public.order_groups(
    customer_id,subtotal,delivery_total,discount_total,grand_total,payment_method,payment_status,status,
    idempotency_key,address_id,address_snapshot,customer_note,created_at,updated_at
  ) values(
    v_uid,v_subtotal,0,0,v_subtotal,'cod','pending','pending',trim(p_idempotency_key),p_address_id,
    jsonb_build_object('label',v_address.label,'description',v_address.description,'area',v_address.area,
      'landmark',v_address.landmark,'phone',trim(p_phone),'delivery_notes',v_address.delivery_notes),
    nullif(trim(coalesce(p_notes,'')),''),now(),now()
  ) returning id into v_group_id;

  for v_seller in
    select seller_id::uuid as seller_id,max(store_name) as store_name,sum(line_total)::numeric as subtotal
    from jsonb_to_recordset(v_lines) as x(seller_id text,store_name text,line_total numeric)
    group by seller_id order by seller_id
  loop
    select d.base_fee into v_delivery_fee from public.delivery_settings d
    where d.seller_id=v_seller.seller_id and d.is_active=true limit 1;
    if not found then raise exception 'Delivery settings are incomplete for %', v_seller.store_name; end if;
    v_delivery_fee := coalesce(v_delivery_fee,0);

    insert into public.orders(
      customer_id,order_group_id,seller_id,subtotal,delivery_fee,discount,total,status,payment_method,payment_status,
      address,phone,customer_name_snapshot,store_name_snapshot,address_snapshot,customer_note,created_at,updated_at
    ) values(
      v_uid,v_group_id,v_seller.seller_id,v_seller.subtotal,v_delivery_fee,0,v_seller.subtotal+v_delivery_fee,
      'pending','cod','pending',v_address_text,trim(p_phone),v_profile.name,v_seller.store_name,
      jsonb_build_object('label',v_address.label,'description',v_address.description,'area',v_address.area,
        'landmark',v_address.landmark,'phone',trim(p_phone),'delivery_notes',v_address.delivery_notes),
      nullif(trim(coalesce(p_notes,'')),''),now(),now()
    ) returning id into v_order_id;

    for v_line in select value from jsonb_array_elements(v_lines)
      where (value->>'seller_id')::uuid=v_seller.seller_id
      order by (value->>'product_id')::uuid
    loop
      insert into public.order_items(
        order_id,product_id,seller_id,quantity,unit_price,product_name_snapshot,discount_snapshot,line_total,created_at
      ) values(
        v_order_id,(v_line->>'product_id')::uuid,v_seller.seller_id,(v_line->>'quantity')::int,
        (v_line->>'unit_price')::numeric,v_line->>'product_name',0,(v_line->>'line_total')::numeric,now()
      );
      update public.products
      set stock=stock-(v_line->>'quantity')::int,
          is_active=(stock-(v_line->>'quantity')::int)>0,
          updated_at=now()
      where id=(v_line->>'product_id')::uuid;
    end loop;

    insert into public.order_status_history(order_id,from_status,to_status,changed_by,note,created_at)
    values(v_order_id,null,'pending',v_uid,'Order created',now());
    v_delivery_total := v_delivery_total + v_delivery_fee;
  end loop;

  update public.order_groups
  set delivery_total=v_delivery_total,grand_total=v_subtotal+v_delivery_total,updated_at=now()
  where id=v_group_id;

  delete from public.cart_items where cart_id in (select c.id from public.carts c where c.user_id=v_uid);
  return v_group_id;
end;
$$;
revoke all on function public.checkout_create_order_group(uuid,text,text,jsonb,text) from public, anon;
grant execute on function public.checkout_create_order_group(uuid,text,text,jsonb,text) to authenticated;

create or replace function public.transition_order_status(
  p_order_id uuid,
  p_to_status text,
  p_note text default null
)
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid(); v_order public.orders%rowtype; v_role text;
  v_is_seller boolean := false; v_allowed boolean := false;
begin
  if v_uid is null then raise exception 'Authentication required'; end if;
  if char_length(coalesce(p_note,'')) > 500 then raise exception 'Status note is too long'; end if;
  if p_to_status not in ('pending','accepted','preparing','ready','out_for_delivery','delivered','cancelled','rejected','failed') then
    raise exception 'Invalid order status';
  end if;
  select * into v_order from public.orders where id=p_order_id for update;
  if not found then raise exception 'Order not found'; end if;
  select public.current_user_role() into v_role;
  select exists(select 1 from public.sellers s where s.id=v_order.seller_id and s.user_id=v_uid) into v_is_seller;
  if v_order.status in ('delivered','cancelled','rejected','failed') then raise exception 'Order is already in a final state'; end if;

  if v_role='admin' then v_allowed:=true;
  elsif v_order.customer_id=v_uid then v_allowed:=(v_order.status='pending' and p_to_status='cancelled');
  elsif v_is_seller then
    v_allowed := case v_order.status
      when 'pending' then p_to_status in ('accepted','rejected')
      when 'accepted' then p_to_status in ('preparing','cancelled')
      when 'preparing' then p_to_status in ('ready','cancelled')
      when 'ready' then p_to_status in ('out_for_delivery','cancelled')
      when 'out_for_delivery' then p_to_status in ('delivered','failed')
      else false end;
  end if;
  if not v_allowed then raise exception 'Status transition is not allowed'; end if;

  if p_to_status in ('cancelled','rejected','failed') and v_order.stock_restored_at is null then
    update public.products p
    set stock=p.stock+oi.quantity,
        is_active=case when p.stock=0 then true else p.is_active end,
        updated_at=now()
    from public.order_items oi
    where oi.order_id=v_order.id and oi.product_id=p.id;
    update public.orders set stock_restored_at=now() where id=v_order.id;
  end if;

  update public.orders
  set status=p_to_status,
      payment_status=case when p_to_status='delivered' and payment_method='cod' then 'paid' else payment_status end,
      updated_at=now()
  where id=v_order.id;

  insert into public.order_status_history(order_id,from_status,to_status,changed_by,note,created_at)
  values(v_order.id,v_order.status,p_to_status,v_uid,nullif(trim(coalesce(p_note,'')),''),now());
  return p_to_status;
end;
$$;
revoke all on function public.transition_order_status(uuid,text,text) from public, anon;
grant execute on function public.transition_order_status(uuid,text,text) to authenticated;

create or replace function public.customer_cancel_order_group(
  p_order_group_id uuid,
  p_note text default null
)
returns int
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid(); v_order record; v_count int := 0;
begin
  if v_uid is null then raise exception 'Authentication required'; end if;
  if not exists(select 1 from public.order_groups og where og.id=p_order_group_id and og.customer_id=v_uid) then
    raise exception 'Order group not found';
  end if;
  if exists(select 1 from public.orders o where o.order_group_id=p_order_group_id and o.customer_id=v_uid and o.status<>'pending') then
    raise exception 'This order can no longer be cancelled as a group';
  end if;
  for v_order in
    select o.id from public.orders o
    where o.order_group_id=p_order_group_id and o.customer_id=v_uid and o.status='pending'
    order by o.id
  loop
    perform public.transition_order_status(v_order.id,'cancelled',p_note);
    v_count:=v_count+1;
  end loop;
  if v_count=0 then raise exception 'No cancellable orders found'; end if;
  perform private.refresh_order_group_status(p_order_group_id);
  return v_count;
end;
$$;
revoke all on function public.customer_cancel_order_group(uuid,text) from public, anon;
grant execute on function public.customer_cancel_order_group(uuid,text) to authenticated;
