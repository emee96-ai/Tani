-- Explicit staff evidence supports the existing onboarding flow without requiring SMS OTP.
CREATE TABLE private.merchant_phone_verifications (
 merchant_id uuid PRIMARY KEY REFERENCES public.merchant_profiles(id) ON DELETE CASCADE,
 phone_digest text NOT NULL,
 verified_at timestamptz NOT NULL DEFAULT now(),
 verified_by uuid REFERENCES auth.users(id) ON DELETE SET NULL,
 note text NOT NULL CHECK(char_length(trim(note)) BETWEEN 10 AND 1000)
);
ALTER TABLE private.merchant_phone_verifications ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON private.merchant_phone_verifications FROM PUBLIC,anon,authenticated;
CREATE OR REPLACE FUNCTION public.admin_verify_merchant_phone(p_merchant_id uuid,p_phone text,p_note text)
RETURNS timestamptz LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $function$
DECLARE mp public.merchant_profiles%ROWTYPE; stamp timestamptz:=now(); normalized text;
BEGIN
 PERFORM private.require_active_account();
 IF public.current_user_role() IS DISTINCT FROM 'admin' THEN RAISE EXCEPTION 'Admin access required' USING ERRCODE='42501'; END IF;
 IF char_length(trim(coalesce(p_note,''))) NOT BETWEEN 10 AND 1000 THEN RAISE EXCEPTION 'Verification evidence is required' USING ERRCODE='23514'; END IF;
 SELECT * INTO mp FROM public.merchant_profiles WHERE id=p_merchant_id FOR UPDATE;
 IF NOT FOUND OR NOT EXISTS(SELECT 1 FROM public.profiles p WHERE p.id=mp.user_id AND p.is_active AND p.deleted_at IS NULL) THEN
  RAISE EXCEPTION 'Merchant account is unavailable' USING ERRCODE='23514';
 END IF;
 normalized:=regexp_replace(coalesce(mp.phone,''),'[^0-9]','','g');
 IF char_length(normalized) NOT BETWEEN 7 AND 30 OR normalized<>regexp_replace(coalesce(p_phone,''),'[^0-9]','','g') THEN
  RAISE EXCEPTION 'Merchant phone changed; reload the application' USING ERRCODE='23514';
 END IF;
 INSERT INTO private.merchant_phone_verifications(merchant_id,phone_digest,verified_at,verified_by,note)
 VALUES(mp.id,encode(pg_catalog.sha256(pg_catalog.convert_to(normalized,'UTF8')),'hex'),stamp,auth.uid(),trim(p_note))
 ON CONFLICT(merchant_id) DO UPDATE SET phone_digest=excluded.phone_digest,verified_at=excluded.verified_at,verified_by=excluded.verified_by,note=excluded.note;
 UPDATE public.merchant_profiles SET phone_verified_at=stamp,updated_at=stamp WHERE id=mp.id;
 INSERT INTO public.audit_logs(actor_id,action,entity_type,entity_id,new_values,source)
 VALUES(auth.uid(),'merchant_phone_verified','merchant_profile',mp.id,jsonb_build_object('method','manual','verified_at',stamp),'admin');
 RETURN stamp;
END;
$function$;
REVOKE ALL ON FUNCTION public.admin_verify_merchant_phone(uuid,text,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.admin_verify_merchant_phone(uuid,text,text) TO authenticated;

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
      and regexp_replace(coalesce(u.phone,''),'[^0-9]','','g')=regexp_replace(coalesce(v_mp.phone,''),'[^0-9]','','g'))
      and not exists(select 1 from private.merchant_phone_verifications v where v.merchant_id=v_mp.id
        and v.phone_digest=encode(pg_catalog.sha256(pg_catalog.convert_to(regexp_replace(coalesce(v_mp.phone,''),'[^0-9]','','g'),'UTF8')),'hex')) then
      raise exception 'Merchant phone verification is required' using errcode='23514';
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
        phone_verified_at=coalesce((select u.phone_confirmed_at from auth.users u where u.id=v_mp.user_id
          and regexp_replace(coalesce(u.phone,''),'[^0-9]','','g')=regexp_replace(coalesce(v_mp.phone,''),'[^0-9]','','g')),
          (select v.verified_at from private.merchant_phone_verifications v where v.merchant_id=v_mp.id
            and v.phone_digest=encode(pg_catalog.sha256(pg_catalog.convert_to(regexp_replace(coalesce(v_mp.phone,''),'[^0-9]','','g'),'UTF8')),'hex'))),
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

CREATE OR REPLACE FUNCTION public.maintenance_finish_deletion(p_user_id uuid,p_lease_id uuid,p_error text DEFAULT NULL)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $function$
DECLARE v_attempts int;
BEGIN
 SELECT attempts INTO v_attempts FROM private.account_deletion_requests WHERE user_id=p_user_id AND lease_id=p_lease_id AND status='processing' FOR UPDATE;
 IF NOT FOUND THEN RETURN; END IF;
 IF p_error IS NOT NULL THEN
  UPDATE private.account_deletion_requests SET status='failed',last_error=left(p_error,200),lease_id=NULL,
    next_attempt_at=now()+make_interval(secs=>least(3600,60*power(2,least(v_attempts,6))::int)) WHERE user_id=p_user_id;
  RETURN;
 END IF;
 IF EXISTS(SELECT 1 FROM private.account_deletion_objects WHERE user_id=p_user_id AND deleted_at IS NULL) THEN
  RAISE EXCEPTION 'Storage erasure is incomplete' USING ERRCODE='23514';
 END IF;
 -- Keep orders and dispute/audit records; remove identity documents and transient account data.
 DELETE FROM private.merchant_phone_verifications WHERE merchant_id IN (SELECT id FROM public.merchant_profiles WHERE user_id=p_user_id);
 DELETE FROM public.merchant_identity_documents WHERE user_id=p_user_id;
 DELETE FROM public.notification_preferences WHERE user_id=p_user_id;
 DELETE FROM public.notifications WHERE user_id=p_user_id;
 DELETE FROM private.account_deletion_objects WHERE user_id=p_user_id;
 UPDATE private.account_deletion_requests SET status='completed',completed_at=now(),last_error=NULL,lease_id=NULL WHERE user_id=p_user_id;
END;
$function$;


CREATE OR REPLACE FUNCTION public.maintenance_launch_gate() RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $function$
DECLARE checks jsonb;
BEGIN
 checks := jsonb_build_object(
  'active_admin',EXISTS(SELECT 1 FROM public.profiles WHERE role='admin' AND is_active AND deleted_at IS NULL),
  'merchant_records_verified',NOT EXISTS(
   SELECT 1 FROM public.merchant_profiles mp WHERE mp.verification_status='approved' AND (
    mp.policies_accepted_at IS NULL OR mp.phone_verified_at IS NULL OR (NOT EXISTS(
     SELECT 1 FROM auth.users u WHERE u.id=mp.user_id AND u.phone_confirmed_at IS NOT NULL
      AND regexp_replace(coalesce(u.phone,''),'[^0-9]','','g')=regexp_replace(coalesce(mp.phone,''),'[^0-9]','','g')
    ) AND NOT EXISTS(SELECT 1 FROM private.merchant_phone_verifications v WHERE v.merchant_id=mp.id
      AND v.phone_digest=encode(pg_catalog.sha256(pg_catalog.convert_to(regexp_replace(coalesce(mp.phone,''),'[^0-9]','','g'),'UTF8')),'hex')))
    OR NOT EXISTS(SELECT 1 FROM public.merchant_identity_documents d JOIN storage.objects o ON o.bucket_id='merchant-private' AND o.name=d.storage_path WHERE d.merchant_id=mp.id AND d.user_id=mp.user_id)
   )
  ),
  'live_catalog',EXISTS(SELECT 1 FROM public.products p JOIN public.sellers s ON s.id=p.seller_id JOIN public.stores st ON st.seller_id=s.id WHERE p.is_active AND s.verification_status='approved' AND st.is_active AND st.is_open AND (p.stock>0 OR EXISTS(SELECT 1 FROM public.product_variants v WHERE v.product_id=p.id AND v.is_active AND v.stock>0))),
  'worker_and_push',EXISTS(SELECT 1 FROM private.maintenance_health WHERE last_run_at>now()-interval '5 minutes' AND push_configured AND last_error IS NULL),
  'queues_healthy',NOT EXISTS(SELECT 1 FROM private.push_delivery_queue WHERE status='failed') AND NOT EXISTS(SELECT 1 FROM private.account_deletion_requests WHERE status='failed'),
  'launch_evidence',(SELECT count(*)=6 AND min(approved_at)>now()-interval '30 days' FROM private.launch_approvals)
 );
 RETURN jsonb_build_object('ready',(SELECT bool_and(value::boolean) FROM jsonb_each(checks)),'checks',checks);
END;
$function$;
REVOKE ALL ON FUNCTION public.maintenance_launch_gate() FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.maintenance_launch_gate() TO service_role;
NOTIFY pgrst,'reload schema';
