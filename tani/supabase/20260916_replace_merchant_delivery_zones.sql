-- TANI — allow an approved merchant to edit per-area delivery fees safely.
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
  v_index integer := 0;
  v_normalized jsonb := '[]'::jsonb;
  v_first_area text;
  v_first_fee numeric;
  v_first_minutes integer;
begin
  if v_uid is null then raise exception 'Authentication required'; end if;

  select mp.seller_id into v_seller_id
  from public.merchant_profiles mp
  where mp.user_id = v_uid
    and mp.verification_status = 'approved'
    and mp.seller_id is not null
  limit 1;

  if v_seller_id is null then raise exception 'Approved merchant account required'; end if;

  if jsonb_typeof(coalesce(p_zones, '[]'::jsonb)) <> 'array'
     or jsonb_array_length(coalesce(p_zones, '[]'::jsonb)) not between 1 and 20 then
    raise exception 'At least one delivery zone is required';
  end if;

  for v_zone in select value from jsonb_array_elements(p_zones) loop
    v_area := trim(coalesce(v_zone->>'area', ''));
    if char_length(v_area) not between 2 and 100 then raise exception 'Invalid delivery area'; end if;
    begin
      v_fee := (v_zone->>'fee')::numeric;
      v_minutes := nullif(v_zone->>'estimated_minutes', '')::integer;
      v_sort := coalesce(nullif(v_zone->>'sort_order', '')::integer, v_index);
    exception when invalid_text_representation or numeric_value_out_of_range then
      raise exception 'Invalid delivery fee or estimate';
    end;
    if v_fee is null or v_fee < 0 then raise exception 'Invalid delivery fee'; end if;
    if v_minutes is not null and v_minutes not between 1 and 1440 then raise exception 'Invalid delivery estimate'; end if;
    if exists (
      select 1 from jsonb_array_elements(v_normalized) e
      where lower(trim(e->>'area')) = lower(v_area)
    ) then raise exception 'Duplicate delivery area'; end if;

    v_normalized := v_normalized || jsonb_build_array(jsonb_build_object(
      'area', v_area,
      'fee', v_fee,
      'estimated_minutes', v_minutes,
      'sort_order', v_sort
    ));
    if v_index = 0 then
      v_first_area := v_area;
      v_first_fee := v_fee;
      v_first_minutes := v_minutes;
    end if;
    v_index := v_index + 1;
  end loop;

  update public.merchant_profiles
  set delivery_zones = v_normalized,
      delivery_area = v_first_area,
      delivery_fee = v_first_fee,
      estimated_minutes = v_first_minutes,
      updated_at = now()
  where user_id = v_uid
    and seller_id = v_seller_id
    and verification_status = 'approved';

  update public.delivery_settings
  set base_fee = v_first_fee,
      delivery_area = v_first_area,
      estimated_minutes = v_first_minutes,
      updated_at = now()
  where seller_id = v_seller_id;

  if not found then
    insert into public.delivery_settings(
      seller_id, base_fee, delivery_area, estimated_minutes, notes, is_active, created_at, updated_at
    ) values (
      v_seller_id, v_first_fee, v_first_area, v_first_minutes, '', true, now(), now()
    );
  end if;

  return query
  select z.* from public.delivery_zones z
  where z.seller_id = v_seller_id
  order by z.sort_order, z.area_name;
end;
$$;

revoke all on function public.replace_my_delivery_zones(jsonb) from public, anon;
grant execute on function public.replace_my_delivery_zones(jsonb) to authenticated;
