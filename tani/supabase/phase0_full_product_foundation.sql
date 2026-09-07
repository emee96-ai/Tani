-- TANI — Full Product Foundation for a fresh Supabase environment
-- Run AFTER schema.sql and BEFORE phase1/phase2/phase3/phase4.
-- Reconstructs the foundation that exists in the live TANI project but was
-- missing from the Phase 4 delivery ZIP.

create extension if not exists pgcrypto;
create schema if not exists extensions;
create extension if not exists pg_trgm with schema extensions;
create schema if not exists private;
revoke all on schema private from public;

-- Core columns used by later phases.
alter table public.categories
  add column if not exists slug text,
  add column if not exists sort_order integer not null default 0,
  add column if not exists is_active boolean not null default true,
  add column if not exists updated_at timestamptz not null default now();

alter table public.products
  add column if not exists updated_at timestamptz not null default now();

alter table public.sellers drop constraint if exists sellers_verification_status_check;
alter table public.sellers add constraint sellers_verification_status_check
  check (verification_status in ('pending','approved','rejected','suspended'));

-- Merchant/store domain.
create table if not exists public.merchant_profiles (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null unique references auth.users(id) on delete cascade,
  seller_id uuid unique references public.sellers(id) on delete set null,
  business_name text not null,
  description text not null default '',
  phone text,
  verification_status text not null default 'pending'
    check (verification_status in ('pending','approved','rejected','changes_requested','suspended')),
  trust_badge boolean not null default false,
  approved_at timestamptz,
  suspended_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  whatsapp text,
  category_id uuid references public.categories(id) on delete set null,
  phone_verified_at timestamptz,
  policies_accepted_at timestamptz,
  policy_version text,
  submitted_at timestamptz,
  review_note text,
  requested_changes_at timestamptz,
  store_name text,
  store_description text,
  city text not null default 'كوستي',
  area text,
  delivery_area text,
  delivery_fee numeric(12,2) not null default 0 check (delivery_fee >= 0),
  estimated_minutes integer check (estimated_minutes is null or estimated_minutes between 1 and 1440)
);

create table if not exists public.stores (
  id uuid primary key default gen_random_uuid(),
  merchant_id uuid not null unique references public.merchant_profiles(id) on delete cascade,
  name text not null,
  description text not null default '',
  logo_url text,
  cover_url text,
  city text not null default 'كوستي',
  area text,
  is_open boolean not null default true,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  seller_id uuid references public.sellers(id) on delete set null,
  category_id uuid references public.categories(id) on delete set null,
  contact_phone text,
  whatsapp text
);

create table if not exists public.delivery_settings (
  id uuid primary key default gen_random_uuid(),
  seller_id uuid not null unique references public.sellers(id) on delete cascade,
  base_fee numeric(12,2) not null default 0 check (base_fee >= 0),
  delivery_area text,
  estimated_minutes integer check (estimated_minutes is null or estimated_minutes between 1 and 1440),
  notes text,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- Catalog extensions.
create table if not exists public.product_images (
  id uuid primary key default gen_random_uuid(),
  product_id uuid not null references public.products(id) on delete cascade,
  storage_path text not null,
  sort_order integer not null default 0,
  is_primary boolean not null default false,
  created_at timestamptz not null default now(),
  unique(product_id, storage_path)
);

create table if not exists public.product_variants (
  id uuid primary key default gen_random_uuid(),
  product_id uuid not null references public.products(id) on delete cascade,
  name text not null,
  sku text,
  price numeric(12,2) check (price is null or price >= 0),
  stock integer not null default 0 check (stock >= 0),
  attributes jsonb not null default '{}'::jsonb,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.reviews (
  id uuid primary key default gen_random_uuid(),
  product_id uuid not null references public.products(id) on delete cascade,
  customer_id uuid not null references public.profiles(id) on delete cascade,
  rating integer not null check (rating between 1 and 5),
  comment text not null default '',
  created_at timestamptz not null default now(),
  unique(product_id, customer_id)
);

create table if not exists public.featured_placements (
  id uuid primary key default gen_random_uuid(),
  product_id uuid references public.products(id) on delete cascade,
  seller_id uuid references public.sellers(id) on delete cascade,
  placement text not null,
  starts_at timestamptz,
  ends_at timestamptz,
  is_active boolean not null default true,
  created_at timestamptz not null default now()
);

-- Customer/cart domain.
create table if not exists public.addresses (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  label text not null default 'المنزل',
  description text not null,
  area text,
  landmark text,
  phone text,
  delivery_notes text,
  is_default boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.carts (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null unique references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.cart_items (
  id uuid primary key default gen_random_uuid(),
  cart_id uuid not null references public.carts(id) on delete cascade,
  product_id uuid not null references public.products(id) on delete restrict,
  variant_id uuid references public.product_variants(id) on delete restrict,
  quantity integer not null check (quantity > 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create unique index if not exists cart_items_unique_base_product
  on public.cart_items(cart_id, product_id) where variant_id is null;
create unique index if not exists cart_items_unique_variant
  on public.cart_items(cart_id, product_id, variant_id) where variant_id is not null;

-- Order domain required before Phase 3.
create table if not exists public.order_groups (
  id uuid primary key default gen_random_uuid(),
  customer_id uuid not null references auth.users(id) on delete restrict,
  subtotal numeric(12,2) not null default 0 check (subtotal >= 0),
  delivery_total numeric(12,2) not null default 0 check (delivery_total >= 0),
  discount_total numeric(12,2) not null default 0 check (discount_total >= 0),
  grand_total numeric(12,2) not null default 0 check (grand_total >= 0),
  payment_method text not null default 'cod' check (payment_method in ('cod','online')),
  payment_status text not null default 'pending'
    check (payment_status in ('pending','paid','failed','refunded','partially_refunded')),
  status text not null default 'pending'
    check (status in ('pending','confirmed','processing','out_for_delivery','delivered','cancelled','returned')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.orders
  add column if not exists order_group_id uuid references public.order_groups(id) on delete set null,
  add column if not exists seller_id uuid references public.sellers(id) on delete restrict,
  add column if not exists subtotal numeric(12,2) not null default 0,
  add column if not exists discount numeric(12,2) not null default 0,
  add column if not exists payment_method text not null default 'cod',
  add column if not exists payment_status text not null default 'pending',
  add column if not exists updated_at timestamptz not null default now();

alter table public.order_items
  add column if not exists created_at timestamptz not null default now(),
  add column if not exists product_name_snapshot text,
  add column if not exists variant_snapshot jsonb,
  add column if not exists discount_snapshot numeric(12,2) not null default 0,
  add column if not exists line_total numeric(12,2);

create table if not exists public.order_status_history (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null references public.orders(id) on delete cascade,
  from_status text,
  to_status text not null,
  changed_by uuid references auth.users(id) on delete set null,
  note text,
  created_at timestamptz not null default now()
);

-- Admin/audit tables referenced by the bundled dashboard.
create table if not exists public.audit_logs (
  id uuid primary key default gen_random_uuid(),
  actor_id uuid references auth.users(id) on delete set null,
  action text not null,
  entity_type text not null,
  entity_id uuid,
  old_values jsonb,
  new_values jsonb,
  source text,
  created_at timestamptz not null default now()
);

create table if not exists public.complaints (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete restrict,
  order_id uuid references public.orders(id) on delete set null,
  seller_id uuid references public.sellers(id) on delete set null,
  subject text not null,
  description text not null,
  status text not null default 'open' check (status in ('open','in_progress','resolved','closed')),
  priority text not null default 'normal' check (priority in ('low','normal','high','urgent')),
  assigned_to uuid references auth.users(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- Useful indexes.
create index if not exists merchant_profiles_status_created_idx on public.merchant_profiles(verification_status,created_at desc);
create index if not exists stores_merchant_id_idx on public.stores(merchant_id);
create index if not exists stores_seller_id_idx on public.stores(seller_id);
create index if not exists delivery_settings_seller_idx on public.delivery_settings(seller_id);
create index if not exists product_images_product_idx on public.product_images(product_id,sort_order);
create index if not exists product_variants_product_idx on public.product_variants(product_id,is_active);
create index if not exists reviews_product_idx on public.reviews(product_id,created_at desc);
create index if not exists addresses_user_idx on public.addresses(user_id,created_at desc);
create index if not exists carts_user_idx on public.carts(user_id);
create index if not exists order_groups_customer_idx on public.order_groups(customer_id,created_at desc);
create index if not exists audit_logs_created_idx on public.audit_logs(created_at desc);

-- RLS on every exposed foundation table.
alter table public.merchant_profiles enable row level security;
alter table public.stores enable row level security;
alter table public.delivery_settings enable row level security;
alter table public.product_images enable row level security;
alter table public.product_variants enable row level security;
alter table public.reviews enable row level security;
alter table public.featured_placements enable row level security;
alter table public.addresses enable row level security;
alter table public.carts enable row level security;
alter table public.cart_items enable row level security;
alter table public.order_groups enable row level security;
alter table public.order_status_history enable row level security;
alter table public.audit_logs enable row level security;
alter table public.complaints enable row level security;

-- Merchant application rows are readable by the owner and admin/support.
drop policy if exists tani_merchant_profile_owner_read on public.merchant_profiles;
create policy tani_merchant_profile_owner_read on public.merchant_profiles
for select to authenticated using (
  user_id=(select auth.uid()) or public.current_user_role()=any(array['admin'::text,'support'::text])
);

-- Stores: public active store discovery, owner/admin visibility.
drop policy if exists tani_store_public_read on public.stores;
create policy tani_store_public_read on public.stores
for select to anon,authenticated using (
  is_active=true
  or exists(select 1 from public.merchant_profiles m where m.id=stores.merchant_id and m.user_id=(select auth.uid()))
  or public.current_user_role()='admin'
);

-- Approved merchant may edit its store. Phase 4 tightens storage and workflow further.
drop policy if exists tani_store_owner_write on public.stores;
create policy tani_store_owner_write on public.stores
for all to authenticated
using (exists(
  select 1 from public.merchant_profiles mp
  join public.sellers s on s.id=mp.seller_id
  where mp.id=stores.merchant_id and mp.user_id=(select auth.uid())
    and mp.verification_status='approved' and s.verification_status='approved'
))
with check (exists(
  select 1 from public.merchant_profiles mp
  join public.sellers s on s.id=mp.seller_id
  where mp.id=stores.merchant_id and mp.user_id=(select auth.uid())
    and mp.verification_status='approved' and s.verification_status='approved'
));

-- Delivery settings: public active read; approved owner write.
drop policy if exists tani_delivery_settings_public_read on public.delivery_settings;
create policy tani_delivery_settings_public_read on public.delivery_settings
for select to anon,authenticated using (
  is_active=true and exists(select 1 from public.sellers s where s.id=delivery_settings.seller_id and s.verification_status='approved')
);
drop policy if exists tani_delivery_settings_merchant_write on public.delivery_settings;
create policy tani_delivery_settings_merchant_write on public.delivery_settings
for all to authenticated
using (
  exists(select 1 from public.sellers s where s.id=delivery_settings.seller_id and s.user_id=(select auth.uid()) and s.verification_status='approved')
  or public.current_user_role()='admin'
)
with check (
  exists(select 1 from public.sellers s where s.id=delivery_settings.seller_id and s.user_id=(select auth.uid()) and s.verification_status='approved')
  or public.current_user_role()='admin'
);

-- Products/images/variants.
drop policy if exists tani_products_own_insert on public.products;
create policy tani_products_own_insert on public.products for insert to authenticated
with check (exists(select 1 from public.sellers s where s.id=products.seller_id and s.user_id=(select auth.uid()) and s.verification_status='approved'));
drop policy if exists tani_products_own_update on public.products;
create policy tani_products_own_update on public.products for update to authenticated
using (exists(select 1 from public.sellers s where s.id=products.seller_id and s.user_id=(select auth.uid()) and s.verification_status='approved'))
with check (exists(select 1 from public.sellers s where s.id=products.seller_id and s.user_id=(select auth.uid()) and s.verification_status='approved'));
drop policy if exists tani_products_own_delete on public.products;
create policy tani_products_own_delete on public.products for delete to authenticated
using (exists(select 1 from public.sellers s where s.id=products.seller_id and s.user_id=(select auth.uid()) and s.verification_status='approved'));
drop policy if exists tani_products_owner_read on public.products;
create policy tani_products_owner_read on public.products for select to authenticated
using (exists(select 1 from public.sellers s where s.id=products.seller_id and s.user_id=(select auth.uid())));

drop policy if exists tani_product_images_public_read on public.product_images;
create policy tani_product_images_public_read on public.product_images for select to anon,authenticated using(true);
drop policy if exists tani_product_images_owner_write on public.product_images;
create policy tani_product_images_owner_write on public.product_images for all to authenticated
using (exists(select 1 from public.products p join public.sellers s on s.id=p.seller_id where p.id=product_images.product_id and s.user_id=(select auth.uid()) and s.verification_status='approved'))
with check (exists(select 1 from public.products p join public.sellers s on s.id=p.seller_id where p.id=product_images.product_id and s.user_id=(select auth.uid()) and s.verification_status='approved'));

drop policy if exists tani_variants_public_read on public.product_variants;
create policy tani_variants_public_read on public.product_variants for select to anon,authenticated
using (is_active=true or exists(select 1 from public.products p join public.sellers s on s.id=p.seller_id where p.id=product_variants.product_id and s.user_id=(select auth.uid())) or public.current_user_role()='admin');
drop policy if exists tani_variants_owner_write on public.product_variants;
create policy tani_variants_owner_write on public.product_variants for all to authenticated
using (exists(select 1 from public.products p join public.sellers s on s.id=p.seller_id where p.id=product_variants.product_id and s.user_id=(select auth.uid()) and s.verification_status='approved'))
with check (exists(select 1 from public.products p join public.sellers s on s.id=p.seller_id where p.id=product_variants.product_id and s.user_id=(select auth.uid()) and s.verification_status='approved'));

-- Customer-owned tables.
drop policy if exists tani_reviews_public_read on public.reviews;
create policy tani_reviews_public_read on public.reviews for select to anon,authenticated using(true);
drop policy if exists tani_reviews_own_insert on public.reviews;
create policy tani_reviews_own_insert on public.reviews for insert to authenticated with check(customer_id=(select auth.uid()));
drop policy if exists tani_reviews_own_update on public.reviews;
create policy tani_reviews_own_update on public.reviews for update to authenticated using(customer_id=(select auth.uid())) with check(customer_id=(select auth.uid()));
drop policy if exists tani_reviews_own_delete on public.reviews;
create policy tani_reviews_own_delete on public.reviews for delete to authenticated using(customer_id=(select auth.uid()));

drop policy if exists tani_addresses_owner_all on public.addresses;
create policy tani_addresses_owner_all on public.addresses for all to authenticated
using(user_id=(select auth.uid())) with check(user_id=(select auth.uid()));
drop policy if exists tani_carts_owner_all on public.carts;
create policy tani_carts_owner_all on public.carts for all to authenticated
using(user_id=(select auth.uid())) with check(user_id=(select auth.uid()));
drop policy if exists tani_cart_items_owner_all on public.cart_items;
create policy tani_cart_items_owner_all on public.cart_items for all to authenticated
using(exists(select 1 from public.carts c where c.id=cart_items.cart_id and c.user_id=(select auth.uid())))
with check(exists(select 1 from public.carts c where c.id=cart_items.cart_id and c.user_id=(select auth.uid())));

drop policy if exists tani_order_groups_owner_read on public.order_groups;
create policy tani_order_groups_owner_read on public.order_groups for select to authenticated
using(customer_id=(select auth.uid()) or public.current_user_role()=any(array['admin'::text,'support'::text]));

drop policy if exists tani_audit_admin_read on public.audit_logs;
create policy tani_audit_admin_read on public.audit_logs for select to authenticated
using(public.current_user_role()=any(array['admin'::text,'support'::text]));

drop policy if exists tani_complaints_owner_insert on public.complaints;
create policy tani_complaints_owner_insert on public.complaints for insert to authenticated with check(user_id=(select auth.uid()));
drop policy if exists tani_complaints_owner_read on public.complaints;
create policy tani_complaints_owner_read on public.complaints for select to authenticated using(
  user_id=(select auth.uid())
  or public.current_user_role()=any(array['admin'::text,'support'::text])
  or exists(select 1 from public.sellers s where s.id=complaints.seller_id and s.user_id=(select auth.uid()))
);

-- Public/read grants required by security_invoker marketplace views.
grant select on public.stores,public.delivery_settings,public.product_images,public.product_variants,public.reviews,public.featured_placements to anon,authenticated;
grant select on public.merchant_profiles,public.addresses,public.carts,public.cart_items,public.order_groups,public.order_status_history,public.audit_logs,public.complaints to authenticated;
grant insert,update,delete on public.addresses to authenticated;
grant insert,update,delete on public.stores,public.delivery_settings,public.products,public.product_images,public.product_variants,public.reviews to authenticated;
grant insert on public.complaints to authenticated;

-- Seller records and merchant workflow status are server controlled.
revoke insert,update,delete,truncate on public.sellers from anon,authenticated;
revoke insert,update,delete,truncate on public.merchant_profiles from anon,authenticated;

