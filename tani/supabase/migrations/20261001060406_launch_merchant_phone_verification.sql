-- Approval must use actual Auth verification, never an invented timestamp.
CREATE OR REPLACE FUNCTION public.admin_review_merchant_application(p_merchant_id uuid, p_status text, p_note text DEFAULT NULL::text)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_uid uuid := auth.uid();
  v_mp public.merchant_profiles%rowtype;
  v_seller_id uuid;
begin
  perform private.require_active_account();
  if not exists(
    select 1 from public.profiles p
    where p.id=v_uid and p.role='admin' and p.is_active=true and p.deleted_at is null
  ) then
    raise exception 'Admin permission required' using errcode='42501';
  end if;

  if p_status is null or p_status not in ('approved','rejected','changes_requested','suspended') then
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

    if not exists(select 1 from public.profiles p where p.id=v_mp.user_id and p.is_active and p.deleted_at is null) then
      raise exception 'Merchant account is inactive' using errcode='23514';
    end if;
    if not exists(select 1 from auth.users u where u.id=v_mp.user_id and u.phone_confirmed_at is not null
      and regexp_replace(coalesce(u.phone,''),'[^0-9]','','g')=regexp_replace(coalesce(v_mp.phone,''),'[^0-9]','','g')) then
      raise exception 'Merchant phone is not verified' using errcode='23514';
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
        phone_verified_at=(select u.phone_confirmed_at from auth.users u where u.id=v_mp.user_id),
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
$function$;

NOTIFY pgrst,'reload schema';
