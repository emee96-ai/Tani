-- TANI Phase 1 — Analytics / Error Monitoring / Admin Foundation
-- Run after schema.sql + phase0_full_product_foundation.sql.

-- Tables are created in schema.sql/phase0; keep this file idempotent.
alter table public.app_events enable row level security;
alter table public.app_errors enable row level security;
alter table public.audit_logs enable row level security;
alter table public.complaints enable row level security;

-- Analytics/error ingestion without user-id spoofing.
drop policy if exists tani_app_events_anon_insert on public.app_events;
create policy tani_app_events_anon_insert on public.app_events for insert to anon
with check(user_id is null);
drop policy if exists tani_app_events_authenticated_insert on public.app_events;
create policy tani_app_events_authenticated_insert on public.app_events for insert to authenticated
with check(user_id=(select auth.uid()));
drop policy if exists tani_app_events_admin_read on public.app_events;
create policy tani_app_events_admin_read on public.app_events for select to authenticated
using(public.current_user_role()=any(array['admin'::text,'support'::text]));

drop policy if exists tani_app_errors_anon_insert on public.app_errors;
create policy tani_app_errors_anon_insert on public.app_errors for insert to anon
with check(user_id is null);
drop policy if exists tani_app_errors_authenticated_insert on public.app_errors;
create policy tani_app_errors_authenticated_insert on public.app_errors for insert to authenticated
with check(user_id=(select auth.uid()));
drop policy if exists tani_app_errors_admin_read on public.app_errors;
create policy tani_app_errors_admin_read on public.app_errors for select to authenticated
using(public.current_user_role()=any(array['admin'::text,'support'::text]));

-- Admin/support visibility needed by the bundled dashboard.
drop policy if exists tani_profiles_admin_support_read on public.profiles;
create policy tani_profiles_admin_support_read on public.profiles for select to authenticated
using(public.current_user_role()=any(array['admin'::text,'support'::text]));

drop policy if exists tani_sellers_admin_support_read on public.sellers;
create policy tani_sellers_admin_support_read on public.sellers for select to authenticated
using(public.current_user_role()=any(array['admin'::text,'support'::text]));

drop policy if exists tani_products_admin_support_read on public.products;
create policy tani_products_admin_support_read on public.products for select to authenticated
using(public.current_user_role()=any(array['admin'::text,'support'::text]));

drop policy if exists tani_orders_admin_support_read on public.orders;
create policy tani_orders_admin_support_read on public.orders for select to authenticated
using(public.current_user_role()=any(array['admin'::text,'support'::text]));

drop policy if exists tani_merchant_profiles_admin_support_read on public.merchant_profiles;
create policy tani_merchant_profiles_admin_support_read on public.merchant_profiles for select to authenticated
using(public.current_user_role()=any(array['admin'::text,'support'::text]));

drop policy if exists tani_audit_admin_read on public.audit_logs;
create policy tani_audit_admin_read on public.audit_logs for select to authenticated
using(public.current_user_role()=any(array['admin'::text,'support'::text]));

drop policy if exists tani_complaints_admin_update on public.complaints;
create policy tani_complaints_admin_update on public.complaints for update to authenticated
using(public.current_user_role()='admin') with check(public.current_user_role()='admin');

-- Account activation is a protected server-side action.
create or replace function public.admin_set_account_active(p_user_id uuid,p_active boolean)
returns void
language plpgsql
security definer
set search_path=''
as $$
begin
  if not exists(
    select 1 from public.profiles p
    where p.id=auth.uid() and p.role='admin' and p.is_active=true and p.deleted_at is null
  ) then
    raise exception 'Admin permission required';
  end if;
  if p_user_id=auth.uid() and p_active=false then
    raise exception 'Admin cannot deactivate own account';
  end if;
  update public.profiles
  set is_active=p_active,
      deleted_at=case when p_active then null else now() end,
      updated_at=now()
  where id=p_user_id;
  if not found then raise exception 'User not found'; end if;
  insert into public.audit_logs(actor_id,action,entity_type,entity_id,new_values,source)
  values(auth.uid(),'set_account_active','profile',p_user_id,jsonb_build_object('is_active',p_active),'admin');
end;
$$;
revoke all on function public.admin_set_account_active(uuid,boolean) from public,anon;
grant execute on function public.admin_set_account_active(uuid,boolean) to authenticated;

-- Table grants. RLS remains the row-level authorization boundary.
grant insert on public.app_events,public.app_errors to anon,authenticated;
grant select on public.app_events,public.app_errors,public.audit_logs,public.complaints to authenticated;
grant update on public.complaints to authenticated;
grant select on public.profiles,public.sellers,public.products,public.orders,public.merchant_profiles to authenticated;
