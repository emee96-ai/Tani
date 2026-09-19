\set ON_ERROR_STOP on
begin;

insert into auth.users(id) values
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1'),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2'),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa3'),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa4');

insert into public.profiles(id,name,phone,role) values
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1','Release Customer','1111111','customer'),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2','Release Merchant A','2222222','seller'),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa3','Release Merchant B','3333333','seller'),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa4','Release Stranger','4444444','customer');

insert into public.sellers(id,user_id,store_name,verification_status) values
  ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2','Release Store A','approved'),
  ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa3','Release Store B','approved');

insert into public.delivery_settings(seller_id,base_fee,delivery_area,is_active) values
  ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1',99,'Fallback A',true),
  ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2',99,'Fallback B',true);

insert into public.delivery_zones(id,seller_id,area_name,fee,is_active) values
  ('cccccccc-cccc-4ccc-8ccc-ccccccccccc1','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1','Zone A',10,true),
  ('cccccccc-cccc-4ccc-8ccc-ccccccccccc2','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2','Zone B',20,true);

insert into public.addresses(id,user_id,label,description,area) values
  ('dddddddd-dddd-4ddd-8ddd-ddddddddddd1','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1','Home','Release Street','Zone A');

insert into public.products(id,seller_id,name,price,stock,is_active) values
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee1','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1','Release Base',100,5,true),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee2','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2','Release Variant',200,0,true);

insert into public.product_variants(id,product_id,name,sku,price,stock,is_active) values
  ('ffffffff-ffff-4fff-8fff-fffffffffff1','eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee2','Large','REL-L',250,2,true);

select set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1',true);

do $$
declare
  v_items jsonb := '[
    {"product_id":"eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee1","quantity":1},
    {"product_id":"eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee2","variant_id":"ffffffff-ffff-4fff-8fff-fffffffffff1","quantity":1}
  ]'::jsonb;
  v_zones jsonb := '{
    "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1":"cccccccc-cccc-4ccc-8ccc-ccccccccccc1",
    "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2":"cccccccc-cccc-4ccc-8ccc-ccccccccccc2"
  }'::jsonb;
  v_quote jsonb;
  v_group uuid;
begin
  v_quote := public.quote_cart_v3(v_items, v_zones);

  perform tests.assert_true((v_quote->>'subtotal')::numeric = 350, 'multi-merchant quote uses base and variant prices');
  perform tests.assert_true((v_quote->>'delivery_total')::numeric = 30, 'selected merchant zones determine delivery total');
  perform tests.assert_true((v_quote->>'grand_total')::numeric = 380, 'multi-merchant grand total is authoritative');
  perform tests.assert_true(jsonb_array_length(v_quote->'deliveries') = 2, 'quote contains one delivery per merchant');
  perform tests.assert_true(jsonb_array_length(v_quote->'items') = 2, 'quote contains both merchant items');

  v_group := public.checkout_create_order_group_v4(
    'dddddddd-dddd-4ddd-8ddd-ddddddddddd1',
    '1234567',
    'release regression',
    v_items,
    'phase7-release-multi-merchant-0001',
    v_zones,
    (v_quote->>'grand_total')::numeric,
    v_quote->>'quote_token'
  );

  perform tests.assert_true((select count(*) from public.orders where order_group_id = v_group) = 2, 'checkout splits a group into two merchant orders');
  perform tests.assert_true((select count(*) from public.order_items oi join public.orders o on o.id = oi.order_id where o.order_group_id = v_group) = 2, 'checkout writes both order-item snapshots');
  perform tests.assert_true((select delivery_total from public.order_groups where id = v_group) = 30, 'order group stores selected zone fees');
  perform tests.assert_true((select grand_total from public.order_groups where id = v_group) = 380, 'order group stores authoritative grand total');
  perform tests.assert_true((select stock from public.products where id = 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee1') = 4, 'base-product stock is decremented once');
  perform tests.assert_true((select stock from public.product_variants where id = 'ffffffff-ffff-4fff-8fff-fffffffffff1') = 1, 'variant stock is decremented once');
  perform tests.assert_true(
    exists(
      select 1
      from public.order_items oi
      join public.orders o on o.id = oi.order_id
      where o.order_group_id = v_group
        and oi.product_id = 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee2'
        and oi.variant_snapshot->>'name' = 'Large'
    ),
    'variant snapshot is preserved in the order'
  );

  begin
    perform public.quote_cart_v3(
      '[{"product_id":"eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee1","quantity":1}]'::jsonb,
      '{}'::jsonb
    );
    raise exception 'expected missing delivery-zone rejection';
  exception when sqlstate '22023' then
    perform tests.assert_true(sqlerrm like '%منطقة التوصيل%', 'missing delivery zone is rejected');
  end;

  begin
    perform public.quote_cart_v3(
      '[{"product_id":"eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee2","variant_id":"ffffffff-ffff-4fff-8fff-fffffffffff1","quantity":2}]'::jsonb,
      jsonb_build_object(
        'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2',
        'cccccccc-cccc-4ccc-8ccc-ccccccccccc1'
      )
    );
    raise exception 'expected foreign delivery-zone rejection';
  exception when sqlstate '22023' then
    perform tests.assert_true(sqlerrm like '%غير متاحة%', 'a zone from another merchant is rejected');
  end;

  begin
    perform public.quote_cart_v3(
      '[{"product_id":"eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee2","variant_id":"ffffffff-ffff-4fff-8fff-fffffffffff1","quantity":2}]'::jsonb,
      jsonb_build_object(
        'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2',
        'cccccccc-cccc-4ccc-8ccc-ccccccccccc2'
      )
    );
    raise exception 'expected out-of-stock rejection';
  exception when sqlstate 'P0001' then
    perform tests.assert_true(sqlerrm like '%الكمية المطلوبة%', 'variant stock exhaustion is rejected after checkout');
  end;
end
$$;

set local role authenticated;

select set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1',true);
do $$ begin
  perform tests.assert_true((select count(*) from public.orders) = 2, 'customer RLS exposes both own merchant orders');
  perform tests.assert_true((select count(*) from public.order_items) = 2, 'customer RLS exposes own order items');
end $$;

select set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa4',true);
do $$ begin
  perform tests.assert_true((select count(*) from public.orders) = 0, 'unrelated customer RLS hides release orders');
  perform tests.assert_true((select count(*) from public.order_items) = 0, 'unrelated customer RLS hides release order items');
end $$;

select set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2',true);
do $$ begin
  perform tests.assert_true((select count(*) from public.orders) = 1, 'merchant A RLS exposes only merchant A order');
  perform tests.assert_true((select count(*) from public.order_items) = 1, 'merchant A RLS exposes only merchant A item');
end $$;

select set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa3',true);
do $$ begin
  perform tests.assert_true((select count(*) from public.orders) = 1, 'merchant B RLS exposes only merchant B order');
  perform tests.assert_true((select count(*) from public.order_items) = 1, 'merchant B RLS exposes only merchant B item');
end $$;

reset role;
rollback;
