\set ON_ERROR_STOP on
begin;

insert into auth.users(id) values
  ('11111111-1111-4111-8111-111111111111'),
  ('22222222-2222-4222-8222-222222222222'),
  ('33333333-3333-4333-8333-333333333333');
insert into public.profiles(id,name,phone,role) values
  ('11111111-1111-4111-8111-111111111111','Customer','1111111','customer'),
  ('22222222-2222-4222-8222-222222222222','Merchant','2222222','seller'),
  ('33333333-3333-4333-8333-333333333333','Stranger','3333333','customer');
insert into public.sellers(id,user_id,store_name,verification_status) values
  ('77777777-7777-4777-8777-777777777777','22222222-2222-4222-8222-222222222222','Test store','approved');
insert into public.delivery_settings(seller_id,base_fee,delivery_area,is_active) values
  ('77777777-7777-4777-8777-777777777777',5,'Test area',true);
insert into public.addresses(id,user_id,label,description,area) values
  ('66666666-6666-4666-8666-666666666666','11111111-1111-4111-8111-111111111111','Home','Street 1','Test area');
insert into public.products(id,seller_id,name,price,stock,is_active) values
  ('44444444-4444-4444-8444-444444444444','77777777-7777-4777-8777-777777777777','Base product',10,200,true),
  ('88888888-8888-4888-8888-888888888888','77777777-7777-4777-8777-777777777777','Changing product',15,5,true),
  ('99999999-9999-4999-8999-999999999999','77777777-7777-4777-8777-777777777777','Variant product',20,0,true);
insert into public.product_variants(id,product_id,name,stock,is_active) values
  ('55555555-5555-4555-8555-555555555551','99999999-9999-4999-8999-999999999999','Red',70,true),
  ('55555555-5555-4555-8555-555555555552','99999999-9999-4999-8999-999999999999','Blue',70,true);

do $$
declare
  v_normalized jsonb;
begin
  v_normalized := private.normalize_checkout_items('[
    {"product_id":"44444444-4444-4444-8444-444444444444","quantity":40},
    {"product_id":"44444444-4444-4444-8444-444444444444","quantity":50}
  ]'::jsonb);
  perform tests.assert_true(jsonb_array_length(v_normalized) = 1, 'duplicate base-product rows are aggregated');
  perform tests.assert_true((v_normalized->0->>'quantity')::int = 90, 'aggregate quantity is preserved');

  begin
    perform private.normalize_checkout_items('[
      {"product_id":"44444444-4444-4444-8444-444444444444","quantity":60},
      {"product_id":"44444444-4444-4444-8444-444444444444","quantity":40}
    ]'::jsonb);
    raise exception 'expected aggregate quantity rejection';
  exception when sqlstate '22023' then
    perform tests.assert_true(sqlerrm like '%99%', 'aggregate quantity above 99 is rejected');
  end;

  v_normalized := private.normalize_checkout_items('[
    {"product_id":"99999999-9999-4999-8999-999999999999","variant_id":"55555555-5555-4555-8555-555555555551","quantity":60},
    {"product_id":"99999999-9999-4999-8999-999999999999","variant_id":"55555555-5555-4555-8555-555555555552","quantity":60}
  ]'::jsonb);
  perform tests.assert_true(jsonb_array_length(v_normalized) = 2, 'quantity limits are isolated per variant');
end
$$;

select set_config('request.jwt.claim.sub','11111111-1111-4111-8111-111111111111',true);
do $$
declare
  v_items jsonb := '[{"product_id":"44444444-4444-4444-8444-444444444444","quantity":2}]';
  v_quote jsonb;
  v_first uuid;
  v_second uuid;
  v_stale jsonb;
begin
  v_quote := public.quote_cart_v3(v_items);
  v_first := public.checkout_create_order_group_v4(
    '66666666-6666-4666-8666-666666666666','1234567',null,v_items,
    'phase4-idempotency-key-0001','{}',
    (v_quote->>'grand_total')::numeric,v_quote->>'quote_token'
  );
  v_second := public.checkout_create_order_group_v4(
    '66666666-6666-4666-8666-666666666666','1234567',null,v_items,
    'phase4-idempotency-key-0001','{}',
    (v_quote->>'grand_total')::numeric,v_quote->>'quote_token'
  );
  perform tests.assert_true(v_first = v_second, 'replayed idempotency key returns the original group');
  perform tests.assert_true((select count(*) from public.order_groups where id = v_first) = 1, 'idempotent replay creates one group');
  perform tests.assert_true((select count(*) from public.orders where order_group_id = v_first) = 1, 'idempotent replay creates one seller order');
  perform tests.assert_true((select sum(quantity) from public.order_items oi join public.orders o on o.id=oi.order_id where o.order_group_id=v_first) = 2, 'idempotent replay creates one item set');
  perform tests.assert_true((select stock from public.products where id='44444444-4444-4444-8444-444444444444') = 198, 'idempotent replay decrements stock once');

  v_items := '[{"product_id":"88888888-8888-4888-8888-888888888888","quantity":1}]';
  v_stale := public.quote_cart_v3(v_items);
  update public.products set price = 16 where id='88888888-8888-4888-8888-888888888888';
  begin
    perform public.checkout_create_order_group_v4(
      '66666666-6666-4666-8666-666666666666','1234567',null,v_items,
      'phase4-stale-quote-key-0001','{}',
      (v_stale->>'grand_total')::numeric,v_stale->>'quote_token'
    );
    raise exception 'expected stale quote rejection';
  exception when sqlstate 'P0001' then
    perform tests.assert_true(sqlerrm like '%تغيّر السعر%', 'stale quote is rejected before order creation');
  end;
  perform tests.assert_true(not exists(select 1 from public.order_groups where idempotency_key='phase4-stale-quote-key-0001'), 'stale quote leaves no order group');
end
$$;

set local role authenticated;
select set_config('request.jwt.claim.sub','11111111-1111-4111-8111-111111111111',true);
do $$ begin
  perform tests.assert_true((select count(*) from public.orders) = 1, 'customer can read own order');
  perform tests.assert_true((select count(*) from public.order_items) = 1, 'customer can read own order items');
end $$;
select set_config('request.jwt.claim.sub','33333333-3333-4333-8333-333333333333',true);
do $$ begin
  perform tests.assert_true((select count(*) from public.orders) = 0, 'unrelated customer cannot read order');
  perform tests.assert_true((select count(*) from public.order_items) = 0, 'unrelated customer cannot read order items');
end $$;
select set_config('request.jwt.claim.sub','22222222-2222-4222-8222-222222222222',true);
do $$ begin
  perform tests.assert_true((select count(*) from public.orders) = 1, 'merchant can read own seller order');
  perform tests.assert_true((select count(*) from public.order_items) = 1, 'merchant can read own seller order items');
end $$;
reset role;

rollback;

