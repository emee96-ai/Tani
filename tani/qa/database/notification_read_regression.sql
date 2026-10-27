BEGIN;
INSERT INTO auth.users(id,email,raw_user_meta_data) VALUES('db000000-0000-4000-8000-000000000001','notify-read@tani.invalid','{"name":"Notify read","phone":"0712345601"}');
INSERT INTO public.profiles(id,name,phone,role) VALUES('db000000-0000-4000-8000-000000000001','Notify read','0712345601','customer')
ON CONFLICT(id) DO NOTHING;
INSERT INTO public.notifications(id,user_id,type,title,body) VALUES('db100000-0000-4000-8000-000000000001','db000000-0000-4000-8000-000000000001','test','t','b');

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claim.sub','db000000-0000-4000-8000-000000000001',true);
UPDATE public.notifications SET read_at=now() WHERE id='db100000-0000-4000-8000-000000000001';
RESET ROLE;

SELECT tests.assert_true((SELECT read_at IS NOT NULL FROM public.notifications WHERE id='db100000-0000-4000-8000-000000000001'),'the owner can mark a notification as read');
ROLLBACK;
