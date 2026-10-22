-- Review report moderation as one atomic step.
-- Both admin clients wrote review_reports.status='actioned', which the live CHECK constraint rejected, and they hid the
-- review in a separate request first, so a failed second request left a hidden review with a still-pending report.
ALTER TABLE public.review_reports DROP CONSTRAINT IF EXISTS review_reports_status_check;
ALTER TABLE public.review_reports ADD CONSTRAINT review_reports_status_check
  CHECK (status = ANY (ARRAY['pending'::text,'reviewed'::text,'dismissed'::text,'actioned'::text]));

CREATE OR REPLACE FUNCTION public.admin_moderate_review_report(p_report_id uuid, p_action text)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_uid uuid := auth.uid();
  v_role text := public.current_user_role();
  v_report public.review_reports%rowtype;
begin
  if v_role is null or v_role not in ('admin','support') then
    raise exception 'Staff permission required' using errcode='42501';
  end if;
  if p_action not in ('hide','dismiss') then
    raise exception 'Invalid moderation action';
  end if;
  -- Reviews are writable by admins only, so support may dismiss a report but not hide the review.
  if p_action='hide' and v_role<>'admin' then
    raise exception 'Admin permission required to hide a review' using errcode='42501';
  end if;

  select * into v_report from public.review_reports where id=p_report_id for update;
  if not found then raise exception 'Report not found'; end if;
  if v_report.status<>'pending' then raise exception 'Report already handled'; end if;

  if p_action='hide' then
    update public.reviews
    set status='hidden', moderation_reason=left(v_report.reason,500), updated_at=now()
    where id=v_report.review_id;
    update public.review_reports set status='actioned' where id=v_report.id;
  else
    update public.review_reports set status='dismissed' where id=v_report.id;
  end if;

  insert into public.audit_logs(actor_id,action,entity_type,entity_id,new_values,source)
  values(v_uid,'review_report_'||p_action,'review_report',v_report.id,
         jsonb_build_object('review_id',v_report.review_id),'admin');
end;
$function$;

REVOKE ALL ON FUNCTION public.admin_moderate_review_report(uuid,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.admin_moderate_review_report(uuid,text) TO authenticated,service_role;
