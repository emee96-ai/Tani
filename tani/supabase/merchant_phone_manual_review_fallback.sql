-- Temporary merchant phone verification fallback for TANI.
-- Purpose: allow merchant applications to be submitted even when hosted Supabase
-- does not have an SMS provider configured. Phone verification is completed as
-- part of the admin approval review. Remove this fallback when production SMS
-- OTP is configured and restore strict pre-submission phone verification.

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
  v_effective_phone_verified_at timestamptz;
  v_status text;
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
  if char_length(trim(coalesce(p_delivery_area,''))) not between 2 and 300 then
    raise exception 'Delivery area is required';
  end if;
  if p_delivery_fee is null or p_delivery_fee < 0 then
    raise exception 'Invalid delivery fee';
  end if;
  if p_estimated_minutes is not null and p_estimated_minutes not between 1 and 1440 then
    raise exception 'Invalid delivery estimate';
  end if;
  if not coalesce(p_accept_policies,false) then raise exception 'Merchant policies must be accepted'; end if;
  if not exists(select 1 from public.categories c where c.id=p_category_id and c.is_active=true) then
    raise exception 'Store category is invalid';
  end if;
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
      and o.owner_id::uuid=v_uid
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
    user_id,business_name,description,phone,whatsapp,category_id,
    store_name,store_description,city,area,delivery_area,delivery_fee,estimated_minutes,
    phone_verified_at,policies_accepted_at,policy_version,submitted_at,
    verification_status,review_note,requested_changes_at,updated_at
  )
  values(
    v_uid,trim(p_business_name),trim(coalesce(p_description,'')),trim(p_phone),
    nullif(trim(coalesce(p_whatsapp,'')),''),
    p_category_id,trim(p_store_name),trim(coalesce(p_store_description,'')),
    trim(p_city),nullif(trim(coalesce(p_area,'')),''),
    trim(p_delivery_area),p_delivery_fee,p_estimated_minutes,
    v_effective_phone_verified_at,now(),trim(p_policy_version),now(),
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
  )
  values(v_merchant_id,v_uid,p_document_type,p_identity_path,now())
  on conflict(merchant_id,storage_path) do nothing;

  insert into public.audit_logs(actor_id,action,entity_type,entity_id,source)
  values(v_uid,'submit','merchant_profile',v_merchant_id,'merchant_onboarding');

  return v_merchant_id;
end;
$$;

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
    if v_mp.policies_accepted_at is null
       or v_mp.category_id is null
       or coalesce(trim(v_mp.store_name),'')=''
       or coalesce(trim(v_mp.delivery_area),'')=''
       or coalesce(trim(v_mp.phone),'')='' then
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
        phone_verified_at=coalesce(phone_verified_at,now()),
        approved_at=coalesce(approved_at,now()),
        suspended_at=null,
        review_note=nullif(trim(coalesce(p_note,'')),''),
        requested_changes_at=null,
        updated_at=now()
    where id=v_mp.id;

    insert into public.stores(
      merchant_id,seller_id,category_id,name,description,city,area,
      contact_phone,whatsapp,is_open,is_active,created_at,updated_at
    )
    values(
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
    )
    values(
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
