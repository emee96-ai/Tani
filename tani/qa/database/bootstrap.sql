\set ON_ERROR_STOP on

create extension if not exists pgcrypto;

do $$
begin
  if not exists (select 1 from pg_roles where rolname = 'anon') then
    create role anon nologin;
  end if;
  if not exists (select 1 from pg_roles where rolname = 'authenticated') then
    create role authenticated nologin;
  end if;
end
$$;

create schema if not exists auth;
create schema if not exists private;
create schema if not exists tests;
grant usage on schema public, auth to anon, authenticated;
grant usage on schema tests to authenticated;

create or replace function auth.uid()
returns uuid
language sql
stable
as $$
  select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid
$$;
grant execute on function auth.uid() to anon, authenticated;

create table auth.users (
  id uuid primary key,
  raw_user_meta_data jsonb not null default '{}'::jsonb
);

create table public.profiles (
  id uuid primary key references auth.users(id),
  name text not null,
  phone text not null default '',
  role text not null default 'customer',
  is_active boolean not null default true,
  deleted_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.categories (
  id uuid primary key default gen_random_uuid(),
  name text not null
);

create table public.sellers (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id),
  store_name text not null,
  verification_status text not null default 'pending',
  delivery_fee numeric(12,2) not null default 0
);

create table public.products (
  id uuid primary key default gen_random_uuid(),
  seller_id uuid not null references public.sellers(id),
  category_id uuid references public.categories(id),
  name text not null,
  description text not null default '',
  price numeric(12,2) not null,
  stock integer not null default 0 check (stock >= 0),
  image text,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.product_variants (
  id uuid primary key default gen_random_uuid(),
  product_id uuid not null references public.products(id),
  name text not null,
  sku text,
  price numeric(12,2),
  stock integer not null default 0 check (stock >= 0),
  attributes jsonb not null default '{}'::jsonb,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.product_images (
  id uuid primary key default gen_random_uuid(),
  product_id uuid not null references public.products(id),
  storage_path text not null,
  media_type text not null default 'image',
  sort_order integer not null default 0,
  is_primary boolean not null default false,
  created_at timestamptz not null default now()
);

create table public.reviews (
  id uuid primary key default gen_random_uuid(),
  product_id uuid not null references public.products(id),
  rating integer not null
);

create table public.stores (
  id uuid primary key default gen_random_uuid(),
  seller_id uuid references public.sellers(id),
  name text not null,
  is_active boolean not null default true
);

create table public.delivery_settings (
  id uuid primary key default gen_random_uuid(),
  seller_id uuid not null references public.sellers(id),
  base_fee numeric(12,2) not null default 0,
  delivery_area text,
  estimated_minutes integer,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.delivery_zones (
  id uuid primary key default gen_random_uuid(),
  seller_id uuid not null references public.sellers(id),
  area_name text not null,
  fee numeric(12,2) not null,
  is_active boolean not null default true
);

create table public.addresses (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id),
  label text not null,
  description text not null,
  area text,
  landmark text,
  phone text,
  delivery_notes text
);

create table public.carts (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null unique references auth.users(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.cart_items (
  id uuid primary key default gen_random_uuid(),
  cart_id uuid not null references public.carts(id),
  product_id uuid not null references public.products(id),
  variant_id uuid references public.product_variants(id),
  quantity integer not null check (quantity between 1 and 99),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.order_groups (
  id uuid primary key default gen_random_uuid(),
  customer_id uuid not null references auth.users(id),
  subtotal numeric(12,2) not null default 0,
  delivery_total numeric(12,2) not null default 0,
  discount_total numeric(12,2) not null default 0,
  grand_total numeric(12,2) not null default 0,
  payment_method text not null default 'cod',
  payment_status text not null default 'pending',
  status text not null default 'pending',
  idempotency_key text,
  address_id uuid references public.addresses(id),
  address_snapshot jsonb,
  customer_note text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create unique index order_groups_customer_idempotency_uidx
  on public.order_groups(customer_id, idempotency_key)
  where idempotency_key is not null;

create table public.orders (
  id uuid primary key default gen_random_uuid(),
  customer_id uuid not null references public.profiles(id),
  order_group_id uuid references public.order_groups(id),
  seller_id uuid not null references public.sellers(id),
  subtotal numeric(12,2) not null default 0,
  delivery_fee numeric(12,2) not null default 0,
  discount numeric(12,2) not null default 0,
  total numeric(12,2) not null default 0,
  status text not null default 'pending',
  payment_method text not null default 'cod',
  payment_status text not null default 'pending',
  address text not null,
  phone text not null,
  customer_name_snapshot text,
  store_name_snapshot text,
  address_snapshot jsonb,
  customer_note text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.order_items (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null references public.orders(id),
  product_id uuid not null references public.products(id),
  seller_id uuid not null references public.sellers(id),
  quantity integer not null check (quantity between 1 and 99),
  unit_price numeric(12,2) not null,
  product_name_snapshot text,
  variant_snapshot jsonb,
  discount_snapshot numeric(12,2) not null default 0,
  line_total numeric(12,2),
  created_at timestamptz not null default now()
);

create table public.order_status_history (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null references public.orders(id),
  from_status text,
  to_status text not null,
  changed_by uuid references auth.users(id),
  note text,
  created_at timestamptz not null default now()
);

create or replace function public.current_user_role()
returns text language sql stable security definer set search_path = '' as $$
  select role from public.profiles where id = auth.uid() and is_active and deleted_at is null
$$;
grant execute on function public.current_user_role() to anon, authenticated;

-- Signatures retained only so the production migration can revoke legacy RPCs.
create function public.sync_my_cart(jsonb) returns uuid language sql as $$ select null::uuid $$;
create function public.quote_cart(jsonb) returns jsonb language sql as $$ select '{}'::jsonb $$;
create function public.quote_cart_v2(jsonb,jsonb) returns jsonb language sql as $$ select '{}'::jsonb $$;
create function public.checkout_create_order_group(uuid,text,text,jsonb,text) returns uuid language sql as $$ select null::uuid $$;
create function public.checkout_create_order_group_v2(uuid,text,text,jsonb,text,jsonb) returns uuid language sql as $$ select null::uuid $$;
create function public.checkout_create_order_group_v3(uuid,text,text,jsonb,text,jsonb,numeric,text) returns uuid language sql as $$ select null::uuid $$;

alter table public.orders enable row level security;
alter table public.order_items enable row level security;
grant select on public.profiles, public.sellers, public.products, public.product_variants,
  public.orders, public.order_items, public.order_groups to authenticated;

create or replace function tests.assert_true(p_condition boolean, p_message text)
returns void language plpgsql as $$
begin
  if coalesce(p_condition, false) is not true then
    raise exception 'assertion failed: %', p_message;
  end if;
  raise notice 'PASS: %', p_message;
end
$$;
grant execute on function tests.assert_true(boolean,text) to authenticated;
