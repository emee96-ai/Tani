-- TANI Phase 2 — Marketplace Core
-- Applied to project: tani / sihttimibjzoahvwuwbm
-- 2026-09-05

alter table public.stores
  add column if not exists seller_id uuid null references public.sellers(id) on delete set null;
create index if not exists stores_seller_id_idx on public.stores(seller_id);

update public.stores st
set seller_id = mp.seller_id
from public.merchant_profiles mp
where st.merchant_id = mp.id
  and st.seller_id is null
  and mp.seller_id is not null;

-- Delivery details are customer-facing, but only for active settings on approved sellers.
drop policy if exists tani_delivery_settings_public_read on public.delivery_settings;
create policy tani_delivery_settings_public_read
on public.delivery_settings for select to anon, authenticated
using (
  is_active = true
  and exists (
    select 1 from public.sellers s
    where s.id = delivery_settings.seller_id
      and s.verification_status = 'approved'
  )
);
grant select on public.delivery_settings to anon, authenticated;

-- Store assets are public to read; merchants can only write inside their user folder.
insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values('store-assets','store-assets',true,5242880,array['image/jpeg','image/png','image/webp']::text[])
on conflict(id) do update set
  public=excluded.public,
  file_size_limit=excluded.file_size_limit,
  allowed_mime_types=excluded.allowed_mime_types;

drop policy if exists tani_store_assets_public_read on storage.objects;
create policy tani_store_assets_public_read on storage.objects
for select to public using(bucket_id='store-assets');

drop policy if exists tani_store_assets_owner_insert on storage.objects;
create policy tani_store_assets_owner_insert on storage.objects
for insert to authenticated
with check(bucket_id='store-assets' and (storage.foldername(name))[1]=(select auth.uid())::text);

drop policy if exists tani_store_assets_owner_update on storage.objects;
create policy tani_store_assets_owner_update on storage.objects
for update to authenticated
using(bucket_id='store-assets' and (storage.foldername(name))[1]=(select auth.uid())::text)
with check(bucket_id='store-assets' and (storage.foldername(name))[1]=(select auth.uid())::text);

drop policy if exists tani_store_assets_owner_delete on storage.objects;
create policy tani_store_assets_owner_delete on storage.objects
for delete to authenticated
using(bucket_id='store-assets' and (storage.foldername(name))[1]=(select auth.uid())::text);

-- Prevent a merchant from linking a store to another seller account.
create or replace function private.validate_store_seller_link()
returns trigger language plpgsql set search_path='' as $$
begin
  if public.current_user_role() = 'admin' then return new; end if;
  if new.seller_id is null then return new; end if;
  if not exists (
    select 1
    from public.merchant_profiles mp
    join public.sellers s on s.id=mp.seller_id
    where mp.id=new.merchant_id
      and mp.user_id=auth.uid()
      and s.id=new.seller_id
      and s.user_id=auth.uid()
  ) then
    raise exception 'Store seller link is not owned by this account';
  end if;
  return new;
end;
$$;
revoke all on function private.validate_store_seller_link() from public;
drop trigger if exists trg_validate_store_seller_link on public.stores;
create trigger trg_validate_store_seller_link
before insert or update of merchant_id,seller_id on public.stores
for each row execute function private.validate_store_seller_link();

-- Safe customer-facing product card. security_invoker keeps underlying RLS active.
create or replace view public.marketplace_product_cards
with (security_invoker=true) as
select
  p.id,p.seller_id,p.category_id,c.name as category_name,p.name,p.description,
  p.price,p.stock,coalesce(p.image,pi.storage_path) as image,p.created_at,
  coalesce(st.name,s.store_name) as store_name,s.verification_status,
  coalesce(round(avg(r.rating)::numeric,2),0::numeric) as average_rating,
  count(r.id)::int as review_count,
  coalesce(ds.base_fee,0::numeric) as delivery_fee,ds.delivery_area,ds.estimated_minutes
from public.products p
join public.sellers s on s.id=p.seller_id
left join public.stores st on st.seller_id=p.seller_id and st.is_active=true
left join public.categories c on c.id=p.category_id
left join lateral (
  select pimg.storage_path from public.product_images pimg
  where pimg.product_id=p.id
  order by pimg.is_primary desc,pimg.sort_order asc,pimg.created_at asc limit 1
) pi on true
left join public.reviews r on r.product_id=p.id
left join lateral (
  select d.base_fee,d.delivery_area,d.estimated_minutes
  from public.delivery_settings d
  where d.seller_id=p.seller_id and d.is_active=true
  order by d.updated_at desc,d.created_at desc limit 1
) ds on true
where p.is_active=true and s.verification_status='approved'
group by p.id,p.seller_id,p.category_id,c.name,p.name,p.description,p.price,p.stock,
  p.image,pi.storage_path,p.created_at,st.name,s.store_name,s.verification_status,
  ds.base_fee,ds.delivery_area,ds.estimated_minutes;
grant select on public.marketplace_product_cards to anon,authenticated;

-- Safe store discovery card; no merchant phone/user identity is exposed.
create or replace view public.marketplace_store_cards
with (security_invoker=true) as
select
  st.id,st.merchant_id,st.seller_id,st.name,st.description,st.logo_url,st.cover_url,
  st.city,st.area,st.is_open,st.created_at,s.verification_status,
  coalesce(ds.base_fee,0::numeric) as delivery_fee,ds.delivery_area,ds.estimated_minutes,
  count(distinct p.id)::int as product_count,
  coalesce(round(avg(r.rating)::numeric,2),0::numeric) as average_rating,
  count(r.id)::int as review_count
from public.stores st
join public.sellers s on s.id=st.seller_id
left join lateral (
  select d.base_fee,d.delivery_area,d.estimated_minutes
  from public.delivery_settings d
  where d.seller_id=st.seller_id and d.is_active=true
  order by d.updated_at desc,d.created_at desc limit 1
) ds on true
left join public.products p on p.seller_id=st.seller_id and p.is_active=true
left join public.reviews r on r.product_id=p.id
where st.is_active=true and s.verification_status='approved'
group by st.id,st.merchant_id,st.seller_id,st.name,st.description,st.logo_url,st.cover_url,
  st.city,st.area,st.is_open,st.created_at,s.verification_status,
  ds.base_fee,ds.delivery_area,ds.estimated_minutes;
grant select on public.marketplace_store_cards to anon,authenticated;

grant select on public.featured_placements to anon,authenticated;

-- Search/performance indexes used by Marketplace Core.
create index if not exists idx_products_search_description_trgm
  on public.products using gin (description extensions.gin_trgm_ops);
create index if not exists idx_sellers_store_name_trgm
  on public.sellers using gin (store_name extensions.gin_trgm_ops);
create index if not exists idx_stores_search_name_trgm
  on public.stores using gin (name extensions.gin_trgm_ops);
create index if not exists idx_stores_search_description_trgm
  on public.stores using gin (description extensions.gin_trgm_ops);
create index if not exists idx_stores_city_area on public.stores(city,area);
create index if not exists idx_featured_placements_active_product
  on public.featured_placements(is_active,product_id,starts_at,ends_at);
create index if not exists app_errors_user_created_idx
  on public.app_errors(user_id,created_at desc);
