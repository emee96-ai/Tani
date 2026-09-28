-- TANI phase 5 follow-up — preserve legacy sellers that do not yet have a stores row.
-- An explicitly closed/inactive store blocks new delivery orders; absence of a
-- stores row does not override otherwise valid delivery settings.

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
  and (
    not exists(select 1 from public.stores st where st.seller_id=delivery_zones.seller_id)
    or exists(
      select 1 from public.stores st
      where st.seller_id=delivery_zones.seller_id
        and st.is_active and st.is_open
    )
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
      where d.seller_id=p_seller_id and d.is_active
    ) then jsonb_build_object('seller_id',p_seller_id,'area',p_area,'available',false)
    when exists(select 1 from public.stores st where st.seller_id=p_seller_id)
         and not exists(
           select 1 from public.stores st
           where st.seller_id=p_seller_id and st.is_active and st.is_open
         )
      then jsonb_build_object('seller_id',p_seller_id,'area',p_area,'available',false)
    when p_city is not null
         and not exists(
           select 1 from public.stores st
           where st.seller_id=p_seller_id
             and st.is_active and st.is_open
             and lower(coalesce(st.city,''))=lower(trim(p_city))
         )
      then jsonb_build_object('seller_id',p_seller_id,'area',p_area,'available',false)
    else coalesce(
      (
        select jsonb_build_object(
          'seller_id',z.seller_id,
          'provider','merchant_delivery',
          'fee',z.fee,
          'estimated_minutes',z.estimated_minutes,
          'delivery_area',z.area_name,
          'city',(
            select st.city from public.stores st
            where st.seller_id=z.seller_id and st.is_active and st.is_open
            order by st.id limit 1
          ),
          'area',p_area,
          'available',true
        )
        from public.delivery_zones z
        join public.sellers s on s.id=z.seller_id and s.verification_status='approved'
        join public.delivery_settings d on d.seller_id=z.seller_id and d.is_active
        where z.seller_id=p_seller_id
          and z.is_active
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
            'city',(
              select st.city from public.stores st
              where st.seller_id=d.seller_id and st.is_active and st.is_open
              order by st.id limit 1
            ),
            'area',p_area,
            'available',true
          )
          from public.delivery_settings d
          join public.sellers s on s.id=d.seller_id and s.verification_status='approved'
          where d.seller_id=p_seller_id
            and d.is_active
            and not exists(select 1 from public.delivery_zones z where z.seller_id=d.seller_id)
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
    left join lateral (
      select st.name
      from public.stores st
      where st.seller_id=p.seller_id and st.is_active
      order by st.id
      limit 1
    ) st on true
  loop
    if not exists(
      select 1 from public.delivery_settings d
      where d.seller_id=v_seller.seller_id and d.is_active
    ) then
      raise exception using errcode='P0001', message=format('التوصيل متوقف مؤقتاً لدى %s',v_seller.store_name);
    end if;

    if exists(select 1 from public.stores st where st.seller_id=v_seller.seller_id)
       and not exists(
         select 1 from public.stores st
         where st.seller_id=v_seller.seller_id and st.is_active and st.is_open
       ) then
      raise exception using errcode='P0001', message=format('المتجر مغلق حالياً: %s',v_seller.store_name);
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
    where d.seller_id=new.seller_id and d.is_active
  ) then
    raise exception using errcode='P0001', message='التوصيل متوقف مؤقتاً لهذا المتجر';
  end if;

  if exists(select 1 from public.stores st where st.seller_id=new.seller_id)
     and not exists(
       select 1 from public.stores st
       where st.seller_id=new.seller_id and st.is_active and st.is_open
     ) then
    raise exception using errcode='P0001', message='المتجر مغلق حالياً';
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
