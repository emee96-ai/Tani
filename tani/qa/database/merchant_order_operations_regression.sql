\set ON_ERROR_STOP on
begin;

insert into auth.users(id) values
  ('71111111-1111-4111-8111-111111111111'),
  ('72222222-2222-4222-8222-222222222222'),
  ('73333333-3333-4333-8333-333333333333');

insert into public.profiles(id,name,phone,role) values
  ('71111111-1111-4111-8111-111111111111','Order Customer','1111111','customer'),
  ('72222222-2222-4222-8222-222222222222','Order Merchant','2222222','seller'),
  ('73333333-3333-4333-8333-333333333333','Order Stranger','3333333','customer');

insert into public.sellers(id,user_id,store_name,verification_status) values
  ('74444444-4444-4444-8444-444444444444','72222222-2222-4222-8222-222222222222','Order Store','approved');

insert into public.products(id,seller_id,name,price,stock,is_active) values
  ('75555555-5555-4555-8555-555555555551','74444444-4444-4444-8444-444444444444','Base Order Product',100,4,true),
  ('75555555-5555-4555-8555-555555555552','74444444-4444-4444-8444-444444444444','Variant Order Product',150,0,true);

insert into public.product_variants(id,product_id,name,sku,price,stock,is_active) values
  ('76666666-6666-4666-8666-666666666666','75555555-5555-4555-8555-555555555552','Large','ORDER-L',175,1,true);

insert into public.orders(
  id, customer_id, seller_id, subtotal, delivery_fee, discount, total,
  status, payment_method, payment_status, address, phone,
  customer_name_snapshot, store_name_snapshot
) values
  ('77777777-7777-4777-8777-777777777771','71111111-1111-4111-8111-111111111111','74444444-4444-4444-8444-444444444444',100,20,0,120,'pending','cod','pending','Order Street','1234567','Order Customer','Order Store'),
  ('77777777-7777-4777-8777-777777777772','71111111-1111-4111-8111-111111111111','74444444-4444-4444-8444-444444444444',175,20,0,195,'pending','cod','pending','Order Street','1234567','Order Customer','Order Store');

insert into public.order_items(
  order_id, product_id, seller_id, quantity, unit_price,
  product_name_snapshot, variant_snapshot, line_total
) values
  ('77777777-7777-4777-8777-777777777771','75555555-5555-4555-8555-555555555551','74444444-4444-4444-8444-444444444444',1,100,'Base Order Product',null,100),
  ('77777777-7777-4777-8777-777777777772','75555555-5555-4555-8555-555555555552','74444444-4444-4444-8444-444444444444',1,175,'Variant Order Product',jsonb_build_object('id','76666666-6666-4666-8666-666666666666','name','Large'),175);

set local role authenticated;

-- A customer who is not the seller cannot use the merchant transition RPC.
select set_config('request.jwt.claim.sub','73333333-3333-4333-8333-333333333333',true);
do $$
begin
  begin
    perform public.merchant_transition_order_status(
      '77777777-7777-4777-8777-777777777771','accepted',null,null
    );
    raise exception 'expected seller ownership rejection';
  exception when sqlstate '42501' then
    perform tests.assert_true(true, 'merchant transition rejects non-seller callers');
  end;
end
$$;

select set_config('request.jwt.claim.sub','72222222-2222-4222-8222-222222222222',true);

-- The legacy/shared RPC must enforce merchant operational rules too, so a
-- custom API caller cannot bypass the wrapper/UI requirements.
do $$
begin
  begin
    perform public.transition_order_status(
      '77777777-7777-4777-8777-777777777771','rejected',''
    );
    raise exception 'expected direct missing rejection reason';
  exception when sqlstate '22023' then
    perform tests.assert_true(true, 'direct seller transition cannot bypass rejection reason');
  end;
  perform tests.assert_true(
    (select status from public.orders where id='77777777-7777-4777-8777-777777777771') = 'pending',
    'rejected direct transition leaves order pending'
  );
end
$$;

-- Destructive merchant statuses require an operational reason.
do $$
begin
  begin
    perform public.merchant_transition_order_status(
      '77777777-7777-4777-8777-777777777772','rejected','',null
    );
    raise exception 'expected missing rejection reason';
  exception when sqlstate '22023' then
    perform tests.assert_true(true, 'merchant rejection requires a reason');
  end;
end
$$;

select public.merchant_transition_order_status(
  '77777777-7777-4777-8777-777777777772',
  'rejected',
  'الخيار غير متوفر فعلياً',
  null
);

do $$
begin
  perform tests.assert_true(
    (select stock from public.product_variants where id='76666666-6666-4666-8666-666666666666') = 2,
    'rejecting a variant order restores variant stock'
  );
  perform tests.assert_true(
    (select stock from public.products where id='75555555-5555-4555-8555-555555555552') = 0,
    'rejecting a variant order does not inflate base-product stock'
  );
  perform tests.assert_true(
    (select stock_restored_at is not null from public.orders where id='77777777-7777-4777-8777-777777777772'),
    'stock restoration is marked exactly once'
  );
  perform tests.assert_true(
    exists(
      select 1 from public.order_status_history
      where order_id='77777777-7777-4777-8777-777777777772'
        and to_status='rejected'
        and note='الخيار غير متوفر فعلياً'
    ),
    'rejection reason is preserved in order history'
  );
end
$$;

-- Test the normal merchant flow and mandatory delivery ETA.
select public.merchant_transition_order_status('77777777-7777-4777-8777-777777777771','accepted',null,null);
select public.merchant_transition_order_status('77777777-7777-4777-8777-777777777771','preparing',null,null);
select public.merchant_transition_order_status('77777777-7777-4777-8777-777777777771','ready',null,null);

do $$
begin
  begin
    perform public.transition_order_status(
      '77777777-7777-4777-8777-777777777771','out_for_delivery','مع المندوب بدون زمن'
    );
    raise exception 'expected direct missing ETA rejection';
  exception when sqlstate '22023' then
    perform tests.assert_true(true, 'direct seller transition cannot bypass delivery ETA');
  end;
  begin
    perform public.merchant_transition_order_status(
      '77777777-7777-4777-8777-777777777771','out_for_delivery','مع المندوب',null
    );
    raise exception 'expected missing ETA rejection';
  exception when sqlstate '22023' then
    perform tests.assert_true(true, 'out-for-delivery transition requires ETA minutes');
  end;
  perform tests.assert_true(
    (select status from public.orders where id='77777777-7777-4777-8777-777777777771') = 'ready',
    'invalid ETA does not advance the order state'
  );
end
$$;

select public.merchant_transition_order_status(
  '77777777-7777-4777-8777-777777777771',
  'out_for_delivery',
  'مع المندوب الآن',
  45
);

do $$
begin
  perform tests.assert_true(
    exists(
      select 1 from public.order_status_history
      where order_id='77777777-7777-4777-8777-777777777771'
        and to_status='out_for_delivery'
        and note like '%45 دقيقة%'
        and note like '%مع المندوب الآن%'
    ),
    'delivery ETA and merchant note are visible in status history'
  );
end
$$;

select public.merchant_transition_order_status(
  '77777777-7777-4777-8777-777777777771','delivered',null,null
);

do $$
begin
  perform tests.assert_true(
    (select status from public.orders where id='77777777-7777-4777-8777-777777777771') = 'delivered',
    'merchant flow reaches delivered state'
  );
  perform tests.assert_true(
    (select payment_status from public.orders where id='77777777-7777-4777-8777-777777777771') = 'paid',
    'COD order is marked paid on delivery'
  );
  perform tests.assert_true(
    (select stock from public.products where id='75555555-5555-4555-8555-555555555551') = 4,
    'successful delivery does not restore reserved base stock'
  );
end
$$;

reset role;
rollback;
