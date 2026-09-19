\set ON_ERROR_STOP on
select tests.assert_true(
  (select count(*) from public.order_groups where idempotency_key='phase4-concurrent-idem-0001') = 1,
  'concurrent idempotent retries create one group'
);
select tests.assert_true(
  (select stock from public.products where id='eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee1') = 9,
  'concurrent idempotent retries decrement stock once'
);
select tests.assert_true(
  (select count(*) from public.order_groups where idempotency_key like 'phase4-concurrent-stock-%') = 1,
  'concurrent stock race creates one order group'
);
select tests.assert_true(
  (select stock from public.products where id='eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee2') = 0,
  'row locking prevents negative stock'
);

