-- Tani phase 7 regression fix.
-- A %rowtype variable must receive a complete product_variants row. Selecting a
-- subset into it shifts fields by physical column order (for example name into
-- product_id), which breaks real variant cart/quote flows.

create or replace function public.sync_my_cart_v2(p_items jsonb)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_cart_id uuid;
  v_item jsonb;
  v_product record;
  v_variant public.product_variants%rowtype;
  v_product_id uuid;
  v_variant_id uuid;
  v_qty int;
begin
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
      select v.* into v_variant
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
$$;

revoke all on function public.sync_my_cart_v2(jsonb) from public, anon;
grant execute on function public.sync_my_cart_v2(jsonb) to authenticated;

create or replace function private.checkout_quote_core_v2(
  p_items jsonb,
  p_delivery_zones jsonb,
  p_lock_inventory boolean default false
)
returns jsonb
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_item jsonb;
  v_product record;
  v_variant public.product_variants%rowtype;
  v_seller record;
  v_product_id uuid;
  v_variant_id uuid;
  v_qty int;
  v_unit_price numeric(14,2);
  v_available_stock int;
  v_variant_snapshot jsonb;
  v_items jsonb := '[]'::jsonb;
  v_deliveries jsonb := '[]'::jsonb;
  v_subtotal numeric(14,2) := 0;
  v_delivery_total numeric(14,2) := 0;
  v_delivery_fee numeric(14,2);
  v_delivery_area text;
  v_selected_zone_id text;
  v_quote_token text;
begin
  if v_uid is null then
    raise exception using errcode = '28000', message = 'يجب تسجيل الدخول أولاً';
  end if;
  if not exists (
    select 1 from public.profiles p
    where p.id = v_uid and p.is_active = true and p.deleted_at is null
  ) then
    raise exception using errcode = '28000', message = 'الحساب غير نشط';
  end if;
  if jsonb_typeof(coalesce(p_delivery_zones, '{}'::jsonb)) <> 'object' then
    raise exception using errcode = '22023', message = 'اختيارات مناطق التوصيل غير صحيحة';
  end if;

  for v_item in select value from jsonb_array_elements(private.normalize_checkout_items(p_items))
  loop
    v_product_id := (v_item ->> 'product_id')::uuid;
    v_variant_id := nullif(v_item ->> 'variant_id', '')::uuid;
    v_qty := (v_item ->> 'quantity')::int;

    if p_lock_inventory then
      select p.id, p.seller_id, p.name, p.price, p.stock, p.has_variants,
             coalesce(st.name, s.store_name) as store_name
      into v_product
      from public.products p
      join public.sellers s on s.id = p.seller_id
      left join public.stores st on st.seller_id = p.seller_id and st.is_active = true
      where p.id = v_product_id and p.is_active = true and s.verification_status = 'approved'
      for update of p;
    else
      select p.id, p.seller_id, p.name, p.price, p.stock, p.has_variants,
             coalesce(st.name, s.store_name) as store_name
      into v_product
      from public.products p
      join public.sellers s on s.id = p.seller_id
      left join public.stores st on st.seller_id = p.seller_id and st.is_active = true
      where p.id = v_product_id and p.is_active = true and s.verification_status = 'approved';
    end if;
    if not found then
      raise exception using errcode = 'P0001', message = 'أحد المنتجات لم يعد متاحاً';
    end if;

    v_variant_snapshot := null;
    if v_product.has_variants then
      if v_variant_id is null then
        raise exception using errcode = '22023', message = format('اختاري المقاس أو اللون للمنتج: %s', v_product.name);
      end if;
      if p_lock_inventory then
        select v.* into v_variant
        from public.product_variants v
        where v.id = v_variant_id and v.product_id = v_product_id and v.is_active = true
        for update of v;
      else
        select v.* into v_variant
        from public.product_variants v
        where v.id = v_variant_id and v.product_id = v_product_id and v.is_active = true;
      end if;
      if not found then
        raise exception using errcode = 'P0001', message = format('الخيار المختار لم يعد متاحاً للمنتج: %s', v_product.name);
      end if;
      v_unit_price := coalesce(v_variant.price, v_product.price);
      v_available_stock := v_variant.stock;
      v_variant_snapshot := jsonb_build_object(
        'id', v_variant.id,
        'name', v_variant.name,
        'sku', v_variant.sku,
        'attributes', v_variant.attributes
      );
    else
      if v_variant_id is not null then
        raise exception using errcode = '22023', message = 'الخيار لا يتبع هذا المنتج';
      end if;
      v_unit_price := v_product.price;
      v_available_stock := v_product.stock;
    end if;

    if v_available_stock < v_qty then
      raise exception using errcode = 'P0001', message = format('الكمية المطلوبة غير متاحة للمنتج: %s', v_product.name);
    end if;

    v_items := v_items || jsonb_build_array(jsonb_build_object(
      'product_id', v_product.id,
      'variant_id', v_variant_id,
      'variant_name', case when v_variant_id is null then null else v_variant.name end,
      'variant_snapshot', v_variant_snapshot,
      'seller_id', v_product.seller_id,
      'name', v_product.name,
      'store_name', v_product.store_name,
      'unit_price', v_unit_price,
      'quantity', v_qty,
      'stock', v_available_stock,
      'available', true,
      'line_total', round(v_unit_price * v_qty, 2)
    ));
    v_subtotal := v_subtotal + round(v_unit_price * v_qty, 2);
  end loop;

  for v_seller in
    select seller_id::uuid as seller_id,
           max(store_name) as store_name,
           sum(line_total)::numeric(14,2) as subtotal
    from jsonb_to_recordset(v_items)
      as x(seller_id text, store_name text, line_total numeric)
    group by seller_id
    order by seller_id
  loop
    v_delivery_fee := null;
    v_delivery_area := null;
    if exists (
      select 1 from public.delivery_zones z
      where z.seller_id = v_seller.seller_id and z.is_active = true
    ) then
      v_selected_zone_id := nullif(trim(coalesce(p_delivery_zones ->> v_seller.seller_id::text, '')), '');
      if v_selected_zone_id is null then
        raise exception using errcode = '22023', message = format('اختاري منطقة التوصيل من %s', v_seller.store_name);
      end if;
      select z.fee, z.area_name into v_delivery_fee, v_delivery_area
      from public.delivery_zones z
      where z.id::text = v_selected_zone_id
        and z.seller_id = v_seller.seller_id
        and z.is_active = true
      limit 1;
      if not found then
        raise exception using errcode = '22023', message = format('منطقة التوصيل المختارة غير متاحة لدى %s', v_seller.store_name);
      end if;
    else
      select d.base_fee, d.delivery_area into v_delivery_fee, v_delivery_area
      from public.delivery_settings d
      where d.seller_id = v_seller.seller_id and d.is_active = true
      limit 1;
      if not found then
        raise exception using errcode = 'P0001', message = format('إعدادات التوصيل غير مكتملة لدى %s', v_seller.store_name);
      end if;
    end if;
    v_delivery_fee := round(coalesce(v_delivery_fee, 0), 2);
    v_delivery_total := v_delivery_total + v_delivery_fee;
    v_deliveries := v_deliveries || jsonb_build_array(jsonb_build_object(
      'seller_id', v_seller.seller_id,
      'store_name', v_seller.store_name,
      'subtotal', v_seller.subtotal,
      'fee', v_delivery_fee,
      'area', v_delivery_area
    ));
  end loop;

  v_quote_token := md5(
    v_items::text || '|' || v_deliveries::text || '|' ||
    round(v_subtotal + v_delivery_total, 2)::text
  );
  return jsonb_build_object(
    'subtotal', round(v_subtotal, 2),
    'delivery_total', round(v_delivery_total, 2),
    'discount_total', 0,
    'grand_total', round(v_subtotal + v_delivery_total, 2),
    'items', v_items,
    'deliveries', v_deliveries,
    'warnings', '[]'::jsonb,
    'quote_token', v_quote_token
  );
end;
$$;

revoke all on function private.checkout_quote_core_v2(jsonb,jsonb,boolean) from public, anon, authenticated;
