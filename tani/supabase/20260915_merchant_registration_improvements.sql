-- TANI — Merchant registration UX/data improvements
-- Adds requested categories and per-area delivery pricing while keeping the
-- legacy delivery_settings row as the default/fallback quote.

alter table public.merchant_profiles
  add column if not exists requested_category text,
  add column if not exists delivery_zones jsonb not null default '[]'::jsonb;

alter table public.merchant_profiles drop constraint if exists merchant_profiles_requested_category_length;
alter table public.merchant_profiles add constraint merchant_profiles_requested_category_length
  check (requested_category is null or char_length(trim(requested_category)) between 2 and 80);

alter table public.merchant_profiles drop constraint if exists merchant_profiles_delivery_zones_array;
alter table public.merchant_profiles add constraint merchant_profiles_delivery_zones_array
  check (jsonb_typeof(delivery_zones) = 'array');

create table if not exists public.delivery_zones (
  id uuid primary key default gen_random_uuid(),
  seller_id uuid not null references public.sellers(id) on delete cascade,
  area_name text not null check (char_length(trim(area_name)) between 2 and 100),
  fee numeric(12,2) not null check (fee >= 0),
  estimated_minutes integer check (estimated_minutes is null or estimated_minutes between 1 and 1440),
  sort_order integer not null default 0,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(seller_id, area_name)
);

create index if not exists delivery_zones_seller_active_idx
  on public.delivery_zones(seller_id, is_active, sort_order);

alter table public.delivery_zones enable row level security;

drop policy if exists tani_delivery_zones_public_read on public.delivery_zones;
create policy tani_delivery_zones_public_read
on public.delivery_zones for select to anon, authenticated
using (
  is_active
  and exists (
    select 1 from public.sellers s
    where s.id = delivery_zones.seller_id
      and s.verification_status = 'approved'
  )
);

drop policy if exists tani_delivery_zones_owner_all on public.delivery_zones;
create policy tani_delivery_zones_owner_all
on public.delivery_zones for all to authenticated
using (
  exists (
    select 1 from public.sellers s
    where s.id = delivery_zones.seller_id
      and s.user_id = (select auth.uid())
      and s.verification_status = 'approved'
  )
  or public.current_user_role() = 'admin'
)
with check (
  exists (
    select 1 from public.sellers s
    where s.id = delivery_zones.seller_id
      and s.user_id = (select auth.uid())
      and s.verification_status = 'approved'
  )
  or public.current_user_role() = 'admin'
);

grant select on public.delivery_zones to anon;
grant select, insert, update, delete on public.delivery_zones to authenticated;

drop function if exists public.submit_merchant_application(
  text,text,text,text,uuid,text,text,text,text,text,numeric,integer,text,text,boolean,text
);

create or replace function public.submit_merchant_application(
  p_business_name text,
  p_description text,
  p_phone text,
  p_whatsapp text,
  p_category_id uuid,
  p_requested_category text,
  p_store_name text,
  p_store_description text,
  p_city text,
  p_area text,
  p_delivery_area text,
  p_delivery_fee numeric,
  p_estimated_minutes integer,
  p_delivery_zones jsonb,
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
  v_effective_phone_verified_at timestamptz;
  v_status text;
  v_category_id uuid := p_category_id;
  v_requested_category text := nullif(trim(coalesce(p_requested_category,'')), '');
  v_zone jsonb;
  v_zone_area text;
  v_zone_fee numeric;
  v_zone_minutes integer;
  v_zone_index integer := 0;
  v_normalized_zones jsonb := '[]'::jsonb;
begin
  if v_uid is null then raise exception 'Authentication required'; end if;

  select u.phone,u.phone_confirmed_at
  into v_auth_phone,v_phone_confirmed_at
  from auth.users u where u.id=v_uid;

  if v_phone_confirmed_at is not null
     and regexp_replace(coalesce(v_auth_phone,''),'[^0-9]','','g')
        = regexp_replace(coalesce(p_phone,''),'[^0-9]','','g') then
    v_effective_phone_verified_at := v_phone_confirmed_at;
  else
    v_effective_phone_verified_at := null;
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
  if not coalesce(p_accept_policies,false) then
    raise exception 'Merchant policies must be accepted';
  end if;

  if v_category_id is not null then
    if not exists(select 1 from public.categories c where c.id=v_category_id and c.is_active=true) then
      raise exception 'Store category is invalid';
    end if;
    v_requested_category := null;
  else
    if v_requested_category is null or char_length(v_requested_category) not between 2 and 80 then
      raise exception 'Choose a category or enter a requested category';
    end if;
    select c.id into v_category_id
    from public.categories c
    where lower(trim(c.name))=lower(v_requested_category)
    limit 1;
    if v_category_id is null then
      insert into public.categories(name,slug,sort_order,is_active,created_at,updated_at)
      values(
        v_requested_category,
        'requested-' || left(md5(lower(v_requested_category)),16),
        999,
        false,
        now(),
        now()
      )
      returning id into v_category_id;
    end if;
  end if;

  if jsonb_typeof(coalesce(p_delivery_zones,'[]'::jsonb)) <> 'array'
     or jsonb_array_length(coalesce(p_delivery_zones,'[]'::jsonb)) not between 1 and 20 then
    raise exception 'At least one delivery zone is required';
  end if;

  for v_zone in select value from jsonb_array_elements(p_delivery_zones) loop
    v_zone_area := trim(coalesce(v_zone->>'area',''));
    if char_length(v_zone_area) not between 2 and 100 then
      raise exception 'Invalid delivery area';
    end if;
    begin
      v_zone_fee := (v_zone->>'fee')::numeric;
      v_zone_minutes := nullif(v_zone->>'estimated_minutes','')::integer;
    exception when invalid_text_representation or numeric_value_out_of_range then
      raise exception 'Invalid delivery fee or estimate';
    end;
    if v_zone_fee is null or v_zone_fee < 0 then raise exception 'Invalid delivery fee'; end if;
    if v_zone_minutes is not null and v_zone_minutes not between 1 and 1440 then
      raise exception 'Invalid delivery estimate';
    end if;
    v_normalized_zones := v_normalized_zones || jsonb_build_array(jsonb_build_object(
      'area',v_zone_area,
      'fee',v_zone_fee,
      'estimated_minutes',v_zone_minutes,
      'sort_order',v_zone_index
    ));
    v_zone_index := v_zone_index + 1;
  end loop;

  if p_document_type not in ('national_id','passport','other') then
    raise exception 'Invalid identity document type';
  end if;
  if coalesce(p_identity_path,'')='' or split_part(p_identity_path,'/',1)<>v_uid::text then
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

  select mp.id,mp.verification_status into v_merchant_id,v_status
  from public.merchant_profiles mp where mp.user_id=v_uid;
  if v_status in ('approved','suspended') then
    raise exception 'This merchant profile cannot be resubmitted';
  end if;

  perform set_config('tani.merchant_workflow','submit',true);

  insert into public.merchant_profiles(
    user_id,business_name,description,phone,whatsapp,category_id,requested_category,
    store_name,store_description,city,area,delivery_area,delivery_fee,estimated_minutes,delivery_zones,
    phone_verified_at,policies_accepted_at,policy_version,submitted_at,
    verification_status,review_note,requested_changes_at,updated_at
  )
  values(
    v_uid,trim(p_business_name),trim(coalesce(p_description,'')),trim(p_phone),
    nullif(trim(coalesce(p_whatsapp,'')),''),v_category_id,v_requested_category,
    trim(p_store_name),trim(coalesce(p_store_description,'')),trim(p_city),
    nullif(trim(coalesce(p_area,'')),''),
    v_normalized_zones->0->>'area',
    (v_normalized_zones->0->>'fee')::numeric,
    nullif(v_normalized_zones->0->>'estimated_minutes','')::integer,
    v_normalized_zones,
    v_effective_phone_verified_at,now(),trim(p_policy_version),now(),
    'pending',null,null,now()
  )
  on conflict(user_id) do update set
    business_name=excluded.business_name,
    description=excluded.description,
    phone=excluded.phone,
    whatsapp=excluded.whatsapp,
    category_id=excluded.category_id,
    requested_category=excluded.requested_category,
    store_name=excluded.store_name,
    store_description=excluded.store_description,
    city=excluded.city,
    area=excluded.area,
    delivery_area=excluded.delivery_area,
    delivery_fee=excluded.delivery_fee,
    estimated_minutes=excluded.estimated_minutes,
    delivery_zones=excluded.delivery_zones,
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
  text,text,text,text,uuid,text,text,text,text,text,text,numeric,integer,jsonb,text,text,boolean,text
) from public,anon;
grant execute on function public.submit_merchant_application(
  text,text,text,text,uuid,text,text,text,text,text,text,numeric,integer,jsonb,text,text,boolean,text
) to authenticated;

-- Backward-compatible overload for installed app versions that still send one
-- delivery area and do not know about requested categories/delivery_zones.
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
language sql
security invoker
set search_path=public,pg_temp
as $$
  select public.submit_merchant_application(
    p_business_name,
    p_description,
    p_phone,
    p_whatsapp,
    p_category_id,
    null,
    p_store_name,
    p_store_description,
    p_city,
    p_area,
    p_delivery_area,
    p_delivery_fee,
    p_estimated_minutes,
    jsonb_build_array(jsonb_build_object(
      'area',p_delivery_area,
      'fee',p_delivery_fee,
      'estimated_minutes',p_estimated_minutes
    )),
    p_identity_path,
    p_document_type,
    p_accept_policies,
    p_policy_version
  );
$$;

revoke all on function public.submit_merchant_application(
  text,text,text,text,uuid,text,text,text,text,text,numeric,integer,text,text,boolean,text
) from public,anon;
grant execute on function public.submit_merchant_application(
  text,text,text,text,uuid,text,text,text,text,text,numeric,integer,text,text,boolean,text
) to authenticated;

create or replace function private.sync_merchant_delivery_zones()
returns trigger
language plpgsql
security definer
set search_path=''
as $$
begin
  if new.verification_status='approved' and new.seller_id is not null then
    delete from public.delivery_zones where seller_id=new.seller_id;
    insert into public.delivery_zones(
      seller_id,area_name,fee,estimated_minutes,sort_order,is_active,created_at,updated_at
    )
    select
      new.seller_id,
      trim(z.value->>'area'),
      (z.value->>'fee')::numeric,
      nullif(z.value->>'estimated_minutes','')::integer,
      coalesce((z.value->>'sort_order')::integer,z.ordinality::integer-1),
      true,
      now(),
      now()
    from jsonb_array_elements(new.delivery_zones) with ordinality as z(value,ordinality);
  end if;
  return new;
end;
$$;

revoke all on function private.sync_merchant_delivery_zones() from public,anon,authenticated;
drop trigger if exists tani_sync_merchant_delivery_zones on public.merchant_profiles;
create trigger tani_sync_merchant_delivery_zones
after insert or update on public.merchant_profiles
for each row execute function private.sync_merchant_delivery_zones();

create or replace function public.delivery_quote(
  p_seller_id uuid,
  p_city text default null,
  p_area text default null
)
returns jsonb
language sql
stable
security invoker
set search_path=public,pg_temp
as $$
  select coalesce(
    (
      select jsonb_build_object(
        'seller_id',z.seller_id,
        'provider','merchant_delivery',
        'fee',z.fee,
        'estimated_minutes',z.estimated_minutes,
        'delivery_area',z.area_name,
        'city',st.city,
        'area',p_area,
        'available',true
      )
      from public.delivery_zones z
      join public.sellers s on s.id=z.seller_id and s.verification_status='approved'
      left join public.stores st on st.seller_id=z.seller_id and st.is_active
      where z.seller_id=p_seller_id
        and z.is_active
        and (p_city is null or lower(coalesce(st.city,''))=lower(trim(p_city)))
        and (p_area is null or lower(trim(z.area_name))=lower(trim(p_area)))
      order by z.sort_order,z.fee
      limit 1
    ),
    case when p_area is not null then
      jsonb_build_object('seller_id',p_seller_id,'area',p_area,'available',false)
    else
      (
        select jsonb_build_object(
          'seller_id',d.seller_id,
          'provider','merchant_delivery',
          'fee',d.base_fee,
          'estimated_minutes',d.estimated_minutes,
          'delivery_area',d.delivery_area,
          'city',st.city,
          'area',p_area,
          'available',d.is_active and st.is_active and st.is_open
        )
        from public.delivery_settings d
        join public.sellers s on s.id=d.seller_id and s.verification_status='approved'
        left join public.stores st on st.seller_id=d.seller_id and st.is_active
        where d.seller_id=p_seller_id
          and (p_city is null or lower(coalesce(st.city,''))=lower(trim(p_city)))
        limit 1
      )
    end,
    jsonb_build_object('seller_id',p_seller_id,'available',false)
  );
$$;

grant execute on function public.delivery_quote(uuid,text,text) to anon,authenticated;
