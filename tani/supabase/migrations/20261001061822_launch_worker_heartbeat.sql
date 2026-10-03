-- Dispatch early enough to keep the five-minute health window green between cron ticks.
-- No user tokens or signing credentials are embedded in the scheduler command.
CREATE OR REPLACE FUNCTION private.dispatch_maintenance() RETURNS bigint LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $function$
DECLARE v_id bigint;
BEGIN
 IF EXISTS(SELECT 1 FROM private.maintenance_health WHERE last_run_at>now()-interval '3 minutes')
   AND NOT EXISTS(SELECT 1 FROM private.account_deletion_requests WHERE status<>'completed' AND next_attempt_at<=now())
   AND NOT EXISTS(SELECT 1 FROM private.push_delivery_queue WHERE status IN ('pending','processing') AND next_attempt_at<=now()) THEN RETURN NULL; END IF;
 SELECT net.http_post(
  url:='https://sihttimibjzoahvwuwbm.supabase.co/functions/v1/maintenance-worker',
  headers:=jsonb_build_object('Content-Type','application/json','X-Tani-Worker-Key',(SELECT secret FROM private.maintenance_credentials WHERE name='worker')),
  body:='{}'::jsonb,timeout_milliseconds:=30000
 ) INTO v_id;
 RETURN v_id;
END;
$function$;
REVOKE ALL ON FUNCTION private.dispatch_maintenance() FROM PUBLIC,anon,authenticated,service_role;
