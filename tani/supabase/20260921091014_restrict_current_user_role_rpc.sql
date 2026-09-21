-- Production hardening: current_user_role() is an internal authorization helper.
-- It is used by authenticated RLS/RPC paths and must not be exposed to anonymous callers.

revoke execute on function public.current_user_role() from public, anon;
grant execute on function public.current_user_role() to authenticated, service_role;
