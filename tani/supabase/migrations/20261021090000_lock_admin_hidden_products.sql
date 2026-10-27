-- A product hidden by an admin stays hidden: the merchant's own toggle/edit (RLS lets the
-- owner update is_active) and stock restoration on cancellation must not republish it.
ALTER TABLE public.products ADD COLUMN IF NOT EXISTS moderation_hidden_at timestamptz;

-- Products hidden before this column existed: the latest admin moderation decision was "hide".
UPDATE public.products p SET moderation_hidden_at=l.created_at
FROM (
 SELECT DISTINCT ON (entity_id) entity_id,created_at,new_values
 FROM public.audit_logs
 WHERE action='product_moderation' AND entity_type='product'
 ORDER BY entity_id,created_at DESC
) l
WHERE l.entity_id=p.id AND l.new_values->>'is_active'='false' AND NOT p.is_active;

CREATE OR REPLACE FUNCTION private.enforce_product_moderation() RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $function$
BEGIN
 -- Requests without a user (service role, SQL editor) and admins manage the flag.
 IF auth.uid() IS NULL OR coalesce(public.current_user_role()='admin',false) THEN RETURN NEW; END IF;
 IF TG_OP='INSERT' THEN NEW.moderation_hidden_at:=NULL; RETURN NEW; END IF;
 NEW.moderation_hidden_at:=OLD.moderation_hidden_at;
 IF NEW.moderation_hidden_at IS NOT NULL THEN NEW.is_active:=false; END IF;
 RETURN NEW;
END;
$function$;
REVOKE ALL ON FUNCTION private.enforce_product_moderation() FROM PUBLIC,anon,authenticated;

DROP TRIGGER IF EXISTS tani_enforce_product_moderation ON public.products;
CREATE TRIGGER tani_enforce_product_moderation BEFORE INSERT OR UPDATE ON public.products
FOR EACH ROW EXECUTE FUNCTION private.enforce_product_moderation();

CREATE OR REPLACE FUNCTION public.admin_set_product_active(p_product_id uuid, p_active boolean, p_reason text DEFAULT NULL::text)
 RETURNS boolean
 LANGUAGE plpgsql
 SET search_path TO ''
AS $function$
declare
  v_uid uuid := auth.uid();
  v_old_active boolean;
begin
  if not exists(
    select 1 from public.profiles p
    where p.id=v_uid and p.role='admin' and p.is_active=true and p.deleted_at is null
  ) then
    raise exception 'Admin permission required';
  end if;
  if char_length(coalesce(p_reason,'')) > 500 then
    raise exception 'Moderation reason is too long';
  end if;
  if p_active=false and coalesce(trim(p_reason),'')='' then
    raise exception 'Moderation reason is required';
  end if;

  select is_active into v_old_active
  from public.products
  where id=p_product_id
  for update;
  if not found then raise exception 'Product not found'; end if;

  update public.products
  set is_active=p_active,
      moderation_hidden_at=case when p_active then null else now() end,
      updated_at=now()
  where id=p_product_id;

  insert into public.audit_logs(actor_id,action,entity_type,entity_id,old_values,new_values,source)
  values(
    v_uid,'product_moderation','product',p_product_id,
    jsonb_build_object('is_active',v_old_active),
    jsonb_build_object('is_active',p_active,'reason',nullif(trim(coalesce(p_reason,'')),'')),
    'admin'
  );
  return true;
end;
$function$;
NOTIFY pgrst,'reload schema';
