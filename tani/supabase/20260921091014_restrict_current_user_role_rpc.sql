-- Production hardening: current_user_role() is an internal authorization helper.
-- It is used by authenticated RLS/RPC paths and must not be exposed to anonymous callers.

revoke execute on function public.current_user_role() from public, anon;
grant execute on function public.current_user_role() to authenticated;

do $$
begin
  -- Supabase provides service_role in production. Plain PostgreSQL CI does not,
  -- so keep the production grant without making local regression setup fail.
  if exists (select 1 from pg_roles where rolname = 'service_role') then
    grant execute on function public.current_user_role() to service_role;
  end if;
end
$$;
