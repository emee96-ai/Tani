-- Lock every order in the group before checking that all are still pending. Without the lock a
-- merchant accepting one order between the check and the loop left the group partly cancelled.
CREATE OR REPLACE FUNCTION public.customer_cancel_order_group(p_order_group_id uuid, p_note text DEFAULT NULL::text)
 RETURNS integer
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_uid uuid := auth.uid();
  v_order record;
  v_count int := 0;
begin
  perform private.require_active_account();
  if v_uid is null then
    raise exception 'Authentication required';
  end if;

  if not exists (
    select 1 from public.order_groups og
    where og.id = p_order_group_id and og.customer_id = v_uid
  ) then
    raise exception 'Order group not found';
  end if;

  perform 1
  from public.orders o
  where o.order_group_id = p_order_group_id
    and o.customer_id = v_uid
  order by o.id
  for update;

  if exists (
    select 1 from public.orders o
    where o.order_group_id = p_order_group_id
      and o.customer_id = v_uid
      and o.status <> 'pending'
  ) then
    raise exception 'This order can no longer be cancelled as a group';
  end if;

  for v_order in
    select o.id
    from public.orders o
    where o.order_group_id = p_order_group_id
      and o.customer_id = v_uid
      and o.status = 'pending'
    order by o.id
  loop
    perform public.transition_order_status(v_order.id, 'cancelled', p_note);
    v_count := v_count + 1;
  end loop;

  if v_count = 0 then
    raise exception 'No cancellable orders found';
  end if;

  perform private.refresh_order_group_status(p_order_group_id);
  return v_count;
end;
$function$;
NOTIFY pgrst,'reload schema';
