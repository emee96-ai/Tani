-- TANI merchant UX phase 4 — order operations hardening.
-- Keeps the existing public transition contract for customer/admin callers,
-- fixes variant-aware stock restoration, and adds a merchant-only wrapper
-- that records delivery ETA in the status-history note.

alter table public.orders
  add column if not exists stock_restored_at timestamptz;

-- Reassert the order-history read boundary here so this migration is safe on
-- both the full production schema and the compact regression-test bootstrap.
alter table public.order_status_history enable row level security;
revoke all on public.order_status_history from anon;
grant select on public.order_status_history to authenticated;

drop policy if exists tani_order_status_owner_read on public.order_status_history;
create policy tani_order_status_owner_read
on public.order_status_history for select to authenticated
using (
  exists (
    select 1
    from public.orders o
    where o.id = order_status_history.order_id
      and (
        o.customer_id = (select auth.uid())
        or exists (
          select 1 from public.sellers s
          where s.id = o.seller_id and s.user_id = (select auth.uid())
        )
        or public.current_user_role() = any(array['admin'::text,'support'::text])
      )
  )
);

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
  v_uid uuid := auth.uid();
  v_order public.orders%rowtype;
  v_role text;
  v_is_seller boolean := false;
  v_allowed boolean := false;
begin
  if v_uid is null then
    raise exception using errcode = '28000', message = 'Authentication required';
  end if;
  if char_length(coalesce(p_note, '')) > 500 then
    raise exception using errcode = '22023', message = 'Status note is too long';
  end if;
  if p_to_status not in (
    'pending','accepted','preparing','ready','out_for_delivery',
    'delivered','cancelled','rejected','failed'
  ) then
    raise exception using errcode = '22023', message = 'Invalid order status';
  end if;

  select * into v_order
  from public.orders
  where id = p_order_id
  for update;
  if not found then
    raise exception using errcode = 'P0001', message = 'Order not found';
  end if;

  select public.current_user_role() into v_role;
  select exists(
    select 1
    from public.sellers s
    where s.id = v_order.seller_id and s.user_id = v_uid
  ) into v_is_seller;

  if v_order.status in ('delivered','cancelled','rejected','failed') then
    raise exception using errcode = 'P0001', message = 'Order is already in a final state';
  end if;

  if v_role = 'admin' then
    v_allowed := true;
  elsif v_order.customer_id = v_uid then
    v_allowed := (v_order.status = 'pending' and p_to_status = 'cancelled');
  elsif v_is_seller then
    v_allowed := case v_order.status
      when 'pending' then p_to_status in ('accepted','rejected')
      when 'accepted' then p_to_status in ('preparing','cancelled')
      when 'preparing' then p_to_status in ('ready','cancelled')
      when 'ready' then p_to_status in ('out_for_delivery','cancelled')
      when 'out_for_delivery' then p_to_status in ('delivered','failed')
      else false
    end;
  end if;
  if not v_allowed then
    raise exception using errcode = 'P0001', message = 'Status transition is not allowed';
  end if;

  if p_to_status in ('cancelled','rejected','failed')
     and v_order.stock_restored_at is null then
    -- Variant orders reserve stock from product_variants. Restore that row,
    -- not the base product stock. variant_snapshot stores the immutable id.
    update public.product_variants v
    set stock = v.stock + oi.quantity,
        updated_at = now()
    from public.order_items oi
    where oi.order_id = v_order.id
      and oi.product_id = v.product_id
      and coalesce(oi.variant_snapshot ->> 'id', '') = v.id::text;

    -- Base-product lines do not have a variant id in their snapshot.
    update public.products p
    set stock = p.stock + oi.quantity,
        is_active = case when p.stock = 0 then true else p.is_active end,
        updated_at = now()
    from public.order_items oi
    where oi.order_id = v_order.id
      and oi.product_id = p.id
      and coalesce(oi.variant_snapshot ->> 'id', '') = '';

    update public.orders
    set stock_restored_at = now()
    where id = v_order.id;
  end if;

  update public.orders
  set status = p_to_status,
      payment_status = case
        when p_to_status = 'delivered' and payment_method = 'cod' then 'paid'
        else payment_status
      end,
      updated_at = now()
  where id = v_order.id;

  insert into public.order_status_history(
    order_id, from_status, to_status, changed_by, note, created_at
  ) values (
    v_order.id,
    v_order.status,
    p_to_status,
    v_uid,
    nullif(trim(coalesce(p_note, '')), ''),
    now()
  );

  return p_to_status;
end;
$$;

revoke all on function public.transition_order_status(uuid,text,text) from public, anon;
grant execute on function public.transition_order_status(uuid,text,text) to authenticated;

create or replace function public.merchant_transition_order_status(
  p_order_id uuid,
  p_to_status text,
  p_note text default null,
  p_estimated_minutes integer default null
)
returns text
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_is_seller boolean := false;
  v_note text := nullif(trim(coalesce(p_note, '')), '');
  v_result text;
begin
  if v_uid is null then
    raise exception using errcode = '28000', message = 'Authentication required';
  end if;

  select exists(
    select 1
    from public.orders o
    join public.sellers s on s.id = o.seller_id
    where o.id = p_order_id and s.user_id = v_uid
  ) into v_is_seller;
  if not v_is_seller then
    raise exception using errcode = '42501', message = 'Only this order seller can update it';
  end if;

  if p_to_status in ('rejected','cancelled','failed')
     and coalesce(char_length(v_note), 0) < 3 then
    raise exception using errcode = '22023', message = 'A reason is required for this status';
  end if;

  if p_to_status = 'out_for_delivery' then
    if p_estimated_minutes is null or p_estimated_minutes not between 1 and 1440 then
      raise exception using errcode = '22023', message = 'A valid delivery ETA is required';
    end if;
    if coalesce(char_length(v_note), 0) > 350 then
      raise exception using errcode = '22023', message = 'Delivery note is too long';
    end if;
    v_note := 'الوقت المتوقع للوصول: ' || p_estimated_minutes || ' دقيقة' ||
      case when v_note is null then '' else E'\n' || v_note end;
  elsif p_estimated_minutes is not null then
    raise exception using errcode = '22023', message = 'Delivery ETA is only valid when the order leaves for delivery';
  end if;

  v_result := public.transition_order_status(p_order_id, p_to_status, v_note);
  return v_result;
end;
$$;

revoke all on function public.merchant_transition_order_status(uuid,text,text,integer) from public, anon;
grant execute on function public.merchant_transition_order_status(uuid,text,text,integer) to authenticated;
