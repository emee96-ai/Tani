-- Tani push notification foundation.
-- Provider credentials are server-side only. Android clients register only their own device token.

create table if not exists public.push_devices (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    provider text not null check (provider in ('fcm','expo','apns')),
    token text not null,
    platform text not null default 'android' check (platform in ('android','ios','web')),
    app_version text,
    device_model text,
    locale text,
    is_active boolean not null default true,
    last_seen_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (provider, token)
);

alter table public.push_devices enable row level security;

revoke all on table public.push_devices from public, anon, authenticated;
grant select, insert, update, delete on table public.push_devices to authenticated;

drop policy if exists tani_push_devices_owner_select on public.push_devices;
create policy tani_push_devices_owner_select
on public.push_devices
for select
to authenticated
using ((select auth.uid()) = user_id);

drop policy if exists tani_push_devices_owner_insert on public.push_devices;
create policy tani_push_devices_owner_insert
on public.push_devices
for insert
to authenticated
with check ((select auth.uid()) = user_id);

drop policy if exists tani_push_devices_owner_update on public.push_devices;
create policy tani_push_devices_owner_update
on public.push_devices
for update
to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

drop policy if exists tani_push_devices_owner_delete on public.push_devices;
create policy tani_push_devices_owner_delete
on public.push_devices
for delete
to authenticated
using ((select auth.uid()) = user_id);

create index if not exists push_devices_user_active_idx
    on public.push_devices(user_id, is_active, updated_at desc);
create index if not exists push_devices_last_seen_idx
    on public.push_devices(last_seen_at desc);

comment on table public.push_devices is
    'Per-user device push registrations. Provider credentials stay server-side; clients only register their own provider token.';

create or replace function public.register_my_push_device(
    p_provider text,
    p_token text,
    p_platform text default 'android',
    p_app_version text default null,
    p_device_model text default null,
    p_locale text default null
) returns uuid
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_uid uuid := (select auth.uid());
    v_id uuid;
    v_provider text := lower(trim(coalesce(p_provider, '')));
    v_platform text := lower(trim(coalesce(p_platform, '')));
    v_token text := trim(coalesce(p_token, ''));
begin
    if v_uid is null then
        raise exception 'authentication required' using errcode = '42501';
    end if;
    if v_provider not in ('fcm','expo','apns') then
        raise exception 'unsupported push provider' using errcode = '22023';
    end if;
    if v_platform not in ('android','ios','web') then
        raise exception 'unsupported platform' using errcode = '22023';
    end if;
    if length(v_token) < 16 or length(v_token) > 4096 then
        raise exception 'invalid push token' using errcode = '22023';
    end if;

    insert into public.push_devices(
        user_id, provider, token, platform, app_version, device_model, locale,
        is_active, last_seen_at, updated_at
    ) values (
        v_uid, v_provider, v_token, v_platform,
        nullif(left(trim(coalesce(p_app_version, '')), 64), ''),
        nullif(left(trim(coalesce(p_device_model, '')), 160), ''),
        nullif(left(trim(coalesce(p_locale, '')), 32), ''),
        true, now(), now()
    )
    on conflict (provider, token) do update
    set user_id = excluded.user_id,
        platform = excluded.platform,
        app_version = excluded.app_version,
        device_model = excluded.device_model,
        locale = excluded.locale,
        is_active = true,
        last_seen_at = now(),
        updated_at = now()
    returning id into v_id;

    return v_id;
end;
$$;

revoke all on function public.register_my_push_device(text,text,text,text,text,text) from public, anon;
grant execute on function public.register_my_push_device(text,text,text,text,text,text) to authenticated;

create or replace function public.unregister_my_push_device(
    p_provider text,
    p_token text
) returns boolean
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_uid uuid := (select auth.uid());
begin
    if v_uid is null then
        raise exception 'authentication required' using errcode = '42501';
    end if;

    delete from public.push_devices
    where user_id = v_uid
      and provider = lower(trim(coalesce(p_provider, '')))
      and token = trim(coalesce(p_token, ''));

    return found;
end;
$$;

revoke all on function public.unregister_my_push_device(text,text) from public, anon;
grant execute on function public.unregister_my_push_device(text,text) to authenticated;
