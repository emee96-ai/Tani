-- Secure admin/staff management for the Android admin app.
-- Applied to production as migration: admin_management

create or replace function public.admin_list_staff()
returns table (
  user_id uuid,
  email text,
  name text,
  phone text,
  role text,
  is_active boolean,
  created_at timestamptz
)
language plpgsql
security definer
set search_path = ''
as $$
begin
  if auth.uid() is null or not exists (
    select 1
    from public.profiles p
    where p.id = auth.uid()
      and p.role = 'admin'
      and p.is_active = true
      and p.deleted_at is null
  ) then
    raise exception 'Admin access required' using errcode = '42501';
  end if;

  return query
  select p.id, u.email::text, p.name, p.phone, p.role, p.is_active, p.created_at
  from public.profiles p
  join auth.users u on u.id = p.id
  where p.role in ('admin', 'support')
    and p.deleted_at is null
  order by case when p.role = 'admin' then 0 else 1 end,
           lower(coalesce(u.email, '')),
           p.created_at;
end;
$$;

revoke all on function public.admin_list_staff() from public;
revoke all on function public.admin_list_staff() from anon;
grant execute on function public.admin_list_staff() to authenticated;

create or replace function public.admin_set_user_role_by_email(
  p_email text,
  p_role text
)
returns table (
  user_id uuid,
  email text,
  name text,
  phone text,
  role text,
  is_active boolean,
  created_at timestamptz
)
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_target_id uuid;
  v_current_role text;
  v_email text := lower(trim(coalesce(p_email, '')));
begin
  if auth.uid() is null or not exists (
    select 1
    from public.profiles p
    where p.id = auth.uid()
      and p.role = 'admin'
      and p.is_active = true
      and p.deleted_at is null
  ) then
    raise exception 'Admin access required' using errcode = '42501';
  end if;

  if v_email = '' then
    raise exception 'Email is required' using errcode = '22023';
  end if;

  if p_role not in ('admin', 'support', 'customer', 'seller') then
    raise exception 'Invalid role' using errcode = '22023';
  end if;

  select u.id
    into v_target_id
  from auth.users u
  where lower(u.email) = v_email
  limit 1;

  if v_target_id is null then
    raise exception 'No registered account found for this email' using errcode = 'P0002';
  end if;

  select p.role
    into v_current_role
  from public.profiles p
  where p.id = v_target_id
    and p.deleted_at is null;

  if v_current_role is null then
    raise exception 'User profile not found' using errcode = 'P0002';
  end if;

  if v_current_role = 'admin' and p_role <> 'admin' then
    if not exists (
      select 1
      from public.profiles p
      where p.role = 'admin'
        and p.id <> v_target_id
        and p.is_active = true
        and p.deleted_at is null
    ) then
      raise exception 'Cannot remove the last active admin' using errcode = '23514';
    end if;
  end if;

  update public.profiles
  set role = p_role,
      updated_at = now()
  where id = v_target_id;

  return query
  select p.id, u.email::text, p.name, p.phone, p.role, p.is_active, p.created_at
  from public.profiles p
  join auth.users u on u.id = p.id
  where p.id = v_target_id;
end;
$$;

revoke all on function public.admin_set_user_role_by_email(text, text) from public;
revoke all on function public.admin_set_user_role_by_email(text, text) from anon;
grant execute on function public.admin_set_user_role_by_email(text, text) to authenticated;
