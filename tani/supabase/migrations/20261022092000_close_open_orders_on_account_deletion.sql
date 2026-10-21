-- Closes an open order during account deletion with the same stock restoration as
-- transition_order_status, without its role checks (the owner is being suspended).
CREATE OR REPLACE FUNCTION private.close_order_for_account_deletion(p_order_id uuid,p_to_status text,p_note text,p_actor uuid)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $function$
DECLARE v_order public.orders%rowtype;
BEGIN
 SELECT * INTO v_order FROM public.orders WHERE id=p_order_id FOR UPDATE;
 IF NOT FOUND OR v_order.status IN ('delivered','cancelled','rejected','failed') THEN RETURN; END IF;
 IF v_order.stock_restored_at IS NULL THEN
   UPDATE public.product_variants v SET stock=v.stock+oi.quantity,updated_at=now()
   FROM public.order_items oi
   WHERE oi.order_id=v_order.id AND oi.product_id=v.product_id AND coalesce(oi.variant_snapshot->>'id','')=v.id::text;
   UPDATE public.products p SET stock=p.stock+oi.quantity,
     is_active=CASE WHEN p.stock=0 THEN true ELSE p.is_active END,updated_at=now()
   FROM public.order_items oi
   WHERE oi.order_id=v_order.id AND oi.product_id=p.id AND coalesce(oi.variant_snapshot->>'id','')='';
   UPDATE public.orders SET stock_restored_at=now() WHERE id=v_order.id;
 END IF;
 UPDATE public.orders SET status=p_to_status,updated_at=now() WHERE id=v_order.id;
 INSERT INTO public.order_status_history(order_id,from_status,to_status,changed_by,note,created_at)
 VALUES(v_order.id,v_order.status,p_to_status,p_actor,p_note,now());
END;
$function$;
REVOKE ALL ON FUNCTION private.close_order_for_account_deletion(uuid,text,text,uuid) FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION public.soft_delete_my_account()
RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $function$
DECLARE
 v_uid uuid := auth.uid();
 v_profile public.profiles%rowtype;
 v_order record;
BEGIN
 IF v_uid IS NULL THEN RAISE EXCEPTION 'authentication required' USING ERRCODE='28000'; END IF;
 SELECT * INTO v_profile FROM public.profiles WHERE id=v_uid FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Account profile not found' USING ERRCODE='P0002'; END IF;
 IF v_profile.deleted_at IS NOT NULL THEN RETURN true; END IF;
 PERFORM private.require_active_account();
 IF v_profile.role='admin' AND NOT EXISTS (
   SELECT 1 FROM public.profiles WHERE role='admin' AND is_active AND deleted_at IS NULL AND id<>v_uid
 ) THEN RAISE EXCEPTION 'Cannot delete the last active admin' USING ERRCODE='23514'; END IF;

 -- Open orders cannot be finished once the account is gone: the store's orders would wait forever
 -- (a suspended merchant cannot move them) and the customer's pending orders would still be prepared.
 FOR v_order IN
   SELECT o.id,o.status,o.customer_id=v_uid AS own_order FROM public.orders o
   WHERE (o.customer_id=v_uid AND o.status='pending')
      OR (o.status IN ('pending','accepted','preparing','ready')
          AND EXISTS(SELECT 1 FROM public.sellers s WHERE s.id=o.seller_id AND s.user_id=v_uid))
   ORDER BY o.id FOR UPDATE
 LOOP
   PERFORM private.close_order_for_account_deletion(
     v_order.id,
     CASE WHEN v_order.status='pending' AND NOT v_order.own_order THEN 'rejected' ELSE 'cancelled' END,
     CASE WHEN v_order.own_order THEN 'Account deleted by customer' ELSE 'Store account deleted' END,
     v_uid);
 END LOOP;

 PERFORM set_config('tani.merchant_workflow','submit',true);
 PERFORM set_config('tani.account_deletion',v_uid::text,true);
 UPDATE public.products p SET is_active=false,updated_at=now()
 WHERE EXISTS(SELECT 1 FROM public.sellers s WHERE s.id=p.seller_id AND s.user_id=v_uid);
 UPDATE public.stores st SET is_active=false,is_open=false,contact_phone=NULL,whatsapp=NULL,updated_at=now()
 WHERE EXISTS(SELECT 1 FROM public.sellers s WHERE s.id=st.seller_id AND s.user_id=v_uid)
    OR EXISTS(SELECT 1 FROM public.merchant_profiles mp WHERE mp.id=st.merchant_id AND mp.user_id=v_uid);
 UPDATE public.merchant_profiles SET verification_status='suspended',suspended_at=coalesce(suspended_at,now()),
   trust_badge=false,phone=NULL,whatsapp=NULL,area=NULL,review_note='Account deleted by owner',updated_at=now()
 WHERE user_id=v_uid;
 UPDATE public.sellers SET verification_status='suspended' WHERE user_id=v_uid;
 UPDATE public.profiles SET is_active=false,deleted_at=now(),role='customer',admin_previous_role=NULL,
   name='محذوف',phone='deleted-'||left(md5(v_uid::text),22),email=NULL,avatar_url=NULL,updated_at=now()
 WHERE id=v_uid;
 UPDATE public.order_groups SET address_id=NULL WHERE customer_id=v_uid;
 DELETE FROM public.addresses WHERE user_id=v_uid;
 DELETE FROM public.carts WHERE user_id=v_uid;
 DELETE FROM public.favorites WHERE user_id=v_uid;
 DELETE FROM public.push_devices WHERE user_id=v_uid;
 INSERT INTO private.account_deletion_requests(user_id) VALUES(v_uid) ON CONFLICT(user_id) DO NOTHING;
 INSERT INTO public.audit_logs(actor_id,action,entity_type,entity_id,source)
 VALUES(v_uid,'soft_delete','profile',v_uid,'account');
 RETURN true;
END;
$function$;
REVOKE ALL ON FUNCTION public.soft_delete_my_account() FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.soft_delete_my_account() TO authenticated;

REVOKE ALL ON FUNCTION public.soft_delete_my_account() FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.soft_delete_my_account() TO authenticated;
NOTIFY pgrst,'reload schema';
