-- Protected moderation actions used by Admin App v2.
-- These functions intentionally use SECURITY DEFINER because product/category rows
-- are otherwise writable only by their merchant owners. Every function performs an
-- explicit active-admin check, fixes search_path, validates input, and writes audit.

create or replace function public.admin_set_product_active(
  p_product_id uuid,
  p_active boolean,
  p_reason text default null
)
returns boolean
language plpgsql
security definer
set search_path=''
as $$
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
  set is_active=p_active,updated_at=now()
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
$$;

revoke all on function public.admin_set_product_active(uuid,boolean,text) from public,anon;
grant execute on function public.admin_set_product_active(uuid,boolean,text) to authenticated;

create or replace function public.admin_set_category_active(
  p_category_id uuid,
  p_active boolean
)
returns boolean
language plpgsql
security definer
set search_path=''
as $$
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

  select is_active into v_old_active
  from public.categories
  where id=p_category_id
  for update;
  if not found then raise exception 'Category not found'; end if;

  update public.categories
  set is_active=p_active,updated_at=now()
  where id=p_category_id;

  insert into public.audit_logs(actor_id,action,entity_type,entity_id,old_values,new_values,source)
  values(
    v_uid,'category_status_change','category',p_category_id,
    jsonb_build_object('is_active',v_old_active),
    jsonb_build_object('is_active',p_active),
    'admin'
  );
  return true;
end;
$$;

revoke all on function public.admin_set_category_active(uuid,boolean) from public,anon;
grant execute on function public.admin_set_category_active(uuid,boolean) to authenticated;
