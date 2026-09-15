-- Optimize hot RLS policies by evaluating identity once per statement.
-- Access rules are preserved; owner-only tables are narrowed from PUBLIC to authenticated.

drop policy if exists tani_addresses_owner_all on public.addresses;
create policy tani_addresses_owner_all
on public.addresses for all to authenticated
using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));

drop policy if exists tani_carts_owner_all on public.carts;
create policy tani_carts_owner_all
on public.carts for all to authenticated
using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));

drop policy if exists tani_cart_items_owner_all on public.cart_items;
create policy tani_cart_items_owner_all
on public.cart_items for all to authenticated
using (
  exists (
    select 1 from public.carts c
    where c.id = cart_items.cart_id
      and c.user_id = (select auth.uid())
  )
)
with check (
  exists (
    select 1 from public.carts c
    where c.id = cart_items.cart_id
      and c.user_id = (select auth.uid())
  )
);

drop policy if exists tani_order_groups_owner_read on public.order_groups;
create policy tani_order_groups_owner_read
on public.order_groups for select to authenticated
using (
  customer_id = (select auth.uid())
  or (select public.current_user_role()) = any (array['admin'::text, 'support'::text])
);

drop policy if exists tani_merchant_profile_owner_insert on public.merchant_profiles;
create policy tani_merchant_profile_owner_insert
on public.merchant_profiles for insert to authenticated
with check (user_id = (select auth.uid()));

drop policy if exists tani_merchant_profile_owner_read on public.merchant_profiles;
create policy tani_merchant_profile_owner_read
on public.merchant_profiles for select to authenticated
using (user_id = (select auth.uid()));

drop policy if exists tani_merchant_profile_owner_update on public.merchant_profiles;
create policy tani_merchant_profile_owner_update
on public.merchant_profiles for update to authenticated
using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));

drop policy if exists tani_store_public_read on public.stores;
create policy tani_store_public_read
on public.stores for select to public
using (
  is_active = true
  or exists (
    select 1 from public.merchant_profiles m
    where m.id = stores.merchant_id
      and m.user_id = (select auth.uid())
  )
  or (select public.current_user_role()) = 'admin'
);

drop policy if exists tani_variants_public_read on public.product_variants;
create policy tani_variants_public_read
on public.product_variants for select to public
using (
  is_active = true
  or exists (
    select 1
    from public.products p
    join public.sellers s on s.id = p.seller_id
    where p.id = product_variants.product_id
      and s.user_id = (select auth.uid())
  )
  or (select public.current_user_role()) = 'admin'
);
