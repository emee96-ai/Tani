-- All three review records commit together. Ownership and products come from the order.
CREATE OR REPLACE FUNCTION public.submit_delivered_order_reviews(
  p_order_id uuid, p_rating integer, p_comment text DEFAULT ''
) RETURNS boolean LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $function$
DECLARE
  v_uid uuid := private.require_active_account();
  v_order public.orders%ROWTYPE;
  v_comment text := btrim(coalesce(p_comment, ''));
BEGIN
  IF p_rating IS NULL OR p_rating NOT BETWEEN 1 AND 5 OR char_length(v_comment) > 1000 THEN
    RAISE EXCEPTION 'Invalid review' USING ERRCODE = '22023';
  END IF;
  SELECT * INTO v_order FROM public.orders WHERE id = p_order_id FOR UPDATE;
  IF NOT FOUND OR v_order.customer_id IS DISTINCT FROM v_uid THEN
    RAISE EXCEPTION 'Order does not belong to this customer' USING ERRCODE = '42501';
  END IF;
  IF v_order.status IS DISTINCT FROM 'delivered' OR v_order.seller_id IS NULL THEN
    RAISE EXCEPTION 'Reviews require a delivered order' USING ERRCODE = '23514';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM public.order_items WHERE order_id = p_order_id) THEN
    RAISE EXCEPTION 'Order has no items' USING ERRCODE = '23514';
  END IF;
  INSERT INTO public.reviews(product_id,customer_id,order_id,rating,comment)
    SELECT DISTINCT product_id,v_uid,p_order_id,p_rating,v_comment FROM public.order_items WHERE order_id=p_order_id
    ON CONFLICT (product_id,customer_id) DO UPDATE SET
      order_id=EXCLUDED.order_id,rating=EXCLUDED.rating,comment=EXCLUDED.comment,updated_at=now();
  INSERT INTO public.merchant_reviews(order_id,seller_id,customer_id,rating,comment)
    VALUES(p_order_id,v_order.seller_id,v_uid,p_rating,v_comment)
    ON CONFLICT (order_id,customer_id) DO UPDATE SET
      rating=EXCLUDED.rating,comment=EXCLUDED.comment,updated_at=now();
  INSERT INTO public.order_reviews(order_id,customer_id,rating,comment)
    VALUES(p_order_id,v_uid,p_rating,v_comment)
    ON CONFLICT (order_id,customer_id) DO UPDATE SET
      rating=EXCLUDED.rating,comment=EXCLUDED.comment,updated_at=now();
  RETURN true;
END;
$function$;
REVOKE ALL ON FUNCTION public.submit_delivered_order_reviews(uuid,integer,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.submit_delivered_order_reviews(uuid,integer,text) TO authenticated;
NOTIFY pgrst, 'reload schema';

CREATE OR REPLACE FUNCTION public.send_support_ticket_message(p_ticket_id uuid,p_body text)
RETURNS public.ticket_messages LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $function$
DECLARE v_uid uuid := private.require_active_account(); v_ticket public.support_tickets%ROWTYPE; v_message public.ticket_messages%ROWTYPE;
BEGIN
  IF char_length(btrim(coalesce(p_body,''))) NOT BETWEEN 1 AND 2000 THEN
    RAISE EXCEPTION 'Invalid message' USING ERRCODE='22023';
  END IF;
  SELECT * INTO v_ticket FROM public.support_tickets WHERE id=p_ticket_id FOR UPDATE;
  IF NOT FOUND OR (v_ticket.user_id IS DISTINCT FROM v_uid AND NOT coalesce(public.current_user_role() IN ('admin','support'),false)) THEN
    RAISE EXCEPTION 'Ticket is not accessible' USING ERRCODE='42501';
  END IF;
  IF v_ticket.status NOT IN ('open','in_progress') THEN
    RAISE EXCEPTION 'Ticket is closed' USING ERRCODE='23514';
  END IF;
  INSERT INTO public.ticket_messages(ticket_id,sender_id,body) VALUES(p_ticket_id,v_uid,btrim(p_body)) RETURNING * INTO v_message;
  RETURN v_message;
END;
$function$;
REVOKE ALL ON FUNCTION public.send_support_ticket_message(uuid,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.send_support_ticket_message(uuid,text) TO authenticated;
NOTIFY pgrst, 'reload schema';
