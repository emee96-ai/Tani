-- Fix recursive RLS between orders and order_items.
-- Applied to production as migration fix_recursive_order_rls on 2026-09-14.

drop policy if exists tani_orders_own_read on public.orders;
create policy tani_orders_own_read
on public.orders
for select
to authenticated
using (
  customer_id = (select auth.uid())
  or exists (
    select 1
    from public.sellers s
    where s.id = orders.seller_id
      and s.user_id = (select auth.uid())
  )
);

drop policy if exists tani_order_items_own_read on public.order_items;
create policy tani_order_items_own_read
on public.order_items
for select
to authenticated
using (
  exists (
    select 1
    from public.orders o
    where o.id = order_items.order_id
      and o.customer_id = (select auth.uid())
  )
  or exists (
    select 1
    from public.sellers s
    where s.id = order_items.seller_id
      and s.user_id = (select auth.uid())
  )
);
