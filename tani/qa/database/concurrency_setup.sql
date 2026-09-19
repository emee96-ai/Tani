\set ON_ERROR_STOP on

insert into auth.users(id) values
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1'),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2'),
  ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
insert into public.profiles(id,name,phone,role) values
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1','Race customer 1','1111111','customer'),
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2','Race customer 2','2222222','customer'),
  ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb','Race merchant','3333333','seller');
insert into public.sellers(id,user_id,store_name,verification_status) values
  ('cccccccc-cccc-4ccc-8ccc-cccccccccccc','bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb','Race store','approved');
insert into public.delivery_settings(seller_id,base_fee,delivery_area,is_active) values
  ('cccccccc-cccc-4ccc-8ccc-cccccccccccc',0,'Race area',true);
insert into public.addresses(id,user_id,label,description) values
  ('dddddddd-dddd-4ddd-8ddd-ddddddddddd1','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1','Home','One'),
  ('dddddddd-dddd-4ddd-8ddd-ddddddddddd2','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2','Home','Two');
insert into public.products(id,seller_id,name,price,stock,is_active) values
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee1','cccccccc-cccc-4ccc-8ccc-cccccccccccc','Idempotency race',10,10,true),
  ('eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee2','cccccccc-cccc-4ccc-8ccc-cccccccccccc','Inventory race',10,1,true);

create table tests.concurrent_cases(
  name text primary key,
  user_id uuid not null,
  address_id uuid not null,
  items jsonb not null,
  idempotency_key text not null,
  total numeric not null,
  token text not null
);

do $$
declare q jsonb;
begin
  perform set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1',true);
  q := public.quote_cart_v3('[{"product_id":"eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee1","quantity":1}]');
  insert into tests.concurrent_cases values ('idem-a','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1','dddddddd-dddd-4ddd-8ddd-ddddddddddd1','[{"product_id":"eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee1","quantity":1}]','phase4-concurrent-idem-0001',(q->>'grand_total')::numeric,q->>'quote_token');
  insert into tests.concurrent_cases select 'idem-b',user_id,address_id,items,idempotency_key,total,token from tests.concurrent_cases where name='idem-a';

  q := public.quote_cart_v3('[{"product_id":"eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee2","quantity":1}]');
  insert into tests.concurrent_cases values ('stock-a','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1','dddddddd-dddd-4ddd-8ddd-ddddddddddd1','[{"product_id":"eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee2","quantity":1}]','phase4-concurrent-stock-0001',(q->>'grand_total')::numeric,q->>'quote_token');
  perform set_config('request.jwt.claim.sub','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2',true);
  q := public.quote_cart_v3('[{"product_id":"eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee2","quantity":1}]');
  insert into tests.concurrent_cases values ('stock-b','aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2','dddddddd-dddd-4ddd-8ddd-ddddddddddd2','[{"product_id":"eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee2","quantity":1}]','phase4-concurrent-stock-0002',(q->>'grand_total')::numeric,q->>'quote_token');
end
$$;

create or replace function tests.delay_inventory_update()
returns trigger language plpgsql as $$
begin
  if new.stock is distinct from old.stock then perform pg_sleep(1); end if;
  return new;
end
$$;
create trigger tests_delay_inventory_update before update of stock on public.products
for each row execute function tests.delay_inventory_update();
