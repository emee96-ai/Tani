\set ON_ERROR_STOP on
select user_id, address_id, items::text as items, idempotency_key, total, token
from tests.concurrent_cases where name = :'case_name' \gset
select set_config('request.jwt.claim.sub', :'user_id', false);
select public.checkout_create_order_group_v4(
  :'address_id'::uuid,'1234567',null,:'items'::jsonb,:'idempotency_key','{}'::jsonb,
  :'total'::numeric,:'token'
);

