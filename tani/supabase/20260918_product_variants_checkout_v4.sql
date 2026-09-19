-- TANI phase 2 — end-to-end product variants in catalog, cart, quote and checkout.

alter table public.products
  add column if not exists has_variants boolean not null default false;

update public.products p
set has_variants = exists (
  select 1 from public.product_variants v where v.product_id = p.id
);

create or replace function private.refresh_product_has_variants()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_product_id uuid := coalesce(new.product_id, old.product_id);
begin
  update public.products p
  set has_variants = exists (
        select 1 from public.product_variants v where v.product_id = v_product_id
      ),
      updated_at = now()
  where p.id = v_product_id;

  if tg_op = 'UPDATE' and old.product_id is distinct from new.product_id then
    update public.products p
    set has_variants = exists (
          select 1 from public.product_variants v where v.product_id = old.product_id
        ),
        updated_at = now()
    where p.id = old.product_id;
  end if;
  if tg_op = 'DELETE' then
    return old;
  end if;

  return new;
end;
$$;

revoke all on function private.refresh_product_has_variants() from public, anon, authenticated;

drop trigger if exists tani_refresh_product_has_variants on public.product_variants;
create trigger tani_refresh_product_has_variants
after insert or delete or update of product_id on public.product_variants
for each row execute function private.refresh_product_has_variants();

create or replace view public.marketplace_product_cards
with (security_invoker = true) as
select
  p.id,
  p.seller_id,
  p.category_id,
  c.name as category_name,
  p.name,
  p.description,
  (case when p.has_variants then coalesce(vs.min_price, p.price) else p.price end)::numeric(12,2) as price,
  case when p.has_variants then coalesce(vs.available_stock, 0) else p.stock end::integer as stock,
  coalesce(p.image, pi.storage_path) as image,
  p.created_at,
  coalesce(st.name, s.store_name) as store_name,
  s.verification_status,
  coalesce(round(avg(r.rating)::numeric, 2), 0::numeric) as average_rating,
  count(r.id)::int as review_count,
  coalesce(ds.base_fee, 0::numeric) as delivery_fee,
  ds.delivery_area,
  ds.estimated_minutes,
  p.has_variants
from public.products p
join public.sellers s on s.id = p.seller_id
left join public.stores st on st.seller_id = p.seller_id and st.is_active = true
left join public.categories c on c.id = p.category_id
left join lateral (
  select pimg.storage_path
  from public.product_images pimg
  where pimg.product_id = p.id and pimg.media_type = 'image'
  order by pimg.is_primary desc, pimg.sort_order asc, pimg.created_at asc
  limit 1
) pi on true
left join lateral (
  select
    min(coalesce(v.price, p.price)) filter (where v.is_active) as min_price,
    coalesce(sum(v.stock) filter (where v.is_active), 0)::integer as available_stock
  from public.product_variants v
  where v.product_id = p.id
) vs on true
left join public.reviews r on r.product_id = p.id
left join lateral (
  select d.base_fee, d.delivery_area, d.estimated_minutes
  from public.delivery_settings d
  where d.seller_id = p.seller_id and d.is_active = true
  order by d.updated_at desc, d.created_at desc
  limit 1
) ds on true
where p.is_active = true and s.verification_status = 'approved'
group by p.id, p.seller_id, p.category_id, c.name, p.name, p.description,
  p.price, p.stock, p.image, pi.storage_path, p.created_at, st.name, s.store_name,
  s.verification_status, ds.base_fee, ds.delivery_area, ds.estimated_minutes,
  p.has_variants, vs.min_price, vs.available_stock;

grant select on public.marketplace_product_cards to anon, authenticated;

create or replace view public.my_cart_items
with (security_invoker = true) as
select
  ci.id as cart_item_id,
  ci.cart_id,
  ci.product_id,
  ci.variant_id,
  ci.quantity,
  p.seller_id,
  p.category_id,
  p.name,
  p.description,
  p.price,
  p.stock,
  coalesce(p.image, pi.storage_path) as image,
  coalesce(st.name, s.store_name) as store_name,
  coalesce(ds.base_fee, 0::numeric) as delivery_fee,
  ds.delivery_area,
  ds.estimated_minutes,
  ci.updated_at,
  pv.name as variant_name,
  pv.sku as variant_sku,
  pv.price as variant_price,
  pv.stock as variant_stock,
  pv.attributes as variant_attributes
from public.cart_items ci
join public.carts c on c.id = ci.cart_id
join public.products p on p.id = ci.product_id
join public.sellers s on s.id = p.seller_id
left join public.product_variants pv on pv.id = ci.variant_id and pv.product_id = p.id
left join public.stores st on st.seller_id = p.seller_id and st.is_active = true
left join lateral (
  select pimg.storage_path
  from public.product_images pimg
  where pimg.product_id = p.id and pimg.media_type = 'image'
  order by pimg.is_primary desc, pimg.sort_order asc, pimg.created_at asc
  limit 1
) pi on true
left join public.delivery_settings ds on ds.seller_id = p.seller_id and ds.is_active = true
where c.user_id = (select auth.uid())
  and p.is_active = true
  and s.verification_status = 'approved'
  and (
    (p.has_variants = true and ci.variant_id is not null and pv.is_active = true)
    or (p.has_variants = false and ci.variant_id is null)
  );

grant select on public.my_cart_items to authenticated;

create or replace function private.normalize_checkout_items(p_items jsonb)
returns jsonb
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_item jsonb;
  v_product_text text;
  v_variant_text text;
  v_quantity_text text;
  v_key text;
  v_qty int;
  v_total int;
  v_quantities jsonb := '{}'::jsonb;
  v_result jsonb := '[]'::jsonb;
begin
  if jsonb_typeof(coalesce(p_items, '[]'::jsonb)) <> 'array'
     or jsonb_array_length(coalesce(p_items, '[]'::jsonb)) = 0 then
    raise exception using errcode = '22023', message = 'السلة فارغة أو بياناتها غير صحيحة';
  end if;
  if jsonb_array_length(p_items) > 100 then
    raise exception using errcode = '22023', message = 'السلة تحتوي على منتجات كثيرة جداً';
  end if;

  for v_item in select value from jsonb_array_elements(p_items)
  loop
    if jsonb_typeof(v_item) <> 'object' then
      raise exception using errcode = '22023', message = 'أحد عناصر السلة غير صحيح';
    end if;
    v_product_text := lower(trim(coalesce(v_item ->> 'product_id', '')));
    v_variant_text := lower(trim(coalesce(v_item ->> 'variant_id', '')));
    v_quantity_text := trim(coalesce(v_item ->> 'quantity', ''));

    if v_product_text !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$' then
      raise exception using errcode = '22023', message = 'معرّف أحد المنتجات غير صحيح';
    end if;
    if v_variant_text <> '' and v_variant_text !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$' then
      raise exception using errcode = '22023', message = 'معرّف خيار المنتج غير صحيح';
    end if;
    if v_quantity_text !~ '^[0-9]{1,3}$' then
      raise exception using errcode = '22023', message = 'كمية أحد المنتجات غير صحيحة';
    end if;

    v_qty := v_quantity_text::int;
    if v_qty < 1 then
      raise exception using errcode = '22023', message = 'الكمية يجب أن تكون 1 على الأقل';
    end if;
    v_key := v_product_text || '|' || v_variant_text;
    v_total := coalesce((v_quantities ->> v_key)::int, 0) + v_qty;
    if v_total > 99 then
      raise exception using errcode = '22023', message = 'الحد الأقصى للخيار الواحد هو 99';
    end if;
    v_quantities := jsonb_set(v_quantities, array[v_key], to_jsonb(v_total), true);
  end loop;

  for v_key, v_quantity_text in
    select key, value from jsonb_each_text(v_quantities) order by key
  loop
    v_result := v_result || jsonb_build_array(jsonb_build_object(
      'product_id', split_part(v_key, '|', 1),
      'variant_id', nullif(split_part(v_key, '|', 2), ''),
      'quantity', v_quantity_text::int
    ));
  end loop;
  return v_result;
end;
$$;

revoke all on function private.normalize_checkout_items(jsonb) from public, anon, authenticated;

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
  v_variant record;
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
  v_variant record;
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
        select v.id, v.name, v.sku, v.price, v.stock, v.attributes
        into v_variant
        from public.product_variants v
        where v.id = v_variant_id and v.product_id = v_product_id and v.is_active = true
        for update of v;
      else
        select v.id, v.name, v.sku, v.price, v.stock, v.attributes
        into v_variant
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

create or replace function public.quote_cart_v3(
  p_items jsonb,
  p_delivery_zones jsonb default '{}'::jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
begin
  return private.checkout_quote_core_v2(p_items, p_delivery_zones, false);
end;
$$;

revoke all on function public.quote_cart_v3(jsonb,jsonb) from public, anon;
grant execute on function public.quote_cart_v3(jsonb,jsonb) to authenticated;

create or replace function public.checkout_create_order_group_v4(
  p_address_id uuid,
  p_phone text,
  p_notes text,
  p_items jsonb,
  p_idempotency_key text,
  p_delivery_zones jsonb,
  p_expected_grand_total numeric,
  p_quote_token text
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
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
$$;

revoke all on function public.checkout_create_order_group_v4(uuid,text,text,jsonb,text,jsonb,numeric,text) from public, anon;
grant execute on function public.checkout_create_order_group_v4(uuid,text,text,jsonb,text,jsonb,numeric,text) to authenticated;

-- Older contracts do not carry variant ids. Disable them once products can
-- require an option so legacy clients cannot silently order the base product.
revoke execute on function public.sync_my_cart(jsonb) from authenticated;
revoke execute on function public.quote_cart(jsonb) from authenticated;
revoke execute on function public.quote_cart_v2(jsonb,jsonb) from authenticated;
revoke execute on function public.checkout_create_order_group(uuid,text,text,jsonb,text) from authenticated;
revoke execute on function public.checkout_create_order_group_v2(uuid,text,text,jsonb,text,jsonb) from authenticated;
revoke execute on function public.checkout_create_order_group_v3(uuid,text,text,jsonb,text,jsonb,numeric,text) from authenticated;
