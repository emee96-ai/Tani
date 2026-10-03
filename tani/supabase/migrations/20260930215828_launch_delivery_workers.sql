-- Service-only queues; the mobile client never receives a service credential.
CREATE TABLE private.maintenance_credentials (
 name text PRIMARY KEY, secret text NOT NULL CHECK(char_length(secret)>=64)
);
ALTER TABLE private.maintenance_credentials ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON private.maintenance_credentials FROM PUBLIC,anon,authenticated,service_role;
INSERT INTO private.maintenance_credentials(name,secret)
 VALUES('worker',replace(gen_random_uuid()::text,'-','')||replace(gen_random_uuid()::text,'-',''));
CREATE OR REPLACE FUNCTION public.maintenance_worker_key() RETURNS text LANGUAGE sql SECURITY DEFINER SET search_path='' AS $function$
 SELECT secret FROM private.maintenance_credentials WHERE name='worker';
$function$;

CREATE TABLE private.account_deletion_objects (
 user_id uuid NOT NULL REFERENCES private.account_deletion_requests(user_id) ON DELETE CASCADE,
 bucket_id text NOT NULL, object_name text NOT NULL, deleted_at timestamptz,
 PRIMARY KEY(user_id,bucket_id,object_name)
);
ALTER TABLE private.account_deletion_objects ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON private.account_deletion_objects FROM PUBLIC,anon,authenticated;
CREATE OR REPLACE FUNCTION private.snapshot_account_deletion_objects() RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $function$
BEGIN
 INSERT INTO private.account_deletion_objects(user_id,bucket_id,object_name)
 SELECT NEW.user_id,o.bucket_id,o.name FROM storage.objects o
 WHERE o.owner_id=NEW.user_id::text OR (o.bucket_id IN ('avatars','merchant-private','store-assets','product-images') AND o.name LIKE NEW.user_id::text||'/%')
   OR (o.bucket_id='merchant-private' AND EXISTS(SELECT 1 FROM public.merchant_identity_documents d WHERE d.user_id=NEW.user_id AND d.storage_path=o.name))
 ON CONFLICT DO NOTHING;
 RETURN NEW;
END;
$function$;
REVOKE ALL ON FUNCTION private.snapshot_account_deletion_objects() FROM PUBLIC,anon,authenticated;
CREATE TRIGGER snapshot_account_deletion_objects AFTER INSERT ON private.account_deletion_requests FOR EACH ROW EXECUTE FUNCTION private.snapshot_account_deletion_objects();
ALTER TABLE private.account_deletion_requests ADD COLUMN lease_id uuid;
CREATE INDEX account_deletion_due_idx ON private.account_deletion_requests(next_attempt_at) WHERE status<>'completed';
CREATE OR REPLACE FUNCTION public.maintenance_claim_deletions(p_user_id uuid DEFAULT NULL)
RETURNS TABLE(user_id uuid,lease_id uuid) LANGUAGE sql SECURITY DEFINER SET search_path='' AS $function$
 WITH due AS (
  SELECT r.user_id FROM private.account_deletion_requests r
  WHERE r.status IN ('pending','processing','failed') AND r.next_attempt_at<=now()
    AND (p_user_id IS NULL OR r.user_id=p_user_id)
  ORDER BY r.next_attempt_at LIMIT 10 FOR UPDATE SKIP LOCKED
 ) UPDATE private.account_deletion_requests r SET status='processing',attempts=attempts+1,
   next_attempt_at=now()+interval '5 minutes',lease_id=gen_random_uuid()
 FROM due WHERE r.user_id=due.user_id RETURNING r.user_id,r.lease_id;
$function$;
CREATE OR REPLACE FUNCTION public.maintenance_deletion_objects(p_user_id uuid,p_lease_id uuid)
RETURNS TABLE(bucket_id text,object_name text) LANGUAGE sql SECURITY DEFINER SET search_path='' AS $function$
 SELECT o.bucket_id,o.object_name FROM private.account_deletion_objects o
 JOIN private.account_deletion_requests r USING(user_id)
 WHERE r.user_id=p_user_id AND r.lease_id=p_lease_id AND r.status='processing' AND o.deleted_at IS NULL;
$function$;
CREATE OR REPLACE FUNCTION public.maintenance_mark_file_deleted(p_user_id uuid,p_lease_id uuid,p_bucket_id text,p_object_name text)
RETURNS void LANGUAGE sql SECURITY DEFINER SET search_path='' AS $function$
 UPDATE private.account_deletion_objects o SET deleted_at=now()
 WHERE o.user_id=p_user_id AND o.bucket_id=p_bucket_id AND o.object_name=p_object_name
 AND EXISTS(SELECT 1 FROM private.account_deletion_requests r WHERE r.user_id=p_user_id AND r.lease_id=p_lease_id AND r.status='processing');
$function$;
CREATE OR REPLACE FUNCTION public.maintenance_finish_deletion(p_user_id uuid,p_lease_id uuid,p_error text DEFAULT NULL)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $function$
DECLARE v_attempts int;
BEGIN
 SELECT attempts INTO v_attempts FROM private.account_deletion_requests WHERE user_id=p_user_id AND lease_id=p_lease_id AND status='processing' FOR UPDATE;
 IF NOT FOUND THEN RETURN; END IF;
 IF p_error IS NOT NULL THEN
  UPDATE private.account_deletion_requests SET status='failed',last_error=left(p_error,200),lease_id=NULL,
    next_attempt_at=now()+make_interval(secs=>least(3600,60*power(2,least(v_attempts,6))::int)) WHERE user_id=p_user_id;
  RETURN;
 END IF;
 IF EXISTS(SELECT 1 FROM private.account_deletion_objects WHERE user_id=p_user_id AND deleted_at IS NULL) THEN
  RAISE EXCEPTION 'Storage erasure is incomplete' USING ERRCODE='23514';
 END IF;
 -- Keep orders and dispute/audit records; remove identity documents and transient account data.
 DELETE FROM public.merchant_identity_documents WHERE user_id=p_user_id;
 DELETE FROM public.notification_preferences WHERE user_id=p_user_id;
 DELETE FROM public.notifications WHERE user_id=p_user_id;
 DELETE FROM private.account_deletion_objects WHERE user_id=p_user_id;
 UPDATE private.account_deletion_requests SET status='completed',completed_at=now(),last_error=NULL,lease_id=NULL WHERE user_id=p_user_id;
END;
$function$;

CREATE OR REPLACE FUNCTION public.maintenance_deletion_pending(p_user_id uuid) RETURNS boolean LANGUAGE sql SECURITY DEFINER SET search_path='' AS $function$
 SELECT coalesce((SELECT status<>'completed' FROM private.account_deletion_requests WHERE user_id=p_user_id),false);
$function$;

CREATE TABLE private.push_delivery_queue (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),notification_id uuid NOT NULL REFERENCES public.notifications(id) ON DELETE CASCADE,
 device_id uuid NOT NULL REFERENCES public.push_devices(id) ON DELETE CASCADE,
 user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
 status text NOT NULL DEFAULT 'pending' CHECK(status IN ('pending','processing','sent','failed','cancelled')),
 attempts int NOT NULL DEFAULT 0,next_attempt_at timestamptz NOT NULL DEFAULT now(),
 lease_id uuid,last_error text,sent_at timestamptz,created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(notification_id,device_id)
);
ALTER TABLE private.push_delivery_queue ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON private.push_delivery_queue FROM PUBLIC,anon,authenticated;
CREATE INDEX push_delivery_due_idx ON private.push_delivery_queue(next_attempt_at) WHERE status IN ('pending','processing');
CREATE INDEX push_delivery_user_idx ON private.push_delivery_queue(user_id);
CREATE INDEX push_delivery_device_idx ON private.push_delivery_queue(device_id);
CREATE OR REPLACE FUNCTION private.enqueue_push_delivery() RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $function$
BEGIN
 INSERT INTO private.push_delivery_queue(notification_id,device_id,user_id)
 SELECT NEW.id,d.id,NEW.user_id FROM public.push_devices d JOIN public.profiles p ON p.id=d.user_id
 LEFT JOIN public.notification_preferences pref ON pref.user_id=d.user_id
 WHERE d.user_id=NEW.user_id AND d.provider='fcm' AND d.is_active AND p.is_active AND p.deleted_at IS NULL
   AND coalesce(pref.push_enabled,true)
   AND CASE WHEN NEW.type LIKE '%order%' OR NEW.type='stock_alert' THEN coalesce(pref.order_updates,true)
     WHEN NEW.type LIKE '%message%' OR NEW.type LIKE '%ticket%' THEN coalesce(pref.messages,true)
     ELSE coalesce(pref.promotions,true) END
 ON CONFLICT DO NOTHING;
 RETURN NEW;
END;
$function$;
REVOKE ALL ON FUNCTION private.enqueue_push_delivery() FROM PUBLIC,anon,authenticated;
CREATE TRIGGER enqueue_push_delivery AFTER INSERT ON public.notifications FOR EACH ROW EXECUTE FUNCTION private.enqueue_push_delivery();
CREATE OR REPLACE FUNCTION public.maintenance_claim_push_deliveries()
RETURNS TABLE(id uuid,lease_id uuid,token text,user_id uuid,notification_id uuid)
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $function$
BEGIN
 -- Re-check both ownership and preferences when sending; tokens may have moved to another account.
 UPDATE private.push_delivery_queue q SET status='cancelled',lease_id=NULL
 WHERE q.status IN ('pending','processing') AND NOT EXISTS(
  SELECT 1 FROM public.push_devices d JOIN public.profiles p ON p.id=d.user_id
  JOIN public.notifications n ON n.id=q.notification_id LEFT JOIN public.notification_preferences pref ON pref.user_id=d.user_id
  WHERE d.id=q.device_id AND d.user_id=q.user_id AND n.user_id=q.user_id AND d.is_active AND p.is_active AND p.deleted_at IS NULL
   AND coalesce(pref.push_enabled,true) AND n.created_at>now()-interval '24 hours'
   AND CASE WHEN n.type LIKE '%order%' OR n.type='stock_alert' THEN coalesce(pref.order_updates,true)
     WHEN n.type LIKE '%message%' OR n.type LIKE '%ticket%' THEN coalesce(pref.messages,true) ELSE coalesce(pref.promotions,true) END
 );
 RETURN QUERY WITH due AS (
  SELECT q.id FROM private.push_delivery_queue q WHERE q.status IN ('pending','processing') AND q.next_attempt_at<=now()
  ORDER BY q.next_attempt_at LIMIT 50 FOR UPDATE SKIP LOCKED
 ), claimed AS (
  UPDATE private.push_delivery_queue q SET status='processing',attempts=q.attempts+1,lease_id=gen_random_uuid(),next_attempt_at=now()+interval '5 minutes'
  FROM due WHERE q.id=due.id RETURNING q.*
 ) SELECT c.id,c.lease_id,d.token,c.user_id,c.notification_id FROM claimed c JOIN public.push_devices d ON d.id=c.device_id;
END;
$function$;
CREATE OR REPLACE FUNCTION public.maintenance_ack_push_delivery(p_id uuid,p_lease_id uuid,p_error text DEFAULT NULL,p_invalid_token boolean DEFAULT false)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $function$
DECLARE v_job private.push_delivery_queue%ROWTYPE;
BEGIN
 SELECT * INTO v_job FROM private.push_delivery_queue WHERE id=p_id AND lease_id=p_lease_id AND status='processing' FOR UPDATE;
 IF NOT FOUND THEN RETURN; END IF;
 IF p_invalid_token THEN UPDATE public.push_devices SET is_active=false WHERE id=v_job.device_id AND user_id=v_job.user_id; END IF;
 UPDATE private.push_delivery_queue SET status=CASE WHEN p_error IS NULL THEN 'sent' WHEN p_invalid_token OR attempts>=8 THEN 'failed' ELSE 'pending' END,
  last_error=left(p_error,200),sent_at=CASE WHEN p_error IS NULL THEN now() END,lease_id=NULL,
  next_attempt_at=now()+make_interval(secs=>least(3600,30*power(2,least(attempts,7))::int)) WHERE id=p_id;
END;
$function$;

DO $$ DECLARE f regprocedure;
BEGIN
 FOR f IN SELECT p.oid::regprocedure FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname LIKE 'maintenance_%'
 LOOP
  EXECUTE format('REVOKE ALL ON FUNCTION %s FROM PUBLIC,anon,authenticated',f);
  EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO service_role',f);
 END LOOP;
END $$;
NOTIFY pgrst,'reload schema';

CREATE TABLE private.maintenance_health (
 id boolean PRIMARY KEY DEFAULT true CHECK(id), last_run_at timestamptz,
 push_configured boolean NOT NULL DEFAULT false,last_error text,deletions_completed int NOT NULL DEFAULT 0,push_sent int NOT NULL DEFAULT 0
);
ALTER TABLE private.maintenance_health ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON private.maintenance_health FROM PUBLIC,anon,authenticated;
INSERT INTO private.maintenance_health(id) VALUES(true);
CREATE OR REPLACE FUNCTION public.maintenance_record_health(p_push_configured boolean,p_error text DEFAULT NULL,p_erased integer DEFAULT 0,p_sent integer DEFAULT 0)
RETURNS void LANGUAGE sql SECURITY DEFINER SET search_path='' AS $function$
 UPDATE private.maintenance_health SET last_run_at=now(),push_configured=p_push_configured,last_error=left(p_error,100),
  deletions_completed=deletions_completed+greatest(p_erased,0),push_sent=push_sent+greatest(p_sent,0) WHERE id;
$function$;
REVOKE ALL ON FUNCTION public.maintenance_record_health(boolean,text,integer,integer) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.maintenance_record_health(boolean,text,integer,integer) TO service_role;
