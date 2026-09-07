-- TANI Phase 4 — Merchant Operations (reproducible fresh deployment)
-- Run AFTER phase3_commerce.sql.
-- Reconstructed against the live TANI database on 2026-09-05.

-- ---------------------------------------------------------------------------
-- 1. Merchant onboarding data + identity storage
-- ---------------------------------------------------------------------------

alter table public.merchant_profiles drop constraint if exists merchant_profiles_verification_status_check;
alter table public.merchant_profiles add constraint merchant_profiles_verification_status_check
  check (verification_status in ('pending','approved','rejected','changes_requested','suspended'));

create table if not exists public.merchant_identity_documents (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid not null references public.merchant_profiles(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  document_type text not null default 'national_id'
    check (document_type in ('national_id','passport','other')),
  storage_path text not null,
  created_at timestamptz not null default now(),
  reviewed_at timestamptz,
  unique(merchant_id, storage_path)
);
create index if not exists merchant_identity_documents_merchant_idx
  on public.merchant_identity_documents(merchant_id,created_at desc);
create index if not exists merchant_identity_documents_user_idx
  on public.merchant_identity_documents(user_id,created_at desc);

alter table public.merchant_identity_documents enable row level security;

drop policy if exists tani_merchant_docs_owner_read on public.merchant_identity_documents;
create policy tani_merchant_docs_owner_read
on public.merchant_identity_documents for select to authenticated
using (
  user_id=(select auth.uid())
  or public.current_user_role()=any(array['admin'::text,'support'::text])
);

insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values(
  'merchant-private','merchant-private',false,8388608,
  array['image/jpeg','image/png','image/webp','application/pdf']::text[]
)
on conflict(id) do update set
  public=excluded.public,
  file_size_limit=excluded.file_size_limit,
  allowed_mime_types=excluded.allowed_mime_types;

-- Identity documents are private: owner + admin/support only.
drop policy if exists tani_merchant_private_owner_insert on storage.objects;
create policy tani_merchant_private_owner_insert
on storage.objects for insert to authenticated
with check (
  bucket_id='merchant-private'
  and (storage.foldername(name))[1]=(select auth.uid())::text
  and not exists(
    select 1 from public.merchant_profiles mp
    where mp.user_id=(select auth.uid())
      and mp.verification_status in ('approved','suspended')
  )
);

drop policy if exists tani_merchant_private_owner_read on storage.objects;
create policy tani_merchant_private_owner_read
on storage.objects for select to authenticated
using (
  bucket_id='merchant-private'
  and (
    (storage.foldername(name))[1]=(select auth.uid())::text
    or public.current_user_role()=any(array['admin'::text,'support'::text])
  )
);

drop policy if exists tani_merchant_private_owner_delete on storage.objects;
create policy tani_merchant_private_owner_delete
on storage.objects for delete to authenticated
using (
  bucket_id='merchant-private'
  and (storage.foldername(name))[1]=(select auth.uid())::text
  and not exists(
    select 1 from public.merchant_profiles mp
    where mp.user_id=(select auth.uid())
      and mp.verification_status in ('approved','suspended')
  )
);

-- ---------------------------------------------------------------------------
-- 2. Merchant application workflow
-- ---------------------------------------------------------------------------

create or replace function public.submit_merchant_application(
  p_business_name text,
  p_description text,
  p_phone text,
  p_whatsapp text,
  p_category_id uuid,
  p_store_name text,
  p_store_description text,
  p_city text,
  p_area text,
  p_delivery_area text,
  p_delivery_fee numeric,
  p_estimated_minutes integer,
  p_identity_path text,
  p_document_type text,
  p_accept_policies boolean,
  p_policy_version text default '2026-09'
)
returns uuid
language plpgsql
security definer
set search_path=''
as $$
declare
  v_uid uuid := auth.uid();
  v_merchant_id uuid;
  v_auth_phone text;
  v_phone_confirmed_at timestamptz;
  v_status text;
begin
  if v_uid is null then raise exception 'Authentication required'; end if;

  select u.phone,u.phone_confirmed_at
  into v_auth_phone,v_phone_confirmed_at
  from auth.users u where u.id=v_uid;

  if v_phone_confirmed_at is null
     or regexp_replace(coalesce(v_auth_phone,''),'[^0-9]','','g')
        <> regexp_replace(coalesce(p_phone,''),'[^0-9]','','g') then
    raise exception 'Phone verification is required before submitting';
  end if;

  if char_length(trim(coalesce(p_business_name,''))) not between 2 and 120 then
    raise exception 'Business name is required';
  end if;
  if char_length(trim(coalesce(p_store_name,''))) not between 2 and 120 then
    raise exception 'Store name is required';
  end if;
  if char_length(trim(coalesce(p_description,''))) > 1000
     or char_length(trim(coalesce(p_store_description,''))) > 1000 then
    raise exception 'Description is too long';
  end if;
  if char_length(trim(coalesce(p_phone,''))) not between 7 and 30 then
    raise exception 'Invalid phone number';
  end if;
  if char_length(trim(coalesce(p_city,''))) not between 2 and 100 then
    raise exception 'City is required';
  end if;
  if char_length(trim(coalesce(p_delivery_area,''))) not between 2 and 300 then
    raise exception 'Delivery area is required';
  end if;
  if p_delivery_fee is null or p_delivery_fee < 0 then
    raise exception 'Invalid delivery fee';
  end if;
  if p_estimated_minutes is not null and p_estimated_minutes not between 1 and 1440 then
    raise exception 'Invalid delivery estimate';
  end if;
  if not coalesce(p_accept_policies,false) then
    raise exception 'Merchant policies must be accepted';
  end if;
  if not exists(
    select 1 from public.categories c
    where c.id=p_category_id and c.is_active=true
  ) then
    raise exception 'Store category is invalid';
  end if;
  if p_document_type not in ('national_id','passport','other') then
    raise exception 'Invalid identity document type';
  end if;
  if coalesce(p_identity_path,'')=''
     or split_part(p_identity_path,'/',1)<>v_uid::text then
    raise exception 'Identity document is required';
  end if;
  if not exists(
    select 1 from storage.objects o
    where o.bucket_id='merchant-private'
      and o.name=p_identity_path
      and o.owner_id=v_uid::text
  ) then
    raise exception 'Identity document was not uploaded';
  end if;

  select mp.id,mp.verification_status
  into v_merchant_id,v_status
  from public.merchant_profiles mp
  where mp.user_id=v_uid;

  if v_status in ('approved','suspended') then
    raise exception 'This merchant profile cannot be resubmitted';
  end if;

  insert into public.merchant_profiles(
    user_id,business_name,description,phone,whatsapp,category_id,
    store_name,store_description,city,area,delivery_area,delivery_fee,estimated_minutes,
    phone_verified_at,policies_accepted_at,policy_version,submitted_at,
    verification_status,review_note,requested_changes_at,updated_at
  )
  values(
    v_uid,trim(p_business_name),trim(coalesce(p_description,'')),trim(p_phone),
    nullif(trim(coalesce(p_whatsapp,'')),''),p_category_id,
    trim(p_store_name),trim(coalesce(p_store_description,'')),trim(p_city),
    nullif(trim(coalesce(p_area,'')),''),trim(p_delivery_area),p_delivery_fee,p_estimated_minutes,
    v_phone_confirmed_at,now(),trim(p_policy_version),now(),
    'pending',null,null,now()
  )
  on conflict(user_id) do update set
    business_name=excluded.business_name,
    description=excluded.description,
    phone=excluded.phone,
    whatsapp=excluded.whatsapp,
    category_id=excluded.category_id,
    store_name=excluded.store_name,
    store_description=excluded.store_description,
    city=excluded.city,
    area=excluded.area,
    delivery_area=excluded.delivery_area,
    delivery_fee=excluded.delivery_fee,
    estimated_minutes=excluded.estimated_minutes,
    phone_verified_at=excluded.phone_verified_at,
    policies_accepted_at=excluded.policies_accepted_at,
    policy_version=excluded.policy_version,
    submitted_at=excluded.submitted_at,
    verification_status='pending',
    review_note=null,
    requested_changes_at=null,
    updated_at=now()
  returning id into v_merchant_id;

  insert into public.merchant_identity_documents(
    merchant_id,user_id,document_type,storage_path,created_at
  ) values(v_merchant_id,v_uid,p_document_type,p_identity_path,now())
  on conflict(merchant_id,storage_path) do nothing;

  insert into public.audit_logs(actor_id,action,entity_type,entity_id,source)
  values(v_uid,'submit','merchant_profile',v_merchant_id,'merchant_onboarding');

  return v_merchant_id;
end;
$$;
revoke all on function public.submit_merchant_application(
  text,text,text,text,uuid,text,text,text,text,text,numeric,integer,text,text,boolean,text
) from public,anon;
grant execute on function public.submit_merchant_application(
  text,text,text,text,uuid,text,text,text,text,text,numeric,integer,text,text,boolean,text
) to authenticated;

-- ---------------------------------------------------------------------------
-- 3. Admin review / approval workflow
-- ---------------------------------------------------------------------------

create or replace function public.admin_review_merchant_application(
  p_merchant_id uuid,
  p_status text,
  p_note text default null
)
returns void
language plpgsql
security definer
set search_path=''
as $$
declare
  v_uid uuid := auth.uid();
  v_mp public.merchant_profiles%rowtype;
  v_seller_id uuid;
begin
  if not exists(
    select 1 from public.profiles p
    where p.id=v_uid and p.role='admin' and p.is_active=true and p.deleted_at is null
  ) then
    raise exception 'Admin permission required';
  end if;

  if p_status not in ('approved','rejected','changes_requested','suspended') then
    raise exception 'Invalid review status';
  end if;
  if char_length(coalesce(p_note,'')) > 1000 then
    raise exception 'Review note is too long';
  end if;

  select * into v_mp
  from public.merchant_profiles
  where id=p_merchant_id
  for update;
  if not found then raise exception 'Merchant application not found'; end if;

  if p_status='approved' then
    if v_mp.phone_verified_at is null
       or v_mp.policies_accepted_at is null
       or v_mp.category_id is null
       or coalesce(trim(v_mp.store_name),'')=''
       or coalesce(trim(v_mp.delivery_area),'')='' then
      raise exception 'Merchant application is incomplete';
    end if;

    if not exists(
      select 1
      from public.merchant_identity_documents d
      join storage.objects o
        on o.bucket_id='merchant-private' and o.name=d.storage_path
      where d.merchant_id=v_mp.id
        and d.user_id=v_mp.user_id
        and o.owner_id=v_mp.user_id::text
    ) then
      raise exception 'Merchant identity document is missing';
    end if;

    insert into public.sellers(user_id,store_name,verification_status,created_at)
    values(v_mp.user_id,v_mp.store_name,'approved',now())
    on conflict(user_id) do update
      set store_name=excluded.store_name,verification_status='approved'
    returning id into v_seller_id;

    update public.merchant_profiles
    set seller_id=v_seller_id,
        verification_status='approved',
        approved_at=coalesce(approved_at,now()),
        suspended_at=null,
        review_note=nullif(trim(coalesce(p_note,'')),''),
        requested_changes_at=null,
        updated_at=now()
    where id=v_mp.id;

    insert into public.stores(
      merchant_id,seller_id,category_id,name,description,city,area,
      contact_phone,whatsapp,is_open,is_active,created_at,updated_at
    ) values(
      v_mp.id,v_seller_id,v_mp.category_id,v_mp.store_name,
      coalesce(v_mp.store_description,v_mp.description),v_mp.city,v_mp.area,
      v_mp.phone,v_mp.whatsapp,true,true,now(),now()
    )
    on conflict(merchant_id) do update set
      seller_id=excluded.seller_id,
      category_id=excluded.category_id,
      name=excluded.name,
      description=excluded.description,
      city=excluded.city,
      area=excluded.area,
      contact_phone=excluded.contact_phone,
      whatsapp=excluded.whatsapp,
      is_active=true,
      updated_at=now();

    insert into public.delivery_settings(
      seller_id,base_fee,delivery_area,estimated_minutes,is_active,created_at,updated_at
    ) values(
      v_seller_id,v_mp.delivery_fee,v_mp.delivery_area,v_mp.estimated_minutes,true,now(),now()
    )
    on conflict(seller_id) do update set
      base_fee=excluded.base_fee,
      delivery_area=excluded.delivery_area,
      estimated_minutes=excluded.estimated_minutes,
      is_active=true,
      updated_at=now();

    update public.profiles
    set role='seller',updated_at=now()
    where id=v_mp.user_id;

    update public.merchant_identity_documents
    set reviewed_at=now()
    where merchant_id=v_mp.id;

  elsif p_status='suspended' then
    update public.merchant_profiles
    set verification_status='suspended',suspended_at=now(),
        review_note=nullif(trim(coalesce(p_note,'')),''),updated_at=now()
    where id=v_mp.id;

    update public.sellers set verification_status='suspended'
    where id=v_mp.seller_id;
    update public.stores set is_active=false,updated_at=now()
    where merchant_id=v_mp.id;
    update public.products set is_active=false,updated_at=now()
    where seller_id=v_mp.seller_id;

  else
    update public.merchant_profiles
    set verification_status=p_status,
        review_note=nullif(trim(coalesce(p_note,'')),''),
        requested_changes_at=case when p_status='changes_requested' then now() else requested_changes_at end,
        updated_at=now()
    where id=v_mp.id;

    if v_mp.seller_id is not null and p_status='rejected' then
      update public.sellers set verification_status='rejected'
      where id=v_mp.seller_id;
    end if;
  end if;

  insert into public.audit_logs(actor_id,action,entity_type,entity_id,new_values,source)
  values(
    v_uid,'merchant_review','merchant_profile',v_mp.id,
    jsonb_build_object('status',p_status,'note',p_note),'admin'
  );
end;
$$;
revoke all on function public.admin_review_merchant_application(uuid,text,text) from public,anon;
grant execute on function public.admin_review_merchant_application(uuid,text,text) to authenticated;

create or replace function public.admin_set_merchant_status(p_merchant_id uuid,p_status text)
returns void
language plpgsql
set search_path=''
as $$
begin
  perform public.admin_review_merchant_application(p_merchant_id,p_status,null);
end;
$$;
revoke all on function public.admin_set_merchant_status(uuid,text) from public,anon;
grant execute on function public.admin_set_merchant_status(uuid,text) to authenticated;

-- ---------------------------------------------------------------------------
-- 4. Product image storage + store asset hardening
-- ---------------------------------------------------------------------------

insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values('product-images','product-images',true,5242880,array['image/jpeg','image/png','image/webp']::text[])
on conflict(id) do update set
  public=excluded.public,
  file_size_limit=excluded.file_size_limit,
  allowed_mime_types=excluded.allowed_mime_types;

drop policy if exists tani_product_images_storage_public_read on storage.objects;
create policy tani_product_images_storage_public_read
on storage.objects for select to public
using(bucket_id='product-images');

drop policy if exists tani_product_images_owner_insert on storage.objects;
create policy tani_product_images_owner_insert
on storage.objects for insert to authenticated
with check (
  bucket_id='product-images'
  and (storage.foldername(name))[1]=(select auth.uid())::text
  and exists(
    select 1 from public.products p
    join public.sellers s on s.id=p.seller_id
    where p.id::text=(storage.foldername(name))[2]
      and s.user_id=(select auth.uid())
      and s.verification_status='approved'
  )
);

drop policy if exists tani_product_images_owner_update on storage.objects;
create policy tani_product_images_owner_update
on storage.objects for update to authenticated
using (
  bucket_id='product-images'
  and (storage.foldername(name))[1]=(select auth.uid())::text
  and exists(
    select 1 from public.products p
    join public.sellers s on s.id=p.seller_id
    where p.id::text=(storage.foldername(name))[2]
      and s.user_id=(select auth.uid())
      and s.verification_status='approved'
  )
)
with check (
  bucket_id='product-images'
  and (storage.foldername(name))[1]=(select auth.uid())::text
  and exists(
    select 1 from public.products p
    join public.sellers s on s.id=p.seller_id
    where p.id::text=(storage.foldername(name))[2]
      and s.user_id=(select auth.uid())
      and s.verification_status='approved'
  )
);

drop policy if exists tani_product_images_owner_delete on storage.objects;
create policy tani_product_images_owner_delete
on storage.objects for delete to authenticated
using (
  bucket_id='product-images'
  and (storage.foldername(name))[1]=(select auth.uid())::text
  and exists(
    select 1 from public.products p
    join public.sellers s on s.id=p.seller_id
    where p.id::text=(storage.foldername(name))[2]
      and s.user_id=(select auth.uid())
      and s.verification_status='approved'
  )
);

-- Phase 2 made these path-owner-only. Phase 4 requires an approved merchant too.
drop policy if exists tani_store_assets_owner_insert on storage.objects;
create policy tani_store_assets_owner_insert
on storage.objects for insert to authenticated
with check (
  bucket_id='store-assets'
  and (storage.foldername(name))[1]=(select auth.uid())::text
  and exists(
    select 1 from public.merchant_profiles mp
    where mp.user_id=(select auth.uid()) and mp.verification_status='approved'
  )
);

drop policy if exists tani_store_assets_owner_update on storage.objects;
create policy tani_store_assets_owner_update
on storage.objects for update to authenticated
using (
  bucket_id='store-assets'
  and (storage.foldername(name))[1]=(select auth.uid())::text
  and exists(
    select 1 from public.merchant_profiles mp
    where mp.user_id=(select auth.uid()) and mp.verification_status='approved'
  )
)
with check (
  bucket_id='store-assets'
  and (storage.foldername(name))[1]=(select auth.uid())::text
  and exists(
    select 1 from public.merchant_profiles mp
    where mp.user_id=(select auth.uid()) and mp.verification_status='approved'
  )
);

drop policy if exists tani_store_assets_owner_delete on storage.objects;
create policy tani_store_assets_owner_delete
on storage.objects for delete to authenticated
using (
  bucket_id='store-assets'
  and (storage.foldername(name))[1]=(select auth.uid())::text
  and exists(
    select 1 from public.merchant_profiles mp
    where mp.user_id=(select auth.uid()) and mp.verification_status='approved'
  )
);

-- ---------------------------------------------------------------------------
-- 5. Product deletion + dashboard summary RPCs
-- ---------------------------------------------------------------------------

create or replace function public.merchant_delete_product(p_product_id uuid)
returns jsonb
language plpgsql
security definer
set search_path=''
as $$
declare
  v_uid uuid:=auth.uid();
  v_paths jsonb;
begin
  if v_uid is null then raise exception 'Authentication required'; end if;

  if not exists(
    select 1
    from public.products p
    join public.sellers s on s.id=p.seller_id
    where p.id=p_product_id
      and s.user_id=v_uid
      and s.verification_status='approved'
  ) then
    raise exception 'Product not found or merchant is not approved';
  end if;

  if exists(select 1 from public.order_items oi where oi.product_id=p_product_id) then
    raise exception 'Product has order history. Deactivate it instead of deleting it';
  end if;

  select coalesce(jsonb_agg(pi.storage_path),'[]'::jsonb)
  into v_paths
  from public.product_images pi
  where pi.product_id=p_product_id;

  delete from public.products where id=p_product_id;

  insert into public.audit_logs(actor_id,action,entity_type,entity_id,source)
  values(v_uid,'delete','product',p_product_id,'merchant');

  return v_paths;
end;
$$;
revoke all on function public.merchant_delete_product(uuid) from public,anon;
grant execute on function public.merchant_delete_product(uuid) to authenticated;

create or replace function public.merchant_dashboard_summary()
returns jsonb
language plpgsql
security definer
set search_path=''
as $$
declare
  v_uid uuid:=auth.uid();
  v_seller_id uuid;
  v_orders int:=0;
  v_delivered int:=0;
  v_cancelled int:=0;
  v_gmv numeric:=0;
  v_views int:=0;
  v_customers int:=0;
  v_repeat_customers int:=0;
  v_avg_response_seconds numeric;
  v_products int:=0;
  v_active_products int:=0;
  v_rating numeric:=0;
  v_best jsonb:='[]'::jsonb;
begin
  if v_uid is null then raise exception 'Authentication required'; end if;

  select s.id into v_seller_id
  from public.sellers s
  where s.user_id=v_uid and s.verification_status='approved';
  if v_seller_id is null then raise exception 'Approved merchant required'; end if;

  select
    count(*)::int,
    count(*) filter(where o.status='delivered')::int,
    count(*) filter(where o.status in ('cancelled','rejected','failed'))::int,
    coalesce(sum(o.total) filter(where o.status='delivered'),0),
    count(distinct o.customer_id)::int
  into v_orders,v_delivered,v_cancelled,v_gmv,v_customers
  from public.orders o
  where o.seller_id=v_seller_id;

  select count(*)::int into v_repeat_customers
  from (
    select o.customer_id
    from public.orders o
    where o.seller_id=v_seller_id and o.status='delivered'
    group by o.customer_id
    having count(*)>1
  ) q;

  select count(*)::int,count(*) filter(where p.is_active)::int
  into v_products,v_active_products
  from public.products p where p.seller_id=v_seller_id;

  select coalesce(round(avg(r.rating)::numeric,2),0)
  into v_rating
  from public.reviews r
  join public.products p on p.id=r.product_id
  where p.seller_id=v_seller_id;

  select count(*)::int into v_views
  from public.app_events e
  where e.event_name='product_view'
    and e.entity_type='product'
    and exists(
      select 1 from public.products p
      where p.id=e.entity_id and p.seller_id=v_seller_id
    );

  select round(avg(extract(epoch from (h.created_at-o.created_at)))::numeric,1)
  into v_avg_response_seconds
  from public.orders o
  join lateral(
    select osh.created_at
    from public.order_status_history osh
    where osh.order_id=o.id and osh.to_status='accepted'
    order by osh.created_at asc
    limit 1
  ) h on true
  where o.seller_id=v_seller_id;

  select coalesce(jsonb_agg(to_jsonb(x) order by x.units desc,x.revenue desc),'[]'::jsonb)
  into v_best
  from (
    select
      p.id,
      p.name,
      coalesce(sales.units,0)::int as units,
      coalesce(sales.revenue,0)::numeric as revenue,
      coalesce(views.views,0)::int as views
    from public.products p
    left join lateral(
      select
        coalesce(sum(oi.quantity),0)::int as units,
        coalesce(sum(oi.line_total),0)::numeric as revenue
      from public.order_items oi
      join public.orders o on o.id=oi.order_id
      where oi.product_id=p.id and o.status='delivered'
    ) sales on true
    left join lateral(
      select count(*)::int as views
      from public.app_events e
      where e.entity_id=p.id and e.entity_type='product' and e.event_name='product_view'
    ) views on true
    where p.seller_id=v_seller_id
    order by units desc,revenue desc,views desc
    limit 5
  ) x;

  return jsonb_build_object(
    'seller_id',v_seller_id,
    'orders',v_orders,
    'delivered_orders',v_delivered,
    'cancelled_orders',v_cancelled,
    'gmv',v_gmv,
    'product_views',v_views,
    'conversion_rate',case when v_views>0 then round((v_orders::numeric/v_views::numeric)*100,2) else 0 end,
    'customers',v_customers,
    'repeat_customers',v_repeat_customers,
    'products',v_products,
    'active_products',v_active_products,
    'average_rating',v_rating,
    'completion_rate',case when v_orders>0 then round((v_delivered::numeric/v_orders::numeric)*100,2) else 0 end,
    'cancellation_rate',case when v_orders>0 then round((v_cancelled::numeric/v_orders::numeric)*100,2) else 0 end,
    'average_response_seconds',v_avg_response_seconds,
    'best_sellers',v_best
  );
end;
$$;
revoke all on function public.merchant_dashboard_summary() from public,anon;
grant execute on function public.merchant_dashboard_summary() to authenticated;

-- ---------------------------------------------------------------------------
-- 6. Final table/RLS hardening for Phase 4
-- ---------------------------------------------------------------------------

-- Legacy base policies could otherwise allow direct seller/profile mutation.
drop policy if exists sellers_insert_own on public.sellers;
drop policy if exists sellers_update_own on public.sellers;
drop policy if exists tani_merchant_profile_owner_insert on public.merchant_profiles;
drop policy if exists tani_merchant_profile_owner_update on public.merchant_profiles;

-- Base product update policy did not require approved status; replace it.
drop policy if exists products_update_own on public.products;
drop policy if exists products_insert_approved_seller on public.products;
drop policy if exists tani_products_own_insert on public.products;
create policy tani_products_own_insert on public.products for insert to authenticated
with check(exists(
  select 1 from public.sellers s
  where s.id=products.seller_id and s.user_id=(select auth.uid()) and s.verification_status='approved'
));
drop policy if exists tani_products_own_update on public.products;
create policy tani_products_own_update on public.products for update to authenticated
using(exists(
  select 1 from public.sellers s
  where s.id=products.seller_id and s.user_id=(select auth.uid()) and s.verification_status='approved'
))
with check(exists(
  select 1 from public.sellers s
  where s.id=products.seller_id and s.user_id=(select auth.uid()) and s.verification_status='approved'
));
drop policy if exists tani_products_owner_read on public.products;
create policy tani_products_owner_read on public.products for select to authenticated
using(exists(
  select 1 from public.sellers s
  where s.id=products.seller_id and s.user_id=(select auth.uid())
));

-- Server-controlled workflow tables.
revoke insert,update,delete,truncate on public.merchant_profiles from anon,authenticated;
revoke insert,update,delete,truncate on public.sellers from anon,authenticated;
revoke insert,update,delete,truncate on public.merchant_identity_documents from anon,authenticated;
grant select on public.merchant_profiles,public.merchant_identity_documents to authenticated;
grant select on public.sellers to anon,authenticated;

-- Product delete must go through merchant_delete_product() so order history is respected.
revoke delete on public.products from authenticated;

-- DB rows for product details remain owner-write protected by RLS.
grant select,insert,update,delete on public.product_images,public.product_variants to authenticated;
grant select on public.product_images,public.product_variants to anon;
grant select,insert,update on public.products to authenticated;

