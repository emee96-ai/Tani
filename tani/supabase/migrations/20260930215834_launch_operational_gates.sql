-- RLS controls rows; mobile roles have no table-maintenance or schema-writing rights.
DO $$ DECLARE t regclass;
BEGIN
 FOR t IN SELECT c.oid::regclass FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('r','p') LOOP
  EXECUTE format('REVOKE TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE %s FROM anon,authenticated',t);
 END LOOP;
END $$;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public REVOKE ALL ON TABLES FROM anon,authenticated;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public GRANT SELECT,INSERT,UPDATE,DELETE ON TABLES TO anon,authenticated;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC,anon,authenticated;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA private REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC,anon,authenticated;

CREATE TABLE private.launch_approvals (
 name text PRIMARY KEY CHECK(name IN ('backup_restore','auth_email','live_push','merchant_order','account_erasure','legal_operations')),
 approved_at timestamptz NOT NULL DEFAULT now(), evidence_uri text NOT NULL CHECK(char_length(evidence_uri)>8)
);
ALTER TABLE private.launch_approvals ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON private.launch_approvals FROM PUBLIC,anon,authenticated;
GRANT SELECT,INSERT,UPDATE ON private.launch_approvals TO service_role;

CREATE OR REPLACE FUNCTION public.launch_operational_health() RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $function$
BEGIN
 PERFORM private.require_active_account();
 IF NOT coalesce(public.current_user_role() IN ('admin','support'),false) THEN RAISE EXCEPTION 'Staff access required' USING ERRCODE='42501'; END IF;
 RETURN jsonb_build_object(
  'worker_missing',CASE WHEN EXISTS(SELECT 1 FROM private.maintenance_health WHERE last_run_at>now()-interval '5 minutes' AND last_error IS NULL) THEN 0 ELSE 1 END,
  'push_configuration_missing',CASE WHEN EXISTS(SELECT 1 FROM private.maintenance_health WHERE push_configured) THEN 0 ELSE 1 END,
  'failed_push',(SELECT count(*) FROM private.push_delivery_queue WHERE status='failed'),
  'failed_erasure',(SELECT count(*) FROM private.account_deletion_requests WHERE status='failed' OR (status<>'completed' AND requested_at<now()-interval '24 hours'))
 );
END;
$function$;
REVOKE ALL ON FUNCTION public.launch_operational_health() FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.launch_operational_health() TO authenticated;

CREATE OR REPLACE FUNCTION public.maintenance_launch_gate() RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $function$
DECLARE checks jsonb;
BEGIN
 checks := jsonb_build_object(
  'active_admin',EXISTS(SELECT 1 FROM public.profiles WHERE role='admin' AND is_active AND deleted_at IS NULL),
  'merchant_records_verified',NOT EXISTS(
   SELECT 1 FROM public.merchant_profiles mp WHERE mp.verification_status='approved' AND (
    mp.policies_accepted_at IS NULL OR mp.phone_verified_at IS NULL OR NOT EXISTS(
     SELECT 1 FROM auth.users u WHERE u.id=mp.user_id AND u.phone_confirmed_at IS NOT NULL
      AND regexp_replace(coalesce(u.phone,''),'[^0-9]','','g')=regexp_replace(coalesce(mp.phone,''),'[^0-9]','','g')
    ) OR NOT EXISTS(SELECT 1 FROM public.merchant_identity_documents d JOIN storage.objects o ON o.bucket_id='merchant-private' AND o.name=d.storage_path WHERE d.merchant_id=mp.id AND d.user_id=mp.user_id)
   )
  ),
  'live_catalog',EXISTS(SELECT 1 FROM public.products p JOIN public.sellers s ON s.id=p.seller_id JOIN public.stores st ON st.seller_id=s.id WHERE p.is_active AND s.verification_status='approved' AND st.is_active AND st.is_open AND (p.stock>0 OR EXISTS(SELECT 1 FROM public.product_variants v WHERE v.product_id=p.id AND v.is_active AND v.stock>0))),
  'worker_and_push',EXISTS(SELECT 1 FROM private.maintenance_health WHERE last_run_at>now()-interval '5 minutes' AND push_configured AND last_error IS NULL),
  'queues_healthy',NOT EXISTS(SELECT 1 FROM private.push_delivery_queue WHERE status='failed') AND NOT EXISTS(SELECT 1 FROM private.account_deletion_requests WHERE status='failed'),
  'launch_evidence',(SELECT count(*)=6 AND min(approved_at)>now()-interval '30 days' FROM private.launch_approvals)
 );
 RETURN jsonb_build_object('ready',(SELECT bool_and(value::boolean) FROM jsonb_each(checks)),'checks',checks);
END;
$function$;
REVOKE ALL ON FUNCTION public.maintenance_launch_gate() FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.maintenance_launch_gate() TO service_role;
NOTIFY pgrst,'reload schema';
