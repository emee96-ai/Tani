BEGIN;
INSERT INTO auth.users(id,email,raw_user_meta_data) SELECT ('e1000000-0000-4000-8000-'||lpad(n::text,12,'0'))::uuid,'moderation-'||n||'@tani.invalid',jsonb_build_object('name','Moderation test '||n,'phone','05123456'||n) FROM generate_series(1,4) n;
INSERT INTO public.profiles(id,name,phone,role) SELECT ('e1000000-0000-4000-8000-'||lpad(n::text,12,'0'))::uuid,'Moderation test '||n,'05123456'||n,
 CASE n WHEN 1 THEN 'customer' WHEN 2 THEN 'seller' WHEN 3 THEN 'admin' ELSE 'support' END FROM generate_series(1,4) n
ON CONFLICT(id) DO UPDATE SET role=EXCLUDED.role;
INSERT INTO public.sellers(id,user_id,store_name,verification_status) VALUES('e2000000-0000-4000-8000-000000000001','e1000000-0000-4000-8000-000000000002','Moderation store','approved');
INSERT INTO public.products(id,seller_id,name,price,stock) VALUES('e3000000-0000-4000-8000-000000000001','e2000000-0000-4000-8000-000000000001','Moderation product',100,10);
INSERT INTO public.reviews(id,product_id,customer_id,rating,comment) VALUES
 ('e4000000-0000-4000-8000-000000000001','e3000000-0000-4000-8000-000000000001','e1000000-0000-4000-8000-000000000001',1,'offensive'),
 ('e4000000-0000-4000-8000-000000000002','e3000000-0000-4000-8000-000000000001','e1000000-0000-4000-8000-000000000004',5,'fine');
INSERT INTO public.review_reports(id,review_id,reporter_id,reason) VALUES
 ('e5000000-0000-4000-8000-000000000001','e4000000-0000-4000-8000-000000000001','e1000000-0000-4000-8000-000000000002','abusive'),
 ('e5000000-0000-4000-8000-000000000002','e4000000-0000-4000-8000-000000000002','e1000000-0000-4000-8000-000000000002','spam');

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub','e1000000-0000-4000-8000-000000000001',true);
SELECT tests.expect_denied($q$SELECT public.admin_moderate_review_report('e5000000-0000-4000-8000-000000000001','hide')$q$,'customer cannot moderate reports');
SELECT set_config('request.jwt.claim.sub','e1000000-0000-4000-8000-000000000004',true);
SELECT tests.expect_denied($q$SELECT public.admin_moderate_review_report('e5000000-0000-4000-8000-000000000001','hide')$q$,'support cannot hide reviews');
SELECT public.admin_moderate_review_report('e5000000-0000-4000-8000-000000000002','dismiss');
SELECT set_config('request.jwt.claim.sub','e1000000-0000-4000-8000-000000000003',true);
SELECT public.admin_moderate_review_report('e5000000-0000-4000-8000-000000000001','hide');
SELECT tests.assert_true((SELECT status='hidden' AND moderation_reason='abusive' FROM public.reviews WHERE id='e4000000-0000-4000-8000-000000000001'),'hide action hides the review with the report reason');
SELECT tests.assert_true((SELECT status='actioned' FROM public.review_reports WHERE id='e5000000-0000-4000-8000-000000000001'),'hide action closes the report');
SELECT tests.assert_true((SELECT status='dismissed' FROM public.review_reports WHERE id='e5000000-0000-4000-8000-000000000002'),'dismiss closes the report');
SELECT tests.assert_true((SELECT status='published' FROM public.reviews WHERE id='e4000000-0000-4000-8000-000000000002'),'dismiss leaves the review published');
DO $$ BEGIN
 BEGIN PERFORM public.admin_moderate_review_report('e5000000-0000-4000-8000-000000000001','hide');
 EXCEPTION WHEN raise_exception THEN
   IF SQLERRM='Report already handled' THEN RETURN; END IF;
   RAISE;
 END;
 RAISE EXCEPTION 'A handled report was moderated twice';
END $$;
ROLLBACK;
