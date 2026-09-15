-- TANI product media gallery support
-- Adds video metadata to product_images while preserving existing image rows.

alter table public.product_images
  add column if not exists media_type text not null default 'image';

alter table public.product_images
  add column if not exists mime_type text null;

do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'product_images_media_type_check'
  ) then
    alter table public.product_images
      add constraint product_images_media_type_check
      check (media_type in ('image','video'));
  end if;
end $$;

update public.product_images
set media_type = 'image'
where media_type is null;

update storage.buckets
set
  file_size_limit = 31457280,
  allowed_mime_types = array[
    'image/jpeg',
    'image/png',
    'image/webp',
    'video/mp4',
    'video/webm'
  ]::text[]
where id = 'product-images';

-- Product cards must always use an image thumbnail, never a video object.
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
  select pimg.storage_path
  from public.product_images pimg
  where pimg.product_id=p.id
    and pimg.media_type='image'
  order by pimg.is_primary desc,pimg.sort_order asc,pimg.created_at asc
  limit 1
) pi on true
left join public.reviews r on r.product_id=p.id
left join lateral (
  select d.base_fee,d.delivery_area,d.estimated_minutes
  from public.delivery_settings d
  where d.seller_id=p.seller_id and d.is_active=true
  order by d.updated_at desc,d.created_at desc
  limit 1
) ds on true
where p.is_active=true and s.verification_status='approved'
group by p.id,p.seller_id,p.category_id,c.name,p.name,p.description,p.price,p.stock,
  p.image,pi.storage_path,p.created_at,st.name,s.store_name,s.verification_status,
  ds.base_fee,ds.delivery_area,ds.estimated_minutes;

grant select on public.marketplace_product_cards to anon, authenticated;
