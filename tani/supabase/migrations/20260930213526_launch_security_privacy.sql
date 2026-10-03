-- Reject missing/inactive profiles before any privileged workflow.
-- Definitions are taken from the reviewed live schema to retain the current business rules.
CREATE OR REPLACE FUNCTION public.account_is_active()
RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path = ''
AS $function$
  SELECT EXISTS (SELECT 1 FROM public.profiles p WHERE p.id = (SELECT auth.uid())
    AND p.is_active AND p.deleted_at IS NULL)
$function$;
REVOKE ALL ON FUNCTION public.account_is_active() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.account_is_active() TO authenticated, service_role;

CREATE OR REPLACE FUNCTION private.require_active_account()
RETURNS uuid LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = ''
AS $function$
DECLARE v_uid uuid := auth.uid();
BEGIN
  IF v_uid IS NULL OR NOT public.account_is_active() THEN
    RAISE EXCEPTION 'الحساب غير متاح أو تسجيل الدخول مطلوب' USING ERRCODE = '42501';
  END IF;
  RETURN v_uid;
END;
$function$;
REVOKE ALL ON FUNCTION private.require_active_account() FROM PUBLIC, anon, authenticated;

-- admin_list_staff
CREATE OR REPLACE FUNCTION public.admin_list_staff()
 RETURNS TABLE(user_id uuid, email text, name text, phone text, role text, is_active boolean, created_at timestamp with time zone)
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin
  perform private.require_active_account();
  if auth.uid() is null or not exists (
    select 1
    from public.profiles p
    where p.id = auth.uid()
      and p.role = 'admin'
      and p.is_active = true
      and p.deleted_at is null
  ) then
    raise exception 'Admin access required' using errcode = '42501';
  end if;

  return query
  select p.id, u.email::text, p.name, p.phone, p.role, p.is_active, p.created_at
  from public.profiles p
  join auth.users u on u.id = p.id
  where p.role in ('admin', 'support')
    and p.deleted_at is null
  order by case when p.role = 'admin' then 0 else 1 end,
           lower(coalesce(u.email, '')),
           p.created_at;
end;
$function$;

-- admin_review_featured_request
CREATE OR REPLACE FUNCTION public.admin_review_featured_request(p_request_id uuid, p_status text, p_note text DEFAULT NULL::text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_req public.featured_requests%rowtype; v_placement_id uuid;
begin
  perform private.require_active_account();
 if public.current_user_role() is distinct from 'admin' then raise exception 'admin required' using errcode='42501'; end if;
 if p_status not in ('approved','rejected') then raise exception 'invalid status'; end if;
 select * into v_req from public.featured_requests where id=p_request_id for update;
 if v_req.id is null then raise exception 'request not found'; end if;
 if v_req.status<>'pending' then raise exception 'request already reviewed'; end if;
 if p_status='approved' then
   insert into public.featured_placements(product_id,seller_id,placement,starts_at,ends_at,is_active,is_sponsored,sponsored_label,request_id,priority)
   values(v_req.product_id,v_req.seller_id,v_req.placement,coalesce(v_req.requested_starts_at,now()),v_req.requested_ends_at,true,true,'ممول',v_req.id,0)
   returning id into v_placement_id;
 end if;
 update public.featured_requests set status=p_status,admin_note=p_note,reviewed_at=now(),reviewed_by=(select auth.uid()) where id=p_request_id;
 return v_placement_id;
end; $function$;

-- admin_review_merchant_application
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
$function$;

-- admin_review_subscription_request
CREATE OR REPLACE FUNCTION public.admin_review_subscription_request(p_request_id uuid, p_status text, p_note text DEFAULT NULL::text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_req public.subscription_requests%rowtype; v_plan public.subscription_plans%rowtype; v_subscription_id uuid;
begin
  perform private.require_active_account();
 if public.current_user_role() is distinct from 'admin' then raise exception 'admin required' using errcode='42501'; end if;
 if p_status not in ('approved','rejected') then raise exception 'invalid status'; end if;
 select * into v_req from public.subscription_requests where id=p_request_id for update;
 if v_req.id is null then raise exception 'request not found'; end if;
 if v_req.status<>'pending' then raise exception 'request already reviewed'; end if;
 select * into v_plan from public.subscription_plans where id=v_req.plan_id and is_active;
 if v_plan.id is null then raise exception 'plan not available'; end if;
 if p_status='approved' then
   update public.subscriptions set status='expired',updated_at=now() where seller_id=v_req.seller_id and status='active';
   insert into public.subscriptions(user_id,seller_id,plan_id,status,starts_at,ends_at,source,auto_renew)
   values(v_req.user_id,v_req.seller_id,v_req.plan_id,'active',now(),now()+make_interval(days=>v_plan.duration_days),'admin',false)
   returning id into v_subscription_id;
 end if;
 update public.subscription_requests set status=p_status,admin_note=p_note,reviewed_at=now(),reviewed_by=(select auth.uid()) where id=p_request_id;
 return v_subscription_id;
end; $function$;

-- admin_set_user_role_by_email
CREATE OR REPLACE FUNCTION public.admin_set_user_role_by_email(p_email text, p_role text)
 RETURNS TABLE(user_id uuid, email text, name text, phone text, role text, is_active boolean, created_at timestamp with time zone)
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_target_id uuid;
  v_current_role text;
  v_email text := lower(trim(coalesce(p_email, '')));
begin
  perform private.require_active_account();
  if auth.uid() is null or not exists (
    select 1
    from public.profiles p
    where p.id = auth.uid()
      and p.role = 'admin'
      and p.is_active = true
      and p.deleted_at is null
  ) then
    raise exception 'Admin access required' using errcode = '42501';
  end if;

  if v_email = '' then
    raise exception 'Email is required' using errcode = '22023';
  end if;

  if p_role not in ('admin', 'support', 'customer', 'seller') then
    raise exception 'Invalid role' using errcode = '22023';
  end if;

  select u.id
    into v_target_id
  from auth.users u
  where lower(u.email) = v_email
  limit 1;

  if v_target_id is null then
    raise exception 'No registered account found for this email' using errcode = 'P0002';
  end if;

  select p.role
    into v_current_role
  from public.profiles p
  where p.id = v_target_id
    and p.deleted_at is null;

  if v_current_role is null then
    raise exception 'User profile not found' using errcode = 'P0002';
  end if;

  if v_current_role = 'admin' and p_role <> 'admin' then
    if not exists (
      select 1
      from public.profiles p
      where p.role = 'admin'
        and p.id <> v_target_id
        and p.is_active = true
        and p.deleted_at is null
    ) then
      raise exception 'Cannot remove the last active admin' using errcode = '23514';
    end if;
  end if;

  update public.profiles
  set role = p_role,
      updated_at = now()
  where id = v_target_id;

  return query
  select p.id, u.email::text, p.name, p.phone, p.role, p.is_active, p.created_at
  from public.profiles p
  join auth.users u on u.id = p.id
  where p.id = v_target_id;
end;
$function$;

-- apply_referral_code
CREATE OR REPLACE FUNCTION public.apply_referral_code(p_code text)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_uid uuid := (select auth.uid()); v_referrer uuid; v_code text := upper(trim(p_code));
begin
  perform private.require_active_account();
  if v_uid is null then raise exception 'authentication required'; end if;
  select user_id into v_referrer from public.referral_codes where upper(code)=v_code;
  if v_referrer is null then raise exception 'invalid referral code'; end if;
  if v_referrer=v_uid then raise exception 'cannot refer yourself'; end if;
  if exists(select 1 from public.referrals where referred_id=v_uid) then return false; end if;
  insert into public.referrals(referrer_id,referred_id,code,status) values(v_referrer,v_uid,v_code,'pending');
  return true;
end; $function$;

-- checkout_create_order_group_v4
CREATE OR REPLACE FUNCTION public.checkout_create_order_group_v4(p_address_id uuid, p_phone text, p_notes text, p_items jsonb, p_idempotency_key text, p_delivery_zones jsonb, p_expected_grand_total numeric, p_quote_token text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_uid uuid := auth.uid();
  v_existing uuid;
  v_group_id uuid;
  v_order_id uuid;
  v_quote jsonb;
  v_line jsonb;
  v_delivery record;
  v_address record;
  v_profile record;
  v_address_text text;
  v_current_total numeric(14,2);
begin
  perform private.require_active_account();
  if v_uid is null then
    raise exception using errcode = '28000', message = 'يجب تسجيل الدخول أولاً';
  end if;
  select p.id, p.name into v_profile
  from public.profiles p
  where p.id = v_uid and p.is_active = true and p.deleted_at is null;
  if not found then raise exception using errcode = '28000', message = 'الحساب غير نشط'; end if;

  if coalesce(char_length(trim(p_idempotency_key)), 0) < 16
     or char_length(trim(p_idempotency_key)) > 100 then
    raise exception using errcode = '22023', message = 'مفتاح الطلب غير صحيح';
  end if;
  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended(v_uid::text || ':' || trim(p_idempotency_key), 0)
  );
  select og.id into v_existing
  from public.order_groups og
  where og.customer_id = v_uid and og.idempotency_key = trim(p_idempotency_key)
  limit 1;
  if found then return v_existing; end if;

  select a.* into v_address
  from public.addresses a
  where a.id = p_address_id and a.user_id = v_uid;
  if not found then raise exception using errcode = '22023', message = 'عنوان التوصيل غير موجود'; end if;
  if coalesce(char_length(trim(p_phone)), 0) < 7 or char_length(trim(p_phone)) > 30 then
    raise exception using errcode = '22023', message = 'رقم الهاتف غير صحيح';
  end if;
  if char_length(coalesce(p_notes, '')) > 1000 then
    raise exception using errcode = '22023', message = 'ملاحظات الطلب طويلة جداً';
  end if;
  if p_expected_grand_total is null or p_expected_grand_total < 0 then
    raise exception using errcode = '22023', message = 'إجمالي الطلب المتوقع غير صحيح';
  end if;
  if coalesce(char_length(trim(p_quote_token)), 0) <> 32 then
    raise exception using errcode = '22023', message = 'انتهت صلاحية تسعير الطلب. أعيدي المحاولة';
  end if;

  v_quote := private.checkout_quote_core_v2(p_items, p_delivery_zones, true);
  v_current_total := (v_quote ->> 'grand_total')::numeric;
  if abs(v_current_total - p_expected_grand_total) > 0.005
     or (v_quote ->> 'quote_token') <> trim(p_quote_token) then
    raise exception using errcode = 'P0001',
      message = 'تغيّر السعر أو الخيار أو التوصيل. راجعي الإجمالي الجديد ثم أكدي الطلب مرة أخرى';
  end if;

  v_address_text := concat_ws(
    ' - ', nullif(trim(v_address.label), ''), nullif(trim(v_address.description), ''),
    nullif(trim(coalesce(v_address.area, '')), ''), nullif(trim(coalesce(v_address.landmark, '')), '')
  );
  insert into public.order_groups(
    customer_id, subtotal, delivery_total, discount_total, grand_total,
    payment_method, payment_status, status, idempotency_key,
    address_id, address_snapshot, customer_note, created_at, updated_at
  ) values (
    v_uid, (v_quote ->> 'subtotal')::numeric, (v_quote ->> 'delivery_total')::numeric,
    (v_quote ->> 'discount_total')::numeric, v_current_total,
    'cod', 'pending', 'pending', trim(p_idempotency_key), p_address_id,
    jsonb_build_object(
      'label', v_address.label, 'description', v_address.description,
      'area', v_address.area, 'landmark', v_address.landmark,
      'phone', trim(p_phone), 'delivery_notes', v_address.delivery_notes,
      'delivery_zones', coalesce(p_delivery_zones, '{}'::jsonb),
      'quote_token', v_quote ->> 'quote_token'
    ),
    nullif(trim(coalesce(p_notes, '')), ''), now(), now()
  ) returning id into v_group_id;

  for v_delivery in
    select * from jsonb_to_recordset(v_quote -> 'deliveries') as x(
      seller_id uuid, store_name text, subtotal numeric, fee numeric, area text
    ) order by seller_id
  loop
    insert into public.orders(
      customer_id, order_group_id, seller_id, subtotal, delivery_fee, discount, total,
      status, payment_method, payment_status, address, phone,
      customer_name_snapshot, store_name_snapshot, address_snapshot, customer_note,
      created_at, updated_at
    ) values (
      v_uid, v_group_id, v_delivery.seller_id, v_delivery.subtotal, v_delivery.fee, 0,
      v_delivery.subtotal + v_delivery.fee, 'pending', 'cod', 'pending',
      v_address_text, trim(p_phone), v_profile.name, v_delivery.store_name,
      jsonb_build_object(
        'label', v_address.label, 'description', v_address.description,
        'area', v_address.area, 'landmark', v_address.landmark,
        'phone', trim(p_phone), 'delivery_notes', v_address.delivery_notes,
        'delivery_zone', v_delivery.area
      ),
      nullif(trim(coalesce(p_notes, '')), ''), now(), now()
    ) returning id into v_order_id;

    for v_line in
      select value from jsonb_array_elements(v_quote -> 'items')
      where (value ->> 'seller_id')::uuid = v_delivery.seller_id
      order by (value ->> 'product_id')::uuid, coalesce(value ->> 'variant_id', '')
    loop
      insert into public.order_items(
        order_id, product_id, seller_id, quantity, unit_price,
        product_name_snapshot, variant_snapshot, discount_snapshot, line_total, created_at
      ) values (
        v_order_id, (v_line ->> 'product_id')::uuid, v_delivery.seller_id,
        (v_line ->> 'quantity')::int, (v_line ->> 'unit_price')::numeric,
        v_line ->> 'name', v_line -> 'variant_snapshot', 0,
        (v_line ->> 'line_total')::numeric, now()
      );

      if nullif(v_line ->> 'variant_id', '') is not null then
        update public.product_variants
        set stock = stock - (v_line ->> 'quantity')::int,
            updated_at = now()
        where id = (v_line ->> 'variant_id')::uuid
          and product_id = (v_line ->> 'product_id')::uuid
          and is_active = true
          and stock >= (v_line ->> 'quantity')::int;
      else
        update public.products
        set stock = stock - (v_line ->> 'quantity')::int,
            is_active = (stock - (v_line ->> 'quantity')::int) > 0,
            updated_at = now()
        where id = (v_line ->> 'product_id')::uuid
          and has_variants = false
          and is_active = true
          and stock >= (v_line ->> 'quantity')::int;
      end if;
      if not found then
        raise exception using errcode = 'P0001', message = 'تغيّر المخزون. راجعي السلة ثم حاولي مرة أخرى';
      end if;
    end loop;

    insert into public.order_status_history(
      order_id, from_status, to_status, changed_by, note, created_at
    ) values (v_order_id, null, 'pending', v_uid, 'Order created', now());
  end loop;

  delete from public.cart_items
  where cart_id in (select c.id from public.carts c where c.user_id = v_uid);
  return v_group_id;
end;
$function$;

-- customer_cancel_order_group
CREATE OR REPLACE FUNCTION public.customer_cancel_order_group(p_order_group_id uuid, p_note text DEFAULT NULL::text)
 RETURNS integer
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_uid uuid := auth.uid();
  v_order record;
  v_count int := 0;
begin
  perform private.require_active_account();
  if v_uid is null then
    raise exception 'Authentication required';
  end if;

  if not exists (
    select 1 from public.order_groups og
    where og.id = p_order_group_id and og.customer_id = v_uid
  ) then
    raise exception 'Order group not found';
  end if;

  if exists (
    select 1 from public.orders o
    where o.order_group_id = p_order_group_id
      and o.customer_id = v_uid
      and o.status <> 'pending'
  ) then
    raise exception 'This order can no longer be cancelled as a group';
  end if;

  for v_order in
    select o.id
    from public.orders o
    where o.order_group_id = p_order_group_id
      and o.customer_id = v_uid
      and o.status = 'pending'
    order by o.id
  loop
    perform public.transition_order_status(v_order.id, 'cancelled', p_note);
    v_count := v_count + 1;
  end loop;

  if v_count = 0 then
    raise exception 'No cancellable orders found';
  end if;

  perform private.refresh_order_group_status(p_order_group_id);
  return v_count;
end;
$function$;

-- merchant_dashboard_summary
CREATE OR REPLACE FUNCTION public.merchant_dashboard_summary()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
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
  perform private.require_active_account();
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
$function$;

-- merchant_delete_product
CREATE OR REPLACE FUNCTION public.merchant_delete_product(p_product_id uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_uid uuid:=auth.uid();
  v_paths jsonb;
begin
  perform private.require_active_account();
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
$function$;

-- quote_cart_v3
CREATE OR REPLACE FUNCTION public.quote_cart_v3(p_items jsonb, p_delivery_zones jsonb DEFAULT '{}'::jsonb)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin
  perform private.require_active_account();
  perform private.assert_delivery_selection_available(p_items,p_delivery_zones);
  return private.checkout_quote_core_v2(p_items,p_delivery_zones,false);
end;
$function$;

-- register_my_push_device
CREATE OR REPLACE FUNCTION public.register_my_push_device(p_provider text, p_token text, p_platform text DEFAULT 'android'::text, p_app_version text DEFAULT NULL::text, p_device_model text DEFAULT NULL::text, p_locale text DEFAULT NULL::text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
    v_uid uuid := (select auth.uid());
    v_id uuid;
    v_provider text := lower(trim(coalesce(p_provider, '')));
    v_platform text := lower(trim(coalesce(p_platform, '')));
    v_token text := trim(coalesce(p_token, ''));
begin
  perform private.require_active_account();
    if v_uid is null then
        raise exception 'authentication required' using errcode = '42501';
    end if;
    if v_provider not in ('fcm','expo','apns') then
        raise exception 'unsupported push provider' using errcode = '22023';
    end if;
    if v_platform not in ('android','ios','web') then
        raise exception 'unsupported platform' using errcode = '22023';
    end if;
    if length(v_token) < 16 or length(v_token) > 4096 then
        raise exception 'invalid push token' using errcode = '22023';
    end if;

    insert into public.push_devices(
        user_id, provider, token, platform, app_version, device_model, locale,
        is_active, last_seen_at, updated_at
    ) values (
        v_uid, v_provider, v_token, v_platform,
        nullif(left(trim(coalesce(p_app_version, '')), 64), ''),
        nullif(left(trim(coalesce(p_device_model, '')), 160), ''),
        nullif(left(trim(coalesce(p_locale, '')), 32), ''),
        true, now(), now()
    )
    on conflict (provider, token) do update
    set user_id = excluded.user_id,
        platform = excluded.platform,
        app_version = excluded.app_version,
        device_model = excluded.device_model,
        locale = excluded.locale,
        is_active = true,
        last_seen_at = now(),
        updated_at = now()
    returning id into v_id;

    return v_id;
end;
$function$;

-- replace_my_delivery_zones
CREATE OR REPLACE FUNCTION public.replace_my_delivery_zones(p_zones jsonb)
 RETURNS SETOF delivery_zones
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_uid uuid := auth.uid();
  v_seller_id uuid;
  v_zone jsonb;
  v_area text;
  v_fee numeric;
  v_minutes integer;
  v_sort integer;
  v_is_active boolean;
  v_active_text text;
  v_index integer := 0;
  v_normalized jsonb := '[]'::jsonb;
  v_first_area text;
  v_first_fee numeric;
  v_first_minutes integer;
begin
  perform private.require_active_account();
  if v_uid is null then
    raise exception using errcode='28000', message='Authentication required';
  end if;

  select mp.seller_id into v_seller_id
  from public.merchant_profiles mp
  where mp.user_id=v_uid
    and mp.verification_status='approved'
    and mp.seller_id is not null
  limit 1;

  if v_seller_id is null then
    raise exception using errcode='42501', message='Approved merchant account required';
  end if;

  if jsonb_typeof(coalesce(p_zones,'[]'::jsonb)) <> 'array'
     or jsonb_array_length(coalesce(p_zones,'[]'::jsonb)) not between 1 and 20 then
    raise exception using errcode='22023', message='At least one delivery zone is required';
  end if;

  for v_zone in select value from jsonb_array_elements(p_zones) loop
    v_area := trim(coalesce(v_zone->>'area',''));
    if char_length(v_area) not between 2 and 100 then
      raise exception using errcode='22023', message='Invalid delivery area';
    end if;

    begin
      v_fee := (v_zone->>'fee')::numeric;
      v_minutes := nullif(v_zone->>'estimated_minutes','')::integer;
      v_sort := coalesce(nullif(v_zone->>'sort_order','')::integer,v_index);
    exception when invalid_text_representation or numeric_value_out_of_range then
      raise exception using errcode='22023', message='Invalid delivery fee or estimate';
    end;

    v_active_text := lower(trim(coalesce(v_zone->>'is_active','true')));
    if v_active_text not in ('true','false') then
      raise exception using errcode='22023', message='Invalid delivery-zone availability';
    end if;
    v_is_active := v_active_text='true';

    if v_fee is null or v_fee < 0 then
      raise exception using errcode='22023', message='Invalid delivery fee';
    end if;
    if v_minutes is not null and v_minutes not between 1 and 1440 then
      raise exception using errcode='22023', message='Invalid delivery estimate';
    end if;
    if exists(
      select 1 from jsonb_array_elements(v_normalized) e
      where lower(trim(e->>'area'))=lower(v_area)
    ) then
      raise exception using errcode='22023', message='Duplicate delivery area';
    end if;

    v_normalized := v_normalized || jsonb_build_array(jsonb_build_object(
      'area',v_area,
      'fee',round(v_fee,2),
      'estimated_minutes',v_minutes,
      'sort_order',v_sort,
      'is_active',v_is_active
    ));
    v_index := v_index+1;
  end loop;

  select e->>'area',(e->>'fee')::numeric,nullif(e->>'estimated_minutes','')::integer
  into v_first_area,v_first_fee,v_first_minutes
  from jsonb_array_elements(v_normalized) e
  where coalesce((e->>'is_active')::boolean,true)
  order by coalesce((e->>'sort_order')::integer,0)
  limit 1;

  if v_first_area is null then
    select e->>'area',(e->>'fee')::numeric,nullif(e->>'estimated_minutes','')::integer
    into v_first_area,v_first_fee,v_first_minutes
    from jsonb_array_elements(v_normalized) e
    order by coalesce((e->>'sort_order')::integer,0)
    limit 1;
  end if;

  update public.merchant_profiles
  set delivery_zones=v_normalized,
      delivery_area=v_first_area,
      delivery_fee=v_first_fee,
      estimated_minutes=v_first_minutes,
      updated_at=now()
  where user_id=v_uid
    and seller_id=v_seller_id
    and verification_status='approved';

  update public.delivery_settings
  set base_fee=v_first_fee,
      delivery_area=v_first_area,
      estimated_minutes=v_first_minutes,
      updated_at=now()
  where seller_id=v_seller_id;

  if not found then
    insert into public.delivery_settings(
      seller_id,base_fee,delivery_area,estimated_minutes,notes,is_active,created_at,updated_at
    ) values(v_seller_id,v_first_fee,v_first_area,v_first_minutes,'',true,now(),now());
  end if;

  return query
  select z.* from public.delivery_zones z
  where z.seller_id=v_seller_id
  order by z.sort_order,z.area_name;
end;
$function$;

-- submit_merchant_application
CREATE OR REPLACE FUNCTION public.submit_merchant_application(p_business_name text, p_description text, p_phone text, p_whatsapp text, p_category_id uuid, p_requested_category text, p_store_name text, p_store_description text, p_city text, p_area text, p_delivery_area text, p_delivery_fee numeric, p_estimated_minutes integer, p_delivery_zones jsonb, p_identity_path text, p_document_type text, p_accept_policies boolean, p_policy_version text DEFAULT '2026-09'::text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
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
  perform private.require_active_account();
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
$function$;

-- sync_my_cart_v2
CREATE OR REPLACE FUNCTION public.sync_my_cart_v2(p_items jsonb)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_uid uuid := auth.uid();
  v_cart_id uuid;
  v_item jsonb;
  v_product record;
  v_variant record;
  v_product_id uuid;
  v_variant_id uuid;
  v_qty int;
begin
  perform private.require_active_account();
  if v_uid is null then
    raise exception using errcode = '28000', message = 'يجب تسجيل الدخول أولاً';
  end if;

  insert into public.carts(user_id, created_at, updated_at)
  values(v_uid, now(), now())
  on conflict (user_id) do update set updated_at = now()
  returning id into v_cart_id;

  delete from public.cart_items where cart_id = v_cart_id;

  for v_item in select value from jsonb_array_elements(private.normalize_checkout_items(p_items))
  loop
    v_product_id := (v_item ->> 'product_id')::uuid;
    v_variant_id := nullif(v_item ->> 'variant_id', '')::uuid;
    v_qty := (v_item ->> 'quantity')::int;

    select p.id, p.stock, p.has_variants into v_product
    from public.products p
    join public.sellers s on s.id = p.seller_id
    where p.id = v_product_id and p.is_active = true and s.verification_status = 'approved';
    if not found then
      raise exception using errcode = 'P0001', message = 'أحد المنتجات لم يعد متاحاً';
    end if;

    if v_product.has_variants then
      if v_variant_id is null then
        raise exception using errcode = '22023', message = 'اختاري المقاس أو اللون للمنتج';
      end if;
      select v.id, v.stock into v_variant
      from public.product_variants v
      where v.id = v_variant_id and v.product_id = v_product_id and v.is_active = true;
      if not found then
        raise exception using errcode = 'P0001', message = 'خيار المنتج لم يعد متاحاً';
      end if;
      if v_variant.stock < v_qty then
        raise exception using errcode = 'P0001', message = 'الكمية المطلوبة من الخيار غير متاحة';
      end if;
    else
      if v_variant_id is not null then
        raise exception using errcode = '22023', message = 'الخيار لا يتبع هذا المنتج';
      end if;
      if v_product.stock < v_qty then
        raise exception using errcode = 'P0001', message = 'الكمية المطلوبة غير متاحة';
      end if;
    end if;

    insert into public.cart_items(cart_id, product_id, variant_id, quantity, created_at, updated_at)
    values(v_cart_id, v_product_id, v_variant_id, v_qty, now(), now());
  end loop;
  return v_cart_id;
end;
$function$;

-- transition_order_status
CREATE OR REPLACE FUNCTION public.transition_order_status(p_order_id uuid, p_to_status text, p_note text DEFAULT NULL::text)
 RETURNS text
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_uid uuid := auth.uid();
  v_order public.orders%rowtype;
  v_role text;
  v_is_seller boolean := false;
  v_allowed boolean := false;
  v_eta_text text;
  v_eta_minutes integer;
begin
  perform private.require_active_account();
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
      and s.verification_status = 'approved'
      and not exists (select 1 from public.merchant_profiles mp where mp.user_id=v_uid and mp.verification_status <> 'approved')
  ) into v_is_seller;

  if v_order.status in ('delivered','cancelled','rejected','failed') then
    raise exception using errcode = 'P0001', message = 'Order is already in a final state';
  end if;

  if v_is_seller and v_role is distinct from 'admin' then
    if p_to_status in ('rejected','cancelled','failed')
       and char_length(trim(coalesce(p_note, ''))) < 3 then
      raise exception using errcode = '22023', message = 'A reason is required for this status';
    end if;

    if p_to_status = 'out_for_delivery' then
      v_eta_text := substring(
        coalesce(p_note, '')
        from 'الوقت المتوقع للوصول: ([0-9]+) دقيقة'
      );
      if v_eta_text is null then
        raise exception using errcode = '22023', message = 'A valid delivery ETA is required';
      end if;
      v_eta_minutes := v_eta_text::integer;
      if v_eta_minutes not between 1 and 1440 then
        raise exception using errcode = '22023', message = 'A valid delivery ETA is required';
      end if;
    end if;
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
    update public.product_variants v
    set stock = v.stock + oi.quantity,
        updated_at = now()
    from public.order_items oi
    where oi.order_id = v_order.id
      and oi.product_id = v.product_id
      and coalesce(oi.variant_snapshot ->> 'id', '') = v.id::text;

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
$function$;

-- unregister_my_push_device
CREATE OR REPLACE FUNCTION public.unregister_my_push_device(p_provider text, p_token text)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
    v_uid uuid := (select auth.uid());
begin
  perform private.require_active_account();
    if v_uid is null then
        raise exception 'authentication required' using errcode = '42501';
    end if;

    delete from public.push_devices
    where user_id = v_uid
      and provider = lower(trim(coalesce(p_provider, '')))
      and token = trim(coalesce(p_token, ''));

    return found;
end;
$function$;

-- weekly_marketplace_kpis
CREATE OR REPLACE FUNCTION public.weekly_marketplace_kpis(p_start timestamp with time zone DEFAULT NULL::timestamp with time zone, p_end timestamp with time zone DEFAULT NULL::timestamp with time zone)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_start timestamptz:=coalesce(p_start,now()-interval '7 days'); v_end timestamptz:=coalesce(p_end,now());
begin
  perform private.require_active_account();
 if coalesce(public.current_user_role(), '') not in ('admin','support') then raise exception 'staff required' using errcode='42501'; end if;
 return jsonb_build_object(
  'start',v_start,'end',v_end,
  'orders',(select count(*) from public.orders where created_at>=v_start and created_at<v_end),
  'completed_orders',(select count(*) from public.orders where status='delivered' and created_at>=v_start and created_at<v_end),
  'cancelled_orders',(select count(*) from public.orders where status in ('cancelled','rejected','failed') and created_at>=v_start and created_at<v_end),
  'gmv',(select coalesce(sum(total),0) from public.orders where status='delivered' and created_at>=v_start and created_at<v_end),
  'active_customers',(select count(distinct customer_id) from public.orders where created_at>=v_start and created_at<v_end),
  'active_merchants',(select count(distinct seller_id) from public.orders where created_at>=v_start and created_at<v_end),
  'repeat_customers',(select count(*) from (select customer_id from public.orders where status='delivered' and created_at<v_end group by customer_id having count(*)>1) q),
  'product_views',(select count(*) from public.app_events where event_name='product_view' and created_at>=v_start and created_at<v_end),
  'searches',(select count(*) from public.app_events where event_name in ('search','search_submitted') and created_at>=v_start and created_at<v_end),
  'complaints',(select count(*) from public.complaints where created_at>=v_start and created_at<v_end),
  'app_errors',(select count(*) from public.app_errors where created_at>=v_start and created_at<v_end)
 );
end; $function$;


-- Restrictive policies also stop a still-valid JWT from reading owner data after suspension/deletion.
DO $policy$
DECLARE r record;
BEGIN
 FOR r IN SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
   WHERE n.nspname='public' AND c.relkind='r'
 LOOP
  EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY',r.relname);
  EXECUTE format('DROP POLICY IF EXISTS tani_active_account_only ON public.%I',r.relname);
  EXECUTE format('CREATE POLICY tani_active_account_only ON public.%I AS RESTRICTIVE FOR ALL TO authenticated USING ((SELECT public.account_is_active())) WITH CHECK ((SELECT public.account_is_active()))',r.relname);
 END LOOP;
END;
$policy$;

DROP POLICY IF EXISTS tani_order_items_staff_read ON public.order_items;
CREATE POLICY tani_order_items_staff_read ON public.order_items FOR SELECT TO authenticated
USING ((SELECT public.current_user_role()) IN ('admin','support'));

-- Only the owner-deletion workflow may change the owner's protected fields.
CREATE OR REPLACE FUNCTION public.protect_profile_authorization_fields()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $function$
BEGIN
 IF auth.uid()=OLD.id AND current_setting('tani.account_deletion',true)=OLD.id::text THEN
   IF NEW.id=OLD.id AND NOT NEW.is_active AND NEW.deleted_at IS NOT NULL AND NEW.role='customer' THEN
     RETURN NEW;
   END IF;
   RAISE EXCEPTION 'Invalid account deletion transition' USING ERRCODE='42501';
 END IF;
 IF auth.uid() IS NOT NULL AND auth.uid()=OLD.id
    AND public.current_user_role() IS DISTINCT FROM 'admin'
    AND (NEW.role IS DISTINCT FROM OLD.role OR NEW.is_active IS DISTINCT FROM OLD.is_active
         OR NEW.deleted_at IS DISTINCT FROM OLD.deleted_at) THEN
   RAISE EXCEPTION 'Protected profile authorization fields cannot be changed by the account owner' USING ERRCODE='42501';
 END IF;
 RETURN NEW;
END;
$function$;
REVOKE ALL ON FUNCTION public.protect_profile_authorization_fields() FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION public.protect_profile_admin_fields()
RETURNS trigger LANGUAGE plpgsql SET search_path = ''
AS $function$
BEGIN
 IF auth.uid()=OLD.id AND current_setting('tani.account_deletion',true)=OLD.id::text
    AND current_user IN ('postgres','supabase_admin')
    AND NEW.id=OLD.id AND NOT NEW.is_active AND NEW.deleted_at IS NOT NULL
    AND NEW.role='customer' THEN RETURN NEW; END IF;
 IF NEW.role IS DISTINCT FROM OLD.role OR NEW.is_active IS DISTINCT FROM OLD.is_active
    OR NEW.email IS DISTINCT FROM OLD.email OR NEW.admin_previous_role IS DISTINCT FROM OLD.admin_previous_role THEN
   IF auth.uid() IS NULL AND current_user IN ('postgres','supabase_admin') THEN RETURN NEW; END IF;
   IF public.current_user_role() IS DISTINCT FROM 'admin' THEN
     RAISE EXCEPTION 'غير مسموح بتعديل البريد أو الصلاحية أو حالة الحساب' USING ERRCODE='42501';
   END IF;
 END IF;
 RETURN NEW;
END;
$function$;
REVOKE ALL ON FUNCTION public.protect_profile_admin_fields() FROM PUBLIC,anon,authenticated;

CREATE TABLE IF NOT EXISTS private.account_deletion_requests (
 user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
 requested_at timestamptz NOT NULL DEFAULT now(),
 status text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','processing','completed','failed')),
 attempts integer NOT NULL DEFAULT 0,
 next_attempt_at timestamptz NOT NULL DEFAULT now(),
 last_error text,
 completed_at timestamptz
);
ALTER TABLE private.account_deletion_requests ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON private.account_deletion_requests FROM PUBLIC,anon,authenticated;
GRANT ALL ON private.account_deletion_requests TO service_role;

CREATE OR REPLACE FUNCTION public.soft_delete_my_account()
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $function$
DECLARE
 v_uid uuid := auth.uid();
 v_profile public.profiles%rowtype;
BEGIN
 IF v_uid IS NULL THEN RAISE EXCEPTION 'authentication required' USING ERRCODE='28000'; END IF;
 SELECT * INTO v_profile FROM public.profiles WHERE id=v_uid FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Account profile not found' USING ERRCODE='P0002'; END IF;
 IF v_profile.deleted_at IS NOT NULL THEN RETURN true; END IF;
 PERFORM private.require_active_account();
 IF v_profile.role='admin' AND NOT EXISTS (
   SELECT 1 FROM public.profiles WHERE role='admin' AND is_active AND deleted_at IS NULL AND id<>v_uid
 ) THEN RAISE EXCEPTION 'Cannot delete the last active admin' USING ERRCODE='23514'; END IF;

 PERFORM set_config('tani.merchant_workflow','submit',true);
 PERFORM set_config('tani.account_deletion',v_uid::text,true);
 UPDATE public.products p SET is_active=false,updated_at=now()
 WHERE EXISTS(SELECT 1 FROM public.sellers s WHERE s.id=p.seller_id AND s.user_id=v_uid);
 UPDATE public.stores st SET is_active=false,is_open=false,contact_phone=NULL,whatsapp=NULL,updated_at=now()
 WHERE EXISTS(SELECT 1 FROM public.sellers s WHERE s.id=st.seller_id AND s.user_id=v_uid)
    OR EXISTS(SELECT 1 FROM public.merchant_profiles mp WHERE mp.id=st.merchant_id AND mp.user_id=v_uid);
 UPDATE public.merchant_profiles SET verification_status='suspended',suspended_at=coalesce(suspended_at,now()),
   trust_badge=false,phone=NULL,whatsapp=NULL,area=NULL,review_note='Account deleted by owner',updated_at=now()
 WHERE user_id=v_uid;
 UPDATE public.sellers SET verification_status='suspended' WHERE user_id=v_uid;
 UPDATE public.profiles SET is_active=false,deleted_at=now(),role='customer',admin_previous_role=NULL,
   name='محذوف',phone='deleted-'||left(md5(v_uid::text),22),email=NULL,avatar_url=NULL,updated_at=now()
 WHERE id=v_uid;
 UPDATE public.order_groups SET address_id=NULL WHERE customer_id=v_uid;
 DELETE FROM public.addresses WHERE user_id=v_uid;
 DELETE FROM public.carts WHERE user_id=v_uid;
 DELETE FROM public.favorites WHERE user_id=v_uid;
 DELETE FROM public.push_devices WHERE user_id=v_uid;
 INSERT INTO private.account_deletion_requests(user_id) VALUES(v_uid) ON CONFLICT(user_id) DO NOTHING;
 INSERT INTO public.audit_logs(actor_id,action,entity_type,entity_id,source)
 VALUES(v_uid,'soft_delete','profile',v_uid,'account');
 RETURN true;
END;
$function$;
REVOKE ALL ON FUNCTION public.soft_delete_my_account() FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.soft_delete_my_account() TO authenticated;

NOTIFY pgrst,'reload schema';
