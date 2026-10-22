BEGIN;
INSERT INTO auth.users(id,email,raw_user_meta_data) SELECT ('e6000000-0000-4000-8000-'||lpad(n::text,12,'0'))::uuid,'product-moderation-'||n||'@tani.invalid',jsonb_build_object('name','Product moderation '||n,'phone','05223456'||n) FROM generate_series(1,2) n;
INSERT INTO public.profiles(id,name,phone,role) SELECT ('e6000000-0000-4000-8000-'||lpad(n::text,12,'0'))::uuid,'Product moderation '||n,'05223456'||n,
 CASE n WHEN 1 THEN 'seller' ELSE 'admin' END FROM generate_series(1,2) n
ON CONFLICT(id) DO UPDATE SET role=EXCLUDED.role;
INSERT INTO public.sellers(id,user_id,store_name,verification_status) VALUES('e7000000-0000-4000-8000-000000000001','e6000000-0000-4000-8000-000000000001','Product moderation store','approved');
INSERT INTO public.products(id,seller_id,name,price,stock) VALUES('e8000000-0000-4000-8000-000000000001','e7000000-0000-4000-8000-000000000001','Moderated product',100,0);

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub','e6000000-0000-4000-8000-000000000002',true);
SELECT public.admin_set_product_active('e8000000-0000-4000-8000-000000000001',false,'counterfeit');
SELECT tests.assert_true((SELECT NOT is_active AND moderation_hidden_at IS NOT NULL FROM public.products WHERE id='e8000000-0000-4000-8000-000000000001'),'admin hide marks the product as moderated');

SELECT set_config('request.jwt.claim.sub','e6000000-0000-4000-8000-000000000001',true);
UPDATE public.products SET is_active=true WHERE id='e8000000-0000-4000-8000-000000000001';
SELECT tests.assert_true((SELECT NOT is_active FROM public.products WHERE id='e8000000-0000-4000-8000-000000000001'),'merchant cannot republish an admin-hidden product');
UPDATE public.products SET moderation_hidden_at=NULL,is_active=true WHERE id='e8000000-0000-4000-8000-000000000001';
SELECT tests.assert_true((SELECT NOT is_active AND moderation_hidden_at IS NOT NULL FROM public.products WHERE id='e8000000-0000-4000-8000-000000000001'),'merchant cannot clear the moderation flag');
-- Same shape as stock restoration on a cancelled order: stock rises and an empty product is reactivated.
UPDATE public.products SET stock=stock+2,is_active=true,name='Edited name' WHERE id='e8000000-0000-4000-8000-000000000001';
SELECT tests.assert_true((SELECT NOT is_active AND stock=2 AND name='Edited name' FROM public.products WHERE id='e8000000-0000-4000-8000-000000000001'),'stock and detail edits apply but keep the product hidden');
INSERT INTO public.products(id,seller_id,name,price,stock,moderation_hidden_at) VALUES('e8000000-0000-4000-8000-000000000002','e7000000-0000-4000-8000-000000000001','Own product',50,1,now());
SELECT tests.assert_true((SELECT is_active AND moderation_hidden_at IS NULL FROM public.products WHERE id='e8000000-0000-4000-8000-000000000002'),'merchant cannot set the moderation flag on insert');

SELECT set_config('request.jwt.claim.sub','e6000000-0000-4000-8000-000000000002',true);
SELECT public.admin_set_product_active('e8000000-0000-4000-8000-000000000001',true,null);
SELECT tests.assert_true((SELECT is_active AND moderation_hidden_at IS NULL FROM public.products WHERE id='e8000000-0000-4000-8000-000000000001'),'admin republish clears the moderation flag');

SELECT set_config('request.jwt.claim.sub','e6000000-0000-4000-8000-000000000001',true);
UPDATE public.products SET is_active=false WHERE id='e8000000-0000-4000-8000-000000000001';
UPDATE public.products SET is_active=true WHERE id='e8000000-0000-4000-8000-000000000001';
SELECT tests.assert_true((SELECT is_active FROM public.products WHERE id='e8000000-0000-4000-8000-000000000001'),'merchant toggles a product the admin did not hide');
ROLLBACK;
