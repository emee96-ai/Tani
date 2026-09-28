-- TANI merchant UX phase 5 — delivery configuration and service-area hardening.
-- Per-zone availability is preserved, the global delivery switch is authoritative,
-- and checkout is protected from stale/disabled delivery configuration.

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
      case lower(coalesce(z.value->>'is_active','true'))
        when 'false' then false
        else true
      end,
      now(),
      now()
    from jsonb_array_elements(new.delivery_zones) with ordinality as z(value,ordinality);
  end if;
  return new;
end;
$$;

revoke all on function private.sync_merchant_delivery_zones() from public,anon,authenticated;

create or replace function public.replace_my_delivery_zones(p_zones jsonb)
returns setof public.delivery_zones
language plpgsql
security definer
set search_path=''
as $$
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

  -- Keep the legacy default fields pointed at the first active zone when possible.
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
$$;

revoke all on function public.replace_my_delivery_zones(jsonb) from public,anon;
grant execute on function public.replace_my_delivery_zones(jsonb) to authenticated;

create or replace function public.save_my_delivery_configuration(
  p_zones jsonb,
  p_notes text default null,
  p_is_active boolean default true
)
returns setof public.delivery_zones
language plpgsql
security invoker
set search_path=''
as $$
declare
  v_uid uuid := auth.uid();
  v_seller_id uuid;
begin
  if v_uid is null then
    raise exception using errcode='28000', message='Authentication required';
  end if;
  if char_length(coalesce(p_notes,'')) > 500 then
    raise exception using errcode='22023', message='Delivery notes are too long';
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

  perform public.replace_my_delivery_zones(p_zones);

  if coalesce(p_is_active,false)
     and not exists(
       select 1 from public.delivery_zones z
       where z.seller_id=v_seller_id and z.is_active
     ) then
    raise exception using errcode='22023', message='Enable at least one delivery zone';
  end if;

  update public.delivery_settings d
  set notes=nullif(trim(coalesce(p_notes,'')),''),
      is_active=coalesce(p_is_active,false),
      updated_at=now()
  where d.seller_id=v_seller_id;
  if not found then
    raise exception using errcode='P0001', message='Delivery settings are incomplete';
  end if;

  return query
  select z.* from public.delivery_zones z
  where z.seller_id=v_seller_id
  order by z.sort_order,z.area_name;
end;
$$;

revoke all on function public.save_my_delivery_configuration(jsonb,text,boolean) from public,anon;
grant execute on function public.save_my_delivery_configuration(jsonb,text,boolean) to authenticated;

-- Customers must not see zones while delivery or the store itself is unavailable.
drop policy if exists tani_delivery_zones_public_read on public.delivery_zones;
create policy tani_delivery_zones_public_read
on public.delivery_zones for select to anon,authenticated
using(
  is_active
  and exists(
    select 1 from public.sellers s
    where s.id=delivery_zones.seller_id
      and s.verification_status='approved'
  )
  and exists(
    select 1 from public.delivery_settings d
    where d.seller_id=delivery_zones.seller_id and d.is_active
  )
  and exists(
    select 1 from public.stores st
    where st.seller_id=delivery_zones.seller_id
      and st.is_active and st.is_open
  )
);

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
  select case
    when not exists(
      select 1
      from public.delivery_settings d
      join public.sellers s on s.id=d.seller_id and s.verification_status='approved'
      join public.stores st on st.seller_id=d.seller_id and st.is_active and st.is_open
      where d.seller_id=p_seller_id
        and d.is_active
        and (p_city is null or lower(coalesce(st.city,''))=lower(trim(p_city)))
    ) then jsonb_build_object('seller_id',p_seller_id,'area',p_area,'available',false)
    else coalesce(
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
        join public.delivery_settings d on d.seller_id=z.seller_id and d.is_active
        join public.stores st on st.seller_id=z.seller_id and st.is_active and st.is_open
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
            'available',true
          )
          from public.delivery_settings d
          join public.sellers s on s.id=d.seller_id and s.verification_status='approved'
          join public.stores st on st.seller_id=d.seller_id and st.is_active and st.is_open
          where d.seller_id=p_seller_id
            and d.is_active
            and not exists(select 1 from public.delivery_zones z where z.seller_id=d.seller_id)
            and (p_city is null or lower(coalesce(st.city,''))=lower(trim(p_city)))
          limit 1
        )
      end,
      jsonb_build_object('seller_id',p_seller_id,'area',p_area,'available',false)
    )
  end;
$$;

create or replace function private.assert_delivery_selection_available(
  p_items jsonb,
  p_delivery_zones jsonb
)
returns void
language plpgsql
security definer
set search_path=''
as $$
declare
  v_seller record;
  v_selected_zone text;
begin
  if jsonb_typeof(coalesce(p_delivery_zones,'{}'::jsonb)) <> 'object' then
    raise exception using errcode='22023', message='اختيارات مناطق التوصيل غير صحيحة';
  end if;

  for v_seller in
    select distinct p.seller_id,coalesce(st.name,s.store_name) as store_name
    from jsonb_array_elements(private.normalize_checkout_items(p_items)) i
    join public.products p on p.id=(i.value->>'product_id')::uuid
    join public.sellers s on s.id=p.seller_id
    left join public.stores st on st.seller_id=p.seller_id and st.is_active
  loop
    if not exists(
      select 1 from public.delivery_settings d
      join public.stores st on st.seller_id=d.seller_id and st.is_active and st.is_open
      where d.seller_id=v_seller.seller_id and d.is_active
    ) then
      raise exception using errcode='P0001', message=format('التوصيل متوقف مؤقتاً لدى %s',v_seller.store_name);
    end if;

    if exists(select 1 from public.delivery_zones z where z.seller_id=v_seller.seller_id) then
      v_selected_zone := nullif(trim(coalesce(p_delivery_zones->>v_seller.seller_id::text,'')),'');
      if v_selected_zone is null then
        raise exception using errcode='22023', message=format('اختاري منطقة التوصيل من %s',v_seller.store_name);
      end if;
      if not exists(
        select 1 from public.delivery_zones z
        where z.id::text=v_selected_zone
          and z.seller_id=v_seller.seller_id
          and z.is_active
      ) then
        raise exception using errcode='22023', message=format('منطقة التوصيل المختارة غير متاحة لدى %s',v_seller.store_name);
      end if;
    end if;
  end loop;
end;
$$;

revoke all on function private.assert_delivery_selection_available(jsonb,jsonb) from public,anon,authenticated;

create or replace function public.quote_cart_v3(
  p_items jsonb,
  p_delivery_zones jsonb default '{}'::jsonb
)
returns jsonb
language plpgsql
security definer
set search_path=''
as $$
begin
  perform private.assert_delivery_selection_available(p_items,p_delivery_zones);
  return private.checkout_quote_core_v2(p_items,p_delivery_zones,false);
end;
$$;

revoke all on function public.quote_cart_v3(jsonb,jsonb) from public,anon;
grant execute on function public.quote_cart_v3(jsonb,jsonb) to authenticated;

create or replace function private.enforce_checkout_delivery_snapshot()
returns trigger
language plpgsql
security definer
set search_path=''
as $$
declare
  v_area text;
begin
  if new.order_group_id is null then
    return new;
  end if;

  if not exists(
    select 1 from public.delivery_settings d
    join public.stores st on st.seller_id=d.seller_id and st.is_active and st.is_open
    where d.seller_id=new.seller_id and d.is_active
  ) then
    raise exception using errcode='P0001', message='التوصيل متوقف مؤقتاً لهذا المتجر';
  end if;

  if exists(select 1 from public.delivery_zones z where z.seller_id=new.seller_id) then
    v_area := nullif(trim(coalesce(new.address_snapshot->>'delivery_zone','')),'');
    if v_area is null or not exists(
      select 1 from public.delivery_zones z
      where z.seller_id=new.seller_id
        and z.is_active
        and lower(trim(z.area_name))=lower(v_area)
    ) then
      raise exception using errcode='P0001', message='منطقة التوصيل المختارة لم تعد متاحة';
    end if;
  end if;

  return new;
end;
$$;

revoke all on function private.enforce_checkout_delivery_snapshot() from public,anon,authenticated;
drop trigger if exists tani_checkout_delivery_snapshot_guard on public.orders;
create trigger tani_checkout_delivery_snapshot_guard
before insert on public.orders
for each row execute function private.enforce_checkout_delivery_snapshot();
