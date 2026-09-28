\set ON_ERROR_STOP on

alter table public.stores
  add column if not exists city text not null default 'كوستي',
  add column if not exists is_open boolean not null default true;

alter table public.delivery_settings
  add column if not exists notes text;

alter table public.delivery_zones
  add column if not exists estimated_minutes integer,
  add column if not exists sort_order integer not null default 0,
  add column if not exists created_at timestamptz not null default now(),
  add column if not exists updated_at timestamptz not null default now();

create table if not exists public.merchant_profiles (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null unique references auth.users(id),
  seller_id uuid unique references public.sellers(id),
  business_name text not null default '',
  description text not null default '',
  verification_status text not null default 'pending',
  delivery_area text,
  delivery_fee numeric(12,2) not null default 0,
  estimated_minutes integer,
  delivery_zones jsonb not null default '[]'::jsonb,
  updated_at timestamptz not null default now()
);

grant select,insert,update,delete on public.merchant_profiles,public.delivery_settings,public.delivery_zones to authenticated;
