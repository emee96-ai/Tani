BEGIN;
INSERT INTO auth.users(id,email,raw_user_meta_data) SELECT ('ca000000-0000-4000-8000-'||lpad(n::text,12,'0'))::uuid,'deletion-orders-'||n||'@tani.invalid',jsonb_build_object('name','Deletion orders '||n,'phone','07123456'||n) FROM generate_series(1,3) n;
INSERT INTO public.profiles(id,name,phone,role) SELECT ('ca000000-0000-4000-8000-'||lpad(n::text,12,'0'))::uuid,'Deletion orders '||n,'07123456'||n,
 CASE WHEN n=2 THEN 'seller' ELSE 'customer' END FROM generate_series(1,3) n
ON CONFLICT(id) DO UPDATE SET role=EXCLUDED.role;
INSERT INTO public.sellers(id,user_id,store_name,verification_status) VALUES('cb000000-0000-4000-8000-000000000001','ca000000-0000-4000-8000-000000000002','Deletion store','approved');
INSERT INTO public.merchant_profiles(user_id,seller_id,business_name,verification_status) VALUES('ca000000-0000-4000-8000-000000000002','cb000000-0000-4000-8000-000000000001','Deletion merchant','approved');
INSERT INTO public.products(id,seller_id,name,price,stock) VALUES('cc000000-0000-4000-8000-000000000001','cb000000-0000-4000-8000-000000000001','Deletion product',100,10);
INSERT INTO public.delivery_zones(id,seller_id,area_name,fee,is_active) VALUES('cd000000-0000-4000-8000-000000000001','cb000000-0000-4000-8000-000000000001','Deletion area',10,true);
INSERT INTO public.delivery_settings(seller_id,delivery_area,base_fee,is_active) VALUES('cb000000-0000-4000-8000-000000000001','Deletion area',10,true);
INSERT INTO public.addresses(id,user_id,label,description,phone) VALUES
 ('ce000000-0000-4000-8000-000000000001','ca000000-0000-4000-8000-000000000001','Home 1','Deletion area','0912345678'),
 ('ce000000-0000-4000-8000-000000000003','ca000000-0000-4000-8000-000000000003','Home 3','Deletion area','0912345679');

CREATE FUNCTION tests.place_deletion_order(p_address uuid,p_key text) RETURNS uuid LANGUAGE plpgsql AS $$
DECLARE q jsonb; items jsonb := '[{"product_id":"cc000000-0000-4000-8000-000000000001","quantity":2}]'; zones jsonb := '{"cb000000-0000-4000-8000-000000000001":"cd000000-0000-4000-8000-000000000001"}';
BEGIN
 q := public.quote_cart_v3(items,zones);
 RETURN public.checkout_create_order_group_v4(p_address,'0912345678','',items,p_key,zones,(q->>'grand_total')::numeric,q->>'quote_token');
END $$;
GRANT EXECUTE ON FUNCTION tests.place_deletion_order(uuid,text) TO authenticated;

SET LOCAL ROLE authenticated;
-- Customer 1 deletes the account with a pending order.
SELECT set_config('request.jwt.claim.sub','ca000000-0000-4000-8000-000000000001',true);
SELECT tests.place_deletion_order('ce000000-0000-4000-8000-000000000001','deletion-order-key-0001');
SELECT tests.assert_true((SELECT stock FROM public.products WHERE id='cc000000-0000-4000-8000-000000000001')=8,'checkout reserves stock');
SELECT public.soft_delete_my_account();
RESET ROLE;
SELECT tests.assert_true((SELECT status='cancelled' AND stock_restored_at IS NOT NULL FROM public.orders WHERE customer_id='ca000000-0000-4000-8000-000000000001'),'deleting a customer cancels the pending order');
SELECT tests.assert_true((SELECT stock FROM public.products WHERE id='cc000000-0000-4000-8000-000000000001')=10,'customer deletion restores stock');

-- The merchant deletes the store account with one accepted and one pending order.
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub','ca000000-0000-4000-8000-000000000003',true);
SELECT tests.place_deletion_order('ce000000-0000-4000-8000-000000000003','deletion-order-key-0003');
SELECT tests.place_deletion_order('ce000000-0000-4000-8000-000000000003','deletion-order-key-0004');
SELECT set_config('request.jwt.claim.sub','ca000000-0000-4000-8000-000000000002',true);
SELECT public.transition_order_status((SELECT id FROM public.orders WHERE customer_id='ca000000-0000-4000-8000-000000000003' ORDER BY id LIMIT 1),'accepted',null);
SELECT public.soft_delete_my_account();
RESET ROLE;
SELECT tests.assert_true((SELECT count(*) FROM public.orders WHERE customer_id='ca000000-0000-4000-8000-000000000003' AND status IN ('cancelled','rejected'))=2,'deleting a merchant closes the store''s open orders');
SELECT tests.assert_true((SELECT count(*) FROM public.order_status_history h JOIN public.orders o ON o.id=h.order_id WHERE o.customer_id='ca000000-0000-4000-8000-000000000003' AND h.note='Store account deleted')=2,'closed store orders record the reason');
SELECT tests.assert_true((SELECT stock FROM public.products WHERE id='cc000000-0000-4000-8000-000000000001')=10,'merchant deletion restores stock');
ROLLBACK;
