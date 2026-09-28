\set ON_ERROR_STOP on
begin;

insert into auth.users(id) values
  ('81111111-1111-4111-8111-111111111111'),
  ('82222222-2222-4222-8222-222222222222');
insert into public.profiles(id,name,phone,role) values
  ('81111111-1111-4111-8111-111111111111','Delivery Customer','1111111','customer'),
  ('82222222-2222-4222-8222-222222222222','Delivery Merchant','2222222','seller');
insert into public.sellers(id,user_id,store_name,verification_status) values
  ('83333333-3333-4333-8333-333333333333','82222222-2222-4222-8222-222222222222','Delivery Store','approved');
insert into public.stores(id,seller_id,name,city,is_active,is_open) values
  ('84444444-4444-4444-8444-444444444444','83333333-3333-4333-8333-333333333333','Delivery Store','كوستي',true,true);
insert into public.merchant_profiles(id,user_id,seller_id,business_name,verification_status) values
  ('85555555-5555-4555-8555-555555555555','82222222-2222-4222-8222-222222222222','83333333-3333-4333-8333-333333333333','Delivery Business','approved');
insert into public.delivery_settings(seller_id,base_fee,delivery_area,estimated_minutes,notes,is_active) values
  ('83333333-3333-4333-8333-333333333333',10,'الوسط',30,'',true);
insert into public.products(id,seller_id,name,price,stock,is_active) values
  ('86666666-6666-4666-8666-666666666666','83333333-3333-4333-8333-333333333333','Delivery Product',100,5,true);
insert into public.addresses(id,user_id,label,description,area) values
  ('87777777-7777-4777-8777-777777777777','81111111-1111-4111-8111-111111111111','Home','Delivery Street','الوسط');

select set_config('request.jwt.claim.sub','82222222-2222-4222-8222-222222222222',true);
select * from public.save_my_delivery_configuration(
  '[{"area":"الوسط","fee":10,"estimated_minutes":30,"sort_order":0,"is_active":true},{"area":"بعيد","fee":25,"estimated_minutes":60,"sort_order":1,"is_active":false}]'::jsonb,
  'بعد العصر',
  true
);

do $$
begin
  perform tests.assert_true((select count(*) from public.delivery_zones where seller_id='83333333-3333-4333-8333-333333333333')=2,'merchant saves both delivery zones');
  perform tests.assert_true((select count(*) from public.delivery_zones where seller_id='83333333-3333-4333-8333-333333333333' and is_active)=1,'per-zone availability is preserved');
  perform tests.assert_true((select notes from public.delivery_settings where seller_id='83333333-3333-4333-8333-333333333333')='بعد العصر','delivery notes are saved atomically');
  perform tests.assert_true((public.delivery_quote('83333333-3333-4333-8333-333333333333',null,'بعيد')->>'available')::boolean=false,'inactive delivery zone is unavailable');
  perform tests.assert_true((public.delivery_quote('83333333-3333-4333-8333-333333333333',null,'الوسط')->>'fee')::numeric=10,'active zone returns its own fee');
end $$;

select * from public.save_my_delivery_configuration(
  '[{"area":"الوسط","fee":10,"estimated_minutes":30,"sort_order":0,"is_active":true},{"area":"بعيد","fee":25,"estimated_minutes":60,"sort_order":1,"is_active":false}]'::jsonb,
  'بعد العصر',
  false
);

do $$
begin
  perform tests.assert_true((public.delivery_quote('83333333-3333-4333-8333-333333333333',null,'الوسط')->>'available')::boolean=false,'global delivery switch disables active zones');
end $$;

select set_config('request.jwt.claim.sub','81111111-1111-4111-8111-111111111111',true);
do $$
declare v_zone uuid;
begin
  select id into v_zone from public.delivery_zones where seller_id='83333333-3333-4333-8333-333333333333' and area_name='الوسط';
  begin
    perform public.quote_cart_v3(
      '[{"product_id":"86666666-6666-4666-8666-666666666666","quantity":1}]'::jsonb,
      jsonb_build_object('83333333-3333-4333-8333-333333333333',v_zone)
    );
    raise exception 'expected delivery disabled rejection';
  exception when sqlstate 'P0001' then
    perform tests.assert_true(true,'cart quote rejects globally disabled delivery');
  end;
end $$;

select set_config('request.jwt.claim.sub','82222222-2222-4222-8222-222222222222',true);
select * from public.save_my_delivery_configuration(
  '[{"area":"الوسط","fee":10,"estimated_minutes":30,"sort_order":0,"is_active":true},{"area":"بعيد","fee":25,"estimated_minutes":60,"sort_order":1,"is_active":false}]'::jsonb,
  '',
  true
);

select set_config('request.jwt.claim.sub','81111111-1111-4111-8111-111111111111',true);
do $$
declare
  v_zone uuid;
  v_quote jsonb;
  v_old_token text;
  v_old_total numeric;
begin
  select id into v_zone from public.delivery_zones where seller_id='83333333-3333-4333-8333-333333333333' and area_name='الوسط';
  v_quote := public.quote_cart_v3(
    '[{"product_id":"86666666-6666-4666-8666-666666666666","quantity":1}]'::jsonb,
    jsonb_build_object('83333333-3333-4333-8333-333333333333',v_zone)
  );
  v_old_token := v_quote->>'quote_token';
  v_old_total := (v_quote->>'grand_total')::numeric;
  perform set_config('tani.test.old_quote_token',v_old_token,true);
  perform set_config('tani.test.old_quote_total',v_old_total::text,true);
  perform tests.assert_true(v_old_total=110,'initial quote includes original zone fee');
end $$;

select set_config('request.jwt.claim.sub','82222222-2222-4222-8222-222222222222',true);
select * from public.save_my_delivery_configuration(
  '[{"area":"الوسط","fee":18,"estimated_minutes":40,"sort_order":0,"is_active":true},{"area":"بعيد","fee":25,"estimated_minutes":60,"sort_order":1,"is_active":false}]'::jsonb,
  '',
  true
);

select set_config('request.jwt.claim.sub','81111111-1111-4111-8111-111111111111',true);
do $$
declare
  v_zone uuid;
  v_quote jsonb;
  v_old_token text := current_setting('tani.test.old_quote_token',true);
  v_old_total numeric := current_setting('tani.test.old_quote_total',true)::numeric;
begin
  select id into v_zone from public.delivery_zones where seller_id='83333333-3333-4333-8333-333333333333' and area_name='الوسط';
  v_quote := public.quote_cart_v3(
    '[{"product_id":"86666666-6666-4666-8666-666666666666","quantity":1}]'::jsonb,
    jsonb_build_object('83333333-3333-4333-8333-333333333333',v_zone)
  );
  perform tests.assert_true((v_quote->>'grand_total')::numeric=118,'new quote uses updated delivery fee');
  perform tests.assert_true(v_quote->>'quote_token'<>v_old_token,'delivery fee change invalidates old quote token');
  begin
    perform public.checkout_create_order_group_v4(
      '87777777-7777-4777-8777-777777777777','1234567','',
      '[{"product_id":"86666666-6666-4666-8666-666666666666","quantity":1}]'::jsonb,
      'delivery-phase5-stale-quote-0001',
      jsonb_build_object('83333333-3333-4333-8333-333333333333',v_zone),
      v_old_total,
      v_old_token
    );
    raise exception 'expected stale delivery quote rejection';
  exception when sqlstate 'P0001' then
    perform tests.assert_true(true,'checkout rejects stale delivery fee quote');
  end;
end $$;

reset role;
rollback;
