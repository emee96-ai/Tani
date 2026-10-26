-- Keeps logs and scheduler history from growing without bound.
-- Rows are removed in batches so no table is locked for long.
CREATE OR REPLACE FUNCTION private.purge_old_records(p_batch integer DEFAULT 5000) RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $function$
DECLARE v_errors bigint:=0; v_events bigint:=0; v_notifications bigint:=0; v_cron bigint:=0; v_n bigint;
BEGIN
 LOOP
  DELETE FROM public.app_errors WHERE id IN (SELECT id FROM public.app_errors WHERE created_at<now()-interval '90 days' LIMIT p_batch);
  GET DIAGNOSTICS v_n=ROW_COUNT; v_errors:=v_errors+v_n; EXIT WHEN v_n<p_batch;
 END LOOP;
 LOOP
  DELETE FROM public.app_events WHERE id IN (SELECT id FROM public.app_events WHERE created_at<now()-interval '180 days' LIMIT p_batch);
  GET DIAGNOSTICS v_n=ROW_COUNT; v_events:=v_events+v_n; EXIT WHEN v_n<p_batch;
 END LOOP;
 -- Unread notifications are kept so nobody loses a message they have not seen.
 LOOP
  DELETE FROM public.notifications WHERE id IN (SELECT id FROM public.notifications WHERE read_at IS NOT NULL AND created_at<now()-interval '180 days' LIMIT p_batch);
  GET DIAGNOSTICS v_n=ROW_COUNT; v_notifications:=v_notifications+v_n; EXIT WHEN v_n<p_batch;
 END LOOP;
 IF to_regclass('cron.job_run_details') IS NOT NULL THEN
  LOOP
   DELETE FROM cron.job_run_details WHERE runid IN (SELECT runid FROM cron.job_run_details WHERE end_time<now()-interval '7 days' LIMIT p_batch);
   GET DIAGNOSTICS v_n=ROW_COUNT; v_cron:=v_cron+v_n; EXIT WHEN v_n<p_batch;
  END LOOP;
 END IF;
 RETURN jsonb_build_object('app_errors',v_errors,'app_events',v_events,'notifications',v_notifications,'cron_runs',v_cron);
END;
$function$;
REVOKE ALL ON FUNCTION private.purge_old_records(integer) FROM PUBLIC,anon,authenticated,service_role;
DO $$ BEGIN
 IF to_regclass('cron.job') IS NOT NULL THEN
  PERFORM cron.schedule('tani-purge-old-records','17 3 * * *','SELECT private.purge_old_records();');
 ELSE
  RAISE NOTICE 'pg_cron is absent in this isolated test database';
 END IF;
END $$;
