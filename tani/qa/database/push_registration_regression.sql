\set ON_ERROR_STOP on

begin;

-- Structural safety assertions.
do $$
begin
  if not exists (
    select 1
    from pg_class c
    join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public'
      and c.relname = 'push_devices'
      and c.relrowsecurity
  ) then
    raise exception 'push_devices must have RLS enabled';
  end if;

  if has_table_privilege('anon', 'public.push_devices', 'SELECT')
     or has_table_privilege('anon', 'public.push_devices', 'INSERT')
     or has_table_privilege('anon', 'public.push_devices', 'UPDATE')
     or has_table_privilege('anon', 'public.push_devices', 'DELETE') then
    raise exception 'anon must not have push_devices privileges';
  end if;

  if not has_table_privilege('authenticated', 'public.push_devices', 'SELECT')
     or not has_table_privilege('authenticated', 'public.push_devices', 'INSERT')
     or not has_table_privilege('authenticated', 'public.push_devices', 'UPDATE')
     or not has_table_privilege('authenticated', 'public.push_devices', 'DELETE') then
    raise exception 'authenticated must have owner-scoped CRUD grants';
  end if;

  if has_table_privilege('authenticated', 'public.push_devices', 'TRUNCATE')
     or has_table_privilege('authenticated', 'public.push_devices', 'TRIGGER')
     or has_table_privilege('authenticated', 'public.push_devices', 'REFERENCES') then
    raise exception 'authenticated has excessive push_devices grants';
  end if;

  if has_function_privilege('anon', 'public.register_my_push_device(text,text,text,text,text,text)', 'EXECUTE')
     or has_function_privilege('anon', 'public.unregister_my_push_device(text,text)', 'EXECUTE') then
    raise exception 'anon must not execute push registration RPCs';
  end if;
end
$$;

insert into auth.users(id, raw_user_meta_data)
values
  ('11111111-1111-1111-1111-111111111111', '{}'::jsonb),
  ('22222222-2222-2222-2222-222222222222', '{}'::jsonb)
on conflict (id) do nothing;

-- First account owns the device.
set local role authenticated;
select set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', true);
select public.register_my_push_device(
  'FCM',
  'test_push_token_12345678901234567890',
  'android',
  'test',
  'CI device',
  'ar-SD'
);

do $$
begin
  if (select count(*) from public.push_devices where token = 'test_push_token_12345678901234567890') <> 1 then
    raise exception 'owner must see its registered push device';
  end if;
end
$$;
reset role;

-- A real provider token belongs to the installation, not permanently to one account.
-- Re-registering after account switch must atomically move it to the new signed-in user.
set local role authenticated;
select set_config('request.jwt.claim.sub', '22222222-2222-2222-2222-222222222222', true);
select public.register_my_push_device(
  'fcm',
  'test_push_token_12345678901234567890',
  'android',
  'test-2',
  'CI device',
  'ar-SD'
);

do $$
begin
  if (select count(*) from public.push_devices where token = 'test_push_token_12345678901234567890') <> 1 then
    raise exception 'token reassignment must not duplicate the device';
  end if;
end
$$;
reset role;

-- Old account must no longer be able to see the reassigned device.
set local role authenticated;
select set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', true);
do $$
begin
  if exists (select 1 from public.push_devices where token = 'test_push_token_12345678901234567890') then
    raise exception 'old account can still see reassigned push token';
  end if;
end
$$;
reset role;

-- New owner can see and unregister it.
set local role authenticated;
select set_config('request.jwt.claim.sub', '22222222-2222-2222-2222-222222222222', true);
do $$
begin
  if (select count(*) from public.push_devices where token = 'test_push_token_12345678901234567890') <> 1 then
    raise exception 'new owner cannot see reassigned push token';
  end if;
end
$$;
select public.unregister_my_push_device('fcm', 'test_push_token_12345678901234567890');

do $$
begin
  if exists (select 1 from public.push_devices where token = 'test_push_token_12345678901234567890') then
    raise exception 'unregister did not remove push device';
  end if;
end
$$;
reset role;

rollback;

\echo 'PASS: push registration RLS and account-switch regression'
