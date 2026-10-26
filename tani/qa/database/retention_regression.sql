BEGIN;
INSERT INTO auth.users(id,email,raw_user_meta_data) VALUES('da000000-0000-4000-8000-000000000001','retention@tani.invalid','{"name":"Retention","phone":"0712345600"}');
INSERT INTO public.app_errors(user_id,session_id,source,error_type,message,created_at) VALUES
 ('da000000-0000-4000-8000-000000000001','retention','test','old','old error',now()-interval '91 days'),
 ('da000000-0000-4000-8000-000000000001','retention','test','new','new error',now()-interval '89 days');
INSERT INTO public.app_events(user_id,session_id,event_name,created_at) VALUES
 ('da000000-0000-4000-8000-000000000001','retention','old_event',now()-interval '181 days'),
 ('da000000-0000-4000-8000-000000000001','retention','new_event',now()-interval '179 days');
INSERT INTO public.notifications(user_id,type,title,body,read_at,created_at) VALUES
 ('da000000-0000-4000-8000-000000000001','test','old read','b',now()-interval '200 days',now()-interval '200 days'),
 ('da000000-0000-4000-8000-000000000001','test','old unread','b',null,now()-interval '200 days'),
 ('da000000-0000-4000-8000-000000000001','test','new read','b',now(),now()-interval '10 days');

-- A batch of 1 forces the loops to run several times.
SELECT private.purge_old_records(1);

SELECT tests.assert_true((SELECT array_agg(error_type ORDER BY error_type) FROM public.app_errors WHERE session_id='retention')=ARRAY['new'],'errors older than 90 days are purged');
SELECT tests.assert_true((SELECT array_agg(event_name ORDER BY event_name) FROM public.app_events WHERE session_id='retention')=ARRAY['new_event'],'events older than 180 days are purged');
SELECT tests.assert_true((SELECT array_agg(title ORDER BY title) FROM public.notifications WHERE user_id='da000000-0000-4000-8000-000000000001')=ARRAY['new read','old unread'],'only old read notifications are purged');
SELECT tests.assert_true(NOT has_function_privilege('authenticated','private.purge_old_records(integer)','EXECUTE'),'clients cannot run the purge');
ROLLBACK;
