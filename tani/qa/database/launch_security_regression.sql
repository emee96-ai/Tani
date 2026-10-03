BEGIN;
INSERT INTO auth.users(id,email,raw_user_meta_data) SELECT ('a1000000-0000-4000-8000-'||lpad(n::text,12,'0'))::uuid,'launch-'||n||'@tani.invalid',jsonb_build_object('name','Launch test '||n,'phone','09123456'||n) FROM generate_series(1,8) n;
INSERT INTO public.profiles(id,name,phone,role,is_active,deleted_at)
SELECT ('a1000000-0000-4000-8000-'||lpad(n::text,12,'0'))::uuid,'Launch test '||n,'09123456'||n,
 CASE n WHEN 2 THEN 'seller' WHEN 3 THEN 'admin' WHEN 4 THEN 'support' ELSE 'customer' END,
 n<>6, CASE WHEN n=7 THEN now() ELSE NULL END FROM generate_series(1,7) n
ON CONFLICT(id) DO UPDATE SET role=EXCLUDED.role,is_active=EXCLUDED.is_active,deleted_at=EXCLUDED.deleted_at;
DELETE FROM public.profiles WHERE id='a1000000-0000-4000-8000-000000000008';
INSERT INTO public.sellers(id,user_id,store_name,verification_status)
VALUES('a2000000-0000-4000-8000-000000000001','a1000000-0000-4000-8000-000000000002','Launch test store','approved');
INSERT INTO public.merchant_profiles(id,user_id,seller_id,business_name,verification_status)
VALUES('a3000000-0000-4000-8000-000000000001','a1000000-0000-4000-8000-000000000002','a2000000-0000-4000-8000-000000000001','Launch test merchant','approved');
INSERT INTO public.products(id,seller_id,name,price,stock)
VALUES('a4000000-0000-4000-8000-000000000001','a2000000-0000-4000-8000-000000000001','Launch test product',100,10);
INSERT INTO public.orders(id,customer_id,seller_id,total,subtotal,address,phone,customer_name_snapshot,store_name_snapshot)
VALUES('a5000000-0000-4000-8000-000000000001','a1000000-0000-4000-8000-000000000001','a2000000-0000-4000-8000-000000000001',100,100,'Launch test address','0912345678','Launch test customer','Launch test store');
INSERT INTO public.order_items(order_id,product_id,seller_id,quantity,unit_price,line_total,product_name_snapshot)
VALUES('a5000000-0000-4000-8000-000000000001','a4000000-0000-4000-8000-000000000001','a2000000-0000-4000-8000-000000000001',1,100,100,'Launch test product');
INSERT INTO public.addresses(user_id,label,description,phone)
VALUES('a1000000-0000-4000-8000-000000000001','Launch test home','Launch test address','0912345678');

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub','a1000000-0000-4000-8000-000000000008',true);
SELECT tests.assert_true(public.current_user_role() IS NULL,'missing profile has no role');
SELECT tests.expect_denied('SELECT public.weekly_marketplace_kpis()','missing role cannot read staff KPIs');
SELECT tests.expect_denied($q$SELECT public.admin_review_subscription_request('00000000-0000-4000-8000-000000000000','approved')$q$,'missing role cannot approve subscriptions');
SELECT tests.expect_denied($q$SELECT public.admin_review_featured_request('00000000-0000-4000-8000-000000000000','approved')$q$,'missing role cannot approve featured placements');

SELECT set_config('request.jwt.claim.sub','a1000000-0000-4000-8000-000000000006',true);
SELECT tests.expect_denied('SELECT public.weekly_marketplace_kpis()','inactive profile cannot read staff KPIs');
SELECT tests.expect_denied($q$SELECT public.quote_cart_v3('[]'::jsonb,'{}'::jsonb)$q$,'inactive profile cannot quote checkout');
SELECT tests.assert_true((SELECT count(*) FROM public.profiles)=0,'inactive JWT cannot read owner profile');

SELECT set_config('request.jwt.claim.sub','a1000000-0000-4000-8000-000000000007',true);
SELECT tests.expect_denied('SELECT public.weekly_marketplace_kpis()','deleted profile cannot read staff KPIs');

SELECT set_config('request.jwt.claim.sub','a1000000-0000-4000-8000-000000000005',true);
SELECT tests.expect_denied('SELECT public.weekly_marketplace_kpis()','ordinary customer cannot read staff KPIs');
SELECT tests.assert_true((SELECT count(*) FROM public.order_items WHERE order_id='a5000000-0000-4000-8000-000000000001')=0,'unrelated customer cannot read another order items');

SELECT set_config('request.jwt.claim.sub','a1000000-0000-4000-8000-000000000003',true);
SELECT tests.assert_true(public.weekly_marketplace_kpis() IS NOT NULL,'active admin can read staff KPIs');
SELECT tests.assert_true((SELECT count(*) FROM public.order_items WHERE order_id='a5000000-0000-4000-8000-000000000001')=1,'admin sees order items');
DO $$ BEGIN
 BEGIN PERFORM public.soft_delete_my_account();
 EXCEPTION WHEN check_violation THEN RETURN; END;
 RAISE EXCEPTION 'Last active admin deleted their account';
END $$;

SELECT set_config('request.jwt.claim.sub','a1000000-0000-4000-8000-000000000004',true);
SELECT tests.assert_true(public.weekly_marketplace_kpis() IS NOT NULL,'active support can read staff KPIs');
SELECT tests.assert_true((SELECT count(*) FROM public.order_items WHERE order_id='a5000000-0000-4000-8000-000000000001')=1,'support sees order items');
SELECT tests.expect_denied($q$SELECT public.admin_review_featured_request('00000000-0000-4000-8000-000000000000','approved')$q$,'support cannot approve placements');

RESET ROLE;
SELECT set_config('request.jwt.claim.sub','',true);
UPDATE public.sellers SET verification_status='suspended' WHERE id='a2000000-0000-4000-8000-000000000001';
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub','a1000000-0000-4000-8000-000000000002',true);
DO $$ BEGIN
 BEGIN PERFORM public.transition_order_status('a5000000-0000-4000-8000-000000000001','accepted');
 EXCEPTION WHEN raise_exception THEN
   IF SQLERRM='Status transition is not allowed' THEN RETURN; END IF;
   RAISE;
 END;
 RAISE EXCEPTION 'Suspended merchant changed an order';
END $$;

SELECT set_config('request.jwt.claim.sub','a1000000-0000-4000-8000-000000000001',true);
SELECT tests.assert_true((SELECT count(*) FROM public.order_items WHERE order_id='a5000000-0000-4000-8000-000000000001')=1,'customer sees their own order items');
SELECT tests.expect_denied($q$UPDATE public.profiles SET role='admin' WHERE id='a1000000-0000-4000-8000-000000000001'$q$,'customer cannot promote themselves');
SELECT tests.assert_true(public.soft_delete_my_account(),'customer deletion succeeds through protected triggers');
SELECT tests.assert_true(public.soft_delete_my_account(),'deletion retry is idempotent');
SELECT tests.assert_true(public.current_user_role() IS NULL,'deleted account immediately loses its role');
SELECT tests.assert_true((SELECT count(*) FROM public.addresses)=0,'deleted account cannot read private addresses');
SELECT tests.expect_denied('SELECT public.weekly_marketplace_kpis()','deleted account JWT cannot bypass staff guard');
RESET ROLE;
SELECT tests.assert_true((SELECT NOT is_active AND deleted_at IS NOT NULL AND email IS NULL AND avatar_url IS NULL AND char_length(phone)<=30 FROM public.profiles WHERE id='a1000000-0000-4000-8000-000000000001'),'deleted profile is anonymized within its constraints');
SELECT tests.assert_true((SELECT count(*) FROM public.addresses WHERE user_id='a1000000-0000-4000-8000-000000000001')=0,'unnecessary private addresses are erased');
SELECT tests.assert_true((SELECT count(*) FROM public.orders WHERE id='a5000000-0000-4000-8000-000000000001')=1,'deletion retains order business records');
SELECT tests.assert_true((SELECT count(*) FROM private.account_deletion_requests WHERE user_id='a1000000-0000-4000-8000-000000000001' AND status='pending')=1,'Auth and Storage cleanup is queued once');
ROLLBACK;
