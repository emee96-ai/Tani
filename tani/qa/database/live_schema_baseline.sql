-- Schema-only baseline captured from Tani on 2026-09-30; contains no account or commerce data.
-- For isolated PostgreSQL regression databases only. Never apply this file to production.
SET check_function_bodies = false;
-- table: addresses
CREATE TABLE public.addresses (id uuid NOT NULL DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  label text NOT NULL DEFAULT 'المنزل'::text,
  description text NOT NULL,
  area text,
  landmark text,
  phone text,
  delivery_notes text,
  is_default boolean NOT NULL DEFAULT false,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now());

-- table: app_design_tokens
CREATE TABLE public.app_design_tokens (id uuid NOT NULL DEFAULT gen_random_uuid(),
  token_key text NOT NULL,
  token_value text NOT NULL,
  token_type text NOT NULL,
  description text,
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now());

-- table: app_errors
CREATE TABLE public.app_errors (id uuid NOT NULL DEFAULT gen_random_uuid(),
  user_id uuid,
  session_id text NOT NULL,
  source text NOT NULL,
  error_type text NOT NULL,
  message text,
  context jsonb NOT NULL DEFAULT '{}'::jsonb,
  app_version text,
  platform text NOT NULL DEFAULT 'android'::text,
  created_at timestamp with time zone NOT NULL DEFAULT now());

-- table: app_events
CREATE TABLE public.app_events (id uuid NOT NULL DEFAULT gen_random_uuid(),
  user_id uuid,
  session_id text NOT NULL,
  event_name text NOT NULL,
  screen text,
  entity_type text,
  entity_id uuid,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  app_version text,
  platform text NOT NULL DEFAULT 'android'::text,
  created_at timestamp with time zone NOT NULL DEFAULT now());

-- table: app_settings
CREATE TABLE public.app_settings (key text NOT NULL,
  value jsonb NOT NULL DEFAULT '{}'::jsonb,
  updated_by uuid,
  updated_at timestamp with time zone NOT NULL DEFAULT now());

-- table: audit_logs
CREATE TABLE public.audit_logs (id uuid NOT NULL DEFAULT gen_random_uuid(),
  actor_id uuid,
  action text NOT NULL,
  entity_type text NOT NULL,
  entity_id uuid,
  old_values jsonb,
  new_values jsonb,
  source text,
  created_at timestamp with time zone NOT NULL DEFAULT now());

-- table: banners
CREATE TABLE public.banners (id uuid NOT NULL DEFAULT gen_random_uuid(),
  title text NOT NULL,
  image_url text NOT NULL,
  action_type text,
  action_value text,
  starts_at timestamp with time zone,
  ends_at timestamp with time zone,
  sort_order integer NOT NULL DEFAULT 0,
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamp with time zone NOT NULL DEFAULT now());

-- table: billing_transactions
CREATE TABLE public.billing_transactions (id uuid NOT NULL DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  seller_id uuid,
  purpose text NOT NULL,
  reference_id uuid,
  amount numeric(14,2) NOT NULL,
  currency text NOT NULL DEFAULT 'SDG'::text,
  payment_method text NOT NULL,
  provider text,
  provider_reference text,
  status text NOT NULL DEFAULT 'pending'::text,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now());

-- table: campaigns
CREATE TABLE public.campaigns (id uuid NOT NULL DEFAULT gen_random_uuid(),
  name text NOT NULL,
  description text NOT NULL DEFAULT ''::text,
  starts_at timestamp with time zone,
  ends_at timestamp with time zone,
  status text NOT NULL DEFAULT 'draft'::text,
  created_at timestamp with time zone NOT NULL DEFAULT now());

-- table: cart_items
CREATE TABLE public.cart_items (id uuid NOT NULL DEFAULT gen_random_uuid(),
  cart_id uuid NOT NULL,
  product_id uuid NOT NULL,
  variant_id uuid,
  quantity integer NOT NULL,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now());

-- table: carts
CREATE TABLE public.carts (id uuid NOT NULL DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now());

-- table: categories
CREATE TABLE public.categories (id uuid NOT NULL DEFAULT gen_random_uuid(),
  name text NOT NULL,
  image text,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  slug text,
  sort_order integer NOT NULL DEFAULT 0,
  is_active boolean NOT NULL DEFAULT true,
  updated_at timestamp with time zone NOT NULL DEFAULT now());

-- table: complaints
CREATE TABLE public.complaints (id uuid NOT NULL DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  order_id uuid,
  seller_id uuid,
  subject text NOT NULL,
  description text NOT NULL,
  status text NOT NULL DEFAULT 'open'::text,
  priority text NOT NULL DEFAULT 'normal'::text,
  assigned_to uuid,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now(),
  category text NOT NULL DEFAULT 'order'::text,
  resolution text,
  resolved_by uuid,
  resolved_at timestamp with time zone);

-- table: conversations
CREATE TABLE public.conversations (id uuid NOT NULL DEFAULT gen_random_uuid(),
  customer_id uuid NOT NULL,
  seller_id uuid,
  order_id uuid,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now());

-- table: coupon_usages
CREATE TABLE public.coupon_usages (id uuid NOT NULL DEFAULT gen_random_uuid(),
  coupon_id uuid NOT NULL,
  user_id uuid NOT NULL,
  order_id uuid,
  discount_amount numeric(12,2) NOT NULL DEFAULT 0,
  created_at timestamp with time zone NOT NULL DEFAULT now());

-- table: coupons
CREATE TABLE public.coupons (id uuid NOT NULL DEFAULT gen_random_uuid(),
  code text NOT NULL,
  description text NOT NULL DEFAULT ''::text,
  discount_type text NOT NULL,
  discount_value numeric(12,2) NOT NULL,
  max_discount numeric(12,2),
  min_order_amount numeric(12,2) NOT NULL DEFAULT 0,
  usage_limit integer,
  usage_count integer NOT NULL DEFAULT 0,
  starts_at timestamp with time zone,
  expires_at timestamp with time zone,
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamp with time zone NOT NULL DEFAULT now());

-- table: delivery_options
CREATE TABLE public.delivery_options (id uuid NOT NULL DEFAULT gen_random_uuid(),
  order_id uuid NOT NULL,
  fee numeric(12,2) NOT NULL DEFAULT 0,
  provider_type text NOT NULL DEFAULT 'merchant'::text,
  status text NOT NULL DEFAULT 'pending'::text,
  area text,
  notes text,
  tracking_reference text,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now());

-- table: delivery_provider_assignments
CREATE TABLE public.delivery_provider_assignments (id uuid NOT NULL DEFAULT gen_random_uuid(),
  seller_id uuid NOT NULL,
  provider_code text NOT NULL,
  is_active boolean NOT NULL DEFAULT true,
  merchant_config jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now());

-- table: delivery_providers
CREATE TABLE public.delivery_providers (code text NOT NULL,
  display_name text NOT NULL,
  provider_type text NOT NULL,
  is_active boolean NOT NULL DEFAULT false,
  supported_cities text[] NOT NULL DEFAULT '{}'::text[],
  public_config jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now());

-- table: delivery_settings
CREATE TABLE public.delivery_settings (id uuid NOT NULL DEFAULT gen_random_uuid(),
  seller_id uuid NOT NULL,
  base_fee numeric(12,2) NOT NULL DEFAULT 0,
  delivery_area text,
  estimated_minutes integer,
  notes text,
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now());

-- table: delivery_zones
CREATE TABLE public.delivery_zones (id uuid NOT NULL DEFAULT gen_random_uuid(),
  seller_id uuid NOT NULL,
  area_name text NOT NULL,
  fee numeric(12,2) NOT NULL,
  estimated_minutes integer,
  sort_order integer NOT NULL DEFAULT 0,
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now());

-- table: disputes
CREATE TABLE public.disputes (id uuid NOT NULL DEFAULT gen_random_uuid(),
  order_id uuid NOT NULL,
  opened_by uuid NOT NULL,
  reason text NOT NULL,
  status text NOT NULL DEFAULT 'open'::text,
  resolution text,
  resolved_by uuid,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  resolved_at timestamp with time zone);

-- table: favorites
CREATE TABLE public.favorites (id uuid NOT NULL DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  product_id uuid NOT NULL,
  created_at timestamp with time zone NOT NULL DEFAULT now());

-- table: feature_flags
CREATE TABLE public.feature_flags (key text NOT NULL,
  enabled boolean NOT NULL DEFAULT false,
  config jsonb NOT NULL DEFAULT '{}'::jsonb,
  updated_by uuid,
  updated_at timestamp with time zone NOT NULL DEFAULT now());

-- table: featured_placements
CREATE TABLE public.featured_placements (id uuid NOT NULL DEFAULT gen_random_uuid(),
  product_id uuid,
  seller_id uuid,
  placement text NOT NULL,
  starts_at timestamp with time zone,
  ends_at timestamp with time zone,
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  is_sponsored boolean NOT NULL DEFAULT false,
  sponsored_label text NOT NULL DEFAULT 'ممول'::text,
  request_id uuid,
  priority integer NOT NULL DEFAULT 0);

-- table: featured_requests
CREATE TABLE public.featured_requests (id uuid NOT NULL DEFAULT gen_random_uuid(),
  seller_id uuid NOT NULL,
  product_id uuid,
  placement text NOT NULL,
  requested_starts_at timestamp with time zone,
  requested_ends_at timestamp with time zone,
  status text NOT NULL DEFAULT 'pending'::text,
  merchant_note text,
  admin_note text,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  reviewed_at timestamp with time zone,
  reviewed_by uuid);

-- table: fee_rules
CREATE TABLE public.fee_rules (id uuid NOT NULL DEFAULT gen_random_uuid(),
  code text NOT NULL,
  context text NOT NULL,
  percentage numeric(7,4) NOT NULL DEFAULT 0,
  fixed_amount numeric(14,2) NOT NULL DEFAULT 0,
  min_amount numeric(14,2),
  max_fee numeric(14,2),
  is_active boolean NOT NULL DEFAULT false,
  starts_at timestamp with time zone,
  ends_at timestamp with time zone,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now());

-- table: inventory
CREATE TABLE public.inventory (id uuid NOT NULL DEFAULT gen_random_uuid(),
  product_id uuid NOT NULL,
  quantity integer NOT NULL DEFAULT 0,
  reserved_quantity integer NOT NULL DEFAULT 0,
  updated_at timestamp with time zone NOT NULL DEFAULT now());

-- table: loyalty_accounts
CREATE TABLE public.loyalty_accounts (id uuid NOT NULL DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  points integer NOT NULL DEFAULT 0,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now());

-- table: loyalty_transactions
CREATE TABLE public.loyalty_transactions (id uuid NOT NULL DEFAULT gen_random_uuid(),
  account_id uuid NOT NULL,
  points integer NOT NULL,
  type text NOT NULL,
  reference_id uuid,
  description text NOT NULL DEFAULT ''::text,
  created_at timestamp with time zone NOT NULL DEFAULT now());

-- table: market_cities
CREATE TABLE public.market_cities (id uuid NOT NULL DEFAULT gen_random_uuid(),
  code text NOT NULL,
  name text NOT NULL,
  state_name text,
  is_active boolean NOT NULL DEFAULT false,
  sort_order integer NOT NULL DEFAULT 0,
  launch_notes text,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now());

-- table: merchant_ad_campaigns
CREATE TABLE public.merchant_ad_campaigns (id uuid NOT NULL DEFAULT gen_random_uuid(),
  seller_id uuid NOT NULL,
  name text NOT NULL,
  placement text NOT NULL,
  product_id uuid,
  budget numeric(14,2),
  starts_at timestamp with time zone,
  ends_at timestamp with time zone,
  status text NOT NULL DEFAULT 'draft'::text,
  impressions bigint NOT NULL DEFAULT 0,
  clicks bigint NOT NULL DEFAULT 0,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now());

-- table: merchant_identity_documents
CREATE TABLE public.merchant_identity_documents (id uuid NOT NULL DEFAULT gen_random_uuid(),
  merchant_id uuid NOT NULL,
  user_id uuid NOT NULL,
  document_type text NOT NULL DEFAULT 'national_id'::text,
  storage_path text NOT NULL,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  reviewed_at timestamp with time zone);

-- table: merchant_metrics
CREATE TABLE public.merchant_metrics (id uuid NOT NULL DEFAULT gen_random_uuid(),
  seller_id uuid NOT NULL,
  total_orders integer NOT NULL DEFAULT 0,
  completed_orders integer NOT NULL DEFAULT 0,
  cancelled_orders integer NOT NULL DEFAULT 0,
  total_sales numeric(14,2) NOT NULL DEFAULT 0,
  average_rating numeric(4,2),
  updated_at timestamp with time zone NOT NULL DEFAULT now(),
  product_views integer NOT NULL DEFAULT 0,
  repeat_customers integer NOT NULL DEFAULT 0,
  conversion_rate numeric(6,2) NOT NULL DEFAULT 0,
  cancellation_rate numeric(6,2) NOT NULL DEFAULT 0,
  avg_response_minutes numeric(10,2));

-- table: merchant_profiles
CREATE TABLE public.merchant_profiles (id uuid NOT NULL DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  seller_id uuid,
  business_name text NOT NULL,
  description text NOT NULL DEFAULT ''::text,
  phone text,
  verification_status text NOT NULL DEFAULT 'pending'::text,
  trust_badge boolean NOT NULL DEFAULT false,
  approved_at timestamp with time zone,
  suspended_at timestamp with time zone,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now(),
  whatsapp text,
  category_id uuid,
  phone_verified_at timestamp with time zone,
  policies_accepted_at timestamp with time zone,
  policy_version text,
  submitted_at timestamp with time zone,
  review_note text,
  requested_changes_at timestamp with time zone,
  store_name text,
  store_description text,
  city text NOT NULL DEFAULT 'كوستي'::text,
  area text,
  delivery_area text,
  delivery_fee numeric NOT NULL DEFAULT 0,
  estimated_minutes integer,
  requested_category text,
  delivery_zones jsonb NOT NULL DEFAULT '[]'::jsonb);

-- table: merchant_reports
CREATE TABLE public.merchant_reports (id uuid NOT NULL DEFAULT gen_random_uuid(),
  reporter_id uuid NOT NULL,
  seller_id uuid NOT NULL,
  reason text NOT NULL,
  status text NOT NULL DEFAULT 'pending'::text,
  created_at timestamp with time zone NOT NULL DEFAULT now());

-- table: merchant_reviews
CREATE TABLE public.merchant_reviews (id uuid NOT NULL DEFAULT gen_random_uuid(),
  order_id uuid NOT NULL,
  seller_id uuid NOT NULL,
  customer_id uuid NOT NULL,
  rating integer NOT NULL,
  comment text NOT NULL DEFAULT ''::text,
  status text NOT NULL DEFAULT 'published'::text,
  moderation_reason text,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now());

-- table: merchant_settlements
CREATE TABLE public.merchant_settlements (id uuid NOT NULL DEFAULT gen_random_uuid(),
  seller_id uuid NOT NULL,
  order_id uuid,
  gross_amount numeric(12,2) NOT NULL DEFAULT 0,
  commission_amount numeric(12,2) NOT NULL DEFAULT 0,
  net_amount numeric(12,2) NOT NULL DEFAULT 0,
  status text NOT NULL DEFAULT 'pending'::text,
  paid_at timestamp with time zone,
  created_at timestamp with time zone NOT NULL DEFAULT now());

-- table: merchant_trust_scores
CREATE TABLE public.merchant_trust_scores (seller_id uuid NOT NULL,
  trust_level text NOT NULL DEFAULT 'verified'::text,
  trust_score numeric(5,2) NOT NULL DEFAULT 50,
  completed_orders integer NOT NULL DEFAULT 0,
  cancelled_orders integer NOT NULL DEFAULT 0,
  review_count integer NOT NULL DEFAULT 0,
  average_rating numeric(3,2),
  open_complaints integer NOT NULL DEFAULT 0,
  completion_rate numeric(5,2) NOT NULL DEFAULT 0,
  updated_at timestamp with time zone NOT NULL DEFAULT now());

-- table: merchant_verifications
CREATE TABLE public.merchant_verifications (id uuid NOT NULL DEFAULT gen_random_uuid(),
  merchant_id uuid NOT NULL,
  document_type text NOT NULL,
  document_path text,
  status text NOT NULL DEFAULT 'pending'::text,
  reviewer_id uuid,
  notes text NOT NULL DEFAULT ''::text,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  reviewed_at timestamp with time zone);

-- table: messages
CREATE TABLE public.messages (id uuid NOT NULL DEFAULT gen_random_uuid(),
  conversation_id uuid NOT NULL,
  sender_id uuid NOT NULL,
  body text NOT NULL,
  attachment_path text,
  read_at timestamp with time zone,
  created_at timestamp with time zone NOT NULL DEFAULT now());

-- table: notification_preferences
CREATE TABLE public.notification_preferences (user_id uuid NOT NULL,
  push_enabled boolean NOT NULL DEFAULT true,
  order_updates boolean NOT NULL DEFAULT true,
  promotions boolean NOT NULL DEFAULT true,
  messages boolean NOT NULL DEFAULT true,
  updated_at timestamp with time zone NOT NULL DEFAULT now());

-- table: notifications
CREATE TABLE public.notifications (id uuid NOT NULL DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  type text NOT NULL,
  title text NOT NULL,
  body text NOT NULL,
  data jsonb NOT NULL DEFAULT '{}'::jsonb,
  read_at timestamp with time zone,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  dedupe_key text);

-- table: operational_alerts
CREATE TABLE public.operational_alerts (id uuid NOT NULL DEFAULT gen_random_uuid(),
  type text NOT NULL,
  severity text NOT NULL DEFAULT 'warning'::text,
  entity_type text,
  entity_id uuid,
  title text NOT NULL,
  details jsonb NOT NULL DEFAULT '{}'::jsonb,
  status text NOT NULL DEFAULT 'open'::text,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  resolved_at timestamp with time zone,
  resolved_by uuid);

-- table: order_groups
CREATE TABLE public.order_groups (id uuid NOT NULL DEFAULT gen_random_uuid(),
  customer_id uuid NOT NULL,
  subtotal numeric(12,2) NOT NULL DEFAULT 0,
  delivery_total numeric(12,2) NOT NULL DEFAULT 0,
  discount_total numeric(12,2) NOT NULL DEFAULT 0,
  grand_total numeric(12,2) NOT NULL DEFAULT 0,
  payment_method text NOT NULL DEFAULT 'cod'::text,
  payment_status text NOT NULL DEFAULT 'pending'::text,
  status text NOT NULL DEFAULT 'pending'::text,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now(),
  idempotency_key text,
  address_id uuid,
  address_snapshot jsonb,
  customer_note text);

-- table: order_items
CREATE TABLE public.order_items (id uuid NOT NULL DEFAULT gen_random_uuid(),
  order_id uuid NOT NULL,
  product_id uuid NOT NULL,
  seller_id uuid NOT NULL,
  quantity integer NOT NULL,
  unit_price numeric(12,2) NOT NULL,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  product_name_snapshot text,
  variant_snapshot jsonb,
  discount_snapshot numeric(12,2) NOT NULL DEFAULT 0,
  line_total numeric(12,2));

-- table: order_reviews
CREATE TABLE public.order_reviews (id uuid NOT NULL DEFAULT gen_random_uuid(),
  order_id uuid NOT NULL,
  customer_id uuid NOT NULL,
  rating integer NOT NULL,
  comment text NOT NULL DEFAULT ''::text,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now());

-- table: order_status_history
CREATE TABLE public.order_status_history (id uuid NOT NULL DEFAULT gen_random_uuid(),
  order_id uuid NOT NULL,
  from_status text,
  to_status text NOT NULL,
  changed_by uuid,
  note text,
  created_at timestamp with time zone NOT NULL DEFAULT now());

-- table: orders
CREATE TABLE public.orders (id uuid NOT NULL DEFAULT gen_random_uuid(),
  customer_id uuid NOT NULL,
  total numeric(12,2) NOT NULL,
  status text NOT NULL DEFAULT 'pending'::text,
  address text NOT NULL,
  phone text NOT NULL,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  delivery_fee numeric(12,2) NOT NULL DEFAULT 0,
  order_group_id uuid,
  seller_id uuid,
  subtotal numeric(12,2) NOT NULL DEFAULT 0,
  discount numeric(12,2) NOT NULL DEFAULT 0,
  payment_method text NOT NULL DEFAULT 'cod'::text,
  payment_status text NOT NULL DEFAULT 'pending'::text,
  updated_at timestamp with time zone NOT NULL DEFAULT now(),
  customer_name_snapshot text,
  store_name_snapshot text,
  address_snapshot jsonb,
  customer_note text,
  stock_restored_at timestamp with time zone);

-- table: payment_methods
CREATE TABLE public.payment_methods (code text NOT NULL,
  display_name text NOT NULL,
  provider text,
  is_online boolean NOT NULL DEFAULT false,
  is_active boolean NOT NULL DEFAULT false,
  sort_order integer NOT NULL DEFAULT 0,
  public_config jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now());

-- table: payments
CREATE TABLE public.payments (id uuid NOT NULL DEFAULT gen_random_uuid(),
  order_id uuid NOT NULL,
  amount numeric(12,2) NOT NULL,
  method text NOT NULL DEFAULT 'cod'::text,
  status text NOT NULL DEFAULT 'pending'::text,
  provider text,
  provider_reference text,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now());

-- table: product_images
CREATE TABLE public.product_images (id uuid NOT NULL DEFAULT gen_random_uuid(),
  product_id uuid NOT NULL,
  storage_path text NOT NULL,
  sort_order integer NOT NULL DEFAULT 0,
  is_primary boolean NOT NULL DEFAULT false,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  media_type text NOT NULL DEFAULT 'image'::text,
  mime_type text);

-- table: product_reports
CREATE TABLE public.product_reports (id uuid NOT NULL DEFAULT gen_random_uuid(),
  reporter_id uuid NOT NULL,
  product_id uuid NOT NULL,
  reason text NOT NULL,
  status text NOT NULL DEFAULT 'pending'::text,
  created_at timestamp with time zone NOT NULL DEFAULT now());

-- table: product_variants
CREATE TABLE public.product_variants (id uuid NOT NULL DEFAULT gen_random_uuid(),
  product_id uuid NOT NULL,
  name text NOT NULL,
  sku text,
  price numeric(12,2),
  stock integer NOT NULL DEFAULT 0,
  attributes jsonb NOT NULL DEFAULT '{}'::jsonb,
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now());

-- table: products
CREATE TABLE public.products (id uuid NOT NULL DEFAULT gen_random_uuid(),
  seller_id uuid NOT NULL,
  category_id uuid,
  name text NOT NULL,
  description text NOT NULL DEFAULT ''::text,
  price numeric(12,2) NOT NULL,
  stock integer NOT NULL DEFAULT 0,
  image text,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  is_active boolean NOT NULL DEFAULT true,
  updated_at timestamp with time zone NOT NULL DEFAULT now(),
  has_variants boolean NOT NULL DEFAULT false);

-- table: profiles
CREATE TABLE public.profiles (id uuid NOT NULL,
  name text NOT NULL,
  phone text NOT NULL,
  role text NOT NULL DEFAULT 'customer'::text,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now(),
  avatar_url text,
  is_active boolean NOT NULL DEFAULT true,
  deleted_at timestamp with time zone,
  email text,
  admin_previous_role text);

-- table: promotions
CREATE TABLE public.promotions (id uuid NOT NULL DEFAULT gen_random_uuid(),
  seller_id uuid,
  product_id uuid,
  title text NOT NULL,
  discount_type text NOT NULL,
  discount_value numeric(12,2) NOT NULL,
  starts_at timestamp with time zone,
  ends_at timestamp with time zone,
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamp with time zone NOT NULL DEFAULT now());

-- table: push_devices
CREATE TABLE public.push_devices (id uuid NOT NULL DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  provider text NOT NULL,
  token text NOT NULL,
  platform text NOT NULL DEFAULT 'android'::text,
  app_version text,
  device_model text,
  locale text,
  is_active boolean NOT NULL DEFAULT true,
  last_seen_at timestamp with time zone NOT NULL DEFAULT now(),
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now());

-- table: referral_codes
CREATE TABLE public.referral_codes (user_id uuid NOT NULL,
  code text NOT NULL,
  created_at timestamp with time zone NOT NULL DEFAULT now());

-- table: referrals
CREATE TABLE public.referrals (id uuid NOT NULL DEFAULT gen_random_uuid(),
  referrer_id uuid NOT NULL,
  referred_id uuid NOT NULL,
  code text,
  status text NOT NULL DEFAULT 'pending'::text,
  created_at timestamp with time zone NOT NULL DEFAULT now());

-- table: refunds
CREATE TABLE public.refunds (id uuid NOT NULL DEFAULT gen_random_uuid(),
  payment_id uuid NOT NULL,
  amount numeric(12,2) NOT NULL,
  reason text NOT NULL,
  status text NOT NULL DEFAULT 'pending'::text,
  processed_by uuid,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  processed_at timestamp with time zone);

-- table: reports
CREATE TABLE public.reports (id uuid NOT NULL DEFAULT gen_random_uuid(),
  report_type text NOT NULL,
  period_start date,
  period_end date,
  generated_by uuid,
  data jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamp with time zone NOT NULL DEFAULT now());

-- table: review_reports
CREATE TABLE public.review_reports (id uuid NOT NULL DEFAULT gen_random_uuid(),
  review_id uuid NOT NULL,
  reporter_id uuid NOT NULL,
  reason text NOT NULL,
  status text NOT NULL DEFAULT 'pending'::text,
  created_at timestamp with time zone NOT NULL DEFAULT now());

-- table: reviews
CREATE TABLE public.reviews (id uuid NOT NULL DEFAULT gen_random_uuid(),
  product_id uuid NOT NULL,
  customer_id uuid NOT NULL,
  rating integer NOT NULL,
  comment text NOT NULL DEFAULT ''::text,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  order_id uuid,
  status text NOT NULL DEFAULT 'published'::text,
  moderation_reason text,
  updated_at timestamp with time zone NOT NULL DEFAULT now());

-- table: sellers
CREATE TABLE public.sellers (id uuid NOT NULL DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  store_name text NOT NULL,
  verification_status text NOT NULL DEFAULT 'pending'::text,
  created_at timestamp with time zone NOT NULL DEFAULT now());

-- table: stores
CREATE TABLE public.stores (id uuid NOT NULL DEFAULT gen_random_uuid(),
  merchant_id uuid NOT NULL,
  name text NOT NULL,
  description text NOT NULL DEFAULT ''::text,
  logo_url text,
  cover_url text,
  city text NOT NULL DEFAULT 'كوستي'::text,
  area text,
  is_open boolean NOT NULL DEFAULT true,
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now(),
  seller_id uuid,
  category_id uuid,
  contact_phone text,
  whatsapp text);

-- table: subscription_plans
CREATE TABLE public.subscription_plans (id uuid NOT NULL DEFAULT gen_random_uuid(),
  name text NOT NULL,
  description text NOT NULL DEFAULT ''::text,
  price numeric(12,2) NOT NULL DEFAULT 0,
  duration_days integer NOT NULL,
  features jsonb NOT NULL DEFAULT '{}'::jsonb,
  is_active boolean NOT NULL DEFAULT true,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  slug text,
  audience text NOT NULL DEFAULT 'merchant'::text,
  sort_order integer NOT NULL DEFAULT 0,
  updated_at timestamp with time zone NOT NULL DEFAULT now());

-- table: subscription_requests
CREATE TABLE public.subscription_requests (id uuid NOT NULL DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  seller_id uuid NOT NULL,
  plan_id uuid NOT NULL,
  status text NOT NULL DEFAULT 'pending'::text,
  note text,
  admin_note text,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  reviewed_at timestamp with time zone,
  reviewed_by uuid);

-- table: subscriptions
CREATE TABLE public.subscriptions (id uuid NOT NULL DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  plan_id uuid NOT NULL,
  status text NOT NULL DEFAULT 'active'::text,
  starts_at timestamp with time zone NOT NULL DEFAULT now(),
  ends_at timestamp with time zone,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  seller_id uuid,
  source text NOT NULL DEFAULT 'admin'::text,
  auto_renew boolean NOT NULL DEFAULT false,
  cancelled_at timestamp with time zone,
  updated_at timestamp with time zone NOT NULL DEFAULT now());

-- table: support_tickets
CREATE TABLE public.support_tickets (id uuid NOT NULL DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  subject text NOT NULL,
  description text NOT NULL,
  status text NOT NULL DEFAULT 'open'::text,
  priority text NOT NULL DEFAULT 'normal'::text,
  assigned_to uuid,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now());

-- table: system_settings
CREATE TABLE public.system_settings (key text NOT NULL,
  value jsonb NOT NULL DEFAULT '{}'::jsonb,
  updated_by uuid,
  updated_at timestamp with time zone NOT NULL DEFAULT now());

-- table: ticket_messages
CREATE TABLE public.ticket_messages (id uuid NOT NULL DEFAULT gen_random_uuid(),
  ticket_id uuid NOT NULL,
  sender_id uuid NOT NULL,
  body text NOT NULL,
  created_at timestamp with time zone NOT NULL DEFAULT now());

-- table: user_reports
CREATE TABLE public.user_reports (id uuid NOT NULL DEFAULT gen_random_uuid(),
  reporter_id uuid NOT NULL,
  reported_user_id uuid NOT NULL,
  reason text NOT NULL,
  status text NOT NULL DEFAULT 'pending'::text,
  created_at timestamp with time zone NOT NULL DEFAULT now());

-- table: wallet_transactions
CREATE TABLE public.wallet_transactions (id uuid NOT NULL DEFAULT gen_random_uuid(),
  wallet_id uuid NOT NULL,
  type text NOT NULL,
  amount numeric(12,2) NOT NULL,
  reference_type text,
  reference_id uuid,
  description text NOT NULL DEFAULT ''::text,
  created_at timestamp with time zone NOT NULL DEFAULT now());

-- table: wallets
CREATE TABLE public.wallets (id uuid NOT NULL DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  balance numeric(12,2) NOT NULL DEFAULT 0,
  currency text NOT NULL DEFAULT 'SDG'::text,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now());

-- constraint: addresses_description_length
ALTER TABLE addresses ADD CONSTRAINT addresses_description_length CHECK (((char_length(TRIM(BOTH FROM description)) >= 2) AND (char_length(TRIM(BOTH FROM description)) <= 500)));

-- constraint: addresses_pkey
ALTER TABLE addresses ADD CONSTRAINT addresses_pkey PRIMARY KEY (id);

-- constraint: app_design_tokens_pkey
ALTER TABLE app_design_tokens ADD CONSTRAINT app_design_tokens_pkey PRIMARY KEY (id);

-- constraint: app_design_tokens_token_key_key
ALTER TABLE app_design_tokens ADD CONSTRAINT app_design_tokens_token_key_key UNIQUE (token_key);

-- constraint: app_design_tokens_token_type_check
ALTER TABLE app_design_tokens ADD CONSTRAINT app_design_tokens_token_type_check CHECK ((token_type = ANY (ARRAY['color'::text, 'typography'::text, 'dimension'::text, 'radius'::text, 'shadow'::text, 'other'::text])));

-- constraint: app_errors_error_type_check
ALTER TABLE app_errors ADD CONSTRAINT app_errors_error_type_check CHECK (((char_length(error_type) >= 2) AND (char_length(error_type) <= 120)));

-- constraint: app_errors_message_check
ALTER TABLE app_errors ADD CONSTRAINT app_errors_message_check CHECK (((message IS NULL) OR (char_length(message) <= 500)));

-- constraint: app_errors_pkey
ALTER TABLE app_errors ADD CONSTRAINT app_errors_pkey PRIMARY KEY (id);

-- constraint: app_errors_source_check
ALTER TABLE app_errors ADD CONSTRAINT app_errors_source_check CHECK (((char_length(source) >= 2) AND (char_length(source) <= 100)));

-- constraint: app_events_entity_type_check
ALTER TABLE app_events ADD CONSTRAINT app_events_entity_type_check CHECK (((entity_type IS NULL) OR (char_length(entity_type) <= 50)));

-- constraint: app_events_event_name_check
ALTER TABLE app_events ADD CONSTRAINT app_events_event_name_check CHECK (((char_length(event_name) >= 2) AND (char_length(event_name) <= 80)));

-- constraint: app_events_pkey
ALTER TABLE app_events ADD CONSTRAINT app_events_pkey PRIMARY KEY (id);

-- constraint: app_events_screen_check
ALTER TABLE app_events ADD CONSTRAINT app_events_screen_check CHECK (((screen IS NULL) OR (char_length(screen) <= 80)));

-- constraint: app_settings_pkey
ALTER TABLE app_settings ADD CONSTRAINT app_settings_pkey PRIMARY KEY (key);

-- constraint: audit_logs_pkey
ALTER TABLE audit_logs ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (id);

-- constraint: banners_pkey
ALTER TABLE banners ADD CONSTRAINT banners_pkey PRIMARY KEY (id);

-- constraint: billing_transactions_amount_check
ALTER TABLE billing_transactions ADD CONSTRAINT billing_transactions_amount_check CHECK ((amount >= (0)::numeric));

-- constraint: billing_transactions_pkey
ALTER TABLE billing_transactions ADD CONSTRAINT billing_transactions_pkey PRIMARY KEY (id);

-- constraint: billing_transactions_purpose_check
ALTER TABLE billing_transactions ADD CONSTRAINT billing_transactions_purpose_check CHECK ((purpose = ANY (ARRAY['subscription'::text, 'featured_placement'::text, 'advertising'::text, 'service_fee'::text])));

-- constraint: billing_transactions_status_check
ALTER TABLE billing_transactions ADD CONSTRAINT billing_transactions_status_check CHECK ((status = ANY (ARRAY['pending'::text, 'authorized'::text, 'paid'::text, 'failed'::text, 'cancelled'::text, 'refunded'::text])));

-- constraint: campaigns_pkey
ALTER TABLE campaigns ADD CONSTRAINT campaigns_pkey PRIMARY KEY (id);

-- constraint: campaigns_status_check
ALTER TABLE campaigns ADD CONSTRAINT campaigns_status_check CHECK ((status = ANY (ARRAY['draft'::text, 'scheduled'::text, 'active'::text, 'ended'::text, 'cancelled'::text])));

-- constraint: cart_items_cart_id_product_id_variant_id_key
ALTER TABLE cart_items ADD CONSTRAINT cart_items_cart_id_product_id_variant_id_key UNIQUE (cart_id, product_id, variant_id);

-- constraint: cart_items_pkey
ALTER TABLE cart_items ADD CONSTRAINT cart_items_pkey PRIMARY KEY (id);

-- constraint: cart_items_quantity_check
ALTER TABLE cart_items ADD CONSTRAINT cart_items_quantity_check CHECK ((quantity > 0));

-- constraint: cart_items_quantity_max_check
ALTER TABLE cart_items ADD CONSTRAINT cart_items_quantity_max_check CHECK ((quantity <= 99));

-- constraint: carts_pkey
ALTER TABLE carts ADD CONSTRAINT carts_pkey PRIMARY KEY (id);

-- constraint: carts_user_id_key
ALTER TABLE carts ADD CONSTRAINT carts_user_id_key UNIQUE (user_id);

-- constraint: categories_name_key
ALTER TABLE categories ADD CONSTRAINT categories_name_key UNIQUE (name);

-- constraint: categories_pkey
ALTER TABLE categories ADD CONSTRAINT categories_pkey PRIMARY KEY (id);

-- constraint: complaints_category_check
ALTER TABLE complaints ADD CONSTRAINT complaints_category_check CHECK ((category = ANY (ARRAY['order'::text, 'availability'::text, 'merchant_response'::text, 'delivery_delay'::text, 'product'::text, 'fees'::text, 'cancellation'::text, 'dispute'::text, 'other'::text])));

-- constraint: complaints_pkey
ALTER TABLE complaints ADD CONSTRAINT complaints_pkey PRIMARY KEY (id);

-- constraint: complaints_priority_check
ALTER TABLE complaints ADD CONSTRAINT complaints_priority_check CHECK ((priority = ANY (ARRAY['low'::text, 'normal'::text, 'high'::text, 'urgent'::text])));

-- constraint: complaints_status_check
ALTER TABLE complaints ADD CONSTRAINT complaints_status_check CHECK ((status = ANY (ARRAY['open'::text, 'in_progress'::text, 'resolved'::text, 'closed'::text])));

-- constraint: conversations_pkey
ALTER TABLE conversations ADD CONSTRAINT conversations_pkey PRIMARY KEY (id);

-- constraint: coupon_usages_coupon_id_user_id_order_id_key
ALTER TABLE coupon_usages ADD CONSTRAINT coupon_usages_coupon_id_user_id_order_id_key UNIQUE (coupon_id, user_id, order_id);

-- constraint: coupon_usages_pkey
ALTER TABLE coupon_usages ADD CONSTRAINT coupon_usages_pkey PRIMARY KEY (id);

-- constraint: coupons_code_key
ALTER TABLE coupons ADD CONSTRAINT coupons_code_key UNIQUE (code);

-- constraint: coupons_discount_type_check
ALTER TABLE coupons ADD CONSTRAINT coupons_discount_type_check CHECK ((discount_type = ANY (ARRAY['fixed'::text, 'percentage'::text])));

-- constraint: coupons_discount_value_check
ALTER TABLE coupons ADD CONSTRAINT coupons_discount_value_check CHECK ((discount_value > (0)::numeric));

-- constraint: coupons_pkey
ALTER TABLE coupons ADD CONSTRAINT coupons_pkey PRIMARY KEY (id);

-- constraint: delivery_options_fee_check
ALTER TABLE delivery_options ADD CONSTRAINT delivery_options_fee_check CHECK ((fee >= (0)::numeric));

-- constraint: delivery_options_pkey
ALTER TABLE delivery_options ADD CONSTRAINT delivery_options_pkey PRIMARY KEY (id);

-- constraint: delivery_options_status_check
ALTER TABLE delivery_options ADD CONSTRAINT delivery_options_status_check CHECK ((status = ANY (ARRAY['pending'::text, 'assigned'::text, 'out_for_delivery'::text, 'delivered'::text, 'failed'::text, 'cancelled'::text])));

-- constraint: delivery_provider_assignments_pkey
ALTER TABLE delivery_provider_assignments ADD CONSTRAINT delivery_provider_assignments_pkey PRIMARY KEY (id);

-- constraint: delivery_provider_assignments_seller_id_provider_code_key
ALTER TABLE delivery_provider_assignments ADD CONSTRAINT delivery_provider_assignments_seller_id_provider_code_key UNIQUE (seller_id, provider_code);

-- constraint: delivery_providers_pkey
ALTER TABLE delivery_providers ADD CONSTRAINT delivery_providers_pkey PRIMARY KEY (code);

-- constraint: delivery_providers_provider_type_check
ALTER TABLE delivery_providers ADD CONSTRAINT delivery_providers_provider_type_check CHECK ((provider_type = ANY (ARRAY['merchant'::text, 'partner'::text, 'platform'::text])));

-- constraint: delivery_settings_base_fee_check
ALTER TABLE delivery_settings ADD CONSTRAINT delivery_settings_base_fee_check CHECK ((base_fee >= (0)::numeric));

-- constraint: delivery_settings_pkey
ALTER TABLE delivery_settings ADD CONSTRAINT delivery_settings_pkey PRIMARY KEY (id);

-- constraint: delivery_settings_seller_id_key
ALTER TABLE delivery_settings ADD CONSTRAINT delivery_settings_seller_id_key UNIQUE (seller_id);

-- constraint: delivery_zones_area_name_check
ALTER TABLE delivery_zones ADD CONSTRAINT delivery_zones_area_name_check CHECK (((char_length(TRIM(BOTH FROM area_name)) >= 2) AND (char_length(TRIM(BOTH FROM area_name)) <= 100)));

-- constraint: delivery_zones_estimated_minutes_check
ALTER TABLE delivery_zones ADD CONSTRAINT delivery_zones_estimated_minutes_check CHECK (((estimated_minutes IS NULL) OR ((estimated_minutes >= 1) AND (estimated_minutes <= 1440))));

-- constraint: delivery_zones_fee_check
ALTER TABLE delivery_zones ADD CONSTRAINT delivery_zones_fee_check CHECK ((fee >= (0)::numeric));

-- constraint: delivery_zones_pkey
ALTER TABLE delivery_zones ADD CONSTRAINT delivery_zones_pkey PRIMARY KEY (id);

-- constraint: delivery_zones_seller_id_area_name_key
ALTER TABLE delivery_zones ADD CONSTRAINT delivery_zones_seller_id_area_name_key UNIQUE (seller_id, area_name);

-- constraint: disputes_pkey
ALTER TABLE disputes ADD CONSTRAINT disputes_pkey PRIMARY KEY (id);

-- constraint: disputes_status_check
ALTER TABLE disputes ADD CONSTRAINT disputes_status_check CHECK ((status = ANY (ARRAY['open'::text, 'investigating'::text, 'resolved'::text, 'rejected'::text])));

-- constraint: favorites_pkey
ALTER TABLE favorites ADD CONSTRAINT favorites_pkey PRIMARY KEY (id);

-- constraint: favorites_user_id_product_id_key
ALTER TABLE favorites ADD CONSTRAINT favorites_user_id_product_id_key UNIQUE (user_id, product_id);

-- constraint: feature_flags_pkey
ALTER TABLE feature_flags ADD CONSTRAINT feature_flags_pkey PRIMARY KEY (key);

-- constraint: featured_placements_pkey
ALTER TABLE featured_placements ADD CONSTRAINT featured_placements_pkey PRIMARY KEY (id);

-- constraint: featured_requests_pkey
ALTER TABLE featured_requests ADD CONSTRAINT featured_requests_pkey PRIMARY KEY (id);

-- constraint: featured_requests_status_check
ALTER TABLE featured_requests ADD CONSTRAINT featured_requests_status_check CHECK ((status = ANY (ARRAY['pending'::text, 'approved'::text, 'rejected'::text, 'cancelled'::text, 'expired'::text])));

-- constraint: fee_rules_code_key
ALTER TABLE fee_rules ADD CONSTRAINT fee_rules_code_key UNIQUE (code);

-- constraint: fee_rules_context_check
ALTER TABLE fee_rules ADD CONSTRAINT fee_rules_context_check CHECK ((context = ANY (ARRAY['order'::text, 'merchant_subscription'::text, 'featured_placement'::text, 'advertising'::text])));

-- constraint: fee_rules_fixed_amount_check
ALTER TABLE fee_rules ADD CONSTRAINT fee_rules_fixed_amount_check CHECK ((fixed_amount >= (0)::numeric));

-- constraint: fee_rules_percentage_check
ALTER TABLE fee_rules ADD CONSTRAINT fee_rules_percentage_check CHECK (((percentage >= (0)::numeric) AND (percentage <= (100)::numeric)));

-- constraint: fee_rules_pkey
ALTER TABLE fee_rules ADD CONSTRAINT fee_rules_pkey PRIMARY KEY (id);

-- constraint: inventory_check
ALTER TABLE inventory ADD CONSTRAINT inventory_check CHECK (((reserved_quantity >= 0) AND (reserved_quantity <= quantity)));

-- constraint: inventory_pkey
ALTER TABLE inventory ADD CONSTRAINT inventory_pkey PRIMARY KEY (id);

-- constraint: inventory_product_id_key
ALTER TABLE inventory ADD CONSTRAINT inventory_product_id_key UNIQUE (product_id);

-- constraint: inventory_quantity_check
ALTER TABLE inventory ADD CONSTRAINT inventory_quantity_check CHECK ((quantity >= 0));

-- constraint: loyalty_accounts_pkey
ALTER TABLE loyalty_accounts ADD CONSTRAINT loyalty_accounts_pkey PRIMARY KEY (id);

-- constraint: loyalty_accounts_points_check
ALTER TABLE loyalty_accounts ADD CONSTRAINT loyalty_accounts_points_check CHECK ((points >= 0));

-- constraint: loyalty_accounts_user_id_key
ALTER TABLE loyalty_accounts ADD CONSTRAINT loyalty_accounts_user_id_key UNIQUE (user_id);

-- constraint: loyalty_transactions_pkey
ALTER TABLE loyalty_transactions ADD CONSTRAINT loyalty_transactions_pkey PRIMARY KEY (id);

-- constraint: loyalty_transactions_points_check
ALTER TABLE loyalty_transactions ADD CONSTRAINT loyalty_transactions_points_check CHECK ((points <> 0));

-- constraint: market_cities_code_key
ALTER TABLE market_cities ADD CONSTRAINT market_cities_code_key UNIQUE (code);

-- constraint: market_cities_pkey
ALTER TABLE market_cities ADD CONSTRAINT market_cities_pkey PRIMARY KEY (id);

-- constraint: merchant_ad_campaigns_budget_check
ALTER TABLE merchant_ad_campaigns ADD CONSTRAINT merchant_ad_campaigns_budget_check CHECK (((budget IS NULL) OR (budget >= (0)::numeric)));

-- constraint: merchant_ad_campaigns_pkey
ALTER TABLE merchant_ad_campaigns ADD CONSTRAINT merchant_ad_campaigns_pkey PRIMARY KEY (id);

-- constraint: merchant_ad_campaigns_status_check
ALTER TABLE merchant_ad_campaigns ADD CONSTRAINT merchant_ad_campaigns_status_check CHECK ((status = ANY (ARRAY['draft'::text, 'pending'::text, 'active'::text, 'paused'::text, 'ended'::text, 'rejected'::text])));

-- constraint: merchant_identity_documents_document_type_check
ALTER TABLE merchant_identity_documents ADD CONSTRAINT merchant_identity_documents_document_type_check CHECK ((document_type = ANY (ARRAY['national_id'::text, 'passport'::text, 'other'::text])));

-- constraint: merchant_identity_documents_merchant_id_storage_path_key
ALTER TABLE merchant_identity_documents ADD CONSTRAINT merchant_identity_documents_merchant_id_storage_path_key UNIQUE (merchant_id, storage_path);

-- constraint: merchant_identity_documents_pkey
ALTER TABLE merchant_identity_documents ADD CONSTRAINT merchant_identity_documents_pkey PRIMARY KEY (id);

-- constraint: merchant_metrics_pkey
ALTER TABLE merchant_metrics ADD CONSTRAINT merchant_metrics_pkey PRIMARY KEY (id);

-- constraint: merchant_metrics_seller_id_key
ALTER TABLE merchant_metrics ADD CONSTRAINT merchant_metrics_seller_id_key UNIQUE (seller_id);

-- constraint: merchant_profiles_delivery_fee_check
ALTER TABLE merchant_profiles ADD CONSTRAINT merchant_profiles_delivery_fee_check CHECK ((delivery_fee >= (0)::numeric));

-- constraint: merchant_profiles_delivery_zones_array
ALTER TABLE merchant_profiles ADD CONSTRAINT merchant_profiles_delivery_zones_array CHECK ((jsonb_typeof(delivery_zones) = 'array'::text));

-- constraint: merchant_profiles_estimated_minutes_check
ALTER TABLE merchant_profiles ADD CONSTRAINT merchant_profiles_estimated_minutes_check CHECK (((estimated_minutes IS NULL) OR ((estimated_minutes >= 1) AND (estimated_minutes <= 1440))));

-- constraint: merchant_profiles_pkey
ALTER TABLE merchant_profiles ADD CONSTRAINT merchant_profiles_pkey PRIMARY KEY (id);

-- constraint: merchant_profiles_requested_category_length
ALTER TABLE merchant_profiles ADD CONSTRAINT merchant_profiles_requested_category_length CHECK (((requested_category IS NULL) OR ((char_length(TRIM(BOTH FROM requested_category)) >= 2) AND (char_length(TRIM(BOTH FROM requested_category)) <= 80))));

-- constraint: merchant_profiles_seller_id_key
ALTER TABLE merchant_profiles ADD CONSTRAINT merchant_profiles_seller_id_key UNIQUE (seller_id);

-- constraint: merchant_profiles_user_id_key
ALTER TABLE merchant_profiles ADD CONSTRAINT merchant_profiles_user_id_key UNIQUE (user_id);

-- constraint: merchant_profiles_verification_status_check
ALTER TABLE merchant_profiles ADD CONSTRAINT merchant_profiles_verification_status_check CHECK ((verification_status = ANY (ARRAY['pending'::text, 'changes_requested'::text, 'approved'::text, 'rejected'::text, 'suspended'::text])));

-- constraint: merchant_reports_pkey
ALTER TABLE merchant_reports ADD CONSTRAINT merchant_reports_pkey PRIMARY KEY (id);

-- constraint: merchant_reports_status_check
ALTER TABLE merchant_reports ADD CONSTRAINT merchant_reports_status_check CHECK ((status = ANY (ARRAY['pending'::text, 'reviewed'::text, 'dismissed'::text])));

-- constraint: merchant_reviews_order_id_customer_id_key
ALTER TABLE merchant_reviews ADD CONSTRAINT merchant_reviews_order_id_customer_id_key UNIQUE (order_id, customer_id);

-- constraint: merchant_reviews_pkey
ALTER TABLE merchant_reviews ADD CONSTRAINT merchant_reviews_pkey PRIMARY KEY (id);

-- constraint: merchant_reviews_rating_check
ALTER TABLE merchant_reviews ADD CONSTRAINT merchant_reviews_rating_check CHECK (((rating >= 1) AND (rating <= 5)));

-- constraint: merchant_reviews_status_check
ALTER TABLE merchant_reviews ADD CONSTRAINT merchant_reviews_status_check CHECK ((status = ANY (ARRAY['published'::text, 'hidden'::text, 'removed'::text])));

-- constraint: merchant_settlements_pkey
ALTER TABLE merchant_settlements ADD CONSTRAINT merchant_settlements_pkey PRIMARY KEY (id);

-- constraint: merchant_settlements_status_check
ALTER TABLE merchant_settlements ADD CONSTRAINT merchant_settlements_status_check CHECK ((status = ANY (ARRAY['pending'::text, 'approved'::text, 'paid'::text, 'cancelled'::text])));

-- constraint: merchant_trust_scores_pkey
ALTER TABLE merchant_trust_scores ADD CONSTRAINT merchant_trust_scores_pkey PRIMARY KEY (seller_id);

-- constraint: merchant_trust_scores_trust_level_check
ALTER TABLE merchant_trust_scores ADD CONSTRAINT merchant_trust_scores_trust_level_check CHECK ((trust_level = ANY (ARRAY['verified'::text, 'trusted'::text, 'high_performing'::text, 'restricted'::text])));

-- constraint: merchant_trust_scores_trust_score_check
ALTER TABLE merchant_trust_scores ADD CONSTRAINT merchant_trust_scores_trust_score_check CHECK (((trust_score >= (0)::numeric) AND (trust_score <= (100)::numeric)));

-- constraint: merchant_verifications_pkey
ALTER TABLE merchant_verifications ADD CONSTRAINT merchant_verifications_pkey PRIMARY KEY (id);

-- constraint: merchant_verifications_status_check
ALTER TABLE merchant_verifications ADD CONSTRAINT merchant_verifications_status_check CHECK ((status = ANY (ARRAY['pending'::text, 'approved'::text, 'rejected'::text])));

-- constraint: messages_pkey
ALTER TABLE messages ADD CONSTRAINT messages_pkey PRIMARY KEY (id);

-- constraint: notification_preferences_pkey
ALTER TABLE notification_preferences ADD CONSTRAINT notification_preferences_pkey PRIMARY KEY (user_id);

-- constraint: notifications_pkey
ALTER TABLE notifications ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);

-- constraint: operational_alerts_pkey
ALTER TABLE operational_alerts ADD CONSTRAINT operational_alerts_pkey PRIMARY KEY (id);

-- constraint: operational_alerts_severity_check
ALTER TABLE operational_alerts ADD CONSTRAINT operational_alerts_severity_check CHECK ((severity = ANY (ARRAY['info'::text, 'warning'::text, 'critical'::text])));

-- constraint: operational_alerts_status_check
ALTER TABLE operational_alerts ADD CONSTRAINT operational_alerts_status_check CHECK ((status = ANY (ARRAY['open'::text, 'acknowledged'::text, 'resolved'::text])));

-- constraint: order_groups_delivery_total_check
ALTER TABLE order_groups ADD CONSTRAINT order_groups_delivery_total_check CHECK ((delivery_total >= (0)::numeric));

-- constraint: order_groups_discount_total_check
ALTER TABLE order_groups ADD CONSTRAINT order_groups_discount_total_check CHECK ((discount_total >= (0)::numeric));

-- constraint: order_groups_grand_total_check
ALTER TABLE order_groups ADD CONSTRAINT order_groups_grand_total_check CHECK ((grand_total >= (0)::numeric));

-- constraint: order_groups_payment_method_check
ALTER TABLE order_groups ADD CONSTRAINT order_groups_payment_method_check CHECK ((payment_method = ANY (ARRAY['cod'::text, 'online'::text])));

-- constraint: order_groups_payment_status_check
ALTER TABLE order_groups ADD CONSTRAINT order_groups_payment_status_check CHECK ((payment_status = ANY (ARRAY['pending'::text, 'paid'::text, 'failed'::text, 'refunded'::text, 'partially_refunded'::text])));

-- constraint: order_groups_pkey
ALTER TABLE order_groups ADD CONSTRAINT order_groups_pkey PRIMARY KEY (id);

-- constraint: order_groups_status_check
ALTER TABLE order_groups ADD CONSTRAINT order_groups_status_check CHECK ((status = ANY (ARRAY['pending'::text, 'confirmed'::text, 'processing'::text, 'out_for_delivery'::text, 'delivered'::text, 'cancelled'::text])));

-- constraint: order_groups_subtotal_check
ALTER TABLE order_groups ADD CONSTRAINT order_groups_subtotal_check CHECK ((subtotal >= (0)::numeric));

-- constraint: order_items_pkey
ALTER TABLE order_items ADD CONSTRAINT order_items_pkey PRIMARY KEY (id);

-- constraint: order_items_quantity_check
ALTER TABLE order_items ADD CONSTRAINT order_items_quantity_check CHECK ((quantity > 0));

-- constraint: order_items_quantity_max_check
ALTER TABLE order_items ADD CONSTRAINT order_items_quantity_max_check CHECK ((quantity <= 99));

-- constraint: order_items_unit_price_check
ALTER TABLE order_items ADD CONSTRAINT order_items_unit_price_check CHECK ((unit_price >= (0)::numeric));

-- constraint: order_reviews_order_id_customer_id_key
ALTER TABLE order_reviews ADD CONSTRAINT order_reviews_order_id_customer_id_key UNIQUE (order_id, customer_id);

-- constraint: order_reviews_pkey
ALTER TABLE order_reviews ADD CONSTRAINT order_reviews_pkey PRIMARY KEY (id);

-- constraint: order_reviews_rating_check
ALTER TABLE order_reviews ADD CONSTRAINT order_reviews_rating_check CHECK (((rating >= 1) AND (rating <= 5)));

-- constraint: order_status_history_pkey
ALTER TABLE order_status_history ADD CONSTRAINT order_status_history_pkey PRIMARY KEY (id);

-- constraint: orders_pkey
ALTER TABLE orders ADD CONSTRAINT orders_pkey PRIMARY KEY (id);

-- constraint: orders_status_check
ALTER TABLE orders ADD CONSTRAINT orders_status_check CHECK ((status = ANY (ARRAY['pending'::text, 'accepted'::text, 'preparing'::text, 'ready'::text, 'out_for_delivery'::text, 'delivered'::text, 'cancelled'::text, 'rejected'::text, 'failed'::text])));

-- constraint: orders_total_check
ALTER TABLE orders ADD CONSTRAINT orders_total_check CHECK ((total >= (0)::numeric));

-- constraint: payment_methods_pkey
ALTER TABLE payment_methods ADD CONSTRAINT payment_methods_pkey PRIMARY KEY (code);

-- constraint: payments_amount_check
ALTER TABLE payments ADD CONSTRAINT payments_amount_check CHECK ((amount >= (0)::numeric));

-- constraint: payments_pkey
ALTER TABLE payments ADD CONSTRAINT payments_pkey PRIMARY KEY (id);

-- constraint: payments_status_check
ALTER TABLE payments ADD CONSTRAINT payments_status_check CHECK ((status = ANY (ARRAY['pending'::text, 'processing'::text, 'paid'::text, 'failed'::text, 'cancelled'::text, 'refunded'::text, 'partially_refunded'::text])));

-- constraint: product_images_media_type_check
ALTER TABLE product_images ADD CONSTRAINT product_images_media_type_check CHECK ((media_type = ANY (ARRAY['image'::text, 'video'::text])));

-- constraint: product_images_pkey
ALTER TABLE product_images ADD CONSTRAINT product_images_pkey PRIMARY KEY (id);

-- constraint: product_reports_pkey
ALTER TABLE product_reports ADD CONSTRAINT product_reports_pkey PRIMARY KEY (id);

-- constraint: product_reports_status_check
ALTER TABLE product_reports ADD CONSTRAINT product_reports_status_check CHECK ((status = ANY (ARRAY['pending'::text, 'reviewed'::text, 'dismissed'::text])));

-- constraint: product_variants_pkey
ALTER TABLE product_variants ADD CONSTRAINT product_variants_pkey PRIMARY KEY (id);

-- constraint: product_variants_stock_check
ALTER TABLE product_variants ADD CONSTRAINT product_variants_stock_check CHECK ((stock >= 0));

-- constraint: products_pkey
ALTER TABLE products ADD CONSTRAINT products_pkey PRIMARY KEY (id);

-- constraint: products_price_check
ALTER TABLE products ADD CONSTRAINT products_price_check CHECK ((price >= (0)::numeric));

-- constraint: products_stock_check
ALTER TABLE products ADD CONSTRAINT products_stock_check CHECK ((stock >= 0));

-- constraint: profiles_name_length
ALTER TABLE profiles ADD CONSTRAINT profiles_name_length CHECK (((char_length(TRIM(BOTH FROM name)) >= 2) AND (char_length(TRIM(BOTH FROM name)) <= 100)));

-- constraint: profiles_phone_length
ALTER TABLE profiles ADD CONSTRAINT profiles_phone_length CHECK (((char_length(TRIM(BOTH FROM phone)) >= 7) AND (char_length(TRIM(BOTH FROM phone)) <= 30)));

-- constraint: profiles_pkey
ALTER TABLE profiles ADD CONSTRAINT profiles_pkey PRIMARY KEY (id);

-- constraint: profiles_role_check
ALTER TABLE profiles ADD CONSTRAINT profiles_role_check CHECK ((role = ANY (ARRAY['customer'::text, 'seller'::text, 'admin'::text, 'support'::text])));

-- constraint: promotions_discount_type_check
ALTER TABLE promotions ADD CONSTRAINT promotions_discount_type_check CHECK ((discount_type = ANY (ARRAY['fixed'::text, 'percentage'::text])));

-- constraint: promotions_pkey
ALTER TABLE promotions ADD CONSTRAINT promotions_pkey PRIMARY KEY (id);

-- constraint: push_devices_pkey
ALTER TABLE push_devices ADD CONSTRAINT push_devices_pkey PRIMARY KEY (id);

-- constraint: push_devices_platform_check
ALTER TABLE push_devices ADD CONSTRAINT push_devices_platform_check CHECK ((platform = ANY (ARRAY['android'::text, 'ios'::text, 'web'::text])));

-- constraint: push_devices_provider_check
ALTER TABLE push_devices ADD CONSTRAINT push_devices_provider_check CHECK ((provider = ANY (ARRAY['fcm'::text, 'expo'::text, 'apns'::text])));

-- constraint: push_devices_provider_token_key
ALTER TABLE push_devices ADD CONSTRAINT push_devices_provider_token_key UNIQUE (provider, token);

-- constraint: referral_codes_code_check
ALTER TABLE referral_codes ADD CONSTRAINT referral_codes_code_check CHECK (((char_length(code) >= 6) AND (char_length(code) <= 32)));

-- constraint: referral_codes_code_key
ALTER TABLE referral_codes ADD CONSTRAINT referral_codes_code_key UNIQUE (code);

-- constraint: referral_codes_pkey
ALTER TABLE referral_codes ADD CONSTRAINT referral_codes_pkey PRIMARY KEY (user_id);

-- constraint: referrals_pkey
ALTER TABLE referrals ADD CONSTRAINT referrals_pkey PRIMARY KEY (id);

-- constraint: referrals_referred_id_key
ALTER TABLE referrals ADD CONSTRAINT referrals_referred_id_key UNIQUE (referred_id);

-- constraint: referrals_status_check
ALTER TABLE referrals ADD CONSTRAINT referrals_status_check CHECK ((status = ANY (ARRAY['pending'::text, 'qualified'::text, 'rewarded'::text, 'rejected'::text])));

-- constraint: refunds_amount_check
ALTER TABLE refunds ADD CONSTRAINT refunds_amount_check CHECK ((amount > (0)::numeric));

-- constraint: refunds_pkey
ALTER TABLE refunds ADD CONSTRAINT refunds_pkey PRIMARY KEY (id);

-- constraint: refunds_status_check
ALTER TABLE refunds ADD CONSTRAINT refunds_status_check CHECK ((status = ANY (ARRAY['pending'::text, 'approved'::text, 'processed'::text, 'rejected'::text])));

-- constraint: reports_pkey
ALTER TABLE reports ADD CONSTRAINT reports_pkey PRIMARY KEY (id);

-- constraint: review_reports_pkey
ALTER TABLE review_reports ADD CONSTRAINT review_reports_pkey PRIMARY KEY (id);

-- constraint: review_reports_review_id_reporter_id_key
ALTER TABLE review_reports ADD CONSTRAINT review_reports_review_id_reporter_id_key UNIQUE (review_id, reporter_id);

-- constraint: review_reports_status_check
ALTER TABLE review_reports ADD CONSTRAINT review_reports_status_check CHECK ((status = ANY (ARRAY['pending'::text, 'reviewed'::text, 'dismissed'::text])));

-- constraint: reviews_pkey
ALTER TABLE reviews ADD CONSTRAINT reviews_pkey PRIMARY KEY (id);

-- constraint: reviews_product_id_customer_id_key
ALTER TABLE reviews ADD CONSTRAINT reviews_product_id_customer_id_key UNIQUE (product_id, customer_id);

-- constraint: reviews_rating_check
ALTER TABLE reviews ADD CONSTRAINT reviews_rating_check CHECK (((rating >= 1) AND (rating <= 5)));

-- constraint: reviews_status_check
ALTER TABLE reviews ADD CONSTRAINT reviews_status_check CHECK ((status = ANY (ARRAY['published'::text, 'hidden'::text, 'removed'::text])));

-- constraint: sellers_pkey
ALTER TABLE sellers ADD CONSTRAINT sellers_pkey PRIMARY KEY (id);

-- constraint: sellers_user_id_key
ALTER TABLE sellers ADD CONSTRAINT sellers_user_id_key UNIQUE (user_id);

-- constraint: sellers_verification_status_check
ALTER TABLE sellers ADD CONSTRAINT sellers_verification_status_check CHECK ((verification_status = ANY (ARRAY['pending'::text, 'approved'::text, 'rejected'::text, 'suspended'::text])));

-- constraint: stores_merchant_id_key
ALTER TABLE stores ADD CONSTRAINT stores_merchant_id_key UNIQUE (merchant_id);

-- constraint: stores_pkey
ALTER TABLE stores ADD CONSTRAINT stores_pkey PRIMARY KEY (id);

-- constraint: subscription_plans_audience_check
ALTER TABLE subscription_plans ADD CONSTRAINT subscription_plans_audience_check CHECK ((audience = ANY (ARRAY['merchant'::text, 'customer'::text, 'all'::text])));

-- constraint: subscription_plans_duration_days_check
ALTER TABLE subscription_plans ADD CONSTRAINT subscription_plans_duration_days_check CHECK ((duration_days > 0));

-- constraint: subscription_plans_name_key
ALTER TABLE subscription_plans ADD CONSTRAINT subscription_plans_name_key UNIQUE (name);

-- constraint: subscription_plans_pkey
ALTER TABLE subscription_plans ADD CONSTRAINT subscription_plans_pkey PRIMARY KEY (id);

-- constraint: subscription_requests_pkey
ALTER TABLE subscription_requests ADD CONSTRAINT subscription_requests_pkey PRIMARY KEY (id);

-- constraint: subscription_requests_status_check
ALTER TABLE subscription_requests ADD CONSTRAINT subscription_requests_status_check CHECK ((status = ANY (ARRAY['pending'::text, 'approved'::text, 'rejected'::text, 'cancelled'::text])));

-- constraint: subscriptions_pkey
ALTER TABLE subscriptions ADD CONSTRAINT subscriptions_pkey PRIMARY KEY (id);

-- constraint: subscriptions_status_check
ALTER TABLE subscriptions ADD CONSTRAINT subscriptions_status_check CHECK ((status = ANY (ARRAY['trial'::text, 'active'::text, 'expired'::text, 'cancelled'::text])));

-- constraint: support_tickets_pkey
ALTER TABLE support_tickets ADD CONSTRAINT support_tickets_pkey PRIMARY KEY (id);

-- constraint: support_tickets_priority_check
ALTER TABLE support_tickets ADD CONSTRAINT support_tickets_priority_check CHECK ((priority = ANY (ARRAY['low'::text, 'normal'::text, 'high'::text, 'urgent'::text])));

-- constraint: support_tickets_status_check
ALTER TABLE support_tickets ADD CONSTRAINT support_tickets_status_check CHECK ((status = ANY (ARRAY['open'::text, 'in_progress'::text, 'resolved'::text, 'closed'::text])));

-- constraint: system_settings_pkey
ALTER TABLE system_settings ADD CONSTRAINT system_settings_pkey PRIMARY KEY (key);

-- constraint: ticket_messages_pkey
ALTER TABLE ticket_messages ADD CONSTRAINT ticket_messages_pkey PRIMARY KEY (id);

-- constraint: user_reports_pkey
ALTER TABLE user_reports ADD CONSTRAINT user_reports_pkey PRIMARY KEY (id);

-- constraint: user_reports_status_check
ALTER TABLE user_reports ADD CONSTRAINT user_reports_status_check CHECK ((status = ANY (ARRAY['pending'::text, 'reviewed'::text, 'dismissed'::text])));

-- constraint: wallet_transactions_amount_check
ALTER TABLE wallet_transactions ADD CONSTRAINT wallet_transactions_amount_check CHECK ((amount > (0)::numeric));

-- constraint: wallet_transactions_pkey
ALTER TABLE wallet_transactions ADD CONSTRAINT wallet_transactions_pkey PRIMARY KEY (id);

-- constraint: wallet_transactions_type_check
ALTER TABLE wallet_transactions ADD CONSTRAINT wallet_transactions_type_check CHECK ((type = ANY (ARRAY['credit'::text, 'debit'::text, 'refund'::text, 'adjustment'::text])));

-- constraint: wallets_balance_check
ALTER TABLE wallets ADD CONSTRAINT wallets_balance_check CHECK ((balance >= (0)::numeric));

-- constraint: wallets_pkey
ALTER TABLE wallets ADD CONSTRAINT wallets_pkey PRIMARY KEY (id);

-- constraint: wallets_user_id_key
ALTER TABLE wallets ADD CONSTRAINT wallets_user_id_key UNIQUE (user_id);

-- constraint: addresses_user_id_fkey
ALTER TABLE addresses ADD CONSTRAINT addresses_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

-- constraint: app_errors_user_id_fkey
ALTER TABLE app_errors ADD CONSTRAINT app_errors_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE SET NULL;

-- constraint: app_events_user_id_fkey
ALTER TABLE app_events ADD CONSTRAINT app_events_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE SET NULL;

-- constraint: app_settings_updated_by_fkey
ALTER TABLE app_settings ADD CONSTRAINT app_settings_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES auth.users(id) ON DELETE SET NULL;

-- constraint: audit_logs_actor_id_fkey
ALTER TABLE audit_logs ADD CONSTRAINT audit_logs_actor_id_fkey FOREIGN KEY (actor_id) REFERENCES auth.users(id) ON DELETE SET NULL;

-- constraint: billing_transactions_payment_method_fkey
ALTER TABLE billing_transactions ADD CONSTRAINT billing_transactions_payment_method_fkey FOREIGN KEY (payment_method) REFERENCES payment_methods(code);

-- constraint: billing_transactions_seller_id_fkey
ALTER TABLE billing_transactions ADD CONSTRAINT billing_transactions_seller_id_fkey FOREIGN KEY (seller_id) REFERENCES sellers(id) ON DELETE SET NULL;

-- constraint: billing_transactions_user_id_fkey
ALTER TABLE billing_transactions ADD CONSTRAINT billing_transactions_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

-- constraint: cart_items_cart_id_fkey
ALTER TABLE cart_items ADD CONSTRAINT cart_items_cart_id_fkey FOREIGN KEY (cart_id) REFERENCES carts(id) ON DELETE CASCADE;

-- constraint: cart_items_product_id_fkey
ALTER TABLE cart_items ADD CONSTRAINT cart_items_product_id_fkey FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT;

-- constraint: cart_items_variant_id_fkey
ALTER TABLE cart_items ADD CONSTRAINT cart_items_variant_id_fkey FOREIGN KEY (variant_id) REFERENCES product_variants(id) ON DELETE RESTRICT;

-- constraint: carts_user_id_fkey
ALTER TABLE carts ADD CONSTRAINT carts_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

-- constraint: complaints_assigned_to_fkey
ALTER TABLE complaints ADD CONSTRAINT complaints_assigned_to_fkey FOREIGN KEY (assigned_to) REFERENCES auth.users(id) ON DELETE SET NULL;

-- constraint: complaints_order_id_fkey
ALTER TABLE complaints ADD CONSTRAINT complaints_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE SET NULL;

-- constraint: complaints_resolved_by_fkey
ALTER TABLE complaints ADD CONSTRAINT complaints_resolved_by_fkey FOREIGN KEY (resolved_by) REFERENCES auth.users(id) ON DELETE SET NULL;

-- constraint: complaints_seller_id_fkey
ALTER TABLE complaints ADD CONSTRAINT complaints_seller_id_fkey FOREIGN KEY (seller_id) REFERENCES sellers(id) ON DELETE SET NULL;

-- constraint: complaints_user_id_fkey
ALTER TABLE complaints ADD CONSTRAINT complaints_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE RESTRICT;

-- constraint: conversations_customer_id_fkey
ALTER TABLE conversations ADD CONSTRAINT conversations_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES auth.users(id) ON DELETE CASCADE;

-- constraint: conversations_order_id_fkey
ALTER TABLE conversations ADD CONSTRAINT conversations_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE SET NULL;

-- constraint: conversations_seller_id_fkey
ALTER TABLE conversations ADD CONSTRAINT conversations_seller_id_fkey FOREIGN KEY (seller_id) REFERENCES sellers(id) ON DELETE SET NULL;

-- constraint: coupon_usages_coupon_id_fkey
ALTER TABLE coupon_usages ADD CONSTRAINT coupon_usages_coupon_id_fkey FOREIGN KEY (coupon_id) REFERENCES coupons(id) ON DELETE RESTRICT;

-- constraint: coupon_usages_order_id_fkey
ALTER TABLE coupon_usages ADD CONSTRAINT coupon_usages_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE SET NULL;

-- constraint: coupon_usages_user_id_fkey
ALTER TABLE coupon_usages ADD CONSTRAINT coupon_usages_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE RESTRICT;

-- constraint: delivery_options_order_id_fkey
ALTER TABLE delivery_options ADD CONSTRAINT delivery_options_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE;

-- constraint: delivery_provider_assignments_provider_code_fkey
ALTER TABLE delivery_provider_assignments ADD CONSTRAINT delivery_provider_assignments_provider_code_fkey FOREIGN KEY (provider_code) REFERENCES delivery_providers(code) ON DELETE RESTRICT;

-- constraint: delivery_provider_assignments_seller_id_fkey
ALTER TABLE delivery_provider_assignments ADD CONSTRAINT delivery_provider_assignments_seller_id_fkey FOREIGN KEY (seller_id) REFERENCES sellers(id) ON DELETE CASCADE;

-- constraint: delivery_settings_seller_id_fkey
ALTER TABLE delivery_settings ADD CONSTRAINT delivery_settings_seller_id_fkey FOREIGN KEY (seller_id) REFERENCES sellers(id) ON DELETE CASCADE;

-- constraint: delivery_zones_seller_id_fkey
ALTER TABLE delivery_zones ADD CONSTRAINT delivery_zones_seller_id_fkey FOREIGN KEY (seller_id) REFERENCES sellers(id) ON DELETE CASCADE;

-- constraint: disputes_opened_by_fkey
ALTER TABLE disputes ADD CONSTRAINT disputes_opened_by_fkey FOREIGN KEY (opened_by) REFERENCES auth.users(id) ON DELETE RESTRICT;

-- constraint: disputes_order_id_fkey
ALTER TABLE disputes ADD CONSTRAINT disputes_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT;

-- constraint: disputes_resolved_by_fkey
ALTER TABLE disputes ADD CONSTRAINT disputes_resolved_by_fkey FOREIGN KEY (resolved_by) REFERENCES auth.users(id) ON DELETE SET NULL;

-- constraint: favorites_product_id_fkey
ALTER TABLE favorites ADD CONSTRAINT favorites_product_id_fkey FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE;

-- constraint: favorites_user_id_fkey
ALTER TABLE favorites ADD CONSTRAINT favorites_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

-- constraint: feature_flags_updated_by_fkey
ALTER TABLE feature_flags ADD CONSTRAINT feature_flags_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES auth.users(id) ON DELETE SET NULL;

-- constraint: featured_placements_product_id_fkey
ALTER TABLE featured_placements ADD CONSTRAINT featured_placements_product_id_fkey FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE;

-- constraint: featured_placements_request_id_fkey
ALTER TABLE featured_placements ADD CONSTRAINT featured_placements_request_id_fkey FOREIGN KEY (request_id) REFERENCES featured_requests(id) ON DELETE SET NULL;

-- constraint: featured_placements_seller_id_fkey
ALTER TABLE featured_placements ADD CONSTRAINT featured_placements_seller_id_fkey FOREIGN KEY (seller_id) REFERENCES sellers(id) ON DELETE CASCADE;

-- constraint: featured_requests_product_id_fkey
ALTER TABLE featured_requests ADD CONSTRAINT featured_requests_product_id_fkey FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE;

-- constraint: featured_requests_reviewed_by_fkey
ALTER TABLE featured_requests ADD CONSTRAINT featured_requests_reviewed_by_fkey FOREIGN KEY (reviewed_by) REFERENCES auth.users(id) ON DELETE SET NULL;

-- constraint: featured_requests_seller_id_fkey
ALTER TABLE featured_requests ADD CONSTRAINT featured_requests_seller_id_fkey FOREIGN KEY (seller_id) REFERENCES sellers(id) ON DELETE CASCADE;

-- constraint: inventory_product_id_fkey
ALTER TABLE inventory ADD CONSTRAINT inventory_product_id_fkey FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE;

-- constraint: loyalty_accounts_user_id_fkey
ALTER TABLE loyalty_accounts ADD CONSTRAINT loyalty_accounts_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

-- constraint: loyalty_transactions_account_id_fkey
ALTER TABLE loyalty_transactions ADD CONSTRAINT loyalty_transactions_account_id_fkey FOREIGN KEY (account_id) REFERENCES loyalty_accounts(id) ON DELETE RESTRICT;

-- constraint: merchant_ad_campaigns_product_id_fkey
ALTER TABLE merchant_ad_campaigns ADD CONSTRAINT merchant_ad_campaigns_product_id_fkey FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE;

-- constraint: merchant_ad_campaigns_seller_id_fkey
ALTER TABLE merchant_ad_campaigns ADD CONSTRAINT merchant_ad_campaigns_seller_id_fkey FOREIGN KEY (seller_id) REFERENCES sellers(id) ON DELETE CASCADE;

-- constraint: merchant_identity_documents_merchant_id_fkey
ALTER TABLE merchant_identity_documents ADD CONSTRAINT merchant_identity_documents_merchant_id_fkey FOREIGN KEY (merchant_id) REFERENCES merchant_profiles(id) ON DELETE CASCADE;

-- constraint: merchant_identity_documents_user_id_fkey
ALTER TABLE merchant_identity_documents ADD CONSTRAINT merchant_identity_documents_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

-- constraint: merchant_metrics_seller_id_fkey
ALTER TABLE merchant_metrics ADD CONSTRAINT merchant_metrics_seller_id_fkey FOREIGN KEY (seller_id) REFERENCES sellers(id) ON DELETE CASCADE;

-- constraint: merchant_profiles_category_id_fkey
ALTER TABLE merchant_profiles ADD CONSTRAINT merchant_profiles_category_id_fkey FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL;

-- constraint: merchant_profiles_seller_id_fkey
ALTER TABLE merchant_profiles ADD CONSTRAINT merchant_profiles_seller_id_fkey FOREIGN KEY (seller_id) REFERENCES sellers(id) ON DELETE SET NULL;

-- constraint: merchant_profiles_user_id_fkey
ALTER TABLE merchant_profiles ADD CONSTRAINT merchant_profiles_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

-- constraint: merchant_reports_reporter_id_fkey
ALTER TABLE merchant_reports ADD CONSTRAINT merchant_reports_reporter_id_fkey FOREIGN KEY (reporter_id) REFERENCES auth.users(id) ON DELETE RESTRICT;

-- constraint: merchant_reports_seller_id_fkey
ALTER TABLE merchant_reports ADD CONSTRAINT merchant_reports_seller_id_fkey FOREIGN KEY (seller_id) REFERENCES sellers(id) ON DELETE RESTRICT;

-- constraint: merchant_reviews_customer_id_fkey
ALTER TABLE merchant_reviews ADD CONSTRAINT merchant_reviews_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES auth.users(id) ON DELETE CASCADE;

-- constraint: merchant_reviews_order_id_fkey
ALTER TABLE merchant_reviews ADD CONSTRAINT merchant_reviews_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE;

-- constraint: merchant_reviews_seller_id_fkey
ALTER TABLE merchant_reviews ADD CONSTRAINT merchant_reviews_seller_id_fkey FOREIGN KEY (seller_id) REFERENCES sellers(id) ON DELETE CASCADE;

-- constraint: merchant_settlements_order_id_fkey
ALTER TABLE merchant_settlements ADD CONSTRAINT merchant_settlements_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE SET NULL;

-- constraint: merchant_settlements_seller_id_fkey
ALTER TABLE merchant_settlements ADD CONSTRAINT merchant_settlements_seller_id_fkey FOREIGN KEY (seller_id) REFERENCES sellers(id) ON DELETE RESTRICT;

-- constraint: merchant_trust_scores_seller_id_fkey
ALTER TABLE merchant_trust_scores ADD CONSTRAINT merchant_trust_scores_seller_id_fkey FOREIGN KEY (seller_id) REFERENCES sellers(id) ON DELETE CASCADE;

-- constraint: merchant_verifications_merchant_id_fkey
ALTER TABLE merchant_verifications ADD CONSTRAINT merchant_verifications_merchant_id_fkey FOREIGN KEY (merchant_id) REFERENCES merchant_profiles(id) ON DELETE CASCADE;

-- constraint: merchant_verifications_reviewer_id_fkey
ALTER TABLE merchant_verifications ADD CONSTRAINT merchant_verifications_reviewer_id_fkey FOREIGN KEY (reviewer_id) REFERENCES auth.users(id) ON DELETE SET NULL;

-- constraint: messages_conversation_id_fkey
ALTER TABLE messages ADD CONSTRAINT messages_conversation_id_fkey FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE;

-- constraint: messages_sender_id_fkey
ALTER TABLE messages ADD CONSTRAINT messages_sender_id_fkey FOREIGN KEY (sender_id) REFERENCES auth.users(id) ON DELETE RESTRICT;

-- constraint: notification_preferences_user_id_fkey
ALTER TABLE notification_preferences ADD CONSTRAINT notification_preferences_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

-- constraint: notifications_user_id_fkey
ALTER TABLE notifications ADD CONSTRAINT notifications_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

-- constraint: operational_alerts_resolved_by_fkey
ALTER TABLE operational_alerts ADD CONSTRAINT operational_alerts_resolved_by_fkey FOREIGN KEY (resolved_by) REFERENCES auth.users(id) ON DELETE SET NULL;

-- constraint: order_groups_address_id_fkey
ALTER TABLE order_groups ADD CONSTRAINT order_groups_address_id_fkey FOREIGN KEY (address_id) REFERENCES addresses(id) ON DELETE SET NULL;

-- constraint: order_groups_customer_id_fkey
ALTER TABLE order_groups ADD CONSTRAINT order_groups_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES auth.users(id) ON DELETE RESTRICT;

-- constraint: order_items_order_id_fkey
ALTER TABLE order_items ADD CONSTRAINT order_items_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE;

-- constraint: order_items_product_id_fkey
ALTER TABLE order_items ADD CONSTRAINT order_items_product_id_fkey FOREIGN KEY (product_id) REFERENCES products(id);

-- constraint: order_items_seller_id_fkey
ALTER TABLE order_items ADD CONSTRAINT order_items_seller_id_fkey FOREIGN KEY (seller_id) REFERENCES sellers(id);

-- constraint: order_reviews_customer_id_fkey
ALTER TABLE order_reviews ADD CONSTRAINT order_reviews_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES auth.users(id) ON DELETE CASCADE;

-- constraint: order_reviews_order_id_fkey
ALTER TABLE order_reviews ADD CONSTRAINT order_reviews_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE;

-- constraint: order_status_history_changed_by_fkey
ALTER TABLE order_status_history ADD CONSTRAINT order_status_history_changed_by_fkey FOREIGN KEY (changed_by) REFERENCES auth.users(id) ON DELETE SET NULL;

-- constraint: order_status_history_order_id_fkey
ALTER TABLE order_status_history ADD CONSTRAINT order_status_history_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE;

-- constraint: orders_customer_id_fkey
ALTER TABLE orders ADD CONSTRAINT orders_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES profiles(id) ON DELETE RESTRICT;

-- constraint: orders_order_group_id_fkey
ALTER TABLE orders ADD CONSTRAINT orders_order_group_id_fkey FOREIGN KEY (order_group_id) REFERENCES order_groups(id) ON DELETE SET NULL;

-- constraint: orders_seller_id_fkey
ALTER TABLE orders ADD CONSTRAINT orders_seller_id_fkey FOREIGN KEY (seller_id) REFERENCES sellers(id) ON DELETE RESTRICT;

-- constraint: payments_order_id_fkey
ALTER TABLE payments ADD CONSTRAINT payments_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT;

-- constraint: product_images_product_id_fkey
ALTER TABLE product_images ADD CONSTRAINT product_images_product_id_fkey FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE;

-- constraint: product_reports_product_id_fkey
ALTER TABLE product_reports ADD CONSTRAINT product_reports_product_id_fkey FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT;

-- constraint: product_reports_reporter_id_fkey
ALTER TABLE product_reports ADD CONSTRAINT product_reports_reporter_id_fkey FOREIGN KEY (reporter_id) REFERENCES auth.users(id) ON DELETE RESTRICT;

-- constraint: product_variants_product_id_fkey
ALTER TABLE product_variants ADD CONSTRAINT product_variants_product_id_fkey FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE;

-- constraint: products_category_id_fkey
ALTER TABLE products ADD CONSTRAINT products_category_id_fkey FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL;

-- constraint: products_seller_id_fkey
ALTER TABLE products ADD CONSTRAINT products_seller_id_fkey FOREIGN KEY (seller_id) REFERENCES sellers(id) ON DELETE CASCADE;

-- constraint: profiles_id_fkey
ALTER TABLE profiles ADD CONSTRAINT profiles_id_fkey FOREIGN KEY (id) REFERENCES auth.users(id) ON DELETE CASCADE;

-- constraint: promotions_product_id_fkey
ALTER TABLE promotions ADD CONSTRAINT promotions_product_id_fkey FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE;

-- constraint: promotions_seller_id_fkey
ALTER TABLE promotions ADD CONSTRAINT promotions_seller_id_fkey FOREIGN KEY (seller_id) REFERENCES sellers(id) ON DELETE CASCADE;

-- constraint: push_devices_user_id_fkey
ALTER TABLE push_devices ADD CONSTRAINT push_devices_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

-- constraint: referral_codes_user_id_fkey
ALTER TABLE referral_codes ADD CONSTRAINT referral_codes_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

-- constraint: referrals_referred_id_fkey
ALTER TABLE referrals ADD CONSTRAINT referrals_referred_id_fkey FOREIGN KEY (referred_id) REFERENCES auth.users(id) ON DELETE RESTRICT;

-- constraint: referrals_referrer_id_fkey
ALTER TABLE referrals ADD CONSTRAINT referrals_referrer_id_fkey FOREIGN KEY (referrer_id) REFERENCES auth.users(id) ON DELETE RESTRICT;

-- constraint: refunds_payment_id_fkey
ALTER TABLE refunds ADD CONSTRAINT refunds_payment_id_fkey FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE RESTRICT;

-- constraint: refunds_processed_by_fkey
ALTER TABLE refunds ADD CONSTRAINT refunds_processed_by_fkey FOREIGN KEY (processed_by) REFERENCES auth.users(id) ON DELETE SET NULL;

-- constraint: reports_generated_by_fkey
ALTER TABLE reports ADD CONSTRAINT reports_generated_by_fkey FOREIGN KEY (generated_by) REFERENCES auth.users(id) ON DELETE SET NULL;

-- constraint: review_reports_reporter_id_fkey
ALTER TABLE review_reports ADD CONSTRAINT review_reports_reporter_id_fkey FOREIGN KEY (reporter_id) REFERENCES auth.users(id) ON DELETE RESTRICT;

-- constraint: review_reports_review_id_fkey
ALTER TABLE review_reports ADD CONSTRAINT review_reports_review_id_fkey FOREIGN KEY (review_id) REFERENCES reviews(id) ON DELETE CASCADE;

-- constraint: reviews_customer_id_fkey
ALTER TABLE reviews ADD CONSTRAINT reviews_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES profiles(id) ON DELETE CASCADE;

-- constraint: reviews_order_id_fkey
ALTER TABLE reviews ADD CONSTRAINT reviews_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE SET NULL;

-- constraint: reviews_product_id_fkey
ALTER TABLE reviews ADD CONSTRAINT reviews_product_id_fkey FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE;

-- constraint: sellers_user_id_fkey
ALTER TABLE sellers ADD CONSTRAINT sellers_user_id_fkey FOREIGN KEY (user_id) REFERENCES profiles(id) ON DELETE CASCADE;

-- constraint: stores_category_id_fkey
ALTER TABLE stores ADD CONSTRAINT stores_category_id_fkey FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL;

-- constraint: stores_merchant_id_fkey
ALTER TABLE stores ADD CONSTRAINT stores_merchant_id_fkey FOREIGN KEY (merchant_id) REFERENCES merchant_profiles(id) ON DELETE CASCADE;

-- constraint: stores_seller_id_fkey
ALTER TABLE stores ADD CONSTRAINT stores_seller_id_fkey FOREIGN KEY (seller_id) REFERENCES sellers(id) ON DELETE SET NULL;

-- constraint: subscription_requests_plan_id_fkey
ALTER TABLE subscription_requests ADD CONSTRAINT subscription_requests_plan_id_fkey FOREIGN KEY (plan_id) REFERENCES subscription_plans(id) ON DELETE RESTRICT;

-- constraint: subscription_requests_reviewed_by_fkey
ALTER TABLE subscription_requests ADD CONSTRAINT subscription_requests_reviewed_by_fkey FOREIGN KEY (reviewed_by) REFERENCES auth.users(id) ON DELETE SET NULL;

-- constraint: subscription_requests_seller_id_fkey
ALTER TABLE subscription_requests ADD CONSTRAINT subscription_requests_seller_id_fkey FOREIGN KEY (seller_id) REFERENCES sellers(id) ON DELETE CASCADE;

-- constraint: subscription_requests_user_id_fkey
ALTER TABLE subscription_requests ADD CONSTRAINT subscription_requests_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

-- constraint: subscriptions_plan_id_fkey
ALTER TABLE subscriptions ADD CONSTRAINT subscriptions_plan_id_fkey FOREIGN KEY (plan_id) REFERENCES subscription_plans(id) ON DELETE RESTRICT;

-- constraint: subscriptions_seller_id_fkey
ALTER TABLE subscriptions ADD CONSTRAINT subscriptions_seller_id_fkey FOREIGN KEY (seller_id) REFERENCES sellers(id) ON DELETE CASCADE;

-- constraint: subscriptions_user_id_fkey
ALTER TABLE subscriptions ADD CONSTRAINT subscriptions_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

-- constraint: support_tickets_assigned_to_fkey
ALTER TABLE support_tickets ADD CONSTRAINT support_tickets_assigned_to_fkey FOREIGN KEY (assigned_to) REFERENCES auth.users(id) ON DELETE SET NULL;

-- constraint: support_tickets_user_id_fkey
ALTER TABLE support_tickets ADD CONSTRAINT support_tickets_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

-- constraint: system_settings_updated_by_fkey
ALTER TABLE system_settings ADD CONSTRAINT system_settings_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES auth.users(id) ON DELETE SET NULL;

-- constraint: ticket_messages_sender_id_fkey
ALTER TABLE ticket_messages ADD CONSTRAINT ticket_messages_sender_id_fkey FOREIGN KEY (sender_id) REFERENCES auth.users(id) ON DELETE RESTRICT;

-- constraint: ticket_messages_ticket_id_fkey
ALTER TABLE ticket_messages ADD CONSTRAINT ticket_messages_ticket_id_fkey FOREIGN KEY (ticket_id) REFERENCES support_tickets(id) ON DELETE CASCADE;

-- constraint: user_reports_reported_user_id_fkey
ALTER TABLE user_reports ADD CONSTRAINT user_reports_reported_user_id_fkey FOREIGN KEY (reported_user_id) REFERENCES auth.users(id) ON DELETE RESTRICT;

-- constraint: user_reports_reporter_id_fkey
ALTER TABLE user_reports ADD CONSTRAINT user_reports_reporter_id_fkey FOREIGN KEY (reporter_id) REFERENCES auth.users(id) ON DELETE RESTRICT;

-- constraint: wallet_transactions_wallet_id_fkey
ALTER TABLE wallet_transactions ADD CONSTRAINT wallet_transactions_wallet_id_fkey FOREIGN KEY (wallet_id) REFERENCES wallets(id) ON DELETE RESTRICT;

-- constraint: wallets_user_id_fkey
ALTER TABLE wallets ADD CONSTRAINT wallets_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

-- function: private.assert_delivery_selection_available
CREATE OR REPLACE FUNCTION private.assert_delivery_selection_available(p_items jsonb, p_delivery_zones jsonb)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_seller record;
  v_selected_zone text;
begin
  if jsonb_typeof(coalesce(p_delivery_zones,'{}'::jsonb)) <> 'object' then
    raise exception using errcode='22023', message='اختيارات مناطق التوصيل غير صحيحة';
  end if;

  for v_seller in
    select distinct p.seller_id,coalesce(st.name,s.store_name) as store_name
    from jsonb_array_elements(private.normalize_checkout_items(p_items)) i
    join public.products p on p.id=(i.value->>'product_id')::uuid
    join public.sellers s on s.id=p.seller_id
    left join lateral (
      select st.name
      from public.stores st
      where st.seller_id=p.seller_id and st.is_active
      order by st.id
      limit 1
    ) st on true
  loop
    if not exists(
      select 1 from public.delivery_settings d
      where d.seller_id=v_seller.seller_id and d.is_active
    ) then
      raise exception using errcode='P0001', message=format('التوصيل متوقف مؤقتاً لدى %s',v_seller.store_name);
    end if;

    if exists(select 1 from public.stores st where st.seller_id=v_seller.seller_id)
       and not exists(
         select 1 from public.stores st
         where st.seller_id=v_seller.seller_id and st.is_active and st.is_open
       ) then
      raise exception using errcode='P0001', message=format('المتجر مغلق حالياً: %s',v_seller.store_name);
    end if;

    if exists(select 1 from public.delivery_zones z where z.seller_id=v_seller.seller_id) then
      v_selected_zone := nullif(trim(coalesce(p_delivery_zones->>v_seller.seller_id::text,'')),'');
      if v_selected_zone is null then
        raise exception using errcode='22023', message=format('اختاري منطقة التوصيل من %s',v_seller.store_name);
      end if;
      if not exists(
        select 1 from public.delivery_zones z
        where z.id::text=v_selected_zone
          and z.seller_id=v_seller.seller_id
          and z.is_active
      ) then
        raise exception using errcode='22023', message=format('منطقة التوصيل المختارة غير متاحة لدى %s',v_seller.store_name);
      end if;
    end if;
  end loop;
end;
$function$;


-- function: private.audit_trust_support_change
CREATE OR REPLACE FUNCTION private.audit_trust_support_change()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
begin
  insert into public.audit_logs(actor_id,action,entity_type,entity_id,old_values,new_values,source)
  values ((select auth.uid()),lower(tg_op),tg_table_name,coalesce(new.id,old.id),
    case when tg_op='INSERT' then null else to_jsonb(old) end,
    case when tg_op='DELETE' then null else to_jsonb(new) end,
    'phase5_trust_support');
  return coalesce(new,old);
end; $function$;


-- function: private.checkout_quote_core
CREATE OR REPLACE FUNCTION private.checkout_quote_core(p_items jsonb, p_delivery_zones jsonb, p_lock_products boolean DEFAULT false)
 RETURNS jsonb
 LANGUAGE plpgsql
 SET search_path TO ''
AS $function$
declare
  v_uid uuid := auth.uid();
  v_item jsonb;
  v_product record;
  v_seller record;
  v_product_text text;
  v_quantity_text text;
  v_product_id uuid;
  v_qty int;
  v_total_qty int;
  v_quantities jsonb := '{}'::jsonb;
  v_items jsonb := '[]'::jsonb;
  v_deliveries jsonb := '[]'::jsonb;
  v_subtotal numeric(14,2) := 0;
  v_delivery_total numeric(14,2) := 0;
  v_delivery_fee numeric(14,2);
  v_delivery_area text;
  v_selected_zone_id text;
  v_quote_token text;
begin
  if v_uid is null then
    raise exception using errcode = '28000', message = 'يجب تسجيل الدخول أولاً';
  end if;

  if not exists (
    select 1 from public.profiles p
    where p.id = v_uid and p.is_active = true and p.deleted_at is null
  ) then
    raise exception using errcode = '28000', message = 'الحساب غير نشط';
  end if;

  if jsonb_typeof(coalesce(p_items, '[]'::jsonb)) <> 'array'
     or jsonb_array_length(coalesce(p_items, '[]'::jsonb)) = 0 then
    raise exception using errcode = '22023', message = 'السلة فارغة أو بياناتها غير صحيحة';
  end if;
  if jsonb_array_length(p_items) > 100 then
    raise exception using errcode = '22023', message = 'السلة تحتوي على منتجات كثيرة جداً';
  end if;
  if jsonb_typeof(coalesce(p_delivery_zones, '{}'::jsonb)) <> 'object' then
    raise exception using errcode = '22023', message = 'اختيارات مناطق التوصيل غير صحيحة';
  end if;

  -- Validate before casting and aggregate duplicates before enforcing the limit.
  for v_item in select value from jsonb_array_elements(p_items)
  loop
    if jsonb_typeof(v_item) <> 'object' then
      raise exception using errcode = '22023', message = 'أحد عناصر السلة غير صحيح';
    end if;

    v_product_text := lower(trim(coalesce(v_item ->> 'product_id', '')));
    v_quantity_text := trim(coalesce(v_item ->> 'quantity', ''));

    if v_product_text !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$' then
      raise exception using errcode = '22023', message = 'معرّف أحد المنتجات غير صحيح';
    end if;
    if v_quantity_text !~ '^[0-9]{1,3}$' then
      raise exception using errcode = '22023', message = 'كمية أحد المنتجات غير صحيحة';
    end if;

    v_qty := v_quantity_text::int;
    if v_qty < 1 then
      raise exception using errcode = '22023', message = 'الكمية يجب أن تكون 1 على الأقل';
    end if;

    v_total_qty := coalesce((v_quantities ->> v_product_text)::int, 0) + v_qty;
    if v_total_qty > 99 then
      raise exception using errcode = '22023', message = 'الحد الأقصى للمنتج الواحد هو 99';
    end if;
    v_quantities := jsonb_set(v_quantities, array[v_product_text], to_jsonb(v_total_qty), true);
  end loop;

  for v_product_text, v_quantity_text in
    select key, value from jsonb_each_text(v_quantities) order by key
  loop
    v_product_id := v_product_text::uuid;
    v_qty := v_quantity_text::int;

    if p_lock_products then
      select p.id, p.seller_id, p.name, p.price, p.stock,
             coalesce(st.name, s.store_name) as store_name
      into v_product
      from public.products p
      join public.sellers s on s.id = p.seller_id
      left join public.stores st on st.seller_id = p.seller_id and st.is_active = true
      where p.id = v_product_id
        and p.is_active = true
        and s.verification_status = 'approved'
      for update of p;
    else
      select p.id, p.seller_id, p.name, p.price, p.stock,
             coalesce(st.name, s.store_name) as store_name
      into v_product
      from public.products p
      join public.sellers s on s.id = p.seller_id
      left join public.stores st on st.seller_id = p.seller_id and st.is_active = true
      where p.id = v_product_id
        and p.is_active = true
        and s.verification_status = 'approved';
    end if;

    if not found then
      raise exception using errcode = 'P0001', message = 'أحد المنتجات لم يعد متاحاً';
    end if;
    if v_product.stock < v_qty then
      raise exception using errcode = 'P0001', message = format('الكمية المطلوبة غير متاحة للمنتج: %s', v_product.name);
    end if;

    v_items := v_items || jsonb_build_array(jsonb_build_object(
      'product_id', v_product.id,
      'seller_id', v_product.seller_id,
      'name', v_product.name,
      'store_name', v_product.store_name,
      'unit_price', v_product.price,
      'quantity', v_qty,
      'stock', v_product.stock,
      'available', true,
      'line_total', round(v_product.price * v_qty, 2)
    ));
    v_subtotal := v_subtotal + round(v_product.price * v_qty, 2);
  end loop;

  for v_seller in
    select seller_id::uuid as seller_id,
           max(store_name) as store_name,
           sum(line_total)::numeric(14,2) as subtotal
    from jsonb_to_recordset(v_items)
      as x(seller_id text, store_name text, line_total numeric)
    group by seller_id
    order by seller_id
  loop
    v_delivery_fee := null;
    v_delivery_area := null;

    if exists (
      select 1 from public.delivery_zones z
      where z.seller_id = v_seller.seller_id and z.is_active = true
    ) then
      v_selected_zone_id := nullif(trim(coalesce(p_delivery_zones ->> v_seller.seller_id::text, '')), '');
      if v_selected_zone_id is null then
        raise exception using errcode = '22023', message = format('اختاري منطقة التوصيل من %s', v_seller.store_name);
      end if;

      select z.fee, z.area_name
      into v_delivery_fee, v_delivery_area
      from public.delivery_zones z
      where z.id::text = v_selected_zone_id
        and z.seller_id = v_seller.seller_id
        and z.is_active = true
      limit 1;

      if not found then
        raise exception using errcode = '22023', message = format('منطقة التوصيل المختارة غير متاحة لدى %s', v_seller.store_name);
      end if;
    else
      select d.base_fee, d.delivery_area
      into v_delivery_fee, v_delivery_area
      from public.delivery_settings d
      where d.seller_id = v_seller.seller_id and d.is_active = true
      limit 1;

      if not found then
        raise exception using errcode = 'P0001', message = format('إعدادات التوصيل غير مكتملة لدى %s', v_seller.store_name);
      end if;
    end if;

    v_delivery_fee := round(coalesce(v_delivery_fee, 0), 2);
    v_delivery_total := v_delivery_total + v_delivery_fee;
    v_deliveries := v_deliveries || jsonb_build_array(jsonb_build_object(
      'seller_id', v_seller.seller_id,
      'store_name', v_seller.store_name,
      'subtotal', v_seller.subtotal,
      'fee', v_delivery_fee,
      'area', v_delivery_area
    ));
  end loop;

  v_quote_token := md5(
    v_items::text || '|' || v_deliveries::text || '|' ||
    round(v_subtotal + v_delivery_total, 2)::text
  );

  return jsonb_build_object(
    'subtotal', round(v_subtotal, 2),
    'delivery_total', round(v_delivery_total, 2),
    'discount_total', 0,
    'grand_total', round(v_subtotal + v_delivery_total, 2),
    'items', v_items,
    'deliveries', v_deliveries,
    'warnings', '[]'::jsonb,
    'quote_token', v_quote_token
  );
end;
$function$;


-- function: private.checkout_quote_core_v2
CREATE OR REPLACE FUNCTION private.checkout_quote_core_v2(p_items jsonb, p_delivery_zones jsonb, p_lock_inventory boolean DEFAULT false)
 RETURNS jsonb
 LANGUAGE plpgsql
 SET search_path TO ''
AS $function$
declare
  v_uid uuid := auth.uid();
  v_item jsonb;
  v_product record;
  -- Base-product quotes never assign a variant row. Keep a known tuple shape
  -- so the conditional variant-name expression can safely resolve the field.
  v_variant public.product_variants%rowtype;
  v_seller record;
  v_product_id uuid;
  v_variant_id uuid;
  v_qty int;
  v_unit_price numeric(14,2);
  v_available_stock int;
  v_variant_snapshot jsonb;
  v_items jsonb := '[]'::jsonb;
  v_deliveries jsonb := '[]'::jsonb;
  v_subtotal numeric(14,2) := 0;
  v_delivery_total numeric(14,2) := 0;
  v_delivery_fee numeric(14,2);
  v_delivery_area text;
  v_selected_zone_id text;
  v_quote_token text;
begin
  if v_uid is null then
    raise exception using errcode = '28000', message = 'يجب تسجيل الدخول أولاً';
  end if;
  if not exists (
    select 1 from public.profiles p
    where p.id = v_uid and p.is_active = true and p.deleted_at is null
  ) then
    raise exception using errcode = '28000', message = 'الحساب غير نشط';
  end if;
  if jsonb_typeof(coalesce(p_delivery_zones, '{}'::jsonb)) <> 'object' then
    raise exception using errcode = '22023', message = 'اختيارات مناطق التوصيل غير صحيحة';
  end if;

  for v_item in select value from jsonb_array_elements(private.normalize_checkout_items(p_items))
  loop
    v_product_id := (v_item ->> 'product_id')::uuid;
    v_variant_id := nullif(v_item ->> 'variant_id', '')::uuid;
    v_qty := (v_item ->> 'quantity')::int;

    if p_lock_inventory then
      select p.id, p.seller_id, p.name, p.price, p.stock, p.has_variants,
             coalesce(st.name, s.store_name) as store_name
      into v_product
      from public.products p
      join public.sellers s on s.id = p.seller_id
      left join public.stores st on st.seller_id = p.seller_id and st.is_active = true
      where p.id = v_product_id and p.is_active = true and s.verification_status = 'approved'
      for update of p;
    else
      select p.id, p.seller_id, p.name, p.price, p.stock, p.has_variants,
             coalesce(st.name, s.store_name) as store_name
      into v_product
      from public.products p
      join public.sellers s on s.id = p.seller_id
      left join public.stores st on st.seller_id = p.seller_id and st.is_active = true
      where p.id = v_product_id and p.is_active = true and s.verification_status = 'approved';
    end if;
    if not found then
      raise exception using errcode = 'P0001', message = 'أحد المنتجات لم يعد متاحاً';
    end if;

    v_variant_snapshot := null;
    if v_product.has_variants then
      if v_variant_id is null then
        raise exception using errcode = '22023', message = format('اختاري المقاس أو اللون للمنتج: %s', v_product.name);
      end if;
      if p_lock_inventory then
        select v.id, v.name, v.sku, v.price, v.stock, v.attributes
        into v_variant
        from public.product_variants v
        where v.id = v_variant_id and v.product_id = v_product_id and v.is_active = true
        for update of v;
      else
        select v.id, v.name, v.sku, v.price, v.stock, v.attributes
        into v_variant
        from public.product_variants v
        where v.id = v_variant_id and v.product_id = v_product_id and v.is_active = true;
      end if;
      if not found then
        raise exception using errcode = 'P0001', message = format('الخيار المختار لم يعد متاحاً للمنتج: %s', v_product.name);
      end if;
      v_unit_price := coalesce(v_variant.price, v_product.price);
      v_available_stock := v_variant.stock;
      v_variant_snapshot := jsonb_build_object(
        'id', v_variant.id,
        'name', v_variant.name,
        'sku', v_variant.sku,
        'attributes', v_variant.attributes
      );
    else
      if v_variant_id is not null then
        raise exception using errcode = '22023', message = 'الخيار لا يتبع هذا المنتج';
      end if;
      v_unit_price := v_product.price;
      v_available_stock := v_product.stock;
    end if;

    if v_available_stock < v_qty then
      raise exception using errcode = 'P0001', message = format('الكمية المطلوبة غير متاحة للمنتج: %s', v_product.name);
    end if;

    v_items := v_items || jsonb_build_array(jsonb_build_object(
      'product_id', v_product.id,
      'variant_id', v_variant_id,
      'variant_name', case when v_variant_id is null then null else v_variant.name end,
      'variant_snapshot', v_variant_snapshot,
      'seller_id', v_product.seller_id,
      'name', v_product.name,
      'store_name', v_product.store_name,
      'unit_price', v_unit_price,
      'quantity', v_qty,
      'stock', v_available_stock,
      'available', true,
      'line_total', round(v_unit_price * v_qty, 2)
    ));
    v_subtotal := v_subtotal + round(v_unit_price * v_qty, 2);
  end loop;

  for v_seller in
    select seller_id::uuid as seller_id,
           max(store_name) as store_name,
           sum(line_total)::numeric(14,2) as subtotal
    from jsonb_to_recordset(v_items)
      as x(seller_id text, store_name text, line_total numeric)
    group by seller_id
    order by seller_id
  loop
    v_delivery_fee := null;
    v_delivery_area := null;
    if exists (
      select 1 from public.delivery_zones z
      where z.seller_id = v_seller.seller_id and z.is_active = true
    ) then
      v_selected_zone_id := nullif(trim(coalesce(p_delivery_zones ->> v_seller.seller_id::text, '')), '');
      if v_selected_zone_id is null then
        raise exception using errcode = '22023', message = format('اختاري منطقة التوصيل من %s', v_seller.store_name);
      end if;
      select z.fee, z.area_name into v_delivery_fee, v_delivery_area
      from public.delivery_zones z
      where z.id::text = v_selected_zone_id
        and z.seller_id = v_seller.seller_id
        and z.is_active = true
      limit 1;
      if not found then
        raise exception using errcode = '22023', message = format('منطقة التوصيل المختارة غير متاحة لدى %s', v_seller.store_name);
      end if;
    else
      select d.base_fee, d.delivery_area into v_delivery_fee, v_delivery_area
      from public.delivery_settings d
      where d.seller_id = v_seller.seller_id and d.is_active = true
      limit 1;
      if not found then
        raise exception using errcode = 'P0001', message = format('إعدادات التوصيل غير مكتملة لدى %s', v_seller.store_name);
      end if;
    end if;
    v_delivery_fee := round(coalesce(v_delivery_fee, 0), 2);
    v_delivery_total := v_delivery_total + v_delivery_fee;
    v_deliveries := v_deliveries || jsonb_build_array(jsonb_build_object(
      'seller_id', v_seller.seller_id,
      'store_name', v_seller.store_name,
      'subtotal', v_seller.subtotal,
      'fee', v_delivery_fee,
      'area', v_delivery_area
    ));
  end loop;

  v_quote_token := md5(
    v_items::text || '|' || v_deliveries::text || '|' ||
    round(v_subtotal + v_delivery_total, 2)::text
  );
  return jsonb_build_object(
    'subtotal', round(v_subtotal, 2),
    'delivery_total', round(v_delivery_total, 2),
    'discount_total', 0,
    'grand_total', round(v_subtotal + v_delivery_total, 2),
    'items', v_items,
    'deliveries', v_deliveries,
    'warnings', '[]'::jsonb,
    'quote_token', v_quote_token
  );
end;
$function$;


-- function: private.complete_referral_after_order
CREATE OR REPLACE FUNCTION private.complete_referral_after_order()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
begin
  if new.status='delivered' and old.status is distinct from new.status then
    update public.referrals set status='completed' where referred_id=new.customer_id and status='pending';
  end if;
  return new;
end; $function$;


-- function: private.enforce_checkout_delivery_snapshot
CREATE OR REPLACE FUNCTION private.enforce_checkout_delivery_snapshot()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_area text;
begin
  if new.order_group_id is null then
    return new;
  end if;

  if not exists(
    select 1 from public.delivery_settings d
    where d.seller_id=new.seller_id and d.is_active
  ) then
    raise exception using errcode='P0001', message='التوصيل متوقف مؤقتاً لهذا المتجر';
  end if;

  if exists(select 1 from public.stores st where st.seller_id=new.seller_id)
     and not exists(
       select 1 from public.stores st
       where st.seller_id=new.seller_id and st.is_active and st.is_open
     ) then
    raise exception using errcode='P0001', message='المتجر مغلق حالياً';
  end if;

  if exists(select 1 from public.delivery_zones z where z.seller_id=new.seller_id) then
    v_area := nullif(trim(coalesce(new.address_snapshot->>'delivery_zone','')),'');
    if v_area is null or not exists(
      select 1 from public.delivery_zones z
      where z.seller_id=new.seller_id
        and z.is_active
        and lower(trim(z.area_name))=lower(v_area)
    ) then
      raise exception using errcode='P0001', message='منطقة التوصيل المختارة لم تعد متاحة';
    end if;
  end if;

  return new;
end;
$function$;


-- function: private.enqueue_merchant_verification_notification
CREATE OR REPLACE FUNCTION private.enqueue_merchant_verification_notification()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
begin
  if old.verification_status is distinct from new.verification_status then
    insert into public.notifications(user_id,type,title,body,data,dedupe_key)
    values(new.user_id,'merchant_verification','تحديث حساب التاجر',
      case new.verification_status
        when 'approved' then 'تم اعتماد متجرك على تاني'
        when 'changes_requested' then 'طلب التاجر يحتاج تعديلات قبل الاعتماد'
        when 'rejected' then 'تمت مراجعة طلب التاجر ولم يتم اعتماده'
        when 'suspended' then 'تم تعليق حساب التاجر مؤقتاً'
        else 'تم تحديث حالة طلب التاجر' end,
      jsonb_build_object('merchant_id',new.id,'status',new.verification_status),
      'merchant_verification:'||new.id::text||':'||new.verification_status)
    on conflict (user_id,dedupe_key) where dedupe_key is not null do nothing;
  end if;
  return new;
end; $function$;


-- function: private.enqueue_new_order_notification
CREATE OR REPLACE FUNCTION private.enqueue_new_order_notification()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v_uid uuid;
  v_store text;
begin
  select user_id into v_uid from public.sellers where id=new.seller_id;
  if v_uid is null then
    return new;
  end if;

  v_store := coalesce(nullif(new.store_name_snapshot,''), 'متجرك');
  insert into public.notifications(user_id,type,title,body,data,dedupe_key)
  values(
    v_uid,
    'new_order',
    'طلب جديد',
    'وصلك طلب جديد في '||v_store||'. افتحي الطلب لمراجعته.',
    jsonb_build_object(
      'order_id',new.id,
      'order_group_id',new.order_group_id,
      'seller_id',new.seller_id,
      'status',new.status
    ),
    'new_order:'||new.id
  )
  on conflict(user_id,dedupe_key) where dedupe_key is not null do nothing;
  return new;
end
$function$;


-- function: private.enqueue_order_notification
CREATE OR REPLACE FUNCTION private.enqueue_order_notification()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v_order public.orders%rowtype;
  v_customer_title text;
  v_merchant_title text;
  v_merchant_body text;
  v_merchant uuid;
begin
  select * into v_order from public.orders where id=new.order_id;
  if v_order.id is null then
    return new;
  end if;

  v_customer_title := case new.to_status
    when 'accepted' then 'تم قبول طلبك'
    when 'preparing' then 'طلبك قيد التجهيز'
    when 'ready' then 'طلبك جاهز'
    when 'out_for_delivery' then 'طلبك خرج للتوصيل'
    when 'delivered' then 'تم تسليم طلبك'
    when 'cancelled' then 'تم إلغاء الطلب'
    when 'rejected' then 'تعذر قبول الطلب'
    else 'تحديث على طلبك'
  end;

  insert into public.notifications(user_id,type,title,body,data,dedupe_key)
  values(
    v_order.customer_id,
    'order_update',
    v_customer_title,
    coalesce(v_order.store_name_snapshot,'المتجر')||' — '||v_customer_title,
    jsonb_build_object(
      'order_id',v_order.id,
      'order_group_id',v_order.order_group_id,
      'status',new.to_status
    ),
    'order:'||v_order.id||':'||new.to_status
  )
  on conflict(user_id,dedupe_key) where dedupe_key is not null do nothing;

  select user_id into v_merchant from public.sellers where id=v_order.seller_id;
  if v_merchant is not null and v_merchant<>v_order.customer_id then
    v_merchant_title := case new.to_status
      when 'accepted' then 'تم قبول الطلب'
      when 'preparing' then 'الطلب قيد التجهيز'
      when 'ready' then 'الطلب جاهز'
      when 'out_for_delivery' then 'الطلب خرج للتوصيل'
      when 'delivered' then 'تم تسليم الطلب'
      when 'cancelled' then 'تم إلغاء الطلب'
      when 'rejected' then 'تم رفض الطلب'
      else 'تحديث على الطلب'
    end;
    v_merchant_body := v_merchant_title||' — افتحي الطلب لمراجعة التفاصيل.';

    insert into public.notifications(user_id,type,title,body,data,dedupe_key)
    values(
      v_merchant,
      'merchant_order_update',
      v_merchant_title,
      v_merchant_body,
      jsonb_build_object(
        'order_id',v_order.id,
        'order_group_id',v_order.order_group_id,
        'seller_id',v_order.seller_id,
        'status',new.to_status
      ),
      'merchant_order:'||v_order.id||':'||new.to_status
    )
    on conflict(user_id,dedupe_key) where dedupe_key is not null do nothing;
  end if;
  return new;
end
$function$;


-- function: private.enqueue_stock_alert
CREATE OR REPLACE FUNCTION private.enqueue_stock_alert()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_user uuid;
begin
  if new.stock<=3 and (old.stock is null or old.stock>new.stock) then
    select user_id into v_user from public.sellers where id=new.seller_id;
    if v_user is not null then
      insert into public.notifications(user_id,type,title,body,data,dedupe_key)
      values(v_user,'stock_alert','تنبيه مخزون',new.name||' — المتبقي '||new.stock,
        jsonb_build_object('product_id',new.id,'stock',new.stock),
        'stock:'||new.id::text||':'||new.stock::text)
      on conflict (user_id,dedupe_key) where dedupe_key is not null do nothing;
    end if;
  end if;
  return new;
end; $function$;


-- function: private.handle_new_user
CREATE OR REPLACE FUNCTION private.handle_new_user()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin
  insert into public.profiles (id, name, phone, email, role, created_at, updated_at, is_active)
  values (
    new.id,
    trim(coalesce(new.raw_user_meta_data ->> 'name', '')),
    trim(coalesce(new.raw_user_meta_data ->> 'phone', '')),
    lower(new.email),
    'customer',
    now(),
    now(),
    true
  )
  on conflict (id) do update
  set name = excluded.name,
      phone = excluded.phone,
      email = excluded.email,
      updated_at = now();
  return new;
end;
$function$;


-- function: private.normalize_checkout_items
CREATE OR REPLACE FUNCTION private.normalize_checkout_items(p_items jsonb)
 RETURNS jsonb
 LANGUAGE plpgsql
 SET search_path TO ''
AS $function$
declare
  v_item jsonb;
  v_product_text text;
  v_variant_text text;
  v_quantity_text text;
  v_key text;
  v_qty int;
  v_total int;
  v_quantities jsonb := '{}'::jsonb;
  v_result jsonb := '[]'::jsonb;
begin
  if jsonb_typeof(coalesce(p_items, '[]'::jsonb)) <> 'array'
     or jsonb_array_length(coalesce(p_items, '[]'::jsonb)) = 0 then
    raise exception using errcode = '22023', message = 'السلة فارغة أو بياناتها غير صحيحة';
  end if;
  if jsonb_array_length(p_items) > 100 then
    raise exception using errcode = '22023', message = 'السلة تحتوي على منتجات كثيرة جداً';
  end if;

  for v_item in select value from jsonb_array_elements(p_items)
  loop
    if jsonb_typeof(v_item) <> 'object' then
      raise exception using errcode = '22023', message = 'أحد عناصر السلة غير صحيح';
    end if;
    v_product_text := lower(trim(coalesce(v_item ->> 'product_id', '')));
    v_variant_text := lower(trim(coalesce(v_item ->> 'variant_id', '')));
    v_quantity_text := trim(coalesce(v_item ->> 'quantity', ''));

    if v_product_text !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$' then
      raise exception using errcode = '22023', message = 'معرّف أحد المنتجات غير صحيح';
    end if;
    if v_variant_text <> '' and v_variant_text !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$' then
      raise exception using errcode = '22023', message = 'معرّف خيار المنتج غير صحيح';
    end if;
    if v_quantity_text !~ '^[0-9]{1,3}$' then
      raise exception using errcode = '22023', message = 'كمية أحد المنتجات غير صحيحة';
    end if;

    v_qty := v_quantity_text::int;
    if v_qty < 1 then
      raise exception using errcode = '22023', message = 'الكمية يجب أن تكون 1 على الأقل';
    end if;
    v_key := v_product_text || '|' || v_variant_text;
    v_total := coalesce((v_quantities ->> v_key)::int, 0) + v_qty;
    if v_total > 99 then
      raise exception using errcode = '22023', message = 'الحد الأقصى للخيار الواحد هو 99';
    end if;
    v_quantities := jsonb_set(v_quantities, array[v_key], to_jsonb(v_total), true);
  end loop;

  for v_key, v_quantity_text in
    select key, value from jsonb_each_text(v_quantities) order by key
  loop
    v_result := v_result || jsonb_build_array(jsonb_build_object(
      'product_id', split_part(v_key, '|', 1),
      'variant_id', nullif(split_part(v_key, '|', 2), ''),
      'quantity', v_quantity_text::int
    ));
  end loop;
  return v_result;
end;
$function$;


-- function: private.normalize_default_address
CREATE OR REPLACE FUNCTION private.normalize_default_address()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO ''
AS $function$
begin
  if new.is_default then
    update public.addresses
    set is_default = false, updated_at = now()
    where user_id = new.user_id
      and id <> new.id
      and is_default = true;
  end if;
  return new;
end;
$function$;


-- function: private.on_order_status_changed
CREATE OR REPLACE FUNCTION private.on_order_status_changed()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin
  if new.status is distinct from old.status and new.order_group_id is not null then
    perform private.refresh_order_group_status(new.order_group_id);
  end if;
  return new;
end;
$function$;


-- function: private.protect_merchant_verification_fields
CREATE OR REPLACE FUNCTION private.protect_merchant_verification_fields()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO ''
AS $function$
declare
  old_row jsonb := to_jsonb(old);
  new_row jsonb := to_jsonb(new);
  v_workflow text := current_setting('tani.merchant_workflow', true);
begin
  if auth.uid() is not null
     and public.current_user_role() <> 'admin'
     and coalesce(v_workflow,'') <> 'submit' then
    if (new_row -> 'verification_status') is distinct from (old_row -> 'verification_status')
       or (new_row -> 'trust_badge') is distinct from (old_row -> 'trust_badge')
       or (new_row -> 'approved_at') is distinct from (old_row -> 'approved_at')
       or (new_row -> 'suspended_at') is distinct from (old_row -> 'suspended_at')
       or (new_row -> 'seller_id') is distinct from (old_row -> 'seller_id') then
      raise exception 'Protected merchant verification fields cannot be changed by this account';
    end if;
  end if;
  return new;
end;
$function$;


-- function: private.refresh_merchant_metrics
CREATE OR REPLACE FUNCTION private.refresh_merchant_metrics(p_seller_id uuid)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v_total int:=0; v_completed int:=0; v_cancelled int:=0; v_sales numeric:=0;
  v_rating numeric:=null; v_views int:=0; v_repeat int:=0; v_conversion numeric:=0;
  v_cancel_rate numeric:=0; v_response numeric:=null;
begin
  if p_seller_id is null then return; end if;
  select count(*),count(*) filter(where status='delivered'),
         count(*) filter(where status in ('cancelled','rejected')),
         coalesce(sum(total) filter(where status='delivered'),0)
  into v_total,v_completed,v_cancelled,v_sales from public.orders where seller_id=p_seller_id;
  if to_regclass('public.merchant_reviews') is not null then
    select avg(rating) into v_rating from public.merchant_reviews where seller_id=p_seller_id and status='published';
  end if;
  select count(*) into v_views from public.app_events e join public.products p on p.id=e.entity_id
  where e.event_name='product_view' and e.entity_type='product' and p.seller_id=p_seller_id;
  select count(*) into v_repeat from (
    select customer_id from public.orders where seller_id=p_seller_id and status='delivered'
    group by customer_id having count(*)>1
  ) q;
  if v_views>0 then v_conversion:=round((v_total::numeric/v_views)*100,2); end if;
  if v_total>0 then v_cancel_rate:=round((v_cancelled::numeric/v_total)*100,2); end if;
  select avg(extract(epoch from (h.created_at-o.created_at))/60.0) into v_response
  from public.orders o
  join lateral (
    select min(created_at) created_at from public.order_status_history
    where order_id=o.id and to_status='accepted'
  ) h on h.created_at is not null
  where o.seller_id=p_seller_id;
  insert into public.merchant_metrics(
    seller_id,total_orders,completed_orders,cancelled_orders,total_sales,average_rating,
    product_views,repeat_customers,conversion_rate,cancellation_rate,avg_response_minutes,updated_at
  ) values(
    p_seller_id,v_total,v_completed,v_cancelled,v_sales,v_rating,v_views,v_repeat,
    v_conversion,v_cancel_rate,v_response,now()
  )
  on conflict(seller_id) do update set
    total_orders=excluded.total_orders,completed_orders=excluded.completed_orders,
    cancelled_orders=excluded.cancelled_orders,total_sales=excluded.total_sales,
    average_rating=excluded.average_rating,product_views=excluded.product_views,
    repeat_customers=excluded.repeat_customers,conversion_rate=excluded.conversion_rate,
    cancellation_rate=excluded.cancellation_rate,avg_response_minutes=excluded.avg_response_minutes,
    updated_at=now();
end; $function$;


-- function: private.refresh_merchant_trust
CREATE OR REPLACE FUNCTION private.refresh_merchant_trust(p_seller_id uuid)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v_completed integer := 0; v_cancelled integer := 0; v_reviews integer := 0;
  v_rating numeric := null; v_open_complaints integer := 0; v_completion numeric := 0;
  v_score numeric := 50; v_level text := 'verified';
begin
  if p_seller_id is null then return; end if;
  select count(*) filter (where status='delivered'),
         count(*) filter (where status in ('cancelled','rejected'))
  into v_completed,v_cancelled from public.orders where seller_id=p_seller_id;
  select count(*),avg(rating)::numeric(3,2) into v_reviews,v_rating
    from public.merchant_reviews where seller_id=p_seller_id and status='published';
  select count(*) into v_open_complaints from public.complaints
    where seller_id=p_seller_id and status in ('open','in_progress');
  if (v_completed+v_cancelled)>0 then
    v_completion := round((v_completed::numeric/(v_completed+v_cancelled))*100,2);
  end if;
  v_score := 50 + least(v_completion*0.25,25)
    + case when v_rating is null then 0 else greatest(least((v_rating-3)*8,16),-16) end
    + least(v_completed,20)*0.5 - least(v_open_complaints,10)*4;
  v_score := greatest(0,least(100,round(v_score,2)));
  if v_open_complaints>=5 or v_score<35 then v_level := 'restricted';
  elsif v_completed>=30 and coalesce(v_rating,0)>=4.5 and v_score>=85 then v_level := 'high_performing';
  elsif v_completed>=8 and coalesce(v_rating,0)>=4.0 and v_score>=70 then v_level := 'trusted';
  else v_level := 'verified'; end if;
  insert into public.merchant_trust_scores(
    seller_id,trust_level,trust_score,completed_orders,cancelled_orders,review_count,
    average_rating,open_complaints,completion_rate,updated_at
  ) values (
    p_seller_id,v_level,v_score,v_completed,v_cancelled,v_reviews,v_rating,
    v_open_complaints,v_completion,now()
  )
  on conflict (seller_id) do update set
    trust_level=excluded.trust_level, trust_score=excluded.trust_score,
    completed_orders=excluded.completed_orders, cancelled_orders=excluded.cancelled_orders,
    review_count=excluded.review_count, average_rating=excluded.average_rating,
    open_complaints=excluded.open_complaints, completion_rate=excluded.completion_rate, updated_at=now();
end; $function$;


-- function: private.refresh_metrics_from_event
CREATE OR REPLACE FUNCTION private.refresh_metrics_from_event()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_seller uuid;
begin
  if new.event_name='product_view' and new.entity_type='product' and new.entity_id is not null then
    select seller_id into v_seller from public.products where id=new.entity_id;
    perform private.refresh_merchant_metrics(v_seller);
  end if;
  return new;
end; $function$;


-- function: private.refresh_metrics_from_order
CREATE OR REPLACE FUNCTION private.refresh_metrics_from_order()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
begin perform private.refresh_merchant_metrics(coalesce(new.seller_id,old.seller_id)); return coalesce(new,old); end; $function$;


-- function: private.refresh_order_group_status
CREATE OR REPLACE FUNCTION private.refresh_order_group_status(p_group_id uuid)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_total int;
  v_pending int;
  v_delivered int;
  v_cancelled int;
  v_out int;
  v_working int;
  v_accepted int;
  v_unpaid int;
  v_status text;
begin
  select
    count(*)::int,
    count(*) filter (where status = 'pending')::int,
    count(*) filter (where status = 'delivered')::int,
    count(*) filter (where status in ('cancelled','rejected','failed'))::int,
    count(*) filter (where status = 'out_for_delivery')::int,
    count(*) filter (where status in ('preparing','ready'))::int,
    count(*) filter (where status = 'accepted')::int,
    count(*) filter (where payment_status <> 'paid')::int
  into v_total, v_pending, v_delivered, v_cancelled, v_out, v_working, v_accepted, v_unpaid
  from public.orders
  where order_group_id = p_group_id;

  if v_total = 0 then
    return;
  elsif v_delivered + v_cancelled = v_total then
    v_status := case when v_delivered > 0 then 'delivered' else 'cancelled' end;
  elsif v_out > 0 then
    v_status := 'out_for_delivery';
  elsif v_working > 0 or v_delivered > 0 then
    v_status := 'processing';
  elsif v_pending > 0 then
    v_status := 'pending';
  elsif v_accepted > 0 then
    v_status := 'confirmed';
  else
    v_status := 'pending';
  end if;

  update public.order_groups
  set status = v_status,
      payment_status = case
        when v_status = 'delivered' and v_unpaid = 0 then 'paid'
        else payment_status
      end,
      updated_at = now()
  where id = p_group_id;
end;
$function$;


-- function: private.refresh_product_has_variants
CREATE OR REPLACE FUNCTION private.refresh_product_has_variants()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_product_id uuid := coalesce(new.product_id, old.product_id);
begin
  update public.products p
  set has_variants = exists (
        select 1 from public.product_variants v where v.product_id = v_product_id
      ),
      updated_at = now()
  where p.id = v_product_id;

  if tg_op = 'UPDATE' and old.product_id is distinct from new.product_id then
    update public.products p
    set has_variants = exists (
          select 1 from public.product_variants v where v.product_id = old.product_id
        ),
        updated_at = now()
    where p.id = old.product_id;
  end if;
  if tg_op = 'DELETE' then
    return old;
  end if;

  return new;
end;
$function$;


-- function: private.refresh_trust_from_complaint
CREATE OR REPLACE FUNCTION private.refresh_trust_from_complaint()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
begin perform private.refresh_merchant_trust(coalesce(new.seller_id,old.seller_id)); return coalesce(new,old); end; $function$;


-- function: private.refresh_trust_from_merchant_review
CREATE OR REPLACE FUNCTION private.refresh_trust_from_merchant_review()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
begin perform private.refresh_merchant_trust(coalesce(new.seller_id,old.seller_id)); return coalesce(new,old); end; $function$;


-- function: private.refresh_trust_from_order
CREATE OR REPLACE FUNCTION private.refresh_trust_from_order()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
begin perform private.refresh_merchant_trust(coalesce(new.seller_id,old.seller_id)); return coalesce(new,old); end; $function$;


-- function: private.sync_merchant_delivery_zones
CREATE OR REPLACE FUNCTION private.sync_merchant_delivery_zones()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin
  if new.verification_status='approved' and new.seller_id is not null then
    delete from public.delivery_zones where seller_id=new.seller_id;
    insert into public.delivery_zones(
      seller_id,area_name,fee,estimated_minutes,sort_order,is_active,created_at,updated_at
    )
    select
      new.seller_id,
      trim(z.value->>'area'),
      (z.value->>'fee')::numeric,
      nullif(z.value->>'estimated_minutes','')::integer,
      coalesce((z.value->>'sort_order')::integer,z.ordinality::integer-1),
      case lower(coalesce(z.value->>'is_active','true'))
        when 'false' then false
        else true
      end,
      now(),
      now()
    from jsonb_array_elements(new.delivery_zones) with ordinality as z(value,ordinality);
  end if;
  return new;
end;
$function$;


-- function: private.validate_store_seller_link
CREATE OR REPLACE FUNCTION private.validate_store_seller_link()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO ''
AS $function$
begin
  if public.current_user_role() = 'admin' then
    return new;
  end if;

  if new.seller_id is null then
    return new;
  end if;

  if not exists (
    select 1
    from public.merchant_profiles mp
    join public.sellers s on s.id = mp.seller_id
    where mp.id = new.merchant_id
      and mp.user_id = auth.uid()
      and s.id = new.seller_id
      and s.user_id = auth.uid()
  ) then
    raise exception 'Store seller link is not owned by this account';
  end if;

  return new;
end;
$function$;


-- function: private.write_audit_log
CREATE OR REPLACE FUNCTION private.write_audit_log()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  row_data jsonb;
  row_id uuid;
begin
  row_data := case when tg_op = 'DELETE' then to_jsonb(old) else to_jsonb(new) end;
  row_id := nullif(row_data ->> 'id','')::uuid;

  insert into public.audit_logs(actor_id, action, entity_type, entity_id, source)
  values (
    auth.uid(),
    lower(tg_op),
    tg_table_name,
    row_id,
    'db_trigger'
  );

  return case when tg_op = 'DELETE' then old else new end;
end;
$function$;


-- function: public.admin_list_staff
CREATE OR REPLACE FUNCTION public.admin_list_staff()
 RETURNS TABLE(user_id uuid, email text, name text, phone text, role text, is_active boolean, created_at timestamp with time zone)
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
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
$function$;


-- function: public.admin_review_featured_request
CREATE OR REPLACE FUNCTION public.admin_review_featured_request(p_request_id uuid, p_status text, p_note text DEFAULT NULL::text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_req public.featured_requests%rowtype; v_placement_id uuid;
begin
 if public.current_user_role()<>'admin' then raise exception 'admin required'; end if;
 if p_status not in ('approved','rejected') then raise exception 'invalid status'; end if;
 select * into v_req from public.featured_requests where id=p_request_id for update;
 if v_req.id is null then raise exception 'request not found'; end if;
 if v_req.status<>'pending' then raise exception 'request already reviewed'; end if;
 if p_status='approved' then
   insert into public.featured_placements(product_id,seller_id,placement,starts_at,ends_at,is_active,is_sponsored,sponsored_label,request_id,priority)
   values(v_req.product_id,v_req.seller_id,v_req.placement,coalesce(v_req.requested_starts_at,now()),v_req.requested_ends_at,true,true,'ممول',v_req.id,0)
   returning id into v_placement_id;
 end if;
 update public.featured_requests set status=p_status,admin_note=p_note,reviewed_at=now(),reviewed_by=(select auth.uid()) where id=p_request_id;
 return v_placement_id;
end; $function$;


-- function: public.admin_review_merchant_application
CREATE OR REPLACE FUNCTION public.admin_review_merchant_application(p_merchant_id uuid, p_status text, p_note text DEFAULT NULL::text)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_uid uuid := auth.uid();
  v_mp public.merchant_profiles%rowtype;
  v_seller_id uuid;
begin
  if not exists(
    select 1 from public.profiles p
    where p.id=v_uid and p.role='admin' and p.is_active=true and p.deleted_at is null
  ) then
    raise exception 'Admin permission required';
  end if;

  if p_status not in ('approved','rejected','changes_requested','suspended') then
    raise exception 'Invalid review status';
  end if;
  if char_length(coalesce(p_note,'')) > 1000 then
    raise exception 'Review note is too long';
  end if;

  select * into v_mp
  from public.merchant_profiles
  where id=p_merchant_id
  for update;

  if not found then raise exception 'Merchant application not found'; end if;

  if p_status='approved' then
    if v_mp.policies_accepted_at is null
       or v_mp.category_id is null
       or coalesce(trim(v_mp.store_name),'')=''
       or coalesce(trim(v_mp.delivery_area),'')=''
       or coalesce(trim(v_mp.phone),'')='' then
      raise exception 'Merchant application is incomplete';
    end if;

    if not exists(
      select 1
      from public.merchant_identity_documents d
      join storage.objects o
        on o.bucket_id='merchant-private' and o.name=d.storage_path
      where d.merchant_id=v_mp.id
        and d.user_id=v_mp.user_id
        and o.owner_id=v_mp.user_id::text
    ) then
      raise exception 'Merchant identity document is missing';
    end if;

    insert into public.sellers(user_id,store_name,verification_status,created_at)
    values(v_mp.user_id,v_mp.store_name,'approved',now())
    on conflict(user_id) do update
      set store_name=excluded.store_name,verification_status='approved'
    returning id into v_seller_id;

    update public.merchant_profiles
    set seller_id=v_seller_id,
        verification_status='approved',
        phone_verified_at=coalesce(phone_verified_at,now()),
        approved_at=coalesce(approved_at,now()),
        suspended_at=null,
        review_note=nullif(trim(coalesce(p_note,'')),''),
        requested_changes_at=null,
        updated_at=now()
    where id=v_mp.id;

    insert into public.stores(
      merchant_id,seller_id,category_id,name,description,city,area,
      contact_phone,whatsapp,is_open,is_active,created_at,updated_at
    )
    values(
      v_mp.id,v_seller_id,v_mp.category_id,v_mp.store_name,
      coalesce(v_mp.store_description,v_mp.description),v_mp.city,v_mp.area,
      v_mp.phone,v_mp.whatsapp,true,true,now(),now()
    )
    on conflict(merchant_id) do update set
      seller_id=excluded.seller_id,
      category_id=excluded.category_id,
      name=excluded.name,
      description=excluded.description,
      city=excluded.city,
      area=excluded.area,
      contact_phone=excluded.contact_phone,
      whatsapp=excluded.whatsapp,
      is_active=true,
      updated_at=now();

    insert into public.delivery_settings(
      seller_id,base_fee,delivery_area,estimated_minutes,is_active,created_at,updated_at
    )
    values(
      v_seller_id,v_mp.delivery_fee,v_mp.delivery_area,v_mp.estimated_minutes,true,now(),now()
    )
    on conflict(seller_id) do update set
      base_fee=excluded.base_fee,
      delivery_area=excluded.delivery_area,
      estimated_minutes=excluded.estimated_minutes,
      is_active=true,
      updated_at=now();

    update public.profiles
    set role='seller',updated_at=now()
    where id=v_mp.user_id;

    update public.merchant_identity_documents
    set reviewed_at=now()
    where merchant_id=v_mp.id;

  elsif p_status='suspended' then
    update public.merchant_profiles
    set verification_status='suspended',suspended_at=now(),
        review_note=nullif(trim(coalesce(p_note,'')),''),updated_at=now()
    where id=v_mp.id;

    update public.sellers set verification_status='suspended'
    where id=v_mp.seller_id;
    update public.stores set is_active=false,updated_at=now()
    where merchant_id=v_mp.id;
    update public.products set is_active=false,updated_at=now()
    where seller_id=v_mp.seller_id;

  else
    update public.merchant_profiles
    set verification_status=p_status,
        review_note=nullif(trim(coalesce(p_note,'')),''),
        requested_changes_at=case when p_status='changes_requested' then now() else requested_changes_at end,
        updated_at=now()
    where id=v_mp.id;

    if v_mp.seller_id is not null and p_status='rejected' then
      update public.sellers set verification_status='rejected'
      where id=v_mp.seller_id;
    end if;
  end if;

  insert into public.audit_logs(actor_id,action,entity_type,entity_id,new_values,source)
  values(
    v_uid,'merchant_review','merchant_profile',v_mp.id,
    jsonb_build_object('status',p_status,'note',p_note),'admin'
  );
end;
$function$;


-- function: public.admin_review_subscription_request
CREATE OR REPLACE FUNCTION public.admin_review_subscription_request(p_request_id uuid, p_status text, p_note text DEFAULT NULL::text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_req public.subscription_requests%rowtype; v_plan public.subscription_plans%rowtype; v_subscription_id uuid;
begin
 if public.current_user_role()<>'admin' then raise exception 'admin required'; end if;
 if p_status not in ('approved','rejected') then raise exception 'invalid status'; end if;
 select * into v_req from public.subscription_requests where id=p_request_id for update;
 if v_req.id is null then raise exception 'request not found'; end if;
 if v_req.status<>'pending' then raise exception 'request already reviewed'; end if;
 select * into v_plan from public.subscription_plans where id=v_req.plan_id and is_active;
 if v_plan.id is null then raise exception 'plan not available'; end if;
 if p_status='approved' then
   update public.subscriptions set status='expired',updated_at=now() where seller_id=v_req.seller_id and status='active';
   insert into public.subscriptions(user_id,seller_id,plan_id,status,starts_at,ends_at,source,auto_renew)
   values(v_req.user_id,v_req.seller_id,v_req.plan_id,'active',now(),now()+make_interval(days=>v_plan.duration_days),'admin',false)
   returning id into v_subscription_id;
 end if;
 update public.subscription_requests set status=p_status,admin_note=p_note,reviewed_at=now(),reviewed_by=(select auth.uid()) where id=p_request_id;
 return v_subscription_id;
end; $function$;


-- function: public.admin_set_account_active
CREATE OR REPLACE FUNCTION public.admin_set_account_active(p_user_id uuid, p_active boolean)
 RETURNS void
 LANGUAGE plpgsql
 SET search_path TO ''
AS $function$
begin
  if not exists (
    select 1 from public.profiles p
    where p.id = auth.uid()
      and p.role = 'admin'
      and p.is_active = true
      and p.deleted_at is null
  ) then
    raise exception 'Admin permission required';
  end if;

  if p_user_id = auth.uid() and p_active = false then
    raise exception 'Admin cannot deactivate own account';
  end if;

  update public.profiles
  set is_active = p_active,
      deleted_at = case when p_active then null else now() end,
      updated_at = now()
  where id = p_user_id;

  if not found then
    raise exception 'User not found';
  end if;
end;
$function$;


-- function: public.admin_set_category_active
CREATE OR REPLACE FUNCTION public.admin_set_category_active(p_category_id uuid, p_active boolean)
 RETURNS boolean
 LANGUAGE plpgsql
 SET search_path TO ''
AS $function$
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
$function$;


-- function: public.admin_set_merchant_status
CREATE OR REPLACE FUNCTION public.admin_set_merchant_status(p_merchant_id uuid, p_status text)
 RETURNS void
 LANGUAGE plpgsql
 SET search_path TO ''
AS $function$
begin
  perform public.admin_review_merchant_application(p_merchant_id,p_status,null);
end;
$function$;


-- function: public.admin_set_product_active
CREATE OR REPLACE FUNCTION public.admin_set_product_active(p_product_id uuid, p_active boolean, p_reason text DEFAULT NULL::text)
 RETURNS boolean
 LANGUAGE plpgsql
 SET search_path TO ''
AS $function$
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
$function$;


-- function: public.admin_set_user_role_by_email
CREATE OR REPLACE FUNCTION public.admin_set_user_role_by_email(p_email text, p_role text)
 RETURNS TABLE(user_id uuid, email text, name text, phone text, role text, is_active boolean, created_at timestamp with time zone)
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
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
$function$;


-- function: public.apply_referral_code
CREATE OR REPLACE FUNCTION public.apply_referral_code(p_code text)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_uid uuid := (select auth.uid()); v_referrer uuid; v_code text := upper(trim(p_code));
begin
  if v_uid is null then raise exception 'authentication required'; end if;
  select user_id into v_referrer from public.referral_codes where upper(code)=v_code;
  if v_referrer is null then raise exception 'invalid referral code'; end if;
  if v_referrer=v_uid then raise exception 'cannot refer yourself'; end if;
  if exists(select 1 from public.referrals where referred_id=v_uid) then return false; end if;
  insert into public.referrals(referrer_id,referred_id,code,status) values(v_referrer,v_uid,v_code,'pending');
  return true;
end; $function$;


-- function: public.checkout_create_order_group
CREATE OR REPLACE FUNCTION public.checkout_create_order_group(p_address_id uuid, p_phone text, p_notes text, p_items jsonb, p_idempotency_key text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_uid uuid := auth.uid();
  v_existing uuid;
  v_group_id uuid;
  v_order_id uuid;
  v_item jsonb;
  v_line jsonb;
  v_lines jsonb := '[]'::jsonb;
  v_product record;
  v_seller record;
  v_address record;
  v_profile record;
  v_product_id uuid;
  v_qty int;
  v_subtotal numeric := 0;
  v_delivery_total numeric := 0;
  v_delivery_fee numeric;
  v_address_text text;
begin
  if v_uid is null then
    raise exception 'Authentication required';
  end if;

  select p.id, p.name
  into v_profile
  from public.profiles p
  where p.id = v_uid and p.is_active = true and p.deleted_at is null;

  if not found then
    raise exception 'Account is not active';
  end if;

  if coalesce(char_length(trim(p_idempotency_key)), 0) < 16
     or char_length(trim(p_idempotency_key)) > 100 then
    raise exception 'Invalid checkout key';
  end if;

  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended(v_uid::text || ':' || trim(p_idempotency_key), 0)
  );

  select og.id into v_existing
  from public.order_groups og
  where og.customer_id = v_uid
    and og.idempotency_key = trim(p_idempotency_key)
  limit 1;

  if found then
    return v_existing;
  end if;

  select a.*
  into v_address
  from public.addresses a
  where a.id = p_address_id and a.user_id = v_uid;

  if not found then
    raise exception 'Address not found';
  end if;

  if coalesce(char_length(trim(p_phone)), 0) < 7
     or char_length(trim(p_phone)) > 30 then
    raise exception 'Invalid phone number';
  end if;

  if char_length(coalesce(p_notes, '')) > 1000 then
    raise exception 'Order notes are too long';
  end if;

  if jsonb_typeof(coalesce(p_items, '[]'::jsonb)) <> 'array'
     or jsonb_array_length(coalesce(p_items, '[]'::jsonb)) = 0 then
    raise exception 'Cart is empty';
  end if;

  if jsonb_array_length(p_items) > 100 then
    raise exception 'Cart has too many items';
  end if;

  v_address_text := concat_ws(
    ' - ',
    nullif(trim(v_address.label), ''),
    nullif(trim(v_address.description), ''),
    nullif(trim(coalesce(v_address.area, '')), ''),
    nullif(trim(coalesce(v_address.landmark, '')), '')
  );

  for v_item in
    select jsonb_build_object(
      'product_id', product_id::text,
      'quantity', sum(quantity)::int
    )
    from (
      select
        (x.value ->> 'product_id')::uuid as product_id,
        greatest(1, least(99, coalesce((x.value ->> 'quantity')::int, 1))) as quantity
      from jsonb_array_elements(p_items) x(value)
      where x.value ? 'product_id'
    ) q
    group by product_id
    order by product_id
  loop
    v_product_id := (v_item ->> 'product_id')::uuid;
    v_qty := (v_item ->> 'quantity')::int;

    select
      p.id,
      p.seller_id,
      p.name,
      p.price,
      p.stock,
      coalesce(st.name, s.store_name) as store_name
    into v_product
    from public.products p
    join public.sellers s on s.id = p.seller_id
    left join public.stores st on st.seller_id = p.seller_id and st.is_active = true
    where p.id = v_product_id
      and p.is_active = true
      and s.verification_status = 'approved'
    for update of p;

    if not found then
      raise exception 'A product in your cart is no longer available';
    end if;

    if v_product.stock < v_qty then
      raise exception 'Insufficient stock for %', v_product.name;
    end if;

    v_lines := v_lines || jsonb_build_array(
      jsonb_build_object(
        'product_id', v_product.id::text,
        'seller_id', v_product.seller_id::text,
        'product_name', v_product.name,
        'store_name', v_product.store_name,
        'unit_price', v_product.price,
        'quantity', v_qty,
        'line_total', v_product.price * v_qty
      )
    );

    v_subtotal := v_subtotal + (v_product.price * v_qty);
  end loop;

  if jsonb_array_length(v_lines) = 0 then
    raise exception 'Cart is empty';
  end if;

  insert into public.order_groups(
    customer_id, subtotal, delivery_total, discount_total, grand_total,
    payment_method, payment_status, status, idempotency_key,
    address_id, address_snapshot, customer_note, created_at, updated_at
  )
  values(
    v_uid, v_subtotal, 0, 0, v_subtotal,
    'cod', 'pending', 'pending', trim(p_idempotency_key),
    p_address_id,
    jsonb_build_object(
      'label', v_address.label,
      'description', v_address.description,
      'area', v_address.area,
      'landmark', v_address.landmark,
      'phone', coalesce(nullif(trim(p_phone), ''), v_address.phone),
      'delivery_notes', v_address.delivery_notes
    ),
    nullif(trim(coalesce(p_notes, '')), ''),
    now(), now()
  )
  returning id into v_group_id;

  for v_seller in
    select
      seller_id::uuid as seller_id,
      max(store_name) as store_name,
      sum(line_total)::numeric as subtotal
    from jsonb_to_recordset(v_lines)
      as x(seller_id text, store_name text, line_total numeric)
    group by seller_id
    order by seller_id
  loop
    select d.base_fee
    into v_delivery_fee
    from public.delivery_settings d
    where d.seller_id = v_seller.seller_id
      and d.is_active = true
    limit 1;

    if not found then
      raise exception 'Delivery settings are incomplete for %', v_seller.store_name;
    end if;

    v_delivery_fee := coalesce(v_delivery_fee, 0);

    insert into public.orders(
      customer_id, order_group_id, seller_id,
      subtotal, delivery_fee, discount, total,
      status, payment_method, payment_status,
      address, phone, customer_name_snapshot, store_name_snapshot,
      address_snapshot, customer_note, created_at, updated_at
    )
    values(
      v_uid, v_group_id, v_seller.seller_id,
      v_seller.subtotal, v_delivery_fee, 0, v_seller.subtotal + v_delivery_fee,
      'pending', 'cod', 'pending',
      v_address_text, trim(p_phone), v_profile.name, v_seller.store_name,
      jsonb_build_object(
        'label', v_address.label,
        'description', v_address.description,
        'area', v_address.area,
        'landmark', v_address.landmark,
        'phone', trim(p_phone),
        'delivery_notes', v_address.delivery_notes
      ),
      nullif(trim(coalesce(p_notes, '')), ''),
      now(), now()
    )
    returning id into v_order_id;

    for v_line in
      select value
      from jsonb_array_elements(v_lines)
      where (value ->> 'seller_id')::uuid = v_seller.seller_id
      order by (value ->> 'product_id')::uuid
    loop
      insert into public.order_items(
        order_id, product_id, seller_id, quantity, unit_price,
        product_name_snapshot, discount_snapshot, line_total, created_at
      )
      values(
        v_order_id,
        (v_line ->> 'product_id')::uuid,
        v_seller.seller_id,
        (v_line ->> 'quantity')::int,
        (v_line ->> 'unit_price')::numeric,
        v_line ->> 'product_name',
        0,
        (v_line ->> 'line_total')::numeric,
        now()
      );

      update public.products
      set stock = stock - (v_line ->> 'quantity')::int,
          is_active = (stock - (v_line ->> 'quantity')::int) > 0,
          updated_at = now()
      where id = (v_line ->> 'product_id')::uuid;
    end loop;

    insert into public.order_status_history(
      order_id, from_status, to_status, changed_by, note, created_at
    )
    values(v_order_id, null, 'pending', v_uid, 'Order created', now());

    v_delivery_total := v_delivery_total + v_delivery_fee;
  end loop;

  update public.order_groups
  set delivery_total = v_delivery_total,
      grand_total = v_subtotal + v_delivery_total,
      updated_at = now()
  where id = v_group_id;

  delete from public.cart_items
  where cart_id in (select c.id from public.carts c where c.user_id = v_uid);

  return v_group_id;
end;
$function$;


-- function: public.checkout_create_order_group_v2
CREATE OR REPLACE FUNCTION public.checkout_create_order_group_v2(p_address_id uuid, p_phone text, p_notes text, p_items jsonb, p_idempotency_key text, p_delivery_zones jsonb DEFAULT '{}'::jsonb)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_uid uuid := auth.uid();
  v_existing uuid;
  v_group_id uuid;
  v_order_id uuid;
  v_item jsonb;
  v_line jsonb;
  v_lines jsonb := '[]'::jsonb;
  v_product record;
  v_seller record;
  v_address record;
  v_profile record;
  v_product_id uuid;
  v_qty int;
  v_subtotal numeric := 0;
  v_delivery_total numeric := 0;
  v_delivery_fee numeric;
  v_delivery_area text;
  v_selected_zone_id text;
  v_address_text text;
begin
  if v_uid is null then raise exception 'Authentication required'; end if;
  if jsonb_typeof(coalesce(p_delivery_zones, '{}'::jsonb)) <> 'object' then
    raise exception 'Invalid delivery zone selections';
  end if;

  select p.id, p.name into v_profile
  from public.profiles p
  where p.id = v_uid and p.is_active = true and p.deleted_at is null;
  if not found then raise exception 'Account is not active'; end if;

  if coalesce(char_length(trim(p_idempotency_key)), 0) < 16
     or char_length(trim(p_idempotency_key)) > 100 then
    raise exception 'Invalid checkout key';
  end if;

  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended(v_uid::text || ':' || trim(p_idempotency_key), 0)
  );
  select og.id into v_existing
  from public.order_groups og
  where og.customer_id = v_uid and og.idempotency_key = trim(p_idempotency_key)
  limit 1;
  if found then return v_existing; end if;

  select a.* into v_address
  from public.addresses a
  where a.id = p_address_id and a.user_id = v_uid;
  if not found then raise exception 'Address not found'; end if;

  if coalesce(char_length(trim(p_phone)), 0) < 7 or char_length(trim(p_phone)) > 30 then
    raise exception 'Invalid phone number';
  end if;
  if char_length(coalesce(p_notes, '')) > 1000 then raise exception 'Order notes are too long'; end if;
  if jsonb_typeof(coalesce(p_items, '[]'::jsonb)) <> 'array'
     or jsonb_array_length(coalesce(p_items, '[]'::jsonb)) = 0 then
    raise exception 'Cart is empty';
  end if;
  if jsonb_array_length(p_items) > 100 then raise exception 'Cart has too many items'; end if;

  v_address_text := concat_ws(
    ' - ',
    nullif(trim(v_address.label), ''),
    nullif(trim(v_address.description), ''),
    nullif(trim(coalesce(v_address.area, '')), ''),
    nullif(trim(coalesce(v_address.landmark, '')), '')
  );

  for v_item in
    select jsonb_build_object('product_id', product_id::text, 'quantity', sum(quantity)::int)
    from (
      select (x.value ->> 'product_id')::uuid as product_id,
             greatest(1, least(99, coalesce((x.value ->> 'quantity')::int, 1))) as quantity
      from jsonb_array_elements(p_items) x(value)
      where x.value ? 'product_id'
    ) q
    group by product_id
    order by product_id
  loop
    v_product_id := (v_item ->> 'product_id')::uuid;
    v_qty := (v_item ->> 'quantity')::int;

    select p.id, p.seller_id, p.name, p.price, p.stock,
           coalesce(st.name, s.store_name) as store_name
    into v_product
    from public.products p
    join public.sellers s on s.id = p.seller_id
    left join public.stores st on st.seller_id = p.seller_id and st.is_active = true
    where p.id = v_product_id
      and p.is_active = true
      and s.verification_status = 'approved'
    for update of p;

    if not found then raise exception 'A product in your cart is no longer available'; end if;
    if v_product.stock < v_qty then raise exception 'Insufficient stock for %', v_product.name; end if;

    v_lines := v_lines || jsonb_build_array(jsonb_build_object(
      'product_id', v_product.id::text,
      'seller_id', v_product.seller_id::text,
      'product_name', v_product.name,
      'store_name', v_product.store_name,
      'unit_price', v_product.price,
      'quantity', v_qty,
      'line_total', v_product.price * v_qty
    ));
    v_subtotal := v_subtotal + (v_product.price * v_qty);
  end loop;
  if jsonb_array_length(v_lines) = 0 then raise exception 'Cart is empty'; end if;

  insert into public.order_groups(
    customer_id, subtotal, delivery_total, discount_total, grand_total,
    payment_method, payment_status, status, idempotency_key,
    address_id, address_snapshot, customer_note, created_at, updated_at
  ) values (
    v_uid, v_subtotal, 0, 0, v_subtotal,
    'cod', 'pending', 'pending', trim(p_idempotency_key), p_address_id,
    jsonb_build_object(
      'label', v_address.label,
      'description', v_address.description,
      'area', v_address.area,
      'landmark', v_address.landmark,
      'phone', coalesce(nullif(trim(p_phone), ''), v_address.phone),
      'delivery_notes', v_address.delivery_notes,
      'delivery_zones', coalesce(p_delivery_zones, '{}'::jsonb)
    ),
    nullif(trim(coalesce(p_notes, '')), ''), now(), now()
  ) returning id into v_group_id;

  for v_seller in
    select seller_id::uuid as seller_id,
           max(store_name) as store_name,
           sum(line_total)::numeric as subtotal
    from jsonb_to_recordset(v_lines)
      as x(seller_id text, store_name text, line_total numeric)
    group by seller_id
    order by seller_id
  loop
    v_delivery_area := null;
    v_delivery_fee := null;

    if exists (
      select 1 from public.delivery_zones z
      where z.seller_id = v_seller.seller_id and z.is_active = true
    ) then
      v_selected_zone_id := nullif(trim(coalesce(p_delivery_zones ->> v_seller.seller_id::text, '')), '');
      if v_selected_zone_id is null then
        raise exception 'Choose a delivery area for %', v_seller.store_name;
      end if;

      select z.fee, z.area_name
      into v_delivery_fee, v_delivery_area
      from public.delivery_zones z
      where z.id::text = v_selected_zone_id
        and z.seller_id = v_seller.seller_id
        and z.is_active = true
      limit 1;

      if not found then
        raise exception 'Selected delivery area is not available for %', v_seller.store_name;
      end if;
    else
      select d.base_fee, d.delivery_area
      into v_delivery_fee, v_delivery_area
      from public.delivery_settings d
      where d.seller_id = v_seller.seller_id and d.is_active = true
      limit 1;

      if not found then
        raise exception 'Delivery settings are incomplete for %', v_seller.store_name;
      end if;
    end if;

    v_delivery_fee := coalesce(v_delivery_fee, 0);

    insert into public.orders(
      customer_id, order_group_id, seller_id,
      subtotal, delivery_fee, discount, total,
      status, payment_method, payment_status,
      address, phone, customer_name_snapshot, store_name_snapshot,
      address_snapshot, customer_note, created_at, updated_at
    ) values (
      v_uid, v_group_id, v_seller.seller_id,
      v_seller.subtotal, v_delivery_fee, 0, v_seller.subtotal + v_delivery_fee,
      'pending', 'cod', 'pending',
      v_address_text, trim(p_phone), v_profile.name, v_seller.store_name,
      jsonb_build_object(
        'label', v_address.label,
        'description', v_address.description,
        'area', v_address.area,
        'landmark', v_address.landmark,
        'phone', trim(p_phone),
        'delivery_notes', v_address.delivery_notes,
        'delivery_zone', v_delivery_area
      ),
      nullif(trim(coalesce(p_notes, '')), ''), now(), now()
    ) returning id into v_order_id;

    for v_line in
      select value
      from jsonb_array_elements(v_lines)
      where (value ->> 'seller_id')::uuid = v_seller.seller_id
      order by (value ->> 'product_id')::uuid
    loop
      insert into public.order_items(
        order_id, product_id, seller_id, quantity, unit_price,
        product_name_snapshot, discount_snapshot, line_total, created_at
      ) values (
        v_order_id,
        (v_line ->> 'product_id')::uuid,
        v_seller.seller_id,
        (v_line ->> 'quantity')::int,
        (v_line ->> 'unit_price')::numeric,
        v_line ->> 'product_name',
        0,
        (v_line ->> 'line_total')::numeric,
        now()
      );

      update public.products
      set stock = stock - (v_line ->> 'quantity')::int,
          is_active = (stock - (v_line ->> 'quantity')::int) > 0,
          updated_at = now()
      where id = (v_line ->> 'product_id')::uuid;
    end loop;

    insert into public.order_status_history(
      order_id, from_status, to_status, changed_by, note, created_at
    ) values (v_order_id, null, 'pending', v_uid, 'Order created', now());

    v_delivery_total := v_delivery_total + v_delivery_fee;
  end loop;

  update public.order_groups
  set delivery_total = v_delivery_total,
      grand_total = v_subtotal + v_delivery_total,
      updated_at = now()
  where id = v_group_id;

  delete from public.cart_items
  where cart_id in (select c.id from public.carts c where c.user_id = v_uid);

  return v_group_id;
end;
$function$;


-- function: public.checkout_create_order_group_v3
CREATE OR REPLACE FUNCTION public.checkout_create_order_group_v3(p_address_id uuid, p_phone text, p_notes text, p_items jsonb, p_idempotency_key text, p_delivery_zones jsonb, p_expected_grand_total numeric, p_quote_token text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_uid uuid := auth.uid();
  v_existing uuid;
  v_group_id uuid;
  v_order_id uuid;
  v_quote jsonb;
  v_line jsonb;
  v_delivery record;
  v_address record;
  v_profile record;
  v_address_text text;
  v_current_total numeric(14,2);
begin
  if v_uid is null then
    raise exception using errcode = '28000', message = 'يجب تسجيل الدخول أولاً';
  end if;

  select p.id, p.name into v_profile
  from public.profiles p
  where p.id = v_uid and p.is_active = true and p.deleted_at is null;
  if not found then
    raise exception using errcode = '28000', message = 'الحساب غير نشط';
  end if;

  if coalesce(char_length(trim(p_idempotency_key)), 0) < 16
     or char_length(trim(p_idempotency_key)) > 100 then
    raise exception using errcode = '22023', message = 'مفتاح الطلب غير صحيح';
  end if;

  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended(v_uid::text || ':' || trim(p_idempotency_key), 0)
  );
  select og.id into v_existing
  from public.order_groups og
  where og.customer_id = v_uid and og.idempotency_key = trim(p_idempotency_key)
  limit 1;
  if found then return v_existing; end if;

  select a.* into v_address
  from public.addresses a
  where a.id = p_address_id and a.user_id = v_uid;
  if not found then
    raise exception using errcode = '22023', message = 'عنوان التوصيل غير موجود';
  end if;

  if coalesce(char_length(trim(p_phone)), 0) < 7 or char_length(trim(p_phone)) > 30 then
    raise exception using errcode = '22023', message = 'رقم الهاتف غير صحيح';
  end if;
  if char_length(coalesce(p_notes, '')) > 1000 then
    raise exception using errcode = '22023', message = 'ملاحظات الطلب طويلة جداً';
  end if;
  if p_expected_grand_total is null or p_expected_grand_total < 0 then
    raise exception using errcode = '22023', message = 'إجمالي الطلب المتوقع غير صحيح';
  end if;
  if coalesce(char_length(trim(p_quote_token)), 0) <> 32 then
    raise exception using errcode = '22023', message = 'انتهت صلاحية تسعير الطلب. أعيدي المحاولة';
  end if;

  -- Product rows are locked while the same authoritative quote is rebuilt.
  v_quote := private.checkout_quote_core(p_items, p_delivery_zones, true);
  v_current_total := (v_quote ->> 'grand_total')::numeric;
  if abs(v_current_total - p_expected_grand_total) > 0.005
     or (v_quote ->> 'quote_token') <> trim(p_quote_token) then
    raise exception using
      errcode = 'P0001',
      message = 'تغيّر السعر أو التوصيل. راجعي الإجمالي الجديد ثم أكدي الطلب مرة أخرى';
  end if;

  v_address_text := concat_ws(
    ' - ',
    nullif(trim(v_address.label), ''),
    nullif(trim(v_address.description), ''),
    nullif(trim(coalesce(v_address.area, '')), ''),
    nullif(trim(coalesce(v_address.landmark, '')), '')
  );

  insert into public.order_groups(
    customer_id, subtotal, delivery_total, discount_total, grand_total,
    payment_method, payment_status, status, idempotency_key,
    address_id, address_snapshot, customer_note, created_at, updated_at
  ) values (
    v_uid,
    (v_quote ->> 'subtotal')::numeric,
    (v_quote ->> 'delivery_total')::numeric,
    (v_quote ->> 'discount_total')::numeric,
    v_current_total,
    'cod', 'pending', 'pending', trim(p_idempotency_key), p_address_id,
    jsonb_build_object(
      'label', v_address.label,
      'description', v_address.description,
      'area', v_address.area,
      'landmark', v_address.landmark,
      'phone', trim(p_phone),
      'delivery_notes', v_address.delivery_notes,
      'delivery_zones', coalesce(p_delivery_zones, '{}'::jsonb),
      'quote_token', v_quote ->> 'quote_token'
    ),
    nullif(trim(coalesce(p_notes, '')), ''), now(), now()
  ) returning id into v_group_id;

  for v_delivery in
    select *
    from jsonb_to_recordset(v_quote -> 'deliveries') as x(
      seller_id uuid,
      store_name text,
      subtotal numeric,
      fee numeric,
      area text
    )
    order by seller_id
  loop
    insert into public.orders(
      customer_id, order_group_id, seller_id,
      subtotal, delivery_fee, discount, total,
      status, payment_method, payment_status,
      address, phone, customer_name_snapshot, store_name_snapshot,
      address_snapshot, customer_note, created_at, updated_at
    ) values (
      v_uid, v_group_id, v_delivery.seller_id,
      v_delivery.subtotal, v_delivery.fee, 0, v_delivery.subtotal + v_delivery.fee,
      'pending', 'cod', 'pending',
      v_address_text, trim(p_phone), v_profile.name, v_delivery.store_name,
      jsonb_build_object(
        'label', v_address.label,
        'description', v_address.description,
        'area', v_address.area,
        'landmark', v_address.landmark,
        'phone', trim(p_phone),
        'delivery_notes', v_address.delivery_notes,
        'delivery_zone', v_delivery.area
      ),
      nullif(trim(coalesce(p_notes, '')), ''), now(), now()
    ) returning id into v_order_id;

    for v_line in
      select value
      from jsonb_array_elements(v_quote -> 'items')
      where (value ->> 'seller_id')::uuid = v_delivery.seller_id
      order by (value ->> 'product_id')::uuid
    loop
      insert into public.order_items(
        order_id, product_id, seller_id, quantity, unit_price,
        product_name_snapshot, discount_snapshot, line_total, created_at
      ) values (
        v_order_id,
        (v_line ->> 'product_id')::uuid,
        v_delivery.seller_id,
        (v_line ->> 'quantity')::int,
        (v_line ->> 'unit_price')::numeric,
        v_line ->> 'name',
        0,
        (v_line ->> 'line_total')::numeric,
        now()
      );

      update public.products
      set stock = stock - (v_line ->> 'quantity')::int,
          is_active = (stock - (v_line ->> 'quantity')::int) > 0,
          updated_at = now()
      where id = (v_line ->> 'product_id')::uuid
        and is_active = true
        and stock >= (v_line ->> 'quantity')::int;
      if not found then
        raise exception using errcode = 'P0001', message = 'تغيّر المخزون. راجعي السلة ثم حاولي مرة أخرى';
      end if;
    end loop;

    insert into public.order_status_history(
      order_id, from_status, to_status, changed_by, note, created_at
    ) values (v_order_id, null, 'pending', v_uid, 'Order created', now());
  end loop;

  delete from public.cart_items
  where cart_id in (select c.id from public.carts c where c.user_id = v_uid);

  return v_group_id;
end;
$function$;


-- function: public.checkout_create_order_group_v4
CREATE OR REPLACE FUNCTION public.checkout_create_order_group_v4(p_address_id uuid, p_phone text, p_notes text, p_items jsonb, p_idempotency_key text, p_delivery_zones jsonb, p_expected_grand_total numeric, p_quote_token text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_uid uuid := auth.uid();
  v_existing uuid;
  v_group_id uuid;
  v_order_id uuid;
  v_quote jsonb;
  v_line jsonb;
  v_delivery record;
  v_address record;
  v_profile record;
  v_address_text text;
  v_current_total numeric(14,2);
begin
  if v_uid is null then
    raise exception using errcode = '28000', message = 'يجب تسجيل الدخول أولاً';
  end if;
  select p.id, p.name into v_profile
  from public.profiles p
  where p.id = v_uid and p.is_active = true and p.deleted_at is null;
  if not found then raise exception using errcode = '28000', message = 'الحساب غير نشط'; end if;

  if coalesce(char_length(trim(p_idempotency_key)), 0) < 16
     or char_length(trim(p_idempotency_key)) > 100 then
    raise exception using errcode = '22023', message = 'مفتاح الطلب غير صحيح';
  end if;
  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended(v_uid::text || ':' || trim(p_idempotency_key), 0)
  );
  select og.id into v_existing
  from public.order_groups og
  where og.customer_id = v_uid and og.idempotency_key = trim(p_idempotency_key)
  limit 1;
  if found then return v_existing; end if;

  select a.* into v_address
  from public.addresses a
  where a.id = p_address_id and a.user_id = v_uid;
  if not found then raise exception using errcode = '22023', message = 'عنوان التوصيل غير موجود'; end if;
  if coalesce(char_length(trim(p_phone)), 0) < 7 or char_length(trim(p_phone)) > 30 then
    raise exception using errcode = '22023', message = 'رقم الهاتف غير صحيح';
  end if;
  if char_length(coalesce(p_notes, '')) > 1000 then
    raise exception using errcode = '22023', message = 'ملاحظات الطلب طويلة جداً';
  end if;
  if p_expected_grand_total is null or p_expected_grand_total < 0 then
    raise exception using errcode = '22023', message = 'إجمالي الطلب المتوقع غير صحيح';
  end if;
  if coalesce(char_length(trim(p_quote_token)), 0) <> 32 then
    raise exception using errcode = '22023', message = 'انتهت صلاحية تسعير الطلب. أعيدي المحاولة';
  end if;

  v_quote := private.checkout_quote_core_v2(p_items, p_delivery_zones, true);
  v_current_total := (v_quote ->> 'grand_total')::numeric;
  if abs(v_current_total - p_expected_grand_total) > 0.005
     or (v_quote ->> 'quote_token') <> trim(p_quote_token) then
    raise exception using errcode = 'P0001',
      message = 'تغيّر السعر أو الخيار أو التوصيل. راجعي الإجمالي الجديد ثم أكدي الطلب مرة أخرى';
  end if;

  v_address_text := concat_ws(
    ' - ', nullif(trim(v_address.label), ''), nullif(trim(v_address.description), ''),
    nullif(trim(coalesce(v_address.area, '')), ''), nullif(trim(coalesce(v_address.landmark, '')), '')
  );
  insert into public.order_groups(
    customer_id, subtotal, delivery_total, discount_total, grand_total,
    payment_method, payment_status, status, idempotency_key,
    address_id, address_snapshot, customer_note, created_at, updated_at
  ) values (
    v_uid, (v_quote ->> 'subtotal')::numeric, (v_quote ->> 'delivery_total')::numeric,
    (v_quote ->> 'discount_total')::numeric, v_current_total,
    'cod', 'pending', 'pending', trim(p_idempotency_key), p_address_id,
    jsonb_build_object(
      'label', v_address.label, 'description', v_address.description,
      'area', v_address.area, 'landmark', v_address.landmark,
      'phone', trim(p_phone), 'delivery_notes', v_address.delivery_notes,
      'delivery_zones', coalesce(p_delivery_zones, '{}'::jsonb),
      'quote_token', v_quote ->> 'quote_token'
    ),
    nullif(trim(coalesce(p_notes, '')), ''), now(), now()
  ) returning id into v_group_id;

  for v_delivery in
    select * from jsonb_to_recordset(v_quote -> 'deliveries') as x(
      seller_id uuid, store_name text, subtotal numeric, fee numeric, area text
    ) order by seller_id
  loop
    insert into public.orders(
      customer_id, order_group_id, seller_id, subtotal, delivery_fee, discount, total,
      status, payment_method, payment_status, address, phone,
      customer_name_snapshot, store_name_snapshot, address_snapshot, customer_note,
      created_at, updated_at
    ) values (
      v_uid, v_group_id, v_delivery.seller_id, v_delivery.subtotal, v_delivery.fee, 0,
      v_delivery.subtotal + v_delivery.fee, 'pending', 'cod', 'pending',
      v_address_text, trim(p_phone), v_profile.name, v_delivery.store_name,
      jsonb_build_object(
        'label', v_address.label, 'description', v_address.description,
        'area', v_address.area, 'landmark', v_address.landmark,
        'phone', trim(p_phone), 'delivery_notes', v_address.delivery_notes,
        'delivery_zone', v_delivery.area
      ),
      nullif(trim(coalesce(p_notes, '')), ''), now(), now()
    ) returning id into v_order_id;

    for v_line in
      select value from jsonb_array_elements(v_quote -> 'items')
      where (value ->> 'seller_id')::uuid = v_delivery.seller_id
      order by (value ->> 'product_id')::uuid, coalesce(value ->> 'variant_id', '')
    loop
      insert into public.order_items(
        order_id, product_id, seller_id, quantity, unit_price,
        product_name_snapshot, variant_snapshot, discount_snapshot, line_total, created_at
      ) values (
        v_order_id, (v_line ->> 'product_id')::uuid, v_delivery.seller_id,
        (v_line ->> 'quantity')::int, (v_line ->> 'unit_price')::numeric,
        v_line ->> 'name', v_line -> 'variant_snapshot', 0,
        (v_line ->> 'line_total')::numeric, now()
      );

      if nullif(v_line ->> 'variant_id', '') is not null then
        update public.product_variants
        set stock = stock - (v_line ->> 'quantity')::int,
            updated_at = now()
        where id = (v_line ->> 'variant_id')::uuid
          and product_id = (v_line ->> 'product_id')::uuid
          and is_active = true
          and stock >= (v_line ->> 'quantity')::int;
      else
        update public.products
        set stock = stock - (v_line ->> 'quantity')::int,
            is_active = (stock - (v_line ->> 'quantity')::int) > 0,
            updated_at = now()
        where id = (v_line ->> 'product_id')::uuid
          and has_variants = false
          and is_active = true
          and stock >= (v_line ->> 'quantity')::int;
      end if;
      if not found then
        raise exception using errcode = 'P0001', message = 'تغيّر المخزون. راجعي السلة ثم حاولي مرة أخرى';
      end if;
    end loop;

    insert into public.order_status_history(
      order_id, from_status, to_status, changed_by, note, created_at
    ) values (v_order_id, null, 'pending', v_uid, 'Order created', now());
  end loop;

  delete from public.cart_items
  where cart_id in (select c.id from public.carts c where c.user_id = v_uid);
  return v_group_id;
end;
$function$;


-- function: public.current_user_role
CREATE OR REPLACE FUNCTION public.current_user_role()
 RETURNS text
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
  select p.role
  from public.profiles p
  where p.id = auth.uid()
    and p.is_active = true
    and p.deleted_at is null
  limit 1
$function$;


-- function: public.customer_cancel_order_group
CREATE OR REPLACE FUNCTION public.customer_cancel_order_group(p_order_group_id uuid, p_note text DEFAULT NULL::text)
 RETURNS integer
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_uid uuid := auth.uid();
  v_order record;
  v_count int := 0;
begin
  if v_uid is null then
    raise exception 'Authentication required';
  end if;

  if not exists (
    select 1 from public.order_groups og
    where og.id = p_order_group_id and og.customer_id = v_uid
  ) then
    raise exception 'Order group not found';
  end if;

  if exists (
    select 1 from public.orders o
    where o.order_group_id = p_order_group_id
      and o.customer_id = v_uid
      and o.status <> 'pending'
  ) then
    raise exception 'This order can no longer be cancelled as a group';
  end if;

  for v_order in
    select o.id
    from public.orders o
    where o.order_group_id = p_order_group_id
      and o.customer_id = v_uid
      and o.status = 'pending'
    order by o.id
  loop
    perform public.transition_order_status(v_order.id, 'cancelled', p_note);
    v_count := v_count + 1;
  end loop;

  if v_count = 0 then
    raise exception 'No cancellable orders found';
  end if;

  perform private.refresh_order_group_status(p_order_group_id);
  return v_count;
end;
$function$;


-- function: public.delivery_quote
CREATE OR REPLACE FUNCTION public.delivery_quote(p_seller_id uuid, p_city text DEFAULT NULL::text, p_area text DEFAULT NULL::text)
 RETURNS jsonb
 LANGUAGE sql
 STABLE
 SET search_path TO 'public', 'pg_temp'
AS $function$
  select case
    when not exists(
      select 1
      from public.delivery_settings d
      join public.sellers s on s.id=d.seller_id and s.verification_status='approved'
      where d.seller_id=p_seller_id and d.is_active
    ) then jsonb_build_object('seller_id',p_seller_id,'area',p_area,'available',false)
    when exists(select 1 from public.stores st where st.seller_id=p_seller_id)
         and not exists(
           select 1 from public.stores st
           where st.seller_id=p_seller_id and st.is_active and st.is_open
         )
      then jsonb_build_object('seller_id',p_seller_id,'area',p_area,'available',false)
    when p_city is not null
         and not exists(
           select 1 from public.stores st
           where st.seller_id=p_seller_id
             and st.is_active and st.is_open
             and lower(coalesce(st.city,''))=lower(trim(p_city))
         )
      then jsonb_build_object('seller_id',p_seller_id,'area',p_area,'available',false)
    else coalesce(
      (
        select jsonb_build_object(
          'seller_id',z.seller_id,
          'provider','merchant_delivery',
          'fee',z.fee,
          'estimated_minutes',z.estimated_minutes,
          'delivery_area',z.area_name,
          'city',(
            select st.city from public.stores st
            where st.seller_id=z.seller_id and st.is_active and st.is_open
            order by st.id limit 1
          ),
          'area',p_area,
          'available',true
        )
        from public.delivery_zones z
        join public.sellers s on s.id=z.seller_id and s.verification_status='approved'
        join public.delivery_settings d on d.seller_id=z.seller_id and d.is_active
        where z.seller_id=p_seller_id
          and z.is_active
          and (p_area is null or lower(trim(z.area_name))=lower(trim(p_area)))
        order by z.sort_order,z.fee
        limit 1
      ),
      case when p_area is not null then
        jsonb_build_object('seller_id',p_seller_id,'area',p_area,'available',false)
      else
        (
          select jsonb_build_object(
            'seller_id',d.seller_id,
            'provider','merchant_delivery',
            'fee',d.base_fee,
            'estimated_minutes',d.estimated_minutes,
            'delivery_area',d.delivery_area,
            'city',(
              select st.city from public.stores st
              where st.seller_id=d.seller_id and st.is_active and st.is_open
              order by st.id limit 1
            ),
            'area',p_area,
            'available',true
          )
          from public.delivery_settings d
          join public.sellers s on s.id=d.seller_id and s.verification_status='approved'
          where d.seller_id=p_seller_id
            and d.is_active
            and not exists(select 1 from public.delivery_zones z where z.seller_id=d.seller_id)
          limit 1
        )
      end,
      jsonb_build_object('seller_id',p_seller_id,'area',p_area,'available',false)
    )
  end;
$function$;


-- function: public.fee_quote
CREATE OR REPLACE FUNCTION public.fee_quote(p_context text, p_amount numeric)
 RETURNS jsonb
 LANGUAGE sql
 STABLE
 SET search_path TO 'public', 'pg_temp'
AS $function$
  with matched as (
    select * from public.fee_rules where context=p_context and is_active
      and (starts_at is null or starts_at<=now()) and (ends_at is null or ends_at>now())
      and (min_amount is null or p_amount>=min_amount)
    order by starts_at desc nulls last,created_at desc limit 1
  ), calculated as (
    select code,least(coalesce(max_fee,1e18::numeric),round((greatest(p_amount,0)*percentage/100)+fixed_amount,2)) fee from matched
  )
  select jsonb_build_object('context',p_context,'base_amount',greatest(p_amount,0),'fee',coalesce((select fee from calculated),0),'rule_code',(select code from calculated));
$function$;


-- function: public.initialize_new_profile
CREATE OR REPLACE FUNCTION public.initialize_new_profile()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$ begin insert into public.notification_preferences(user_id) values (new.id) on conflict (user_id) do nothing; return new; end; $function$;


-- function: public.merchant_dashboard_summary
CREATE OR REPLACE FUNCTION public.merchant_dashboard_summary()
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_uid uuid:=auth.uid();
  v_seller_id uuid;
  v_orders int:=0;
  v_delivered int:=0;
  v_cancelled int:=0;
  v_gmv numeric:=0;
  v_views int:=0;
  v_customers int:=0;
  v_repeat_customers int:=0;
  v_avg_response_seconds numeric;
  v_products int:=0;
  v_active_products int:=0;
  v_rating numeric:=0;
  v_best jsonb:='[]'::jsonb;
begin
  if v_uid is null then raise exception 'Authentication required'; end if;

  select s.id into v_seller_id
  from public.sellers s
  where s.user_id=v_uid and s.verification_status='approved';

  if v_seller_id is null then raise exception 'Approved merchant required'; end if;

  select
    count(*)::int,
    count(*) filter(where o.status='delivered')::int,
    count(*) filter(where o.status in ('cancelled','rejected','failed'))::int,
    coalesce(sum(o.total) filter(where o.status='delivered'),0),
    count(distinct o.customer_id)::int
  into v_orders,v_delivered,v_cancelled,v_gmv,v_customers
  from public.orders o
  where o.seller_id=v_seller_id;

  select count(*)::int into v_repeat_customers
  from (
    select o.customer_id
    from public.orders o
    where o.seller_id=v_seller_id and o.status='delivered'
    group by o.customer_id
    having count(*)>1
  ) q;

  select count(*)::int,count(*) filter(where p.is_active)::int
  into v_products,v_active_products
  from public.products p where p.seller_id=v_seller_id;

  select coalesce(round(avg(r.rating)::numeric,2),0)
  into v_rating
  from public.reviews r
  join public.products p on p.id=r.product_id
  where p.seller_id=v_seller_id;

  select count(*)::int into v_views
  from public.app_events e
  where e.event_name='product_view'
    and e.entity_type='product'
    and exists(
      select 1 from public.products p
      where p.id=e.entity_id and p.seller_id=v_seller_id
    );

  select round(avg(extract(epoch from (h.created_at-o.created_at)))::numeric,1)
  into v_avg_response_seconds
  from public.orders o
  join lateral(
    select osh.created_at
    from public.order_status_history osh
    where osh.order_id=o.id and osh.to_status='accepted'
    order by osh.created_at asc
    limit 1
  ) h on true
  where o.seller_id=v_seller_id;

  select coalesce(jsonb_agg(to_jsonb(x) order by x.units desc,x.revenue desc),'[]'::jsonb)
  into v_best
  from (
    select
      p.id,
      p.name,
      coalesce(sales.units,0)::int as units,
      coalesce(sales.revenue,0)::numeric as revenue,
      coalesce(views.views,0)::int as views
    from public.products p
    left join lateral(
      select
        coalesce(sum(oi.quantity),0)::int as units,
        coalesce(sum(oi.line_total),0)::numeric as revenue
      from public.order_items oi
      join public.orders o on o.id=oi.order_id
      where oi.product_id=p.id and o.status='delivered'
    ) sales on true
    left join lateral(
      select count(*)::int as views
      from public.app_events e
      where e.entity_id=p.id and e.entity_type='product' and e.event_name='product_view'
    ) views on true
    where p.seller_id=v_seller_id
    order by units desc,revenue desc,views desc
    limit 5
  ) x;

  return jsonb_build_object(
    'seller_id',v_seller_id,
    'orders',v_orders,
    'delivered_orders',v_delivered,
    'cancelled_orders',v_cancelled,
    'gmv',v_gmv,
    'product_views',v_views,
    'conversion_rate',case when v_views>0 then round((v_orders::numeric/v_views::numeric)*100,2) else 0 end,
    'customers',v_customers,
    'repeat_customers',v_repeat_customers,
    'products',v_products,
    'active_products',v_active_products,
    'average_rating',v_rating,
    'completion_rate',case when v_orders>0 then round((v_delivered::numeric/v_orders::numeric)*100,2) else 0 end,
    'cancellation_rate',case when v_orders>0 then round((v_cancelled::numeric/v_orders::numeric)*100,2) else 0 end,
    'average_response_seconds',v_avg_response_seconds,
    'best_sellers',v_best
  );
end;
$function$;


-- function: public.merchant_delete_product
CREATE OR REPLACE FUNCTION public.merchant_delete_product(p_product_id uuid)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_uid uuid:=auth.uid();
  v_paths jsonb;
begin
  if v_uid is null then raise exception 'Authentication required'; end if;

  if not exists(
    select 1
    from public.products p
    join public.sellers s on s.id=p.seller_id
    where p.id=p_product_id
      and s.user_id=v_uid
      and s.verification_status='approved'
  ) then
    raise exception 'Product not found or merchant is not approved';
  end if;

  if exists(select 1 from public.order_items oi where oi.product_id=p_product_id) then
    raise exception 'Product has order history. Deactivate it instead of deleting it';
  end if;

  select coalesce(jsonb_agg(pi.storage_path),'[]'::jsonb)
  into v_paths
  from public.product_images pi
  where pi.product_id=p_product_id;

  delete from public.products where id=p_product_id;

  insert into public.audit_logs(actor_id,action,entity_type,entity_id,source)
  values(v_uid,'delete','product',p_product_id,'merchant');

  return v_paths;
end;
$function$;


-- function: public.merchant_inventory_health
CREATE OR REPLACE FUNCTION public.merchant_inventory_health()
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_seller uuid;
begin
 select id into v_seller from public.sellers where user_id=(select auth.uid()) and verification_status='approved' limit 1;
 if v_seller is null then raise exception 'approved merchant required'; end if;
 return jsonb_build_object(
  'seller_id',v_seller,
  'out_of_stock',(select count(*) from public.products where seller_id=v_seller and is_active and stock=0),
  'low_stock',(select count(*) from public.products where seller_id=v_seller and is_active and stock between 1 and 3),
  'inactive',(select count(*) from public.products where seller_id=v_seller and not is_active)
 );
end; $function$;


-- function: public.merchant_transition_order_status
CREATE OR REPLACE FUNCTION public.merchant_transition_order_status(p_order_id uuid, p_to_status text, p_note text DEFAULT NULL::text, p_estimated_minutes integer DEFAULT NULL::integer)
 RETURNS text
 LANGUAGE plpgsql
 SET search_path TO ''
AS $function$
declare
  v_uid uuid := auth.uid();
  v_is_seller boolean := false;
  v_note text := nullif(trim(coalesce(p_note, '')), '');
  v_result text;
begin
  if v_uid is null then
    raise exception using errcode = '28000', message = 'Authentication required';
  end if;

  select exists(
    select 1
    from public.orders o
    join public.sellers s on s.id = o.seller_id
    where o.id = p_order_id and s.user_id = v_uid
  ) into v_is_seller;
  if not v_is_seller then
    raise exception using errcode = '42501', message = 'Only this order seller can update it';
  end if;

  if p_to_status in ('rejected','cancelled','failed')
     and coalesce(char_length(v_note), 0) < 3 then
    raise exception using errcode = '22023', message = 'A reason is required for this status';
  end if;

  if p_to_status = 'out_for_delivery' then
    if p_estimated_minutes is null or p_estimated_minutes not between 1 and 1440 then
      raise exception using errcode = '22023', message = 'A valid delivery ETA is required';
    end if;
    if coalesce(char_length(v_note), 0) > 350 then
      raise exception using errcode = '22023', message = 'Delivery note is too long';
    end if;
    v_note := 'الوقت المتوقع للوصول: ' || p_estimated_minutes || ' دقيقة' ||
      case when v_note is null then '' else E'\n' || v_note end;
  elsif p_estimated_minutes is not null then
    raise exception using errcode = '22023', message = 'Delivery ETA is only valid when the order leaves for delivery';
  end if;

  v_result := public.transition_order_status(p_order_id, p_to_status, v_note);
  return v_result;
end;
$function$;


-- function: public.my_merchant_entitlements
CREATE OR REPLACE FUNCTION public.my_merchant_entitlements()
 RETURNS jsonb
 LANGUAGE sql
 STABLE
 SET search_path TO 'public', 'pg_temp'
AS $function$
  select coalesce(jsonb_object_agg(k,v),'{}'::jsonb)
  from (
    select e.key k, e.value v
    from public.subscriptions s
    join public.subscription_plans p on p.id=s.plan_id and p.is_active
    cross join lateral jsonb_each(p.features) e
    where s.user_id=(select auth.uid())
      and s.status='active'
      and s.starts_at<=now()
      and (s.ends_at is null or s.ends_at>now())
      and (s.seller_id is null or exists(select 1 from public.sellers seller where seller.id=s.seller_id and seller.user_id=(select auth.uid())))
  ) features;
$function$;


-- function: public.my_referral_code
CREATE OR REPLACE FUNCTION public.my_referral_code()
 RETURNS text
 LANGUAGE plpgsql
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_uid uuid := (select auth.uid()); v_code text;
begin
  if v_uid is null then raise exception 'authentication required'; end if;
  select code into v_code from public.referral_codes where user_id=v_uid;
  if v_code is null then
    v_code := 'TANI-'||upper(substr(md5(v_uid::text),1,10));
    insert into public.referral_codes(user_id,code) values(v_uid,v_code)
    on conflict(user_id) do update set code=referral_codes.code returning code into v_code;
  end if;
  return v_code;
end; $function$;


-- function: public.place_order
CREATE OR REPLACE FUNCTION public.place_order(p_address text, p_phone text, p_items jsonb)
 RETURNS uuid
 LANGUAGE plpgsql
 SET search_path TO 'public'
AS $function$ declare v_order_id uuid; v_total numeric:=0; item jsonb; v_product products%rowtype; v_qty integer; begin if auth.uid() is null then raise exception 'Authentication required'; end if; if coalesce(trim(p_address),'')='' or coalesce(trim(p_phone),'')='' then raise exception 'Address and phone are required'; end if; if jsonb_array_length(coalesce(p_items,'[]'::jsonb))=0 then raise exception 'Cart is empty'; end if; for item in select * from jsonb_array_elements(p_items) loop v_qty:=greatest(1,(item->>'quantity')::integer); select * into v_product from products where id=(item->>'product_id')::uuid and is_active=true for update; if not found then raise exception 'Product not found'; end if; if v_product.stock<v_qty then raise exception 'Insufficient stock for %',v_product.name; end if; v_total:=v_total+v_product.price*v_qty; end loop; insert into orders(customer_id,total,status,address,phone,delivery_fee) values(auth.uid(),v_total,'pending',trim(p_address),trim(p_phone),0) returning id into v_order_id; for item in select * from jsonb_array_elements(p_items) loop v_qty:=greatest(1,(item->>'quantity')::integer); select * into v_product from products where id=(item->>'product_id')::uuid for update; insert into order_items(order_id,product_id,seller_id,quantity,unit_price) values(v_order_id,v_product.id,v_product.seller_id,v_qty,v_product.price); update products set stock=stock-v_qty,updated_at=now(),is_active=(stock-v_qty>0) where id=v_product.id; end loop; return v_order_id; end; $function$;


-- function: public.protect_profile_admin_fields
CREATE OR REPLACE FUNCTION public.protect_profile_admin_fields()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO ''
AS $function$
begin
  if new.role is distinct from old.role
     or new.is_active is distinct from old.is_active
     or new.email is distinct from old.email
     or new.admin_previous_role is distinct from old.admin_previous_role then
    if auth.uid() is null and current_user in ('postgres', 'supabase_admin') then
      return new;
    end if;
    if coalesce(public.current_user_role(), '') <> 'admin' then
      raise exception 'غير مسموح بتعديل البريد أو الصلاحية أو حالة الحساب'
        using errcode = '42501';
    end if;
  end if;
  return new;
end;
$function$;


-- function: public.protect_profile_authorization_fields
CREATE OR REPLACE FUNCTION public.protect_profile_authorization_fields()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
begin
  if auth.uid() is not null and auth.uid() = old.id and public.current_user_role() <> 'admin' then
    if new.role is distinct from old.role
       or new.is_active is distinct from old.is_active
       or new.deleted_at is distinct from old.deleted_at then
      raise exception 'Protected profile authorization fields cannot be changed by the account owner';
    end if;
  end if;
  return new;
end;
$function$;


-- function: public.quote_cart
CREATE OR REPLACE FUNCTION public.quote_cart(p_items jsonb)
 RETURNS jsonb
 LANGUAGE plpgsql
 SET search_path TO ''
AS $function$
declare
  v_uid uuid := auth.uid();
  v_item jsonb;
  v_product record;
  v_product_id uuid;
  v_qty int;
  v_subtotal numeric := 0;
  v_delivery_total numeric := 0;
  v_delivery_fee numeric;
  v_items jsonb := '[]'::jsonb;
  v_warnings jsonb := '[]'::jsonb;
  v_seen_sellers uuid[] := array[]::uuid[];
begin
  if v_uid is null then
    raise exception 'يجب تسجيل الدخول أولاً';
  end if;

  if not exists (
    select 1 from public.profiles p
    where p.id = v_uid and p.is_active = true and p.deleted_at is null
  ) then
    raise exception 'الحساب غير نشط';
  end if;

  if jsonb_typeof(coalesce(p_items, '[]'::jsonb)) <> 'array' then
    raise exception 'بيانات السلة غير صحيحة';
  end if;

  if jsonb_array_length(coalesce(p_items, '[]'::jsonb)) = 0 then
    raise exception 'السلة فارغة';
  end if;

  if jsonb_array_length(p_items) > 100 then
    raise exception 'السلة تحتوي على منتجات كثيرة جداً';
  end if;

  for v_item in
    select jsonb_build_object(
      'product_id', product_id::text,
      'quantity', sum(quantity)::int
    )
    from (
      select
        (x.value ->> 'product_id')::uuid as product_id,
        greatest(1, least(99, coalesce((x.value ->> 'quantity')::int, 1))) as quantity
      from jsonb_array_elements(p_items) x(value)
      where x.value ? 'product_id'
    ) q
    group by product_id
    order by product_id
  loop
    v_product_id := (v_item ->> 'product_id')::uuid;
    v_qty := (v_item ->> 'quantity')::int;

    select
      p.id,
      p.seller_id,
      p.name,
      p.price,
      p.stock,
      p.is_active,
      s.verification_status
    into v_product
    from public.products p
    join public.sellers s on s.id = p.seller_id
    where p.id = v_product_id;

    if not found then
      v_warnings := v_warnings || jsonb_build_array('أحد المنتجات لم يعد متاحاً');
      continue;
    end if;

    v_items := v_items || jsonb_build_array(
      jsonb_build_object(
        'product_id', v_product.id,
        'seller_id', v_product.seller_id,
        'name', v_product.name,
        'unit_price', v_product.price,
        'quantity', v_qty,
        'stock', v_product.stock,
        'available', (
          v_product.is_active
          and v_product.verification_status = 'approved'
          and v_product.stock >= v_qty
        ),
        'line_total', v_product.price * v_qty
      )
    );

    v_subtotal := v_subtotal + (v_product.price * v_qty);

    if not (v_product.seller_id = any(v_seen_sellers)) then
      select ds.base_fee
      into v_delivery_fee
      from public.delivery_settings ds
      where ds.seller_id = v_product.seller_id
        and ds.is_active = true
      limit 1;

      if not found then
        v_warnings := v_warnings || jsonb_build_array(
          'إعدادات التوصيل غير مكتملة لهذا المتجر'
        );
        v_delivery_fee := 0;
      end if;

      v_delivery_total := v_delivery_total + coalesce(v_delivery_fee, 0);
      v_seen_sellers := array_append(v_seen_sellers, v_product.seller_id);
    end if;
  end loop;

  return jsonb_build_object(
    'subtotal', v_subtotal,
    'delivery_total', v_delivery_total,
    'discount_total', 0,
    'grand_total', v_subtotal + v_delivery_total,
    'items', v_items,
    'warnings', v_warnings
  );
end;
$function$;


-- function: public.quote_cart_v2
CREATE OR REPLACE FUNCTION public.quote_cart_v2(p_items jsonb, p_delivery_zones jsonb DEFAULT '{}'::jsonb)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin
  return private.checkout_quote_core(p_items, p_delivery_zones, false);
end;
$function$;


-- function: public.quote_cart_v3
CREATE OR REPLACE FUNCTION public.quote_cart_v3(p_items jsonb, p_delivery_zones jsonb DEFAULT '{}'::jsonb)
 RETURNS jsonb
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin
  perform private.assert_delivery_selection_available(p_items,p_delivery_zones);
  return private.checkout_quote_core_v2(p_items,p_delivery_zones,false);
end;
$function$;


-- function: public.recommended_products
CREATE OR REPLACE FUNCTION public.recommended_products(p_limit integer DEFAULT 12)
 RETURNS jsonb
 LANGUAGE sql
 STABLE
 SET search_path TO 'public', 'pg_temp'
AS $function$
 with pref_categories as (
  select category_id,count(*) weight from (
   select p.category_id from public.favorites f join public.products p on p.id=f.product_id
   where f.user_id=(select auth.uid())
   union all
   select p.category_id from public.orders o
   join public.order_items oi on oi.order_id=o.id
   join public.products p on p.id=oi.product_id
   where o.customer_id=(select auth.uid()) and o.status='delivered'
  ) q where category_id is not null group by category_id
 )
 select coalesce(jsonb_agg(to_jsonb(x)),'[]'::jsonb)
 from (
  select m.*,
    (coalesce(pc.weight,0)*20+coalesce(m.average_rating,0)*4+
     case when m.created_at>now()-interval '14 days' then 4 else 0 end) as recommendation_score
  from public.marketplace_product_cards m
  left join pref_categories pc on pc.category_id=m.category_id
  where m.stock>0
  order by recommendation_score desc,m.average_rating desc,m.created_at desc
  limit greatest(1,least(coalesce(p_limit,12),30))
 ) x;
$function$;


-- function: public.register_my_push_device
CREATE OR REPLACE FUNCTION public.register_my_push_device(p_provider text, p_token text, p_platform text DEFAULT 'android'::text, p_app_version text DEFAULT NULL::text, p_device_model text DEFAULT NULL::text, p_locale text DEFAULT NULL::text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
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
$function$;


-- function: public.replace_my_delivery_zones
CREATE OR REPLACE FUNCTION public.replace_my_delivery_zones(p_zones jsonb)
 RETURNS SETOF delivery_zones
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_uid uuid := auth.uid();
  v_seller_id uuid;
  v_zone jsonb;
  v_area text;
  v_fee numeric;
  v_minutes integer;
  v_sort integer;
  v_is_active boolean;
  v_active_text text;
  v_index integer := 0;
  v_normalized jsonb := '[]'::jsonb;
  v_first_area text;
  v_first_fee numeric;
  v_first_minutes integer;
begin
  if v_uid is null then
    raise exception using errcode='28000', message='Authentication required';
  end if;

  select mp.seller_id into v_seller_id
  from public.merchant_profiles mp
  where mp.user_id=v_uid
    and mp.verification_status='approved'
    and mp.seller_id is not null
  limit 1;

  if v_seller_id is null then
    raise exception using errcode='42501', message='Approved merchant account required';
  end if;

  if jsonb_typeof(coalesce(p_zones,'[]'::jsonb)) <> 'array'
     or jsonb_array_length(coalesce(p_zones,'[]'::jsonb)) not between 1 and 20 then
    raise exception using errcode='22023', message='At least one delivery zone is required';
  end if;

  for v_zone in select value from jsonb_array_elements(p_zones) loop
    v_area := trim(coalesce(v_zone->>'area',''));
    if char_length(v_area) not between 2 and 100 then
      raise exception using errcode='22023', message='Invalid delivery area';
    end if;

    begin
      v_fee := (v_zone->>'fee')::numeric;
      v_minutes := nullif(v_zone->>'estimated_minutes','')::integer;
      v_sort := coalesce(nullif(v_zone->>'sort_order','')::integer,v_index);
    exception when invalid_text_representation or numeric_value_out_of_range then
      raise exception using errcode='22023', message='Invalid delivery fee or estimate';
    end;

    v_active_text := lower(trim(coalesce(v_zone->>'is_active','true')));
    if v_active_text not in ('true','false') then
      raise exception using errcode='22023', message='Invalid delivery-zone availability';
    end if;
    v_is_active := v_active_text='true';

    if v_fee is null or v_fee < 0 then
      raise exception using errcode='22023', message='Invalid delivery fee';
    end if;
    if v_minutes is not null and v_minutes not between 1 and 1440 then
      raise exception using errcode='22023', message='Invalid delivery estimate';
    end if;
    if exists(
      select 1 from jsonb_array_elements(v_normalized) e
      where lower(trim(e->>'area'))=lower(v_area)
    ) then
      raise exception using errcode='22023', message='Duplicate delivery area';
    end if;

    v_normalized := v_normalized || jsonb_build_array(jsonb_build_object(
      'area',v_area,
      'fee',round(v_fee,2),
      'estimated_minutes',v_minutes,
      'sort_order',v_sort,
      'is_active',v_is_active
    ));
    v_index := v_index+1;
  end loop;

  select e->>'area',(e->>'fee')::numeric,nullif(e->>'estimated_minutes','')::integer
  into v_first_area,v_first_fee,v_first_minutes
  from jsonb_array_elements(v_normalized) e
  where coalesce((e->>'is_active')::boolean,true)
  order by coalesce((e->>'sort_order')::integer,0)
  limit 1;

  if v_first_area is null then
    select e->>'area',(e->>'fee')::numeric,nullif(e->>'estimated_minutes','')::integer
    into v_first_area,v_first_fee,v_first_minutes
    from jsonb_array_elements(v_normalized) e
    order by coalesce((e->>'sort_order')::integer,0)
    limit 1;
  end if;

  update public.merchant_profiles
  set delivery_zones=v_normalized,
      delivery_area=v_first_area,
      delivery_fee=v_first_fee,
      estimated_minutes=v_first_minutes,
      updated_at=now()
  where user_id=v_uid
    and seller_id=v_seller_id
    and verification_status='approved';

  update public.delivery_settings
  set base_fee=v_first_fee,
      delivery_area=v_first_area,
      estimated_minutes=v_first_minutes,
      updated_at=now()
  where seller_id=v_seller_id;

  if not found then
    insert into public.delivery_settings(
      seller_id,base_fee,delivery_area,estimated_minutes,notes,is_active,created_at,updated_at
    ) values(v_seller_id,v_first_fee,v_first_area,v_first_minutes,'',true,now(),now());
  end if;

  return query
  select z.* from public.delivery_zones z
  where z.seller_id=v_seller_id
  order by z.sort_order,z.area_name;
end;
$function$;


-- function: public.save_my_delivery_configuration
CREATE OR REPLACE FUNCTION public.save_my_delivery_configuration(p_zones jsonb, p_notes text DEFAULT NULL::text, p_is_active boolean DEFAULT true)
 RETURNS SETOF delivery_zones
 LANGUAGE plpgsql
 SET search_path TO ''
AS $function$
declare
  v_uid uuid := auth.uid();
  v_seller_id uuid;
begin
  if v_uid is null then
    raise exception using errcode='28000', message='Authentication required';
  end if;
  if char_length(coalesce(p_notes,'')) > 500 then
    raise exception using errcode='22023', message='Delivery notes are too long';
  end if;

  select mp.seller_id into v_seller_id
  from public.merchant_profiles mp
  where mp.user_id=v_uid
    and mp.verification_status='approved'
    and mp.seller_id is not null
  limit 1;
  if v_seller_id is null then
    raise exception using errcode='42501', message='Approved merchant account required';
  end if;

  perform public.replace_my_delivery_zones(p_zones);

  if coalesce(p_is_active,false)
     and not exists(
       select 1 from public.delivery_zones z
       where z.seller_id=v_seller_id and z.is_active
     ) then
    raise exception using errcode='22023', message='Enable at least one delivery zone';
  end if;

  update public.delivery_settings d
  set notes=nullif(trim(coalesce(p_notes,'')),''),
      is_active=coalesce(p_is_active,false),
      updated_at=now()
  where d.seller_id=v_seller_id;
  if not found then
    raise exception using errcode='P0001', message='Delivery settings are incomplete';
  end if;

  return query
  select z.* from public.delivery_zones z
  where z.seller_id=v_seller_id
  order by z.sort_order,z.area_name;
end;
$function$;


-- function: public.search_marketplace_ranked
CREATE OR REPLACE FUNCTION public.search_marketplace_ranked(p_query text, p_category_id uuid DEFAULT NULL::uuid, p_city text DEFAULT NULL::text, p_limit integer DEFAULT 30, p_offset integer DEFAULT 0)
 RETURNS jsonb
 LANGUAGE sql
 STABLE
 SET search_path TO 'public', 'pg_temp'
AS $function$
  select coalesce(jsonb_agg(to_jsonb(x)),'[]'::jsonb)
  from (
    select m.*,
      (case when lower(m.name)=lower(trim(coalesce(p_query,''))) then 100 else 0 end
       + case when lower(m.name) like lower(trim(coalesce(p_query,'')))||'%' then 40 else 0 end
       + case when lower(m.name) like '%'||lower(trim(coalesce(p_query,'')))||'%' then 20 else 0 end
       + case when lower(coalesce(m.description,'')) like '%'||lower(trim(coalesce(p_query,'')))||'%' then 8 else 0 end
       + coalesce(m.average_rating,0)*3
       + case when m.stock>0 then 5 else 0 end) as rank_score
    from public.marketplace_product_cards m
    left join public.stores st on st.seller_id=m.seller_id and st.is_active
    where m.stock>0
      and (p_category_id is null or m.category_id=p_category_id)
      and (p_city is null or lower(coalesce(st.city,''))=lower(p_city))
      and (coalesce(trim(p_query),'')='' or lower(m.name) like '%'||lower(trim(p_query))||'%'
           or lower(coalesce(m.description,'')) like '%'||lower(trim(p_query))||'%'
           or lower(coalesce(m.store_name,'')) like '%'||lower(trim(p_query))||'%')
    order by rank_score desc,m.average_rating desc,m.created_at desc
    limit greatest(1,least(coalesce(p_limit,30),60))
    offset greatest(coalesce(p_offset,0),0)
  ) x;
$function$;


-- function: public.search_products
CREATE OR REPLACE FUNCTION public.search_products(p_query text DEFAULT NULL::text, p_category_id uuid DEFAULT NULL::uuid, p_min_price numeric DEFAULT NULL::numeric, p_max_price numeric DEFAULT NULL::numeric, p_available_only boolean DEFAULT true, p_sort text DEFAULT 'relevance'::text, p_limit integer DEFAULT 20, p_offset integer DEFAULT 0)
 RETURNS SETOF products
 LANGUAGE sql
 STABLE
 SET search_path TO 'public', 'pg_temp'
AS $function$
 SELECT p.* FROM public.products p
 WHERE (p_query IS NULL OR btrim(p_query)='' OR p.name ILIKE '%'||btrim(p_query)||'%')
 AND (p_category_id IS NULL OR p.category_id=p_category_id)
 AND (p_min_price IS NULL OR p.price>=p_min_price)
 AND (p_max_price IS NULL OR p.price<=p_max_price)
 AND (NOT p_available_only OR (p.is_active=true AND p.stock>0))
 ORDER BY CASE WHEN p_sort='price_asc' THEN p.price END ASC NULLS LAST, CASE WHEN p_sort='price_desc' THEN p.price END DESC NULLS LAST, CASE WHEN p_sort='newest' THEN p.created_at END DESC NULLS LAST, p.created_at DESC
 LIMIT LEAST(GREATEST(COALESCE(p_limit,20),1),100) OFFSET GREATEST(COALESCE(p_offset,0),0);
$function$;


-- function: public.set_app_design_tokens_updated_at
CREATE OR REPLACE FUNCTION public.set_app_design_tokens_updated_at()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
begin new.updated_at = now(); return new; end; $function$;


-- function: public.set_updated_at
CREATE OR REPLACE FUNCTION public.set_updated_at()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$ begin new.updated_at = now(); return new; end; $function$;


-- function: public.soft_delete_my_account
CREATE OR REPLACE FUNCTION public.soft_delete_my_account()
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare
  v_uid uuid := (select auth.uid());
begin
  if v_uid is null then
    raise exception 'authentication required';
  end if;

  perform set_config('tani.merchant_workflow','submit',true);

  update public.products p
  set is_active=false, updated_at=now()
  where exists (
    select 1 from public.sellers s
    where s.id=p.seller_id and s.user_id=v_uid
  );

  update public.stores st
  set is_active=false, is_open=false, updated_at=now()
  where exists (
    select 1 from public.sellers s
    where s.id=st.seller_id and s.user_id=v_uid
  )
  or exists (
    select 1 from public.merchant_profiles mp
    where mp.id=st.merchant_id and mp.user_id=v_uid
  );

  update public.merchant_profiles
  set verification_status='suspended',
      suspended_at=coalesce(suspended_at,now()),
      trust_badge=false,
      review_note='Account deleted by owner',
      updated_at=now()
  where user_id=v_uid;

  update public.sellers
  set verification_status='suspended'
  where user_id=v_uid;

  update public.profiles
  set is_active=false,
      deleted_at=coalesce(deleted_at,now()),
      name='محذوف',
      phone='deleted-'||id::text,
      avatar_url=null,
      updated_at=now()
  where id=v_uid;

  insert into public.audit_logs(actor_id,action,entity_type,entity_id,source)
  values(v_uid,'soft_delete','profile',v_uid,'account');

  return found;
end;
$function$;


-- function: public.submit_merchant_application
CREATE OR REPLACE FUNCTION public.submit_merchant_application(p_business_name text, p_description text, p_phone text, p_whatsapp text, p_category_id uuid, p_requested_category text, p_store_name text, p_store_description text, p_city text, p_area text, p_delivery_area text, p_delivery_fee numeric, p_estimated_minutes integer, p_delivery_zones jsonb, p_identity_path text, p_document_type text, p_accept_policies boolean, p_policy_version text DEFAULT '2026-09'::text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_uid uuid := auth.uid();
  v_merchant_id uuid;
  v_auth_phone text;
  v_phone_confirmed_at timestamptz;
  v_effective_phone_verified_at timestamptz;
  v_status text;
  v_category_id uuid := p_category_id;
  v_requested_category text := nullif(trim(coalesce(p_requested_category,'')), '');
  v_zone jsonb;
  v_zone_area text;
  v_zone_fee numeric;
  v_zone_minutes integer;
  v_zone_index integer := 0;
  v_normalized_zones jsonb := '[]'::jsonb;
begin
  if v_uid is null then raise exception 'Authentication required'; end if;

  select u.phone,u.phone_confirmed_at
  into v_auth_phone,v_phone_confirmed_at
  from auth.users u where u.id=v_uid;

  if v_phone_confirmed_at is not null
     and regexp_replace(coalesce(v_auth_phone,''),'[^0-9]','','g')
        = regexp_replace(coalesce(p_phone,''),'[^0-9]','','g') then
    v_effective_phone_verified_at := v_phone_confirmed_at;
  else
    v_effective_phone_verified_at := null;
  end if;

  if char_length(trim(coalesce(p_business_name,''))) not between 2 and 120 then
    raise exception 'Business name is required';
  end if;
  if char_length(trim(coalesce(p_store_name,''))) not between 2 and 120 then
    raise exception 'Store name is required';
  end if;
  if char_length(trim(coalesce(p_description,''))) > 1000
     or char_length(trim(coalesce(p_store_description,''))) > 1000 then
    raise exception 'Description is too long';
  end if;
  if char_length(trim(coalesce(p_phone,''))) not between 7 and 30 then
    raise exception 'Invalid phone number';
  end if;
  if char_length(trim(coalesce(p_city,''))) not between 2 and 100 then
    raise exception 'City is required';
  end if;
  if not coalesce(p_accept_policies,false) then
    raise exception 'Merchant policies must be accepted';
  end if;

  if v_category_id is not null then
    if not exists(select 1 from public.categories c where c.id=v_category_id and c.is_active=true) then
      raise exception 'Store category is invalid';
    end if;
    v_requested_category := null;
  else
    if v_requested_category is null or char_length(v_requested_category) not between 2 and 80 then
      raise exception 'Choose a category or enter a requested category';
    end if;
    select c.id into v_category_id
    from public.categories c
    where lower(trim(c.name))=lower(v_requested_category)
    limit 1;
    if v_category_id is null then
      insert into public.categories(name,slug,sort_order,is_active,created_at,updated_at)
      values(
        v_requested_category,
        'requested-' || left(md5(lower(v_requested_category)),16),
        999,
        false,
        now(),
        now()
      )
      returning id into v_category_id;
    end if;
  end if;

  if jsonb_typeof(coalesce(p_delivery_zones,'[]'::jsonb)) <> 'array'
     or jsonb_array_length(coalesce(p_delivery_zones,'[]'::jsonb)) not between 1 and 20 then
    raise exception 'At least one delivery zone is required';
  end if;

  for v_zone in select value from jsonb_array_elements(p_delivery_zones) loop
    v_zone_area := trim(coalesce(v_zone->>'area',''));
    if char_length(v_zone_area) not between 2 and 100 then
      raise exception 'Invalid delivery area';
    end if;
    begin
      v_zone_fee := (v_zone->>'fee')::numeric;
      v_zone_minutes := nullif(v_zone->>'estimated_minutes','')::integer;
    exception when invalid_text_representation or numeric_value_out_of_range then
      raise exception 'Invalid delivery fee or estimate';
    end;
    if v_zone_fee is null or v_zone_fee < 0 then raise exception 'Invalid delivery fee'; end if;
    if v_zone_minutes is not null and v_zone_minutes not between 1 and 1440 then
      raise exception 'Invalid delivery estimate';
    end if;
    v_normalized_zones := v_normalized_zones || jsonb_build_array(jsonb_build_object(
      'area',v_zone_area,
      'fee',v_zone_fee,
      'estimated_minutes',v_zone_minutes,
      'sort_order',v_zone_index
    ));
    v_zone_index := v_zone_index + 1;
  end loop;

  if p_document_type not in ('national_id','passport','other') then
    raise exception 'Invalid identity document type';
  end if;
  if coalesce(p_identity_path,'')='' or split_part(p_identity_path,'/',1)<>v_uid::text then
    raise exception 'Identity document is required';
  end if;
  if not exists(
    select 1 from storage.objects o
    where o.bucket_id='merchant-private'
      and o.name=p_identity_path
      and o.owner_id=v_uid::text
  ) then
    raise exception 'Identity document was not uploaded';
  end if;

  select mp.id,mp.verification_status into v_merchant_id,v_status
  from public.merchant_profiles mp where mp.user_id=v_uid;
  if v_status in ('approved','suspended') then
    raise exception 'This merchant profile cannot be resubmitted';
  end if;

  perform set_config('tani.merchant_workflow','submit',true);

  insert into public.merchant_profiles(
    user_id,business_name,description,phone,whatsapp,category_id,requested_category,
    store_name,store_description,city,area,delivery_area,delivery_fee,estimated_minutes,delivery_zones,
    phone_verified_at,policies_accepted_at,policy_version,submitted_at,
    verification_status,review_note,requested_changes_at,updated_at
  )
  values(
    v_uid,trim(p_business_name),trim(coalesce(p_description,'')),trim(p_phone),
    nullif(trim(coalesce(p_whatsapp,'')),''),v_category_id,v_requested_category,
    trim(p_store_name),trim(coalesce(p_store_description,'')),trim(p_city),
    nullif(trim(coalesce(p_area,'')),''),
    v_normalized_zones->0->>'area',
    (v_normalized_zones->0->>'fee')::numeric,
    nullif(v_normalized_zones->0->>'estimated_minutes','')::integer,
    v_normalized_zones,
    v_effective_phone_verified_at,now(),trim(p_policy_version),now(),
    'pending',null,null,now()
  )
  on conflict(user_id) do update set
    business_name=excluded.business_name,
    description=excluded.description,
    phone=excluded.phone,
    whatsapp=excluded.whatsapp,
    category_id=excluded.category_id,
    requested_category=excluded.requested_category,
    store_name=excluded.store_name,
    store_description=excluded.store_description,
    city=excluded.city,
    area=excluded.area,
    delivery_area=excluded.delivery_area,
    delivery_fee=excluded.delivery_fee,
    estimated_minutes=excluded.estimated_minutes,
    delivery_zones=excluded.delivery_zones,
    phone_verified_at=excluded.phone_verified_at,
    policies_accepted_at=excluded.policies_accepted_at,
    policy_version=excluded.policy_version,
    submitted_at=excluded.submitted_at,
    verification_status='pending',
    review_note=null,
    requested_changes_at=null,
    updated_at=now()
  returning id into v_merchant_id;

  insert into public.merchant_identity_documents(
    merchant_id,user_id,document_type,storage_path,created_at
  ) values(v_merchant_id,v_uid,p_document_type,p_identity_path,now())
  on conflict(merchant_id,storage_path) do nothing;

  insert into public.audit_logs(actor_id,action,entity_type,entity_id,source)
  values(v_uid,'submit','merchant_profile',v_merchant_id,'merchant_onboarding');

  return v_merchant_id;
end;
$function$;


-- function: public.submit_merchant_application
CREATE OR REPLACE FUNCTION public.submit_merchant_application(p_business_name text, p_description text, p_phone text, p_whatsapp text, p_category_id uuid, p_store_name text, p_store_description text, p_city text, p_area text, p_delivery_area text, p_delivery_fee numeric, p_estimated_minutes integer, p_identity_path text, p_document_type text, p_accept_policies boolean, p_policy_version text DEFAULT '2026-09'::text)
 RETURNS uuid
 LANGUAGE sql
 SET search_path TO 'public', 'pg_temp'
AS $function$
  select public.submit_merchant_application(
    p_business_name,
    p_description,
    p_phone,
    p_whatsapp,
    p_category_id,
    null,
    p_store_name,
    p_store_description,
    p_city,
    p_area,
    p_delivery_area,
    p_delivery_fee,
    p_estimated_minutes,
    jsonb_build_array(jsonb_build_object(
      'area',p_delivery_area,
      'fee',p_delivery_fee,
      'estimated_minutes',p_estimated_minutes
    )),
    p_identity_path,
    p_document_type,
    p_accept_policies,
    p_policy_version
  );
$function$;


-- function: public.sync_my_cart
CREATE OR REPLACE FUNCTION public.sync_my_cart(p_items jsonb)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_uid uuid := auth.uid();
  v_cart_id uuid;
  v_item jsonb;
  v_product record;
  v_qty int;
  v_product_id uuid;
begin
  if v_uid is null then
    raise exception 'Authentication required';
  end if;

  if jsonb_typeof(coalesce(p_items, '[]'::jsonb)) <> 'array' then
    raise exception 'Invalid cart';
  end if;

  if jsonb_array_length(coalesce(p_items, '[]'::jsonb)) > 100 then
    raise exception 'Cart has too many items';
  end if;

  insert into public.carts(user_id, created_at, updated_at)
  values(v_uid, now(), now())
  on conflict (user_id) do update set updated_at = now()
  returning id into v_cart_id;

  delete from public.cart_items where cart_id = v_cart_id;

  for v_item in
    select jsonb_build_object(
      'product_id', product_id::text,
      'quantity', sum(quantity)::int
    )
    from (
      select
        (x.value ->> 'product_id')::uuid as product_id,
        greatest(1, least(99, coalesce((x.value ->> 'quantity')::int, 1))) as quantity
      from jsonb_array_elements(coalesce(p_items, '[]'::jsonb)) x(value)
      where x.value ? 'product_id'
    ) q
    group by product_id
    order by product_id
  loop
    v_product_id := (v_item ->> 'product_id')::uuid;
    v_qty := (v_item ->> 'quantity')::int;

    select p.id, p.stock
    into v_product
    from public.products p
    join public.sellers s on s.id = p.seller_id
    where p.id = v_product_id
      and p.is_active = true
      and s.verification_status = 'approved';

    if not found then
      raise exception 'Product is not available';
    end if;

    if v_product.stock < v_qty then
      raise exception 'Requested quantity is not available';
    end if;

    insert into public.cart_items(cart_id, product_id, quantity, created_at, updated_at)
    values(v_cart_id, v_product_id, v_qty, now(), now());
  end loop;

  return v_cart_id;
end;
$function$;


-- function: public.sync_my_cart_v2
CREATE OR REPLACE FUNCTION public.sync_my_cart_v2(p_items jsonb)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_uid uuid := auth.uid();
  v_cart_id uuid;
  v_item jsonb;
  v_product record;
  v_variant record;
  v_product_id uuid;
  v_variant_id uuid;
  v_qty int;
begin
  if v_uid is null then
    raise exception using errcode = '28000', message = 'يجب تسجيل الدخول أولاً';
  end if;

  insert into public.carts(user_id, created_at, updated_at)
  values(v_uid, now(), now())
  on conflict (user_id) do update set updated_at = now()
  returning id into v_cart_id;

  delete from public.cart_items where cart_id = v_cart_id;

  for v_item in select value from jsonb_array_elements(private.normalize_checkout_items(p_items))
  loop
    v_product_id := (v_item ->> 'product_id')::uuid;
    v_variant_id := nullif(v_item ->> 'variant_id', '')::uuid;
    v_qty := (v_item ->> 'quantity')::int;

    select p.id, p.stock, p.has_variants into v_product
    from public.products p
    join public.sellers s on s.id = p.seller_id
    where p.id = v_product_id and p.is_active = true and s.verification_status = 'approved';
    if not found then
      raise exception using errcode = 'P0001', message = 'أحد المنتجات لم يعد متاحاً';
    end if;

    if v_product.has_variants then
      if v_variant_id is null then
        raise exception using errcode = '22023', message = 'اختاري المقاس أو اللون للمنتج';
      end if;
      select v.id, v.stock into v_variant
      from public.product_variants v
      where v.id = v_variant_id and v.product_id = v_product_id and v.is_active = true;
      if not found then
        raise exception using errcode = 'P0001', message = 'خيار المنتج لم يعد متاحاً';
      end if;
      if v_variant.stock < v_qty then
        raise exception using errcode = 'P0001', message = 'الكمية المطلوبة من الخيار غير متاحة';
      end if;
    else
      if v_variant_id is not null then
        raise exception using errcode = '22023', message = 'الخيار لا يتبع هذا المنتج';
      end if;
      if v_product.stock < v_qty then
        raise exception using errcode = 'P0001', message = 'الكمية المطلوبة غير متاحة';
      end if;
    end if;

    insert into public.cart_items(cart_id, product_id, variant_id, quantity, created_at, updated_at)
    values(v_cart_id, v_product_id, v_variant_id, v_qty, now(), now());
  end loop;
  return v_cart_id;
end;
$function$;


-- function: public.transition_order_status
CREATE OR REPLACE FUNCTION public.transition_order_status(p_order_id uuid, p_to_status text, p_note text DEFAULT NULL::text)
 RETURNS text
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  v_uid uuid := auth.uid();
  v_order public.orders%rowtype;
  v_role text;
  v_is_seller boolean := false;
  v_allowed boolean := false;
  v_eta_text text;
  v_eta_minutes integer;
begin
  if v_uid is null then
    raise exception using errcode = '28000', message = 'Authentication required';
  end if;
  if char_length(coalesce(p_note, '')) > 500 then
    raise exception using errcode = '22023', message = 'Status note is too long';
  end if;
  if p_to_status not in (
    'pending','accepted','preparing','ready','out_for_delivery',
    'delivered','cancelled','rejected','failed'
  ) then
    raise exception using errcode = '22023', message = 'Invalid order status';
  end if;

  select * into v_order
  from public.orders
  where id = p_order_id
  for update;
  if not found then
    raise exception using errcode = 'P0001', message = 'Order not found';
  end if;

  select public.current_user_role() into v_role;
  select exists(
    select 1
    from public.sellers s
    where s.id = v_order.seller_id and s.user_id = v_uid
  ) into v_is_seller;

  if v_order.status in ('delivered','cancelled','rejected','failed') then
    raise exception using errcode = 'P0001', message = 'Order is already in a final state';
  end if;

  if v_is_seller and v_role <> 'admin' then
    if p_to_status in ('rejected','cancelled','failed')
       and char_length(trim(coalesce(p_note, ''))) < 3 then
      raise exception using errcode = '22023', message = 'A reason is required for this status';
    end if;

    if p_to_status = 'out_for_delivery' then
      v_eta_text := substring(
        coalesce(p_note, '')
        from 'الوقت المتوقع للوصول: ([0-9]+) دقيقة'
      );
      if v_eta_text is null then
        raise exception using errcode = '22023', message = 'A valid delivery ETA is required';
      end if;
      v_eta_minutes := v_eta_text::integer;
      if v_eta_minutes not between 1 and 1440 then
        raise exception using errcode = '22023', message = 'A valid delivery ETA is required';
      end if;
    end if;
  end if;

  if v_role = 'admin' then
    v_allowed := true;
  elsif v_order.customer_id = v_uid then
    v_allowed := (v_order.status = 'pending' and p_to_status = 'cancelled');
  elsif v_is_seller then
    v_allowed := case v_order.status
      when 'pending' then p_to_status in ('accepted','rejected')
      when 'accepted' then p_to_status in ('preparing','cancelled')
      when 'preparing' then p_to_status in ('ready','cancelled')
      when 'ready' then p_to_status in ('out_for_delivery','cancelled')
      when 'out_for_delivery' then p_to_status in ('delivered','failed')
      else false
    end;
  end if;
  if not v_allowed then
    raise exception using errcode = 'P0001', message = 'Status transition is not allowed';
  end if;

  if p_to_status in ('cancelled','rejected','failed')
     and v_order.stock_restored_at is null then
    update public.product_variants v
    set stock = v.stock + oi.quantity,
        updated_at = now()
    from public.order_items oi
    where oi.order_id = v_order.id
      and oi.product_id = v.product_id
      and coalesce(oi.variant_snapshot ->> 'id', '') = v.id::text;

    update public.products p
    set stock = p.stock + oi.quantity,
        is_active = case when p.stock = 0 then true else p.is_active end,
        updated_at = now()
    from public.order_items oi
    where oi.order_id = v_order.id
      and oi.product_id = p.id
      and coalesce(oi.variant_snapshot ->> 'id', '') = '';

    update public.orders
    set stock_restored_at = now()
    where id = v_order.id;
  end if;

  update public.orders
  set status = p_to_status,
      payment_status = case
        when p_to_status = 'delivered' and payment_method = 'cod' then 'paid'
        else payment_status
      end,
      updated_at = now()
  where id = v_order.id;

  insert into public.order_status_history(
    order_id, from_status, to_status, changed_by, note, created_at
  ) values (
    v_order.id,
    v_order.status,
    p_to_status,
    v_uid,
    nullif(trim(coalesce(p_note, '')), ''),
    now()
  );

  return p_to_status;
end;
$function$;


-- function: public.unregister_my_push_device
CREATE OR REPLACE FUNCTION public.unregister_my_push_device(p_provider text, p_token text)
 RETURNS boolean
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
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
$function$;


-- function: public.weekly_marketplace_kpis
CREATE OR REPLACE FUNCTION public.weekly_marketplace_kpis(p_start timestamp with time zone DEFAULT NULL::timestamp with time zone, p_end timestamp with time zone DEFAULT NULL::timestamp with time zone)
 RETURNS jsonb
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'public', 'pg_temp'
AS $function$
declare v_start timestamptz:=coalesce(p_start,now()-interval '7 days'); v_end timestamptz:=coalesce(p_end,now());
begin
 if public.current_user_role() not in ('admin','support') then raise exception 'staff required'; end if;
 return jsonb_build_object(
  'start',v_start,'end',v_end,
  'orders',(select count(*) from public.orders where created_at>=v_start and created_at<v_end),
  'completed_orders',(select count(*) from public.orders where status='delivered' and created_at>=v_start and created_at<v_end),
  'cancelled_orders',(select count(*) from public.orders where status in ('cancelled','rejected','failed') and created_at>=v_start and created_at<v_end),
  'gmv',(select coalesce(sum(total),0) from public.orders where status='delivered' and created_at>=v_start and created_at<v_end),
  'active_customers',(select count(distinct customer_id) from public.orders where created_at>=v_start and created_at<v_end),
  'active_merchants',(select count(distinct seller_id) from public.orders where created_at>=v_start and created_at<v_end),
  'repeat_customers',(select count(*) from (select customer_id from public.orders where status='delivered' and created_at<v_end group by customer_id having count(*)>1) q),
  'product_views',(select count(*) from public.app_events where event_name='product_view' and created_at>=v_start and created_at<v_end),
  'searches',(select count(*) from public.app_events where event_name in ('search','search_submitted') and created_at>=v_start and created_at<v_end),
  'complaints',(select count(*) from public.complaints where created_at>=v_start and created_at<v_end),
  'app_errors',(select count(*) from public.app_errors where created_at>=v_start and created_at<v_end)
 );
end; $function$;


-- view: marketplace_product_cards
CREATE VIEW public.marketplace_product_cards WITH (security_invoker=true) AS  SELECT p.id,
    p.seller_id,
    p.category_id,
    c.name AS category_name,
    p.name,
    p.description,
    (
        CASE
            WHEN p.has_variants THEN COALESCE(vs.min_price, p.price)
            ELSE p.price
        END)::numeric(12,2) AS price,
        CASE
            WHEN p.has_variants THEN COALESCE(vs.available_stock, 0)
            ELSE p.stock
        END AS stock,
    COALESCE(p.image, pi.storage_path) AS image,
    p.created_at,
    COALESCE(st.name, s.store_name) AS store_name,
    s.verification_status,
    COALESCE(round(avg(r.rating), 2), (0)::numeric) AS average_rating,
    (count(r.id))::integer AS review_count,
    COALESCE(ds.base_fee, (0)::numeric) AS delivery_fee,
    ds.delivery_area,
    ds.estimated_minutes,
    p.has_variants
   FROM (((((((products p
     JOIN sellers s ON ((s.id = p.seller_id)))
     LEFT JOIN stores st ON (((st.seller_id = p.seller_id) AND (st.is_active = true))))
     LEFT JOIN categories c ON ((c.id = p.category_id)))
     LEFT JOIN LATERAL ( SELECT pimg.storage_path
           FROM product_images pimg
          WHERE ((pimg.product_id = p.id) AND (pimg.media_type = 'image'::text))
          ORDER BY pimg.is_primary DESC, pimg.sort_order, pimg.created_at
         LIMIT 1) pi ON (true))
     LEFT JOIN LATERAL ( SELECT min(COALESCE(v.price, p.price)) FILTER (WHERE v.is_active) AS min_price,
            (COALESCE(sum(v.stock) FILTER (WHERE v.is_active), (0)::bigint))::integer AS available_stock
           FROM product_variants v
          WHERE (v.product_id = p.id)) vs ON (true))
     LEFT JOIN reviews r ON ((r.product_id = p.id)))
     LEFT JOIN LATERAL ( SELECT d.base_fee,
            d.delivery_area,
            d.estimated_minutes
           FROM delivery_settings d
          WHERE ((d.seller_id = p.seller_id) AND (d.is_active = true))
          ORDER BY d.updated_at DESC, d.created_at DESC
         LIMIT 1) ds ON (true))
  WHERE ((p.is_active = true) AND (s.verification_status = 'approved'::text))
  GROUP BY p.id, p.seller_id, p.category_id, c.name, p.name, p.description, p.price, p.stock, p.image, pi.storage_path, p.created_at, st.name, s.store_name, s.verification_status, ds.base_fee, ds.delivery_area, ds.estimated_minutes, p.has_variants, vs.min_price, vs.available_stock;

-- view: marketplace_store_cards
CREATE VIEW public.marketplace_store_cards WITH (security_invoker=true) AS  SELECT st.id,
    st.merchant_id,
    st.seller_id,
    st.name,
    st.description,
    st.logo_url,
    st.cover_url,
    st.city,
    st.area,
    st.is_open,
    st.created_at,
    s.verification_status,
    COALESCE(ds.base_fee, (0)::numeric) AS delivery_fee,
    ds.delivery_area,
    ds.estimated_minutes,
    (count(DISTINCT p.id))::integer AS product_count,
    COALESCE(round(avg(r.rating), 2), (0)::numeric) AS average_rating,
    (count(r.id))::integer AS review_count
   FROM ((((stores st
     JOIN sellers s ON ((s.id = st.seller_id)))
     LEFT JOIN LATERAL ( SELECT d.base_fee,
            d.delivery_area,
            d.estimated_minutes
           FROM delivery_settings d
          WHERE ((d.seller_id = st.seller_id) AND (d.is_active = true))
          ORDER BY d.updated_at DESC, d.created_at DESC
         LIMIT 1) ds ON (true))
     LEFT JOIN products p ON (((p.seller_id = st.seller_id) AND (p.is_active = true))))
     LEFT JOIN reviews r ON ((r.product_id = p.id)))
  WHERE ((st.is_active = true) AND (s.verification_status = 'approved'::text))
  GROUP BY st.id, st.merchant_id, st.seller_id, st.name, st.description, st.logo_url, st.cover_url, st.city, st.area, st.is_open, st.created_at, s.verification_status, ds.base_fee, ds.delivery_area, ds.estimated_minutes;

-- view: my_cart_items
CREATE VIEW public.my_cart_items WITH (security_invoker=true) AS  SELECT ci.id AS cart_item_id,
    ci.cart_id,
    ci.product_id,
    ci.variant_id,
    ci.quantity,
    p.seller_id,
    p.category_id,
    p.name,
    p.description,
    p.price,
    p.stock,
    COALESCE(p.image, pi.storage_path) AS image,
    COALESCE(st.name, s.store_name) AS store_name,
    COALESCE(ds.base_fee, (0)::numeric) AS delivery_fee,
    ds.delivery_area,
    ds.estimated_minutes,
    ci.updated_at,
    pv.name AS variant_name,
    pv.sku AS variant_sku,
    pv.price AS variant_price,
    pv.stock AS variant_stock,
    pv.attributes AS variant_attributes
   FROM (((((((cart_items ci
     JOIN carts c ON ((c.id = ci.cart_id)))
     JOIN products p ON ((p.id = ci.product_id)))
     JOIN sellers s ON ((s.id = p.seller_id)))
     LEFT JOIN product_variants pv ON (((pv.id = ci.variant_id) AND (pv.product_id = p.id))))
     LEFT JOIN stores st ON (((st.seller_id = p.seller_id) AND (st.is_active = true))))
     LEFT JOIN LATERAL ( SELECT pimg.storage_path
           FROM product_images pimg
          WHERE ((pimg.product_id = p.id) AND (pimg.media_type = 'image'::text))
          ORDER BY pimg.is_primary DESC, pimg.sort_order, pimg.created_at
         LIMIT 1) pi ON (true))
     LEFT JOIN delivery_settings ds ON (((ds.seller_id = p.seller_id) AND (ds.is_active = true))))
  WHERE ((c.user_id = ( SELECT auth.uid() AS uid)) AND (p.is_active = true) AND (s.verification_status = 'approved'::text) AND (((p.has_variants = true) AND (ci.variant_id IS NOT NULL) AND (pv.is_active = true)) OR ((p.has_variants = false) AND (ci.variant_id IS NULL))));

-- index: addresses_one_default_per_user_uidx
CREATE UNIQUE INDEX addresses_one_default_per_user_uidx ON public.addresses USING btree (user_id) WHERE (is_default = true);

-- index: addresses_user_id_idx
CREATE INDEX addresses_user_id_idx ON public.addresses USING btree (user_id);

-- index: app_errors_created_at_idx
CREATE INDEX app_errors_created_at_idx ON public.app_errors USING btree (created_at DESC);

-- index: app_errors_source_created_idx
CREATE INDEX app_errors_source_created_idx ON public.app_errors USING btree (source, created_at DESC);

-- index: app_errors_user_created_idx
CREATE INDEX app_errors_user_created_idx ON public.app_errors USING btree (user_id, created_at DESC);

-- index: app_events_created_at_idx
CREATE INDEX app_events_created_at_idx ON public.app_events USING btree (created_at DESC);

-- index: app_events_entity_idx
CREATE INDEX app_events_entity_idx ON public.app_events USING btree (entity_type, entity_id, created_at DESC);

-- index: app_events_name_created_idx
CREATE INDEX app_events_name_created_idx ON public.app_events USING btree (event_name, created_at DESC);

-- index: app_events_user_created_idx
CREATE INDEX app_events_user_created_idx ON public.app_events USING btree (user_id, created_at DESC);

-- index: app_settings_updated_by_idx
CREATE INDEX app_settings_updated_by_idx ON public.app_settings USING btree (updated_by);

-- index: audit_logs_actor_id_idx
CREATE INDEX audit_logs_actor_id_idx ON public.audit_logs USING btree (actor_id);

-- index: audit_logs_entity_idx
CREATE INDEX audit_logs_entity_idx ON public.audit_logs USING btree (entity_type, entity_id, created_at DESC);

-- index: billing_transactions_payment_method_idx
CREATE INDEX billing_transactions_payment_method_idx ON public.billing_transactions USING btree (payment_method);

-- index: billing_transactions_seller_idx
CREATE INDEX billing_transactions_seller_idx ON public.billing_transactions USING btree (seller_id, created_at DESC);

-- index: billing_transactions_user_idx
CREATE INDEX billing_transactions_user_idx ON public.billing_transactions USING btree (user_id, created_at DESC);

-- index: cart_items_cart_id_idx
CREATE INDEX cart_items_cart_id_idx ON public.cart_items USING btree (cart_id);

-- index: cart_items_product_id_idx
CREATE INDEX cart_items_product_id_idx ON public.cart_items USING btree (product_id);

-- index: cart_items_variant_id_idx
CREATE INDEX cart_items_variant_id_idx ON public.cart_items USING btree (variant_id);

-- index: complaints_assigned_to_idx
CREATE INDEX complaints_assigned_to_idx ON public.complaints USING btree (assigned_to);

-- index: complaints_created_idx
CREATE INDEX complaints_created_idx ON public.complaints USING btree (created_at DESC, status);

-- index: complaints_order_idx
CREATE INDEX complaints_order_idx ON public.complaints USING btree (order_id);

-- index: complaints_resolved_by_idx
CREATE INDEX complaints_resolved_by_idx ON public.complaints USING btree (resolved_by);

-- index: complaints_seller_status_idx
CREATE INDEX complaints_seller_status_idx ON public.complaints USING btree (seller_id, status, created_at DESC);

-- index: complaints_user_status_idx
CREATE INDEX complaints_user_status_idx ON public.complaints USING btree (user_id, status, created_at DESC);

-- index: conversations_customer_idx
CREATE INDEX conversations_customer_idx ON public.conversations USING btree (customer_id);

-- index: conversations_order_idx
CREATE INDEX conversations_order_idx ON public.conversations USING btree (order_id);

-- index: conversations_seller_idx
CREATE INDEX conversations_seller_idx ON public.conversations USING btree (seller_id);

-- index: coupon_usages_order_idx
CREATE INDEX coupon_usages_order_idx ON public.coupon_usages USING btree (order_id);

-- index: coupon_usages_user_idx
CREATE INDEX coupon_usages_user_idx ON public.coupon_usages USING btree (user_id);

-- index: delivery_options_order_id_idx
CREATE INDEX delivery_options_order_id_idx ON public.delivery_options USING btree (order_id);

-- index: delivery_provider_assignments_provider_code_idx
CREATE INDEX delivery_provider_assignments_provider_code_idx ON public.delivery_provider_assignments USING btree (provider_code);

-- index: delivery_zones_seller_active_idx
CREATE INDEX delivery_zones_seller_active_idx ON public.delivery_zones USING btree (seller_id, is_active, sort_order);

-- index: disputes_opened_by_idx
CREATE INDEX disputes_opened_by_idx ON public.disputes USING btree (opened_by);

-- index: disputes_order_id_idx
CREATE INDEX disputes_order_id_idx ON public.disputes USING btree (order_id);

-- index: disputes_resolved_by_idx
CREATE INDEX disputes_resolved_by_idx ON public.disputes USING btree (resolved_by);

-- index: favorites_product_idx
CREATE INDEX favorites_product_idx ON public.favorites USING btree (product_id);

-- index: favorites_user_created_idx
CREATE INDEX favorites_user_created_idx ON public.favorites USING btree (user_id, created_at DESC);

-- index: favorites_user_id_idx
CREATE INDEX favorites_user_id_idx ON public.favorites USING btree (user_id);

-- index: feature_flags_updated_by_idx
CREATE INDEX feature_flags_updated_by_idx ON public.feature_flags USING btree (updated_by);

-- index: featured_placements_product_idx
CREATE INDEX featured_placements_product_idx ON public.featured_placements USING btree (product_id);

-- index: featured_placements_request_idx
CREATE INDEX featured_placements_request_idx ON public.featured_placements USING btree (request_id);

-- index: featured_placements_seller_idx
CREATE INDEX featured_placements_seller_idx ON public.featured_placements USING btree (seller_id);

-- index: featured_requests_product_idx
CREATE INDEX featured_requests_product_idx ON public.featured_requests USING btree (product_id);

-- index: featured_requests_reviewed_by_idx
CREATE INDEX featured_requests_reviewed_by_idx ON public.featured_requests USING btree (reviewed_by);

-- index: featured_requests_seller_status_idx
CREATE INDEX featured_requests_seller_status_idx ON public.featured_requests USING btree (seller_id, status, created_at DESC);

-- index: idx_addresses_default
CREATE INDEX idx_addresses_default ON public.addresses USING btree (user_id, is_default) WHERE (is_default = true);

-- index: idx_banners_active_order
CREATE INDEX idx_banners_active_order ON public.banners USING btree (is_active, sort_order);

-- index: idx_banners_schedule
CREATE INDEX idx_banners_schedule ON public.banners USING btree (starts_at, ends_at);

-- index: idx_categories_active_order
CREATE INDEX idx_categories_active_order ON public.categories USING btree (is_active, sort_order);

-- index: idx_featured_placements_active_product
CREATE INDEX idx_featured_placements_active_product ON public.featured_placements USING btree (is_active, product_id, starts_at, ends_at);

-- index: idx_products_active_stock
CREATE INDEX idx_products_active_stock ON public.products USING btree (is_active, stock);

-- index: idx_products_name_lower
CREATE INDEX idx_products_name_lower ON public.products USING btree (lower(name));

-- index: idx_products_price
CREATE INDEX idx_products_price ON public.products USING btree (price);

-- index: idx_products_search_description_trgm
CREATE INDEX idx_products_search_description_trgm ON public.products USING gin (description gin_trgm_ops);

-- index: idx_products_search_name_trgm
CREATE INDEX idx_products_search_name_trgm ON public.products USING gin (name gin_trgm_ops);

-- index: idx_sellers_store_name_trgm
CREATE INDEX idx_sellers_store_name_trgm ON public.sellers USING gin (store_name gin_trgm_ops);

-- index: idx_stores_city_area
CREATE INDEX idx_stores_city_area ON public.stores USING btree (city, area);

-- index: idx_stores_search_description_trgm
CREATE INDEX idx_stores_search_description_trgm ON public.stores USING gin (description gin_trgm_ops);

-- index: idx_stores_search_name_trgm
CREATE INDEX idx_stores_search_name_trgm ON public.stores USING gin (name gin_trgm_ops);

-- index: loyalty_transactions_account_id_idx
CREATE INDEX loyalty_transactions_account_id_idx ON public.loyalty_transactions USING btree (account_id);

-- index: merchant_ad_campaigns_product_idx
CREATE INDEX merchant_ad_campaigns_product_idx ON public.merchant_ad_campaigns USING btree (product_id);

-- index: merchant_ad_campaigns_seller_status_idx
CREATE INDEX merchant_ad_campaigns_seller_status_idx ON public.merchant_ad_campaigns USING btree (seller_id, status, created_at DESC);

-- index: merchant_identity_documents_merchant_idx
CREATE INDEX merchant_identity_documents_merchant_idx ON public.merchant_identity_documents USING btree (merchant_id, created_at DESC);

-- index: merchant_identity_documents_user_idx
CREATE INDEX merchant_identity_documents_user_idx ON public.merchant_identity_documents USING btree (user_id, created_at DESC);

-- index: merchant_profiles_category_id_idx
CREATE INDEX merchant_profiles_category_id_idx ON public.merchant_profiles USING btree (category_id);

-- index: merchant_reports_reporter_id_idx
CREATE INDEX merchant_reports_reporter_id_idx ON public.merchant_reports USING btree (reporter_id);

-- index: merchant_reports_seller_id_idx
CREATE INDEX merchant_reports_seller_id_idx ON public.merchant_reports USING btree (seller_id);

-- index: merchant_reviews_customer_idx
CREATE INDEX merchant_reviews_customer_idx ON public.merchant_reviews USING btree (customer_id);

-- index: merchant_reviews_seller_idx
CREATE INDEX merchant_reviews_seller_idx ON public.merchant_reviews USING btree (seller_id, status, created_at DESC);

-- index: merchant_settlements_order_id_idx
CREATE INDEX merchant_settlements_order_id_idx ON public.merchant_settlements USING btree (order_id);

-- index: merchant_settlements_seller_id_idx
CREATE INDEX merchant_settlements_seller_id_idx ON public.merchant_settlements USING btree (seller_id);

-- index: merchant_verifications_merchant_id_idx
CREATE INDEX merchant_verifications_merchant_id_idx ON public.merchant_verifications USING btree (merchant_id);

-- index: merchant_verifications_reviewer_id_idx
CREATE INDEX merchant_verifications_reviewer_id_idx ON public.merchant_verifications USING btree (reviewer_id);

-- index: messages_conversation_id_idx
CREATE INDEX messages_conversation_id_idx ON public.messages USING btree (conversation_id, created_at);

-- index: messages_sender_id_idx
CREATE INDEX messages_sender_id_idx ON public.messages USING btree (sender_id);

-- index: notifications_user_dedupe_idx
CREATE UNIQUE INDEX notifications_user_dedupe_idx ON public.notifications USING btree (user_id, dedupe_key) WHERE (dedupe_key IS NOT NULL);

-- index: notifications_user_id_idx
CREATE INDEX notifications_user_id_idx ON public.notifications USING btree (user_id, created_at DESC);

-- index: operational_alerts_resolved_by_idx
CREATE INDEX operational_alerts_resolved_by_idx ON public.operational_alerts USING btree (resolved_by);

-- index: order_groups_address_idx
CREATE INDEX order_groups_address_idx ON public.order_groups USING btree (address_id);

-- index: order_groups_customer_created_idx
CREATE INDEX order_groups_customer_created_idx ON public.order_groups USING btree (customer_id, created_at DESC);

-- index: order_groups_customer_id_idx
CREATE INDEX order_groups_customer_id_idx ON public.order_groups USING btree (customer_id);

-- index: order_groups_customer_idempotency_uidx
CREATE UNIQUE INDEX order_groups_customer_idempotency_uidx ON public.order_groups USING btree (customer_id, idempotency_key) WHERE (idempotency_key IS NOT NULL);

-- index: order_items_order_id_idx
CREATE INDEX order_items_order_id_idx ON public.order_items USING btree (order_id);

-- index: order_items_product_id_idx
CREATE INDEX order_items_product_id_idx ON public.order_items USING btree (product_id);

-- index: order_items_seller_id_idx
CREATE INDEX order_items_seller_id_idx ON public.order_items USING btree (seller_id);

-- index: order_reviews_customer_idx
CREATE INDEX order_reviews_customer_idx ON public.order_reviews USING btree (customer_id);

-- index: order_reviews_order_idx
CREATE INDEX order_reviews_order_idx ON public.order_reviews USING btree (order_id);

-- index: order_status_history_changed_by_idx
CREATE INDEX order_status_history_changed_by_idx ON public.order_status_history USING btree (changed_by);

-- index: order_status_history_order_created_idx
CREATE INDEX order_status_history_order_created_idx ON public.order_status_history USING btree (order_id, created_at);

-- index: order_status_history_order_id_idx
CREATE INDEX order_status_history_order_id_idx ON public.order_status_history USING btree (order_id, created_at DESC);

-- index: orders_created_status_idx
CREATE INDEX orders_created_status_idx ON public.orders USING btree (created_at DESC, status);

-- index: orders_customer_created_idx
CREATE INDEX orders_customer_created_idx ON public.orders USING btree (customer_id, created_at DESC);

-- index: orders_customer_id_idx
CREATE INDEX orders_customer_id_idx ON public.orders USING btree (customer_id);

-- index: orders_group_created_idx
CREATE INDEX orders_group_created_idx ON public.orders USING btree (order_group_id, created_at);

-- index: orders_order_group_id_idx
CREATE INDEX orders_order_group_id_idx ON public.orders USING btree (order_group_id);

-- index: orders_seller_created_idx
CREATE INDEX orders_seller_created_idx ON public.orders USING btree (seller_id, created_at DESC);

-- index: orders_seller_id_idx
CREATE INDEX orders_seller_id_idx ON public.orders USING btree (seller_id);

-- index: orders_seller_status_idx
CREATE INDEX orders_seller_status_idx ON public.orders USING btree (seller_id, status, created_at DESC);

-- index: payments_order_id_idx
CREATE INDEX payments_order_id_idx ON public.payments USING btree (order_id);

-- index: product_images_product_id_idx
CREATE INDEX product_images_product_id_idx ON public.product_images USING btree (product_id, sort_order);

-- index: product_reports_product_idx
CREATE INDEX product_reports_product_idx ON public.product_reports USING btree (product_id);

-- index: product_reports_reporter_idx
CREATE INDEX product_reports_reporter_idx ON public.product_reports USING btree (reporter_id);

-- index: product_variants_product_id_idx
CREATE INDEX product_variants_product_id_idx ON public.product_variants USING btree (product_id);

-- index: products_active_idx
CREATE INDEX products_active_idx ON public.products USING btree (is_active);

-- index: products_category_id_idx
CREATE INDEX products_category_id_idx ON public.products USING btree (category_id);

-- index: products_seller_id_idx
CREATE INDEX products_seller_id_idx ON public.products USING btree (seller_id);

-- index: promotions_product_idx
CREATE INDEX promotions_product_idx ON public.promotions USING btree (product_id);

-- index: promotions_seller_idx
CREATE INDEX promotions_seller_idx ON public.promotions USING btree (seller_id);

-- index: push_devices_last_seen_idx
CREATE INDEX push_devices_last_seen_idx ON public.push_devices USING btree (last_seen_at DESC);

-- index: push_devices_user_active_idx
CREATE INDEX push_devices_user_active_idx ON public.push_devices USING btree (user_id, is_active, updated_at DESC);

-- index: referrals_referrer_idx
CREATE INDEX referrals_referrer_idx ON public.referrals USING btree (referrer_id, created_at DESC);

-- index: refunds_payment_idx
CREATE INDEX refunds_payment_idx ON public.refunds USING btree (payment_id);

-- index: refunds_processed_by_idx
CREATE INDEX refunds_processed_by_idx ON public.refunds USING btree (processed_by);

-- index: reports_generated_by_idx
CREATE INDEX reports_generated_by_idx ON public.reports USING btree (generated_by);

-- index: review_reports_reporter_idx
CREATE INDEX review_reports_reporter_idx ON public.review_reports USING btree (reporter_id);

-- index: reviews_customer_id_idx
CREATE INDEX reviews_customer_id_idx ON public.reviews USING btree (customer_id);

-- index: reviews_order_idx
CREATE INDEX reviews_order_idx ON public.reviews USING btree (order_id);

-- index: reviews_product_id_idx
CREATE INDEX reviews_product_id_idx ON public.reviews USING btree (product_id);

-- index: reviews_status_product_idx
CREATE INDEX reviews_status_product_idx ON public.reviews USING btree (status, product_id, created_at DESC);

-- index: stores_category_id_idx
CREATE INDEX stores_category_id_idx ON public.stores USING btree (category_id);

-- index: stores_seller_id_idx
CREATE INDEX stores_seller_id_idx ON public.stores USING btree (seller_id);

-- index: subscription_plans_slug_uidx
CREATE UNIQUE INDEX subscription_plans_slug_uidx ON public.subscription_plans USING btree (slug) WHERE (slug IS NOT NULL);

-- index: subscription_requests_one_pending_uidx
CREATE UNIQUE INDEX subscription_requests_one_pending_uidx ON public.subscription_requests USING btree (seller_id) WHERE (status = 'pending'::text);

-- index: subscription_requests_plan_idx
CREATE INDEX subscription_requests_plan_idx ON public.subscription_requests USING btree (plan_id);

-- index: subscription_requests_reviewed_by_idx
CREATE INDEX subscription_requests_reviewed_by_idx ON public.subscription_requests USING btree (reviewed_by);

-- index: subscription_requests_status_idx
CREATE INDEX subscription_requests_status_idx ON public.subscription_requests USING btree (status, created_at DESC);

-- index: subscription_requests_user_idx
CREATE INDEX subscription_requests_user_idx ON public.subscription_requests USING btree (user_id);

-- index: subscriptions_plan_idx
CREATE INDEX subscriptions_plan_idx ON public.subscriptions USING btree (plan_id);

-- index: subscriptions_seller_status_idx
CREATE INDEX subscriptions_seller_status_idx ON public.subscriptions USING btree (seller_id, status, ends_at);

-- index: subscriptions_user_status_idx
CREATE INDEX subscriptions_user_status_idx ON public.subscriptions USING btree (user_id, status, ends_at);

-- index: support_tickets_assigned_to_idx
CREATE INDEX support_tickets_assigned_to_idx ON public.support_tickets USING btree (assigned_to);

-- index: support_tickets_user_idx
CREATE INDEX support_tickets_user_idx ON public.support_tickets USING btree (user_id);

-- index: system_settings_updated_by_idx
CREATE INDEX system_settings_updated_by_idx ON public.system_settings USING btree (updated_by);

-- index: ticket_messages_sender_idx
CREATE INDEX ticket_messages_sender_idx ON public.ticket_messages USING btree (sender_id);

-- index: ticket_messages_ticket_idx
CREATE INDEX ticket_messages_ticket_idx ON public.ticket_messages USING btree (ticket_id);

-- index: uq_categories_slug
CREATE UNIQUE INDEX uq_categories_slug ON public.categories USING btree (slug) WHERE (slug IS NOT NULL);

-- index: user_reports_reported_user_id_idx
CREATE INDEX user_reports_reported_user_id_idx ON public.user_reports USING btree (reported_user_id);

-- index: user_reports_reporter_id_idx
CREATE INDEX user_reports_reporter_id_idx ON public.user_reports USING btree (reporter_id);

-- index: wallet_transactions_wallet_id_idx
CREATE INDEX wallet_transactions_wallet_id_idx ON public.wallet_transactions USING btree (wallet_id);

-- trigger: protect_profile_admin_fields_trigger
CREATE TRIGGER protect_profile_admin_fields_trigger BEFORE UPDATE OF role, is_active, email, admin_previous_role ON public.profiles FOR EACH ROW EXECUTE FUNCTION protect_profile_admin_fields();

-- trigger: tani_audit_complaints
CREATE TRIGGER tani_audit_complaints AFTER INSERT OR DELETE OR UPDATE ON public.complaints FOR EACH ROW EXECUTE FUNCTION private.audit_trust_support_change();

-- trigger: tani_audit_review_reports
CREATE TRIGGER tani_audit_review_reports AFTER INSERT OR DELETE OR UPDATE ON public.review_reports FOR EACH ROW EXECUTE FUNCTION private.audit_trust_support_change();

-- trigger: tani_audit_support_tickets
CREATE TRIGGER tani_audit_support_tickets AFTER INSERT OR DELETE OR UPDATE ON public.support_tickets FOR EACH ROW EXECUTE FUNCTION private.audit_trust_support_change();

-- trigger: tani_checkout_delivery_snapshot_guard
CREATE TRIGGER tani_checkout_delivery_snapshot_guard BEFORE INSERT ON public.orders FOR EACH ROW EXECUTE FUNCTION private.enforce_checkout_delivery_snapshot();

-- trigger: tani_complete_referral_order
CREATE TRIGGER tani_complete_referral_order AFTER UPDATE OF status ON public.orders FOR EACH ROW EXECUTE FUNCTION private.complete_referral_after_order();

-- trigger: tani_merchant_verification_notification
CREATE TRIGGER tani_merchant_verification_notification AFTER UPDATE OF verification_status ON public.merchant_profiles FOR EACH ROW EXECUTE FUNCTION private.enqueue_merchant_verification_notification();

-- trigger: tani_new_order_notification
CREATE TRIGGER tani_new_order_notification AFTER INSERT ON public.orders FOR EACH ROW EXECUTE FUNCTION private.enqueue_new_order_notification();

-- trigger: tani_order_status_notifications
CREATE TRIGGER tani_order_status_notifications AFTER INSERT ON public.order_status_history FOR EACH ROW EXECUTE FUNCTION private.enqueue_order_notification();

-- trigger: tani_product_stock_alert
CREATE TRIGGER tani_product_stock_alert AFTER UPDATE OF stock ON public.products FOR EACH ROW EXECUTE FUNCTION private.enqueue_stock_alert();

-- trigger: tani_refresh_metrics_event
CREATE TRIGGER tani_refresh_metrics_event AFTER INSERT ON public.app_events FOR EACH ROW EXECUTE FUNCTION private.refresh_metrics_from_event();

-- trigger: tani_refresh_metrics_order
CREATE TRIGGER tani_refresh_metrics_order AFTER INSERT OR DELETE OR UPDATE OF status ON public.orders FOR EACH ROW EXECUTE FUNCTION private.refresh_metrics_from_order();

-- trigger: tani_refresh_product_has_variants
CREATE TRIGGER tani_refresh_product_has_variants AFTER INSERT OR DELETE OR UPDATE OF product_id ON public.product_variants FOR EACH ROW EXECUTE FUNCTION private.refresh_product_has_variants();

-- trigger: tani_refresh_trust_complaint
CREATE TRIGGER tani_refresh_trust_complaint AFTER INSERT OR DELETE OR UPDATE OF status ON public.complaints FOR EACH ROW EXECUTE FUNCTION private.refresh_trust_from_complaint();

-- trigger: tani_refresh_trust_order
CREATE TRIGGER tani_refresh_trust_order AFTER INSERT OR DELETE OR UPDATE OF status ON public.orders FOR EACH ROW EXECUTE FUNCTION private.refresh_trust_from_order();

-- trigger: tani_refresh_trust_review
CREATE TRIGGER tani_refresh_trust_review AFTER INSERT OR DELETE OR UPDATE ON public.merchant_reviews FOR EACH ROW EXECUTE FUNCTION private.refresh_trust_from_merchant_review();

-- trigger: tani_sync_merchant_delivery_zones
CREATE TRIGGER tani_sync_merchant_delivery_zones AFTER INSERT OR UPDATE OF delivery_zones, verification_status, seller_id ON public.merchant_profiles FOR EACH ROW EXECUTE FUNCTION private.sync_merchant_delivery_zones();

-- trigger: trg_addresses_updated_at
CREATE TRIGGER trg_addresses_updated_at BEFORE UPDATE ON public.addresses FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- trigger: trg_app_design_tokens_updated_at
CREATE TRIGGER trg_app_design_tokens_updated_at BEFORE UPDATE ON public.app_design_tokens FOR EACH ROW EXECUTE FUNCTION set_app_design_tokens_updated_at();

-- trigger: trg_app_settings_updated_at
CREATE TRIGGER trg_app_settings_updated_at BEFORE UPDATE ON public.app_settings FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- trigger: trg_audit_categories
CREATE TRIGGER trg_audit_categories AFTER INSERT OR DELETE OR UPDATE ON public.categories FOR EACH ROW EXECUTE FUNCTION private.write_audit_log();

-- trigger: trg_audit_complaints
CREATE TRIGGER trg_audit_complaints AFTER INSERT OR UPDATE ON public.complaints FOR EACH ROW EXECUTE FUNCTION private.write_audit_log();

-- trigger: trg_audit_merchant_profiles
CREATE TRIGGER trg_audit_merchant_profiles AFTER INSERT OR DELETE OR UPDATE ON public.merchant_profiles FOR EACH ROW EXECUTE FUNCTION private.write_audit_log();

-- trigger: trg_audit_orders
CREATE TRIGGER trg_audit_orders AFTER UPDATE ON public.orders FOR EACH ROW EXECUTE FUNCTION private.write_audit_log();

-- trigger: trg_audit_products
CREATE TRIGGER trg_audit_products AFTER INSERT OR DELETE OR UPDATE ON public.products FOR EACH ROW EXECUTE FUNCTION private.write_audit_log();

-- trigger: trg_audit_profiles
CREATE TRIGGER trg_audit_profiles AFTER UPDATE ON public.profiles FOR EACH ROW EXECUTE FUNCTION private.write_audit_log();

-- trigger: trg_audit_sellers
CREATE TRIGGER trg_audit_sellers AFTER INSERT OR DELETE OR UPDATE ON public.sellers FOR EACH ROW EXECUTE FUNCTION private.write_audit_log();

-- trigger: trg_cart_items_updated_at
CREATE TRIGGER trg_cart_items_updated_at BEFORE UPDATE ON public.cart_items FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- trigger: trg_carts_updated_at
CREATE TRIGGER trg_carts_updated_at BEFORE UPDATE ON public.carts FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- trigger: trg_categories_updated_at
CREATE TRIGGER trg_categories_updated_at BEFORE UPDATE ON public.categories FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- trigger: trg_complaints_updated_at
CREATE TRIGGER trg_complaints_updated_at BEFORE UPDATE ON public.complaints FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- trigger: trg_conversations_updated_at
CREATE TRIGGER trg_conversations_updated_at BEFORE UPDATE ON public.conversations FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- trigger: trg_delivery_options_updated_at
CREATE TRIGGER trg_delivery_options_updated_at BEFORE UPDATE ON public.delivery_options FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- trigger: trg_delivery_settings_updated_at
CREATE TRIGGER trg_delivery_settings_updated_at BEFORE UPDATE ON public.delivery_settings FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- trigger: trg_feature_flags_updated_at
CREATE TRIGGER trg_feature_flags_updated_at BEFORE UPDATE ON public.feature_flags FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- trigger: trg_initialize_new_profile
CREATE TRIGGER trg_initialize_new_profile AFTER INSERT ON public.profiles FOR EACH ROW EXECUTE FUNCTION initialize_new_profile();

-- trigger: trg_inventory_updated_at
CREATE TRIGGER trg_inventory_updated_at BEFORE UPDATE ON public.inventory FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- trigger: trg_loyalty_accounts_updated_at
CREATE TRIGGER trg_loyalty_accounts_updated_at BEFORE UPDATE ON public.loyalty_accounts FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- trigger: trg_merchant_metrics_updated_at
CREATE TRIGGER trg_merchant_metrics_updated_at BEFORE UPDATE ON public.merchant_metrics FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- trigger: trg_merchant_profiles_updated_at
CREATE TRIGGER trg_merchant_profiles_updated_at BEFORE UPDATE ON public.merchant_profiles FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- trigger: trg_normalize_default_address
CREATE TRIGGER trg_normalize_default_address BEFORE INSERT OR UPDATE OF is_default ON public.addresses FOR EACH ROW EXECUTE FUNCTION private.normalize_default_address();

-- trigger: trg_notification_preferences_updated_at
CREATE TRIGGER trg_notification_preferences_updated_at BEFORE UPDATE ON public.notification_preferences FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- trigger: trg_notifications_updated_at
CREATE TRIGGER trg_notifications_updated_at BEFORE UPDATE ON public.notifications FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- trigger: trg_order_groups_updated_at
CREATE TRIGGER trg_order_groups_updated_at BEFORE UPDATE ON public.order_groups FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- trigger: trg_orders_updated_at
CREATE TRIGGER trg_orders_updated_at BEFORE UPDATE ON public.orders FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- trigger: trg_payments_updated_at
CREATE TRIGGER trg_payments_updated_at BEFORE UPDATE ON public.payments FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- trigger: trg_product_variants_updated_at
CREATE TRIGGER trg_product_variants_updated_at BEFORE UPDATE ON public.product_variants FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- trigger: trg_profiles_updated_at
CREATE TRIGGER trg_profiles_updated_at BEFORE UPDATE ON public.profiles FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- trigger: trg_protect_merchant_profile_verification
CREATE TRIGGER trg_protect_merchant_profile_verification BEFORE UPDATE ON public.merchant_profiles FOR EACH ROW EXECUTE FUNCTION private.protect_merchant_verification_fields();

-- trigger: trg_protect_profile_authorization_fields
CREATE TRIGGER trg_protect_profile_authorization_fields BEFORE UPDATE ON public.profiles FOR EACH ROW EXECUTE FUNCTION protect_profile_authorization_fields();

-- trigger: trg_protect_seller_verification
CREATE TRIGGER trg_protect_seller_verification BEFORE UPDATE ON public.sellers FOR EACH ROW EXECUTE FUNCTION private.protect_merchant_verification_fields();

-- trigger: trg_refresh_order_group_status
CREATE TRIGGER trg_refresh_order_group_status AFTER UPDATE OF status ON public.orders FOR EACH ROW EXECUTE FUNCTION private.on_order_status_changed();

-- trigger: trg_stores_updated_at
CREATE TRIGGER trg_stores_updated_at BEFORE UPDATE ON public.stores FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- trigger: trg_support_tickets_updated_at
CREATE TRIGGER trg_support_tickets_updated_at BEFORE UPDATE ON public.support_tickets FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- trigger: trg_system_settings_updated_at
CREATE TRIGGER trg_system_settings_updated_at BEFORE UPDATE ON public.system_settings FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- trigger: trg_validate_store_seller_link
CREATE TRIGGER trg_validate_store_seller_link BEFORE INSERT OR UPDATE OF merchant_id, seller_id ON public.stores FOR EACH ROW EXECUTE FUNCTION private.validate_store_seller_link();

-- trigger: trg_wallets_updated_at
CREATE TRIGGER trg_wallets_updated_at BEFORE UPDATE ON public.wallets FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- rls: addresses
ALTER TABLE public.addresses ENABLE ROW LEVEL SECURITY;

-- rls: app_design_tokens
ALTER TABLE public.app_design_tokens ENABLE ROW LEVEL SECURITY;

-- rls: app_errors
ALTER TABLE public.app_errors ENABLE ROW LEVEL SECURITY;

-- rls: app_events
ALTER TABLE public.app_events ENABLE ROW LEVEL SECURITY;

-- rls: app_settings
ALTER TABLE public.app_settings ENABLE ROW LEVEL SECURITY;

-- rls: audit_logs
ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;

-- rls: banners
ALTER TABLE public.banners ENABLE ROW LEVEL SECURITY;

-- rls: billing_transactions
ALTER TABLE public.billing_transactions ENABLE ROW LEVEL SECURITY;

-- rls: campaigns
ALTER TABLE public.campaigns ENABLE ROW LEVEL SECURITY;

-- rls: cart_items
ALTER TABLE public.cart_items ENABLE ROW LEVEL SECURITY;

-- rls: carts
ALTER TABLE public.carts ENABLE ROW LEVEL SECURITY;

-- rls: categories
ALTER TABLE public.categories ENABLE ROW LEVEL SECURITY;

-- rls: complaints
ALTER TABLE public.complaints ENABLE ROW LEVEL SECURITY;

-- rls: conversations
ALTER TABLE public.conversations ENABLE ROW LEVEL SECURITY;

-- rls: coupon_usages
ALTER TABLE public.coupon_usages ENABLE ROW LEVEL SECURITY;

-- rls: coupons
ALTER TABLE public.coupons ENABLE ROW LEVEL SECURITY;

-- rls: delivery_options
ALTER TABLE public.delivery_options ENABLE ROW LEVEL SECURITY;

-- rls: delivery_provider_assignments
ALTER TABLE public.delivery_provider_assignments ENABLE ROW LEVEL SECURITY;

-- rls: delivery_providers
ALTER TABLE public.delivery_providers ENABLE ROW LEVEL SECURITY;

-- rls: delivery_settings
ALTER TABLE public.delivery_settings ENABLE ROW LEVEL SECURITY;

-- rls: delivery_zones
ALTER TABLE public.delivery_zones ENABLE ROW LEVEL SECURITY;

-- rls: disputes
ALTER TABLE public.disputes ENABLE ROW LEVEL SECURITY;

-- rls: favorites
ALTER TABLE public.favorites ENABLE ROW LEVEL SECURITY;

-- rls: feature_flags
ALTER TABLE public.feature_flags ENABLE ROW LEVEL SECURITY;

-- rls: featured_placements
ALTER TABLE public.featured_placements ENABLE ROW LEVEL SECURITY;

-- rls: featured_requests
ALTER TABLE public.featured_requests ENABLE ROW LEVEL SECURITY;

-- rls: fee_rules
ALTER TABLE public.fee_rules ENABLE ROW LEVEL SECURITY;

-- rls: inventory
ALTER TABLE public.inventory ENABLE ROW LEVEL SECURITY;

-- rls: loyalty_accounts
ALTER TABLE public.loyalty_accounts ENABLE ROW LEVEL SECURITY;

-- rls: loyalty_transactions
ALTER TABLE public.loyalty_transactions ENABLE ROW LEVEL SECURITY;

-- rls: market_cities
ALTER TABLE public.market_cities ENABLE ROW LEVEL SECURITY;

-- rls: merchant_ad_campaigns
ALTER TABLE public.merchant_ad_campaigns ENABLE ROW LEVEL SECURITY;

-- rls: merchant_identity_documents
ALTER TABLE public.merchant_identity_documents ENABLE ROW LEVEL SECURITY;

-- rls: merchant_metrics
ALTER TABLE public.merchant_metrics ENABLE ROW LEVEL SECURITY;

-- rls: merchant_profiles
ALTER TABLE public.merchant_profiles ENABLE ROW LEVEL SECURITY;

-- rls: merchant_reports
ALTER TABLE public.merchant_reports ENABLE ROW LEVEL SECURITY;

-- rls: merchant_reviews
ALTER TABLE public.merchant_reviews ENABLE ROW LEVEL SECURITY;

-- rls: merchant_settlements
ALTER TABLE public.merchant_settlements ENABLE ROW LEVEL SECURITY;

-- rls: merchant_trust_scores
ALTER TABLE public.merchant_trust_scores ENABLE ROW LEVEL SECURITY;

-- rls: merchant_verifications
ALTER TABLE public.merchant_verifications ENABLE ROW LEVEL SECURITY;

-- rls: messages
ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;

-- rls: notification_preferences
ALTER TABLE public.notification_preferences ENABLE ROW LEVEL SECURITY;

-- rls: notifications
ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;

-- rls: operational_alerts
ALTER TABLE public.operational_alerts ENABLE ROW LEVEL SECURITY;

-- rls: order_groups
ALTER TABLE public.order_groups ENABLE ROW LEVEL SECURITY;

-- rls: order_items
ALTER TABLE public.order_items ENABLE ROW LEVEL SECURITY;

-- rls: order_reviews
ALTER TABLE public.order_reviews ENABLE ROW LEVEL SECURITY;

-- rls: order_status_history
ALTER TABLE public.order_status_history ENABLE ROW LEVEL SECURITY;

-- rls: orders
ALTER TABLE public.orders ENABLE ROW LEVEL SECURITY;

-- rls: payment_methods
ALTER TABLE public.payment_methods ENABLE ROW LEVEL SECURITY;

-- rls: payments
ALTER TABLE public.payments ENABLE ROW LEVEL SECURITY;

-- rls: product_images
ALTER TABLE public.product_images ENABLE ROW LEVEL SECURITY;

-- rls: product_reports
ALTER TABLE public.product_reports ENABLE ROW LEVEL SECURITY;

-- rls: product_variants
ALTER TABLE public.product_variants ENABLE ROW LEVEL SECURITY;

-- rls: products
ALTER TABLE public.products ENABLE ROW LEVEL SECURITY;

-- rls: profiles
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

-- rls: promotions
ALTER TABLE public.promotions ENABLE ROW LEVEL SECURITY;

-- rls: push_devices
ALTER TABLE public.push_devices ENABLE ROW LEVEL SECURITY;

-- rls: referral_codes
ALTER TABLE public.referral_codes ENABLE ROW LEVEL SECURITY;

-- rls: referrals
ALTER TABLE public.referrals ENABLE ROW LEVEL SECURITY;

-- rls: refunds
ALTER TABLE public.refunds ENABLE ROW LEVEL SECURITY;

-- rls: reports
ALTER TABLE public.reports ENABLE ROW LEVEL SECURITY;

-- rls: review_reports
ALTER TABLE public.review_reports ENABLE ROW LEVEL SECURITY;

-- rls: reviews
ALTER TABLE public.reviews ENABLE ROW LEVEL SECURITY;

-- rls: sellers
ALTER TABLE public.sellers ENABLE ROW LEVEL SECURITY;

-- rls: stores
ALTER TABLE public.stores ENABLE ROW LEVEL SECURITY;

-- rls: subscription_plans
ALTER TABLE public.subscription_plans ENABLE ROW LEVEL SECURITY;

-- rls: subscription_requests
ALTER TABLE public.subscription_requests ENABLE ROW LEVEL SECURITY;

-- rls: subscriptions
ALTER TABLE public.subscriptions ENABLE ROW LEVEL SECURITY;

-- rls: support_tickets
ALTER TABLE public.support_tickets ENABLE ROW LEVEL SECURITY;

-- rls: system_settings
ALTER TABLE public.system_settings ENABLE ROW LEVEL SECURITY;

-- rls: ticket_messages
ALTER TABLE public.ticket_messages ENABLE ROW LEVEL SECURITY;

-- rls: user_reports
ALTER TABLE public.user_reports ENABLE ROW LEVEL SECURITY;

-- rls: wallet_transactions
ALTER TABLE public.wallet_transactions ENABLE ROW LEVEL SECURITY;

-- rls: wallets
ALTER TABLE public.wallets ENABLE ROW LEVEL SECURITY;

-- policy: addresses.tani_addresses_owner_all
CREATE POLICY tani_addresses_owner_all ON public.addresses AS PERMISSIVE FOR ALL TO authenticated USING ((user_id = ( SELECT auth.uid() AS uid))) WITH CHECK ((user_id = ( SELECT auth.uid() AS uid)));

-- policy: app_design_tokens.app_design_tokens_admin_write
CREATE POLICY app_design_tokens_admin_write ON public.app_design_tokens AS PERMISSIVE FOR ALL TO authenticated USING ((current_user_role() = 'admin'::text)) WITH CHECK ((current_user_role() = 'admin'::text));

-- policy: app_design_tokens.app_design_tokens_public_read
CREATE POLICY app_design_tokens_public_read ON public.app_design_tokens AS PERMISSIVE FOR SELECT TO anon,authenticated USING ((is_active = true));

-- policy: app_errors.tani_app_errors_admin_read
CREATE POLICY tani_app_errors_admin_read ON public.app_errors AS PERMISSIVE FOR SELECT TO authenticated USING ((current_user_role() = ANY (ARRAY['admin'::text, 'support'::text])));

-- policy: app_errors.tani_app_errors_anon_insert
CREATE POLICY tani_app_errors_anon_insert ON public.app_errors AS PERMISSIVE FOR INSERT TO anon WITH CHECK ((user_id IS NULL));

-- policy: app_errors.tani_app_errors_authenticated_insert
CREATE POLICY tani_app_errors_authenticated_insert ON public.app_errors AS PERMISSIVE FOR INSERT TO authenticated WITH CHECK ((user_id = ( SELECT auth.uid() AS uid)));

-- policy: app_events.tani_app_events_admin_read
CREATE POLICY tani_app_events_admin_read ON public.app_events AS PERMISSIVE FOR SELECT TO authenticated USING ((current_user_role() = ANY (ARRAY['admin'::text, 'support'::text])));

-- policy: app_events.tani_app_events_anon_insert
CREATE POLICY tani_app_events_anon_insert ON public.app_events AS PERMISSIVE FOR INSERT TO anon WITH CHECK ((user_id IS NULL));

-- policy: app_events.tani_app_events_authenticated_insert
CREATE POLICY tani_app_events_authenticated_insert ON public.app_events AS PERMISSIVE FOR INSERT TO authenticated WITH CHECK ((user_id = ( SELECT auth.uid() AS uid)));

-- policy: app_settings.tani_admin_app_settings
CREATE POLICY tani_admin_app_settings ON public.app_settings AS PERMISSIVE FOR ALL TO authenticated USING ((current_user_role() = 'admin'::text)) WITH CHECK ((current_user_role() = 'admin'::text));

-- policy: audit_logs.tani_admin_audit_read
CREATE POLICY tani_admin_audit_read ON public.audit_logs AS PERMISSIVE FOR SELECT TO authenticated USING ((current_user_role() = ANY (ARRAY['admin'::text, 'support'::text])));

-- policy: audit_logs.tani_audit_admin_insert
CREATE POLICY tani_audit_admin_insert ON public.audit_logs AS PERMISSIVE FOR INSERT TO authenticated WITH CHECK (((actor_id = ( SELECT auth.uid() AS uid)) AND (( SELECT current_user_role() AS current_user_role) = 'admin'::text)));

-- policy: banners.tani_banners_admin_write
CREATE POLICY tani_banners_admin_write ON public.banners AS PERMISSIVE FOR ALL TO authenticated USING ((current_user_role() = 'admin'::text)) WITH CHECK ((current_user_role() = 'admin'::text));

-- policy: banners.tani_banners_public_read
CREATE POLICY tani_banners_public_read ON public.banners AS PERMISSIVE FOR SELECT TO anon,authenticated USING (((is_active = true) AND ((starts_at IS NULL) OR (starts_at <= now())) AND ((ends_at IS NULL) OR (ends_at >= now()))));

-- policy: billing_transactions.tani_billing_admin_all
CREATE POLICY tani_billing_admin_all ON public.billing_transactions AS PERMISSIVE FOR ALL TO authenticated USING ((current_user_role() = 'admin'::text)) WITH CHECK ((current_user_role() = 'admin'::text));

-- policy: billing_transactions.tani_billing_owner_read
CREATE POLICY tani_billing_owner_read ON public.billing_transactions AS PERMISSIVE FOR SELECT TO authenticated USING (((user_id = ( SELECT auth.uid() AS uid)) OR (current_user_role() = ANY (ARRAY['admin'::text, 'support'::text]))));

-- policy: campaigns.tani_admin_campaigns
CREATE POLICY tani_admin_campaigns ON public.campaigns AS PERMISSIVE FOR ALL TO authenticated USING ((current_user_role() = 'admin'::text)) WITH CHECK ((current_user_role() = 'admin'::text));

-- policy: campaigns.tani_campaigns_admin_all
CREATE POLICY tani_campaigns_admin_all ON public.campaigns AS PERMISSIVE FOR ALL TO authenticated USING ((current_user_role() = 'admin'::text)) WITH CHECK ((current_user_role() = 'admin'::text));

-- policy: cart_items.tani_cart_items_owner_all
CREATE POLICY tani_cart_items_owner_all ON public.cart_items AS PERMISSIVE FOR ALL TO authenticated USING ((EXISTS ( SELECT 1
   FROM carts c
  WHERE ((c.id = cart_items.cart_id) AND (c.user_id = ( SELECT auth.uid() AS uid)))))) WITH CHECK ((EXISTS ( SELECT 1
   FROM carts c
  WHERE ((c.id = cart_items.cart_id) AND (c.user_id = ( SELECT auth.uid() AS uid))))));

-- policy: carts.tani_carts_owner_all
CREATE POLICY tani_carts_owner_all ON public.carts AS PERMISSIVE FOR ALL TO authenticated USING ((user_id = ( SELECT auth.uid() AS uid))) WITH CHECK ((user_id = ( SELECT auth.uid() AS uid)));

-- policy: categories.tani_categories_admin_all
CREATE POLICY tani_categories_admin_all ON public.categories AS PERMISSIVE FOR ALL TO authenticated USING ((current_user_role() = 'admin'::text)) WITH CHECK ((current_user_role() = 'admin'::text));

-- policy: categories.tani_categories_admin_delete
CREATE POLICY tani_categories_admin_delete ON public.categories AS PERMISSIVE FOR DELETE TO authenticated USING ((current_user_role() = 'admin'::text));

-- policy: categories.tani_categories_admin_insert
CREATE POLICY tani_categories_admin_insert ON public.categories AS PERMISSIVE FOR INSERT TO authenticated WITH CHECK ((current_user_role() = 'admin'::text));

-- policy: categories.tani_categories_admin_update
CREATE POLICY tani_categories_admin_update ON public.categories AS PERMISSIVE FOR UPDATE TO authenticated USING ((current_user_role() = 'admin'::text)) WITH CHECK ((current_user_role() = 'admin'::text));

-- policy: categories.tani_categories_public_read
CREATE POLICY tani_categories_public_read ON public.categories AS PERMISSIVE FOR SELECT TO anon,authenticated USING (true);

-- policy: complaints.tani_complaints_admin_support_update
CREATE POLICY tani_complaints_admin_support_update ON public.complaints AS PERMISSIVE FOR UPDATE TO authenticated USING ((current_user_role() = ANY (ARRAY['admin'::text, 'support'::text]))) WITH CHECK ((current_user_role() = ANY (ARRAY['admin'::text, 'support'::text])));

-- policy: complaints.tani_complaints_owner_insert
CREATE POLICY tani_complaints_owner_insert ON public.complaints AS PERMISSIVE FOR INSERT TO authenticated WITH CHECK (((user_id = ( SELECT auth.uid() AS uid)) AND ((order_id IS NULL) OR (EXISTS ( SELECT 1
   FROM orders o
  WHERE ((o.id = complaints.order_id) AND (o.customer_id = ( SELECT auth.uid() AS uid))))))));

-- policy: complaints.tani_complaints_owner_read
CREATE POLICY tani_complaints_owner_read ON public.complaints AS PERMISSIVE FOR SELECT TO authenticated USING (((user_id = auth.uid()) OR (current_user_role() = ANY (ARRAY['admin'::text, 'support'::text])) OR (EXISTS ( SELECT 1
   FROM sellers s
  WHERE ((s.id = complaints.seller_id) AND (s.user_id = auth.uid()))))));

-- policy: conversations.tani_conversation_participant_insert
CREATE POLICY tani_conversation_participant_insert ON public.conversations AS PERMISSIVE FOR INSERT TO authenticated WITH CHECK (((customer_id = auth.uid()) OR (current_user_role() = ANY (ARRAY['admin'::text, 'support'::text]))));

-- policy: conversations.tani_conversation_participant_read
CREATE POLICY tani_conversation_participant_read ON public.conversations AS PERMISSIVE FOR SELECT TO authenticated USING (((customer_id = auth.uid()) OR (EXISTS ( SELECT 1
   FROM sellers s
  WHERE ((s.id = conversations.seller_id) AND (s.user_id = auth.uid())))) OR (current_user_role() = ANY (ARRAY['admin'::text, 'support'::text]))));

-- policy: coupon_usages.tani_coupon_usage_owner_read
CREATE POLICY tani_coupon_usage_owner_read ON public.coupon_usages AS PERMISSIVE FOR SELECT TO authenticated USING (((user_id = auth.uid()) OR (current_user_role() = 'admin'::text)));

-- policy: coupons.tani_coupon_admin_write
CREATE POLICY tani_coupon_admin_write ON public.coupons AS PERMISSIVE FOR ALL TO authenticated USING ((current_user_role() = 'admin'::text)) WITH CHECK ((current_user_role() = 'admin'::text));

-- policy: coupons.tani_coupon_owner_read
CREATE POLICY tani_coupon_owner_read ON public.coupons AS PERMISSIVE FOR SELECT TO authenticated USING (((is_active = true) OR (current_user_role() = ANY (ARRAY['admin'::text, 'merchant'::text]))));

-- policy: coupons.tani_coupons_anon_read
CREATE POLICY tani_coupons_anon_read ON public.coupons AS PERMISSIVE FOR SELECT TO anon USING ((is_active = true));

-- policy: delivery_options.tani_delivery_owner_read
CREATE POLICY tani_delivery_owner_read ON public.delivery_options AS PERMISSIVE FOR SELECT TO authenticated USING ((EXISTS ( SELECT 1
   FROM orders o
  WHERE ((o.id = delivery_options.order_id) AND ((o.customer_id = auth.uid()) OR (current_user_role() = ANY (ARRAY['admin'::text, 'support'::text])) OR (EXISTS ( SELECT 1
           FROM sellers s
          WHERE ((s.id = o.seller_id) AND (s.user_id = auth.uid())))))))));

-- policy: delivery_provider_assignments.tani_delivery_assignments_owner_r
CREATE POLICY tani_delivery_assignments_owner_read ON public.delivery_provider_assignments AS PERMISSIVE FOR SELECT TO authenticated USING (((EXISTS ( SELECT 1
   FROM sellers s
  WHERE ((s.id = delivery_provider_assignments.seller_id) AND (s.user_id = ( SELECT auth.uid() AS uid))))) OR (current_user_role() = ANY (ARRAY['admin'::text, 'support'::text]))));

-- policy: delivery_provider_assignments.tani_delivery_assignments_owner_w
CREATE POLICY tani_delivery_assignments_owner_write ON public.delivery_provider_assignments AS PERMISSIVE FOR ALL TO authenticated USING (((EXISTS ( SELECT 1
   FROM sellers s
  WHERE ((s.id = delivery_provider_assignments.seller_id) AND (s.user_id = ( SELECT auth.uid() AS uid)) AND (s.verification_status = 'approved'::text)))) OR (current_user_role() = 'admin'::text))) WITH CHECK (((EXISTS ( SELECT 1
   FROM sellers s
  WHERE ((s.id = delivery_provider_assignments.seller_id) AND (s.user_id = ( SELECT auth.uid() AS uid)) AND (s.verification_status = 'approved'::text)))) OR (current_user_role() = 'admin'::text)));

-- policy: delivery_providers.tani_delivery_providers_admin_all
CREATE POLICY tani_delivery_providers_admin_all ON public.delivery_providers AS PERMISSIVE FOR ALL TO authenticated USING ((current_user_role() = 'admin'::text)) WITH CHECK ((current_user_role() = 'admin'::text));

-- policy: delivery_providers.tani_delivery_providers_public_read
CREATE POLICY tani_delivery_providers_public_read ON public.delivery_providers AS PERMISSIVE FOR SELECT TO anon,authenticated USING ((is_active = true));

-- policy: delivery_settings.tani_delivery_settings_merchant
CREATE POLICY tani_delivery_settings_merchant ON public.delivery_settings AS PERMISSIVE FOR SELECT TO authenticated USING ((EXISTS ( SELECT 1
   FROM sellers s
  WHERE ((s.id = delivery_settings.seller_id) AND ((s.user_id = auth.uid()) OR (current_user_role() = ANY (ARRAY['admin'::text, 'support'::text])))))));

-- policy: delivery_settings.tani_delivery_settings_merchant_write
CREATE POLICY tani_delivery_settings_merchant_write ON public.delivery_settings AS PERMISSIVE FOR ALL TO authenticated USING (((EXISTS ( SELECT 1
   FROM sellers s
  WHERE ((s.id = delivery_settings.seller_id) AND (s.user_id = ( SELECT auth.uid() AS uid)) AND (s.verification_status = 'approved'::text)))) OR (current_user_role() = 'admin'::text))) WITH CHECK (((EXISTS ( SELECT 1
   FROM sellers s
  WHERE ((s.id = delivery_settings.seller_id) AND (s.user_id = ( SELECT auth.uid() AS uid)) AND (s.verification_status = 'approved'::text)))) OR (current_user_role() = 'admin'::text)));

-- policy: delivery_settings.tani_delivery_settings_public_read
CREATE POLICY tani_delivery_settings_public_read ON public.delivery_settings AS PERMISSIVE FOR SELECT TO anon,authenticated USING (((is_active = true) AND (EXISTS ( SELECT 1
   FROM sellers s
  WHERE ((s.id = delivery_settings.seller_id) AND (s.verification_status = 'approved'::text))))));

-- policy: delivery_zones.tani_delivery_zones_owner_all
CREATE POLICY tani_delivery_zones_owner_all ON public.delivery_zones AS PERMISSIVE FOR ALL TO authenticated USING (((EXISTS ( SELECT 1
   FROM sellers s
  WHERE ((s.id = delivery_zones.seller_id) AND (s.user_id = ( SELECT auth.uid() AS uid)) AND (s.verification_status = 'approved'::text)))) OR (current_user_role() = 'admin'::text))) WITH CHECK (((EXISTS ( SELECT 1
   FROM sellers s
  WHERE ((s.id = delivery_zones.seller_id) AND (s.user_id = ( SELECT auth.uid() AS uid)) AND (s.verification_status = 'approved'::text)))) OR (current_user_role() = 'admin'::text)));

-- policy: delivery_zones.tani_delivery_zones_public_read
CREATE POLICY tani_delivery_zones_public_read ON public.delivery_zones AS PERMISSIVE FOR SELECT TO anon,authenticated USING ((is_active AND (EXISTS ( SELECT 1
   FROM sellers s
  WHERE ((s.id = delivery_zones.seller_id) AND (s.verification_status = 'approved'::text)))) AND (EXISTS ( SELECT 1
   FROM delivery_settings d
  WHERE ((d.seller_id = delivery_zones.seller_id) AND d.is_active))) AND ((NOT (EXISTS ( SELECT 1
   FROM stores st
  WHERE (st.seller_id = delivery_zones.seller_id)))) OR (EXISTS ( SELECT 1
   FROM stores st
  WHERE ((st.seller_id = delivery_zones.seller_id) AND st.is_active AND st.is_open))))));

-- policy: disputes.tani_dispute_owner_insert
CREATE POLICY tani_dispute_owner_insert ON public.disputes AS PERMISSIVE FOR INSERT TO public WITH CHECK ((opened_by = auth.uid()));

-- policy: disputes.tani_dispute_participant_read
CREATE POLICY tani_dispute_participant_read ON public.disputes AS PERMISSIVE FOR SELECT TO authenticated USING (((opened_by = auth.uid()) OR (EXISTS ( SELECT 1
   FROM (orders o
     JOIN sellers s ON ((s.id = o.seller_id)))
  WHERE ((o.id = disputes.order_id) AND (s.user_id = auth.uid())))) OR (current_user_role() = ANY (ARRAY['admin'::text, 'support'::text]))));

-- policy: favorites.tani_favorites_owner_all
CREATE POLICY tani_favorites_owner_all ON public.favorites AS PERMISSIVE FOR ALL TO public USING ((user_id = auth.uid())) WITH CHECK ((user_id = auth.uid()));

-- policy: favorites.tani_favorites_owner_delete
CREATE POLICY tani_favorites_owner_delete ON public.favorites AS PERMISSIVE FOR DELETE TO authenticated USING ((user_id = ( SELECT auth.uid() AS uid)));

-- policy: favorites.tani_favorites_owner_insert
CREATE POLICY tani_favorites_owner_insert ON public.favorites AS PERMISSIVE FOR INSERT TO authenticated WITH CHECK ((user_id = ( SELECT auth.uid() AS uid)));

-- policy: favorites.tani_favorites_owner_read
CREATE POLICY tani_favorites_owner_read ON public.favorites AS PERMISSIVE FOR SELECT TO authenticated USING ((user_id = ( SELECT auth.uid() AS uid)));

-- policy: feature_flags.tani_admin_feature_flags
CREATE POLICY tani_admin_feature_flags ON public.feature_flags AS PERMISSIVE FOR ALL TO authenticated USING ((current_user_role() = 'admin'::text)) WITH CHECK ((current_user_role() = 'admin'::text));

-- policy: featured_placements.tani_featured_admin_write
CREATE POLICY tani_featured_admin_write ON public.featured_placements AS PERMISSIVE FOR ALL TO authenticated USING ((current_user_role() = 'admin'::text)) WITH CHECK ((current_user_role() = 'admin'::text));

-- policy: featured_placements.tani_featured_anon_read
CREATE POLICY tani_featured_anon_read ON public.featured_placements AS PERMISSIVE FOR SELECT TO anon USING ((is_active = true));

-- policy: featured_placements.tani_featured_public_read
CREATE POLICY tani_featured_public_read ON public.featured_placements AS PERMISSIVE FOR SELECT TO authenticated USING (((is_active = true) OR (current_user_role() = 'admin'::text)));

-- policy: featured_requests.tani_featured_requests_admin_update
CREATE POLICY tani_featured_requests_admin_update ON public.featured_requests AS PERMISSIVE FOR UPDATE TO authenticated USING ((current_user_role() = 'admin'::text)) WITH CHECK ((current_user_role() = 'admin'::text));

-- policy: featured_requests.tani_featured_requests_owner_cancel
CREATE POLICY tani_featured_requests_owner_cancel ON public.featured_requests AS PERMISSIVE FOR UPDATE TO authenticated USING (((status = 'pending'::text) AND (EXISTS ( SELECT 1
   FROM sellers s
  WHERE ((s.id = featured_requests.seller_id) AND (s.user_id = ( SELECT auth.uid() AS uid))))))) WITH CHECK (((status = 'cancelled'::text) AND (EXISTS ( SELECT 1
   FROM sellers s
  WHERE ((s.id = featured_requests.seller_id) AND (s.user_id = ( SELECT auth.uid() AS uid)))))));

-- policy: featured_requests.tani_featured_requests_owner_insert
CREATE POLICY tani_featured_requests_owner_insert ON public.featured_requests AS PERMISSIVE FOR INSERT TO authenticated WITH CHECK (((status = 'pending'::text) AND (EXISTS ( SELECT 1
   FROM sellers s
  WHERE ((s.id = featured_requests.seller_id) AND (s.user_id = ( SELECT auth.uid() AS uid)) AND (s.verification_status = 'approved'::text)))) AND ((product_id IS NULL) OR (EXISTS ( SELECT 1
   FROM products p
  WHERE ((p.id = featured_requests.product_id) AND (p.seller_id = featured_requests.seller_id)))))));

-- policy: featured_requests.tani_featured_requests_owner_read
CREATE POLICY tani_featured_requests_owner_read ON public.featured_requests AS PERMISSIVE FOR SELECT TO authenticated USING (((EXISTS ( SELECT 1
   FROM sellers s
  WHERE ((s.id = featured_requests.seller_id) AND (s.user_id = ( SELECT auth.uid() AS uid))))) OR (current_user_role() = ANY (ARRAY['admin'::text, 'support'::text]))));

-- policy: fee_rules.tani_fee_rules_admin_all
CREATE POLICY tani_fee_rules_admin_all ON public.fee_rules AS PERMISSIVE FOR ALL TO authenticated USING ((current_user_role() = 'admin'::text)) WITH CHECK ((current_user_role() = 'admin'::text));

-- policy: fee_rules.tani_fee_rules_public_read
CREATE POLICY tani_fee_rules_public_read ON public.fee_rules AS PERMISSIVE FOR SELECT TO anon,authenticated USING (((is_active = true) AND ((starts_at IS NULL) OR (starts_at <= now())) AND ((ends_at IS NULL) OR (ends_at > now()))));

-- policy: inventory.tani_inventory_owner_read
CREATE POLICY tani_inventory_owner_read ON public.inventory AS PERMISSIVE FOR SELECT TO authenticated USING ((EXISTS ( SELECT 1
   FROM (products p
     JOIN sellers s ON ((s.id = p.seller_id)))
  WHERE ((p.id = inventory.product_id) AND ((s.user_id = auth.uid()) OR (current_user_role() = ANY (ARRAY['admin'::text, 'support'::text])))))));

-- policy: inventory.tani_inventory_owner_write
CREATE POLICY tani_inventory_owner_write ON public.inventory AS PERMISSIVE FOR ALL TO public USING ((EXISTS ( SELECT 1
   FROM (products p
     JOIN sellers s ON ((s.id = p.seller_id)))
  WHERE ((p.id = inventory.product_id) AND (s.user_id = auth.uid()))))) WITH CHECK ((EXISTS ( SELECT 1
   FROM (products p
     JOIN sellers s ON ((s.id = p.seller_id)))
  WHERE ((p.id = inventory.product_id) AND (s.user_id = auth.uid())))));

-- policy: loyalty_accounts.tani_loyalty_owner_read
CREATE POLICY tani_loyalty_owner_read ON public.loyalty_accounts AS PERMISSIVE FOR SELECT TO authenticated USING (((user_id = auth.uid()) OR (current_user_role() = 'admin'::text)));

-- policy: loyalty_transactions.tani_loyalty_tx_owner_read
CREATE POLICY tani_loyalty_tx_owner_read ON public.loyalty_transactions AS PERMISSIVE FOR SELECT TO authenticated USING ((EXISTS ( SELECT 1
   FROM loyalty_accounts a
  WHERE ((a.id = loyalty_transactions.account_id) AND ((a.user_id = auth.uid()) OR (current_user_role() = 'admin'::text))))));

-- policy: market_cities.tani_market_cities_admin_all
CREATE POLICY tani_market_cities_admin_all ON public.market_cities AS PERMISSIVE FOR ALL TO authenticated USING ((current_user_role() = 'admin'::text)) WITH CHECK ((current_user_role() = 'admin'::text));

-- policy: market_cities.tani_market_cities_anon_read
CREATE POLICY tani_market_cities_anon_read ON public.market_cities AS PERMISSIVE FOR SELECT TO anon USING ((is_active = true));

-- policy: market_cities.tani_market_cities_public_read
CREATE POLICY tani_market_cities_public_read ON public.market_cities AS PERMISSIVE FOR SELECT TO authenticated USING (((is_active = true) OR (current_user_role() = 'admin'::text)));

-- policy: merchant_ad_campaigns.tani_ad_campaigns_admin_update
CREATE POLICY tani_ad_campaigns_admin_update ON public.merchant_ad_campaigns AS PERMISSIVE FOR UPDATE TO authenticated USING ((current_user_role() = 'admin'::text)) WITH CHECK ((current_user_role() = 'admin'::text));

-- policy: merchant_ad_campaigns.tani_ad_campaigns_owner_insert
CREATE POLICY tani_ad_campaigns_owner_insert ON public.merchant_ad_campaigns AS PERMISSIVE FOR INSERT TO authenticated WITH CHECK (((status = ANY (ARRAY['draft'::text, 'pending'::text])) AND (EXISTS ( SELECT 1
   FROM sellers s
  WHERE ((s.id = merchant_ad_campaigns.seller_id) AND (s.user_id = ( SELECT auth.uid() AS uid)) AND (s.verification_status = 'approved'::text))))));

-- policy: merchant_ad_campaigns.tani_ad_campaigns_owner_read
CREATE POLICY tani_ad_campaigns_owner_read ON public.merchant_ad_campaigns AS PERMISSIVE FOR SELECT TO authenticated USING (((EXISTS ( SELECT 1
   FROM sellers s
  WHERE ((s.id = merchant_ad_campaigns.seller_id) AND (s.user_id = ( SELECT auth.uid() AS uid))))) OR (current_user_role() = ANY (ARRAY['admin'::text, 'support'::text]))));

-- policy: merchant_identity_documents.tani_merchant_docs_owner_delete
CREATE POLICY tani_merchant_docs_owner_delete ON public.merchant_identity_documents AS PERMISSIVE FOR DELETE TO authenticated USING (((user_id = ( SELECT auth.uid() AS uid)) AND (EXISTS ( SELECT 1
   FROM merchant_profiles mp
  WHERE ((mp.id = merchant_identity_documents.merchant_id) AND (mp.user_id = ( SELECT auth.uid() AS uid)) AND (mp.verification_status = ANY (ARRAY['pending'::text, 'changes_requested'::text, 'rejected'::text])))))));

-- policy: merchant_identity_documents.tani_merchant_docs_owner_insert
CREATE POLICY tani_merchant_docs_owner_insert ON public.merchant_identity_documents AS PERMISSIVE FOR INSERT TO authenticated WITH CHECK (((user_id = ( SELECT auth.uid() AS uid)) AND (EXISTS ( SELECT 1
   FROM merchant_profiles mp
  WHERE ((mp.id = merchant_identity_documents.merchant_id) AND (mp.user_id = ( SELECT auth.uid() AS uid)))))));

-- policy: merchant_identity_documents.tani_merchant_docs_owner_read
CREATE POLICY tani_merchant_docs_owner_read ON public.merchant_identity_documents AS PERMISSIVE FOR SELECT TO authenticated USING (((user_id = ( SELECT auth.uid() AS uid)) OR (current_user_role() = ANY (ARRAY['admin'::text, 'support'::text]))));

-- policy: merchant_metrics.tani_merchant_metrics_owner_admin_read
CREATE POLICY tani_merchant_metrics_owner_admin_read ON public.merchant_metrics AS PERMISSIVE FOR SELECT TO authenticated USING ((EXISTS ( SELECT 1
   FROM sellers s
  WHERE ((s.id = merchant_metrics.seller_id) AND ((s.user_id = ( SELECT auth.uid() AS uid)) OR (current_user_role() = ANY (ARRAY['admin'::text, 'support'::text])))))));

-- policy: merchant_metrics.tani_merchant_metrics_owner_read
CREATE POLICY tani_merchant_metrics_owner_read ON public.merchant_metrics AS PERMISSIVE FOR SELECT TO authenticated USING (((EXISTS ( SELECT 1
   FROM sellers s
  WHERE ((s.id = merchant_metrics.seller_id) AND (s.user_id = ( SELECT auth.uid() AS uid))))) OR (current_user_role() = ANY (ARRAY['admin'::text, 'support'::text]))));

-- policy: merchant_profiles.tani_merchant_profile_owner_insert
CREATE POLICY tani_merchant_profile_owner_insert ON public.merchant_profiles AS PERMISSIVE FOR INSERT TO authenticated WITH CHECK ((user_id = ( SELECT auth.uid() AS uid)));

-- policy: merchant_profiles.tani_merchant_profile_owner_read
CREATE POLICY tani_merchant_profile_owner_read ON public.merchant_profiles AS PERMISSIVE FOR SELECT TO authenticated USING ((user_id = ( SELECT auth.uid() AS uid)));

-- policy: merchant_profiles.tani_merchant_profile_owner_update
CREATE POLICY tani_merchant_profile_owner_update ON public.merchant_profiles AS PERMISSIVE FOR UPDATE TO authenticated USING ((user_id = ( SELECT auth.uid() AS uid))) WITH CHECK ((user_id = ( SELECT auth.uid() AS uid)));

-- policy: merchant_profiles.tani_merchant_profiles_admin_support_read
CREATE POLICY tani_merchant_profiles_admin_support_read ON public.merchant_profiles AS PERMISSIVE FOR SELECT TO authenticated USING ((current_user_role() = ANY (ARRAY['admin'::text, 'support'::text])));

-- policy: merchant_profiles.tani_merchant_profiles_admin_update
CREATE POLICY tani_merchant_profiles_admin_update ON public.merchant_profiles AS PERMISSIVE FOR UPDATE TO authenticated USING ((current_user_role() = 'admin'::text)) WITH CHECK ((current_user_role() = 'admin'::text));

-- policy: merchant_reports.tani_merchant_reports_owner_all
CREATE POLICY tani_merchant_reports_owner_all ON public.merchant_reports AS PERMISSIVE FOR ALL TO authenticated USING (((reporter_id = auth.uid()) OR (current_user_role() = ANY (ARRAY['admin'::text, 'support'::text])))) WITH CHECK ((reporter_id = auth.uid()));

-- policy: merchant_reviews.tani_merchant_reviews_admin_moderate
CREATE POLICY tani_merchant_reviews_admin_moderate ON public.merchant_reviews AS PERMISSIVE FOR UPDATE TO authenticated USING ((current_user_role() = 'admin'::text)) WITH CHECK ((current_user_role() = 'admin'::text));

-- policy: merchant_reviews.tani_merchant_reviews_admin_read
CREATE POLICY tani_merchant_reviews_admin_read ON public.merchant_reviews AS PERMISSIVE FOR SELECT TO authenticated USING ((current_user_role() = ANY (ARRAY['admin'::text, 'support'::text])));

-- policy: merchant_reviews.tani_merchant_reviews_own_delete
CREATE POLICY tani_merchant_reviews_own_delete ON public.merchant_reviews AS PERMISSIVE FOR DELETE TO authenticated USING ((customer_id = ( SELECT auth.uid() AS uid)));

-- policy: merchant_reviews.tani_merchant_reviews_own_update
CREATE POLICY tani_merchant_reviews_own_update ON public.merchant_reviews AS PERMISSIVE FOR UPDATE TO authenticated USING (((customer_id = ( SELECT auth.uid() AS uid)) AND (status = 'published'::text))) WITH CHECK (((customer_id = ( SELECT auth.uid() AS uid)) AND (status = 'published'::text)));

-- policy: merchant_reviews.tani_merchant_reviews_public_read
CREATE POLICY tani_merchant_reviews_public_read ON public.merchant_reviews AS PERMISSIVE FOR SELECT TO anon,authenticated USING ((status = 'published'::text));

-- policy: merchant_reviews.tani_merchant_reviews_transaction_insert
CREATE POLICY tani_merchant_reviews_transaction_insert ON public.merchant_reviews AS PERMISSIVE FOR INSERT TO authenticated WITH CHECK (((customer_id = ( SELECT auth.uid() AS uid)) AND (EXISTS ( SELECT 1
   FROM (orders o
     JOIN sellers s ON ((s.id = o.seller_id)))
  WHERE ((o.id = merchant_reviews.order_id) AND (o.seller_id = merchant_reviews.seller_id) AND (o.customer_id = ( SELECT auth.uid() AS uid)) AND (o.status = 'delivered'::text) AND (s.user_id <> ( SELECT auth.uid() AS uid)))))));

-- policy: merchant_settlements.tani_merchant_settlement_read
CREATE POLICY tani_merchant_settlement_read ON public.merchant_settlements AS PERMISSIVE FOR SELECT TO authenticated USING (((current_user_role() = 'admin'::text) OR (EXISTS ( SELECT 1
   FROM sellers s
  WHERE ((s.id = merchant_settlements.seller_id) AND (s.user_id = auth.uid()))))));

-- policy: merchant_trust_scores.tani_merchant_trust_public_read
CREATE POLICY tani_merchant_trust_public_read ON public.merchant_trust_scores AS PERMISSIVE FOR SELECT TO anon,authenticated USING (true);

-- policy: merchant_verifications.tani_merchant_verification_owner_read
CREATE POLICY tani_merchant_verification_owner_read ON public.merchant_verifications AS PERMISSIVE FOR SELECT TO authenticated USING (((EXISTS ( SELECT 1
   FROM merchant_profiles m
  WHERE ((m.id = merchant_verifications.merchant_id) AND (m.user_id = auth.uid())))) OR (current_user_role() = ANY (ARRAY['admin'::text, 'support'::text]))));

-- policy: messages.tani_message_participant_insert
CREATE POLICY tani_message_participant_insert ON public.messages AS PERMISSIVE FOR INSERT TO authenticated WITH CHECK (((sender_id = auth.uid()) AND (EXISTS ( SELECT 1
   FROM conversations c
  WHERE ((c.id = messages.conversation_id) AND ((c.customer_id = auth.uid()) OR (EXISTS ( SELECT 1
           FROM sellers s
          WHERE ((s.id = c.seller_id) AND (s.user_id = auth.uid())))) OR (current_user_role() = ANY (ARRAY['admin'::text, 'support'::text]))))))));

-- policy: messages.tani_message_participant_read
CREATE POLICY tani_message_participant_read ON public.messages AS PERMISSIVE FOR SELECT TO authenticated USING ((EXISTS ( SELECT 1
   FROM conversations c
  WHERE ((c.id = messages.conversation_id) AND ((c.customer_id = auth.uid()) OR (EXISTS ( SELECT 1
           FROM sellers s
          WHERE ((s.id = c.seller_id) AND (s.user_id = auth.uid())))) OR (current_user_role() = ANY (ARRAY['admin'::text, 'support'::text])))))));

-- policy: notification_preferences.tani_notification_preferences_owner_al
CREATE POLICY tani_notification_preferences_owner_all ON public.notification_preferences AS PERMISSIVE FOR ALL TO public USING ((user_id = auth.uid())) WITH CHECK ((user_id = auth.uid()));

-- policy: notification_preferences.tani_notification_preferences_owner_in
CREATE POLICY tani_notification_preferences_owner_insert ON public.notification_preferences AS PERMISSIVE FOR INSERT TO authenticated WITH CHECK ((user_id = ( SELECT auth.uid() AS uid)));

-- policy: notification_preferences.tani_notification_preferences_owner_re
CREATE POLICY tani_notification_preferences_owner_read ON public.notification_preferences AS PERMISSIVE FOR SELECT TO authenticated USING ((user_id = ( SELECT auth.uid() AS uid)));

-- policy: notification_preferences.tani_notification_preferences_owner_up
CREATE POLICY tani_notification_preferences_owner_update ON public.notification_preferences AS PERMISSIVE FOR UPDATE TO authenticated USING ((user_id = ( SELECT auth.uid() AS uid))) WITH CHECK ((user_id = ( SELECT auth.uid() AS uid)));

-- policy: notifications.tani_notifications_owner_all
CREATE POLICY tani_notifications_owner_all ON public.notifications AS PERMISSIVE FOR ALL TO public USING ((user_id = auth.uid())) WITH CHECK ((user_id = auth.uid()));

-- policy: notifications.tani_notifications_owner_read
CREATE POLICY tani_notifications_owner_read ON public.notifications AS PERMISSIVE FOR SELECT TO authenticated USING ((user_id = ( SELECT auth.uid() AS uid)));

-- policy: notifications.tani_notifications_owner_update
CREATE POLICY tani_notifications_owner_update ON public.notifications AS PERMISSIVE FOR UPDATE TO authenticated USING ((user_id = ( SELECT auth.uid() AS uid))) WITH CHECK ((user_id = ( SELECT auth.uid() AS uid)));

-- policy: operational_alerts.tani_operational_alerts_staff_read
CREATE POLICY tani_operational_alerts_staff_read ON public.operational_alerts AS PERMISSIVE FOR SELECT TO authenticated USING ((current_user_role() = ANY (ARRAY['admin'::text, 'support'::text])));

-- policy: operational_alerts.tani_operational_alerts_staff_update
CREATE POLICY tani_operational_alerts_staff_update ON public.operational_alerts AS PERMISSIVE FOR UPDATE TO authenticated USING ((current_user_role() = ANY (ARRAY['admin'::text, 'support'::text]))) WITH CHECK ((current_user_role() = ANY (ARRAY['admin'::text, 'support'::text])));

-- policy: order_groups.tani_order_groups_owner_read
CREATE POLICY tani_order_groups_owner_read ON public.order_groups AS PERMISSIVE FOR SELECT TO authenticated USING (((customer_id = ( SELECT auth.uid() AS uid)) OR (( SELECT current_user_role() AS current_user_role) = ANY (ARRAY['admin'::text, 'support'::text]))));

-- policy: order_items.tani_order_items_own_read
CREATE POLICY tani_order_items_own_read ON public.order_items AS PERMISSIVE FOR SELECT TO authenticated USING (((EXISTS ( SELECT 1
   FROM orders o
  WHERE ((o.id = order_items.order_id) AND (o.customer_id = ( SELECT auth.uid() AS uid))))) OR (EXISTS ( SELECT 1
   FROM sellers s
  WHERE ((s.id = order_items.seller_id) AND (s.user_id = ( SELECT auth.uid() AS uid)))))));

-- policy: order_reviews.tani_order_reviews_owner_read
CREATE POLICY tani_order_reviews_owner_read ON public.order_reviews AS PERMISSIVE FOR SELECT TO authenticated USING (((customer_id = ( SELECT auth.uid() AS uid)) OR (current_user_role() = ANY (ARRAY['admin'::text, 'support'::text]))));

-- policy: order_reviews.tani_order_reviews_transaction_insert
CREATE POLICY tani_order_reviews_transaction_insert ON public.order_reviews AS PERMISSIVE FOR INSERT TO authenticated WITH CHECK (((customer_id = ( SELECT auth.uid() AS uid)) AND (EXISTS ( SELECT 1
   FROM orders o
  WHERE ((o.id = order_reviews.order_id) AND (o.customer_id = ( SELECT auth.uid() AS uid)) AND (o.status = 'delivered'::text))))));

-- policy: order_status_history.tani_order_status_owner_read
CREATE POLICY tani_order_status_owner_read ON public.order_status_history AS PERMISSIVE FOR SELECT TO authenticated USING ((EXISTS ( SELECT 1
   FROM orders o
  WHERE ((o.id = order_status_history.order_id) AND ((o.customer_id = ( SELECT auth.uid() AS uid)) OR (EXISTS ( SELECT 1
           FROM sellers s
          WHERE ((s.id = o.seller_id) AND (s.user_id = ( SELECT auth.uid() AS uid))))) OR (current_user_role() = ANY (ARRAY['admin'::text, 'support'::text])))))));

-- policy: orders.tani_orders_admin_support_read
CREATE POLICY tani_orders_admin_support_read ON public.orders AS PERMISSIVE FOR SELECT TO authenticated USING ((current_user_role() = ANY (ARRAY['admin'::text, 'support'::text])));

-- policy: orders.tani_orders_admin_update
CREATE POLICY tani_orders_admin_update ON public.orders AS PERMISSIVE FOR UPDATE TO authenticated USING ((current_user_role() = 'admin'::text)) WITH CHECK ((current_user_role() = 'admin'::text));

-- policy: orders.tani_orders_own_insert
CREATE POLICY tani_orders_own_insert ON public.orders AS PERMISSIVE FOR INSERT TO authenticated WITH CHECK ((( SELECT auth.uid() AS uid) = customer_id));

-- policy: orders.tani_orders_own_read
CREATE POLICY tani_orders_own_read ON public.orders AS PERMISSIVE FOR SELECT TO authenticated USING (((customer_id = ( SELECT auth.uid() AS uid)) OR (EXISTS ( SELECT 1
   FROM sellers s
  WHERE ((s.id = orders.seller_id) AND (s.user_id = ( SELECT auth.uid() AS uid)))))));

-- policy: payment_methods.tani_payment_methods_admin_all
CREATE POLICY tani_payment_methods_admin_all ON public.payment_methods AS PERMISSIVE FOR ALL TO authenticated USING ((current_user_role() = 'admin'::text)) WITH CHECK ((current_user_role() = 'admin'::text));

-- policy: payment_methods.tani_payment_methods_public_read
CREATE POLICY tani_payment_methods_public_read ON public.payment_methods AS PERMISSIVE FOR SELECT TO anon,authenticated USING ((is_active = true));

-- policy: payments.tani_payments_owner_read
CREATE POLICY tani_payments_owner_read ON public.payments AS PERMISSIVE FOR SELECT TO authenticated USING ((EXISTS ( SELECT 1
   FROM orders o
  WHERE ((o.id = payments.order_id) AND ((o.customer_id = auth.uid()) OR (current_user_role() = ANY (ARRAY['admin'::text, 'support'::text])) OR (EXISTS ( SELECT 1
           FROM sellers s
          WHERE ((s.id = o.seller_id) AND (s.user_id = auth.uid())))))))));

-- policy: product_images.tani_product_images_owner_write
CREATE POLICY tani_product_images_owner_write ON public.product_images AS PERMISSIVE FOR ALL TO authenticated USING ((EXISTS ( SELECT 1
   FROM (products p
     JOIN sellers s ON ((s.id = p.seller_id)))
  WHERE ((p.id = product_images.product_id) AND (s.user_id = ( SELECT auth.uid() AS uid)) AND (s.verification_status = 'approved'::text))))) WITH CHECK ((EXISTS ( SELECT 1
   FROM (products p
     JOIN sellers s ON ((s.id = p.seller_id)))
  WHERE ((p.id = product_images.product_id) AND (s.user_id = ( SELECT auth.uid() AS uid)) AND (s.verification_status = 'approved'::text)))));

-- policy: product_images.tani_product_images_public_read
CREATE POLICY tani_product_images_public_read ON public.product_images AS PERMISSIVE FOR SELECT TO public USING (true);

-- policy: product_reports.tani_product_reports_owner_all
CREATE POLICY tani_product_reports_owner_all ON public.product_reports AS PERMISSIVE FOR ALL TO authenticated USING (((reporter_id = auth.uid()) OR (current_user_role() = ANY (ARRAY['admin'::text, 'support'::text])))) WITH CHECK ((reporter_id = auth.uid()));

-- policy: product_variants.tani_variants_anon_read
CREATE POLICY tani_variants_anon_read ON public.product_variants AS PERMISSIVE FOR SELECT TO anon USING ((is_active = true));

-- policy: product_variants.tani_variants_owner_write
CREATE POLICY tani_variants_owner_write ON public.product_variants AS PERMISSIVE FOR ALL TO authenticated USING ((EXISTS ( SELECT 1
   FROM (products p
     JOIN sellers s ON ((s.id = p.seller_id)))
  WHERE ((p.id = product_variants.product_id) AND (s.user_id = ( SELECT auth.uid() AS uid)) AND (s.verification_status = 'approved'::text))))) WITH CHECK ((EXISTS ( SELECT 1
   FROM (products p
     JOIN sellers s ON ((s.id = p.seller_id)))
  WHERE ((p.id = product_variants.product_id) AND (s.user_id = ( SELECT auth.uid() AS uid)) AND (s.verification_status = 'approved'::text)))));

-- policy: product_variants.tani_variants_public_read
CREATE POLICY tani_variants_public_read ON public.product_variants AS PERMISSIVE FOR SELECT TO authenticated USING (((is_active = true) OR (EXISTS ( SELECT 1
   FROM (products p
     JOIN sellers s ON ((s.id = p.seller_id)))
  WHERE ((p.id = product_variants.product_id) AND (s.user_id = ( SELECT auth.uid() AS uid))))) OR (( SELECT current_user_role() AS current_user_role) = 'admin'::text)));

-- policy: products.tani_products_admin_support_read
CREATE POLICY tani_products_admin_support_read ON public.products AS PERMISSIVE FOR SELECT TO authenticated USING ((current_user_role() = ANY (ARRAY['admin'::text, 'support'::text])));

-- policy: products.tani_products_admin_update
CREATE POLICY tani_products_admin_update ON public.products AS PERMISSIVE FOR UPDATE TO authenticated USING ((current_user_role() = 'admin'::text)) WITH CHECK ((current_user_role() = 'admin'::text));

-- policy: products.tani_products_own_delete
CREATE POLICY tani_products_own_delete ON public.products AS PERMISSIVE FOR DELETE TO authenticated USING ((EXISTS ( SELECT 1
   FROM sellers s
  WHERE ((s.id = products.seller_id) AND (s.user_id = ( SELECT auth.uid() AS uid)) AND (s.verification_status = 'approved'::text)))));

-- policy: products.tani_products_own_insert
CREATE POLICY tani_products_own_insert ON public.products AS PERMISSIVE FOR INSERT TO authenticated WITH CHECK ((EXISTS ( SELECT 1
   FROM sellers s
  WHERE ((s.id = products.seller_id) AND (s.user_id = ( SELECT auth.uid() AS uid)) AND (s.verification_status = 'approved'::text)))));

-- policy: products.tani_products_own_update
CREATE POLICY tani_products_own_update ON public.products AS PERMISSIVE FOR UPDATE TO authenticated USING ((EXISTS ( SELECT 1
   FROM sellers s
  WHERE ((s.id = products.seller_id) AND (s.user_id = ( SELECT auth.uid() AS uid)) AND (s.verification_status = 'approved'::text))))) WITH CHECK ((EXISTS ( SELECT 1
   FROM sellers s
  WHERE ((s.id = products.seller_id) AND (s.user_id = ( SELECT auth.uid() AS uid)) AND (s.verification_status = 'approved'::text)))));

-- policy: products.tani_products_owner_read
CREATE POLICY tani_products_owner_read ON public.products AS PERMISSIVE FOR SELECT TO authenticated USING ((EXISTS ( SELECT 1
   FROM sellers s
  WHERE ((s.id = products.seller_id) AND (s.user_id = ( SELECT auth.uid() AS uid))))));

-- policy: products.tani_products_public_read_active
CREATE POLICY tani_products_public_read_active ON public.products AS PERMISSIVE FOR SELECT TO anon,authenticated USING ((is_active = true));

-- policy: profiles.tani_profiles_admin_support_read
CREATE POLICY tani_profiles_admin_support_read ON public.profiles AS PERMISSIVE FOR SELECT TO authenticated USING ((current_user_role() = ANY (ARRAY['admin'::text, 'support'::text])));

-- policy: profiles.tani_profiles_admin_update
CREATE POLICY tani_profiles_admin_update ON public.profiles AS PERMISSIVE FOR UPDATE TO authenticated USING ((current_user_role() = 'admin'::text)) WITH CHECK ((current_user_role() = 'admin'::text));

-- policy: profiles.tani_profiles_own_read
CREATE POLICY tani_profiles_own_read ON public.profiles AS PERMISSIVE FOR SELECT TO authenticated USING ((( SELECT auth.uid() AS uid) = id));

-- policy: profiles.tani_profiles_own_update
CREATE POLICY tani_profiles_own_update ON public.profiles AS PERMISSIVE FOR UPDATE TO authenticated USING ((( SELECT auth.uid() AS uid) = id)) WITH CHECK ((( SELECT auth.uid() AS uid) = id));

-- policy: promotions.tani_promotion_owner_write
CREATE POLICY tani_promotion_owner_write ON public.promotions AS PERMISSIVE FOR ALL TO authenticated USING (((current_user_role() = 'admin'::text) OR (EXISTS ( SELECT 1
   FROM sellers s
  WHERE ((s.id = promotions.seller_id) AND (s.user_id = auth.uid())))))) WITH CHECK (((current_user_role() = 'admin'::text) OR (EXISTS ( SELECT 1
   FROM sellers s
  WHERE ((s.id = promotions.seller_id) AND (s.user_id = auth.uid()))))));

-- policy: promotions.tani_promotion_public_read
CREATE POLICY tani_promotion_public_read ON public.promotions AS PERMISSIVE FOR SELECT TO authenticated USING (((is_active = true) OR (current_user_role() = 'admin'::text) OR (EXISTS ( SELECT 1
   FROM sellers s
  WHERE ((s.id = promotions.seller_id) AND (s.user_id = auth.uid()))))));

-- policy: promotions.tani_promotions_anon_read
CREATE POLICY tani_promotions_anon_read ON public.promotions AS PERMISSIVE FOR SELECT TO anon USING ((is_active = true));

-- policy: push_devices.tani_push_devices_owner_delete
CREATE POLICY tani_push_devices_owner_delete ON public.push_devices AS PERMISSIVE FOR DELETE TO authenticated USING ((( SELECT auth.uid() AS uid) = user_id));

-- policy: push_devices.tani_push_devices_owner_insert
CREATE POLICY tani_push_devices_owner_insert ON public.push_devices AS PERMISSIVE FOR INSERT TO authenticated WITH CHECK ((( SELECT auth.uid() AS uid) = user_id));

-- policy: push_devices.tani_push_devices_owner_select
CREATE POLICY tani_push_devices_owner_select ON public.push_devices AS PERMISSIVE FOR SELECT TO authenticated USING ((( SELECT auth.uid() AS uid) = user_id));

-- policy: push_devices.tani_push_devices_owner_update
CREATE POLICY tani_push_devices_owner_update ON public.push_devices AS PERMISSIVE FOR UPDATE TO authenticated USING ((( SELECT auth.uid() AS uid) = user_id)) WITH CHECK ((( SELECT auth.uid() AS uid) = user_id));

-- policy: referral_codes.tani_referral_codes_owner_insert
CREATE POLICY tani_referral_codes_owner_insert ON public.referral_codes AS PERMISSIVE FOR INSERT TO authenticated WITH CHECK ((user_id = ( SELECT auth.uid() AS uid)));

-- policy: referral_codes.tani_referral_codes_owner_read
CREATE POLICY tani_referral_codes_owner_read ON public.referral_codes AS PERMISSIVE FOR SELECT TO authenticated USING ((user_id = ( SELECT auth.uid() AS uid)));

-- policy: referrals.tani_referrals_owner_read
CREATE POLICY tani_referrals_owner_read ON public.referrals AS PERMISSIVE FOR SELECT TO authenticated USING (((referrer_id = auth.uid()) OR (referred_id = auth.uid()) OR (current_user_role() = 'admin'::text)));

-- policy: referrals.tani_referrals_participant_read
CREATE POLICY tani_referrals_participant_read ON public.referrals AS PERMISSIVE FOR SELECT TO authenticated USING (((referrer_id = ( SELECT auth.uid() AS uid)) OR (referred_id = ( SELECT auth.uid() AS uid))));

-- policy: refunds.tani_refund_read
CREATE POLICY tani_refund_read ON public.refunds AS PERMISSIVE FOR SELECT TO authenticated USING (((current_user_role() = ANY (ARRAY['admin'::text, 'support'::text])) OR (EXISTS ( SELECT 1
   FROM (payments p
     JOIN orders o ON ((o.id = p.order_id)))
  WHERE ((p.id = refunds.payment_id) AND ((o.customer_id = auth.uid()) OR (EXISTS ( SELECT 1
           FROM sellers s
          WHERE ((s.id = o.seller_id) AND (s.user_id = auth.uid()))))))))));

-- policy: reports.tani_admin_reports
CREATE POLICY tani_admin_reports ON public.reports AS PERMISSIVE FOR ALL TO authenticated USING ((current_user_role() = ANY (ARRAY['admin'::text, 'support'::text]))) WITH CHECK ((current_user_role() = ANY (ARRAY['admin'::text, 'support'::text])));

-- policy: review_reports.tani_review_reports_owner_insert
CREATE POLICY tani_review_reports_owner_insert ON public.review_reports AS PERMISSIVE FOR INSERT TO authenticated WITH CHECK ((reporter_id = ( SELECT auth.uid() AS uid)));

-- policy: review_reports.tani_review_reports_owner_read
CREATE POLICY tani_review_reports_owner_read ON public.review_reports AS PERMISSIVE FOR SELECT TO authenticated USING (((reporter_id = ( SELECT auth.uid() AS uid)) OR (current_user_role() = ANY (ARRAY['admin'::text, 'support'::text]))));

-- policy: review_reports.tani_review_reports_staff_update
CREATE POLICY tani_review_reports_staff_update ON public.review_reports AS PERMISSIVE FOR UPDATE TO authenticated USING ((current_user_role() = ANY (ARRAY['admin'::text, 'support'::text]))) WITH CHECK ((current_user_role() = ANY (ARRAY['admin'::text, 'support'::text])));

-- policy: reviews.tani_reviews_admin_moderate
CREATE POLICY tani_reviews_admin_moderate ON public.reviews AS PERMISSIVE FOR UPDATE TO authenticated USING ((current_user_role() = 'admin'::text)) WITH CHECK ((current_user_role() = 'admin'::text));

-- policy: reviews.tani_reviews_admin_read
CREATE POLICY tani_reviews_admin_read ON public.reviews AS PERMISSIVE FOR SELECT TO authenticated USING ((current_user_role() = ANY (ARRAY['admin'::text, 'support'::text])));

-- policy: reviews.tani_reviews_own_delete
CREATE POLICY tani_reviews_own_delete ON public.reviews AS PERMISSIVE FOR DELETE TO authenticated USING ((( SELECT auth.uid() AS uid) = customer_id));

-- policy: reviews.tani_reviews_own_update
CREATE POLICY tani_reviews_own_update ON public.reviews AS PERMISSIVE FOR UPDATE TO authenticated USING (((customer_id = ( SELECT auth.uid() AS uid)) AND (status = 'published'::text))) WITH CHECK (((customer_id = ( SELECT auth.uid() AS uid)) AND (status = 'published'::text)));

-- policy: reviews.tani_reviews_public_read
CREATE POLICY tani_reviews_public_read ON public.reviews AS PERMISSIVE FOR SELECT TO anon,authenticated USING ((status = 'published'::text));

-- policy: reviews.tani_reviews_transaction_insert
CREATE POLICY tani_reviews_transaction_insert ON public.reviews AS PERMISSIVE FOR INSERT TO authenticated WITH CHECK (((customer_id = ( SELECT auth.uid() AS uid)) AND (order_id IS NOT NULL) AND (EXISTS ( SELECT 1
   FROM (((orders o
     JOIN order_items oi ON ((oi.order_id = o.id)))
     JOIN products p ON ((p.id = reviews.product_id)))
     JOIN sellers s ON ((s.id = p.seller_id)))
  WHERE ((o.id = reviews.order_id) AND (o.customer_id = ( SELECT auth.uid() AS uid)) AND (o.status = 'delivered'::text) AND (oi.product_id = reviews.product_id) AND (s.user_id <> ( SELECT auth.uid() AS uid)))))));

-- policy: sellers.tani_sellers_admin_support_read
CREATE POLICY tani_sellers_admin_support_read ON public.sellers AS PERMISSIVE FOR SELECT TO authenticated USING ((current_user_role() = ANY (ARRAY['admin'::text, 'support'::text])));

-- policy: sellers.tani_sellers_admin_update
CREATE POLICY tani_sellers_admin_update ON public.sellers AS PERMISSIVE FOR UPDATE TO authenticated USING ((current_user_role() = 'admin'::text)) WITH CHECK ((current_user_role() = 'admin'::text));

-- policy: sellers.tani_sellers_own_insert
CREATE POLICY tani_sellers_own_insert ON public.sellers AS PERMISSIVE FOR INSERT TO authenticated WITH CHECK ((( SELECT auth.uid() AS uid) = user_id));

-- policy: sellers.tani_sellers_own_update
CREATE POLICY tani_sellers_own_update ON public.sellers AS PERMISSIVE FOR UPDATE TO authenticated USING ((( SELECT auth.uid() AS uid) = user_id)) WITH CHECK ((( SELECT auth.uid() AS uid) = user_id));

-- policy: sellers.tani_sellers_public_read_approved
CREATE POLICY tani_sellers_public_read_approved ON public.sellers AS PERMISSIVE FOR SELECT TO anon,authenticated USING (((verification_status = 'approved'::text) OR (user_id = ( SELECT auth.uid() AS uid))));

-- policy: stores.tani_store_owner_write
CREATE POLICY tani_store_owner_write ON public.stores AS PERMISSIVE FOR ALL TO authenticated USING ((EXISTS ( SELECT 1
   FROM (merchant_profiles mp
     JOIN sellers s ON ((s.id = mp.seller_id)))
  WHERE ((mp.id = stores.merchant_id) AND (mp.user_id = ( SELECT auth.uid() AS uid)) AND (mp.verification_status = 'approved'::text) AND (s.verification_status = 'approved'::text))))) WITH CHECK ((EXISTS ( SELECT 1
   FROM (merchant_profiles mp
     JOIN sellers s ON ((s.id = mp.seller_id)))
  WHERE ((mp.id = stores.merchant_id) AND (mp.user_id = ( SELECT auth.uid() AS uid)) AND (mp.verification_status = 'approved'::text) AND (s.verification_status = 'approved'::text)))));

-- policy: stores.tani_store_public_read
CREATE POLICY tani_store_public_read ON public.stores AS PERMISSIVE FOR SELECT TO authenticated USING (((is_active = true) OR (EXISTS ( SELECT 1
   FROM merchant_profiles m
  WHERE ((m.id = stores.merchant_id) AND (m.user_id = ( SELECT auth.uid() AS uid))))) OR (( SELECT current_user_role() AS current_user_role) = 'admin'::text)));

-- policy: stores.tani_stores_anon_read
CREATE POLICY tani_stores_anon_read ON public.stores AS PERMISSIVE FOR SELECT TO anon USING ((is_active = true));

-- policy: subscription_plans.tani_plans_public_read
CREATE POLICY tani_plans_public_read ON public.subscription_plans AS PERMISSIVE FOR SELECT TO authenticated USING (((is_active = true) OR (current_user_role() = 'admin'::text)));

-- policy: subscription_plans.tani_subscription_plans_admin_all
CREATE POLICY tani_subscription_plans_admin_all ON public.subscription_plans AS PERMISSIVE FOR ALL TO authenticated USING ((current_user_role() = 'admin'::text)) WITH CHECK ((current_user_role() = 'admin'::text));

-- policy: subscription_plans.tani_subscription_plans_public_read
CREATE POLICY tani_subscription_plans_public_read ON public.subscription_plans AS PERMISSIVE FOR SELECT TO anon,authenticated USING ((is_active = true));

-- policy: subscription_requests.tani_subscription_requests_admin_update
CREATE POLICY tani_subscription_requests_admin_update ON public.subscription_requests AS PERMISSIVE FOR UPDATE TO authenticated USING ((current_user_role() = 'admin'::text)) WITH CHECK ((current_user_role() = 'admin'::text));

-- policy: subscription_requests.tani_subscription_requests_owner_cancel
CREATE POLICY tani_subscription_requests_owner_cancel ON public.subscription_requests AS PERMISSIVE FOR UPDATE TO authenticated USING (((user_id = ( SELECT auth.uid() AS uid)) AND (status = 'pending'::text))) WITH CHECK (((user_id = ( SELECT auth.uid() AS uid)) AND (status = 'cancelled'::text)));

-- policy: subscription_requests.tani_subscription_requests_owner_insert
CREATE POLICY tani_subscription_requests_owner_insert ON public.subscription_requests AS PERMISSIVE FOR INSERT TO authenticated WITH CHECK (((user_id = ( SELECT auth.uid() AS uid)) AND (status = 'pending'::text) AND (EXISTS ( SELECT 1
   FROM sellers s
  WHERE ((s.id = subscription_requests.seller_id) AND (s.user_id = ( SELECT auth.uid() AS uid)) AND (s.verification_status = 'approved'::text)))) AND (EXISTS ( SELECT 1
   FROM subscription_plans p
  WHERE ((p.id = subscription_requests.plan_id) AND p.is_active AND (p.audience = ANY (ARRAY['merchant'::text, 'all'::text])))))));

-- policy: subscription_requests.tani_subscription_requests_owner_read
CREATE POLICY tani_subscription_requests_owner_read ON public.subscription_requests AS PERMISSIVE FOR SELECT TO authenticated USING (((user_id = ( SELECT auth.uid() AS uid)) OR (current_user_role() = ANY (ARRAY['admin'::text, 'support'::text]))));

-- policy: subscriptions.tani_subscriptions_admin_all
CREATE POLICY tani_subscriptions_admin_all ON public.subscriptions AS PERMISSIVE FOR ALL TO authenticated USING ((current_user_role() = 'admin'::text)) WITH CHECK ((current_user_role() = 'admin'::text));

-- policy: subscriptions.tani_subscriptions_owner_read
CREATE POLICY tani_subscriptions_owner_read ON public.subscriptions AS PERMISSIVE FOR SELECT TO authenticated USING (((user_id = ( SELECT auth.uid() AS uid)) OR (current_user_role() = ANY (ARRAY['admin'::text, 'support'::text]))));

-- policy: support_tickets.tani_ticket_owner_insert
CREATE POLICY tani_ticket_owner_insert ON public.support_tickets AS PERMISSIVE FOR INSERT TO public WITH CHECK ((user_id = auth.uid()));

-- policy: support_tickets.tani_ticket_owner_read
CREATE POLICY tani_ticket_owner_read ON public.support_tickets AS PERMISSIVE FOR SELECT TO authenticated USING (((user_id = auth.uid()) OR (current_user_role() = ANY (ARRAY['admin'::text, 'support'::text]))));

-- policy: support_tickets.tani_ticket_owner_update
CREATE POLICY tani_ticket_owner_update ON public.support_tickets AS PERMISSIVE FOR UPDATE TO authenticated USING ((user_id = ( SELECT auth.uid() AS uid))) WITH CHECK (((user_id = ( SELECT auth.uid() AS uid)) AND (assigned_to IS NULL) AND (status = ANY (ARRAY['open'::text, 'closed'::text]))));

-- policy: support_tickets.tani_ticket_staff_update
CREATE POLICY tani_ticket_staff_update ON public.support_tickets AS PERMISSIVE FOR UPDATE TO authenticated USING ((current_user_role() = ANY (ARRAY['admin'::text, 'support'::text]))) WITH CHECK ((current_user_role() = ANY (ARRAY['admin'::text, 'support'::text])));

-- policy: system_settings.tani_admin_system_settings
CREATE POLICY tani_admin_system_settings ON public.system_settings AS PERMISSIVE FOR ALL TO authenticated USING ((current_user_role() = 'admin'::text)) WITH CHECK ((current_user_role() = 'admin'::text));

-- policy: ticket_messages.tani_ticket_message_insert
CREATE POLICY tani_ticket_message_insert ON public.ticket_messages AS PERMISSIVE FOR INSERT TO authenticated WITH CHECK (((sender_id = auth.uid()) AND (EXISTS ( SELECT 1
   FROM support_tickets t
  WHERE ((t.id = ticket_messages.ticket_id) AND ((t.user_id = auth.uid()) OR (current_user_role() = ANY (ARRAY['admin'::text, 'support'::text]))))))));

-- policy: ticket_messages.tani_ticket_message_read
CREATE POLICY tani_ticket_message_read ON public.ticket_messages AS PERMISSIVE FOR SELECT TO authenticated USING ((EXISTS ( SELECT 1
   FROM support_tickets t
  WHERE ((t.id = ticket_messages.ticket_id) AND ((t.user_id = auth.uid()) OR (current_user_role() = ANY (ARRAY['admin'::text, 'support'::text])))))));

-- policy: user_reports.tani_user_reports_owner_all
CREATE POLICY tani_user_reports_owner_all ON public.user_reports AS PERMISSIVE FOR ALL TO authenticated USING (((reporter_id = auth.uid()) OR (current_user_role() = ANY (ARRAY['admin'::text, 'support'::text])))) WITH CHECK ((reporter_id = auth.uid()));

-- policy: wallet_transactions.tani_wallet_tx_owner_read
CREATE POLICY tani_wallet_tx_owner_read ON public.wallet_transactions AS PERMISSIVE FOR SELECT TO authenticated USING ((EXISTS ( SELECT 1
   FROM wallets w
  WHERE ((w.id = wallet_transactions.wallet_id) AND ((w.user_id = auth.uid()) OR (current_user_role() = 'admin'::text))))));

-- policy: wallets.tani_wallet_admin_insert
CREATE POLICY tani_wallet_admin_insert ON public.wallets AS PERMISSIVE FOR INSERT TO authenticated WITH CHECK (((current_user_role() = 'admin'::text) OR (user_id = auth.uid())));

-- policy: wallets.tani_wallet_admin_update
CREATE POLICY tani_wallet_admin_update ON public.wallets AS PERMISSIVE FOR UPDATE TO authenticated USING ((current_user_role() = 'admin'::text)) WITH CHECK ((current_user_role() = 'admin'::text));

-- policy: wallets.tani_wallet_owner_read
CREATE POLICY tani_wallet_owner_read ON public.wallets AS PERMISSIVE FOR SELECT TO authenticated USING (((user_id = auth.uid()) OR (current_user_role() = 'admin'::text)));

-- grant: addresses.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.addresses TO authenticated;

-- grant: addresses.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.addresses TO service_role;

-- grant: app_design_tokens.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.app_design_tokens TO anon;

-- grant: app_design_tokens.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.app_design_tokens TO authenticated;

-- grant: app_design_tokens.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.app_design_tokens TO service_role;

-- grant: app_errors.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.app_errors TO anon;

-- grant: app_errors.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.app_errors TO authenticated;

-- grant: app_errors.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.app_errors TO service_role;

-- grant: app_events.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.app_events TO anon;

-- grant: app_events.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.app_events TO authenticated;

-- grant: app_events.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.app_events TO service_role;

-- grant: app_settings.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.app_settings TO anon;

-- grant: app_settings.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.app_settings TO authenticated;

-- grant: app_settings.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.app_settings TO service_role;

-- grant: audit_logs.anon
GRANT SELECT,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.audit_logs TO anon;

-- grant: audit_logs.authenticated
GRANT INSERT,SELECT,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.audit_logs TO authenticated;

-- grant: audit_logs.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.audit_logs TO service_role;

-- grant: banners.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.banners TO anon;

-- grant: banners.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.banners TO authenticated;

-- grant: banners.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.banners TO service_role;

-- grant: billing_transactions.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.billing_transactions TO anon;

-- grant: billing_transactions.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.billing_transactions TO authenticated;

-- grant: billing_transactions.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.billing_transactions TO service_role;

-- grant: campaigns.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.campaigns TO anon;

-- grant: campaigns.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.campaigns TO authenticated;

-- grant: campaigns.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.campaigns TO service_role;

-- grant: cart_items.authenticated
GRANT SELECT,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.cart_items TO authenticated;

-- grant: cart_items.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.cart_items TO service_role;

-- grant: carts.authenticated
GRANT SELECT,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.carts TO authenticated;

-- grant: carts.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.carts TO service_role;

-- grant: categories.anon
GRANT SELECT,MAINTAIN ON TABLE public.categories TO anon;

-- grant: categories.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.categories TO authenticated;

-- grant: categories.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.categories TO service_role;

-- grant: complaints.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.complaints TO anon;

-- grant: complaints.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.complaints TO authenticated;

-- grant: complaints.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.complaints TO service_role;

-- grant: conversations.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.conversations TO anon;

-- grant: conversations.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.conversations TO authenticated;

-- grant: conversations.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.conversations TO service_role;

-- grant: coupon_usages.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.coupon_usages TO anon;

-- grant: coupon_usages.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.coupon_usages TO authenticated;

-- grant: coupon_usages.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.coupon_usages TO service_role;

-- grant: coupons.anon
GRANT SELECT,MAINTAIN ON TABLE public.coupons TO anon;

-- grant: coupons.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.coupons TO authenticated;

-- grant: coupons.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.coupons TO service_role;

-- grant: delivery_options.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.delivery_options TO anon;

-- grant: delivery_options.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.delivery_options TO authenticated;

-- grant: delivery_options.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.delivery_options TO service_role;

-- grant: delivery_provider_assignments.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.delivery_provider_assignments TO anon;

-- grant: delivery_provider_assignments.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.delivery_provider_assignments TO authenticated;

-- grant: delivery_provider_assignments.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.delivery_provider_assignments TO service_role;

-- grant: delivery_providers.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.delivery_providers TO anon;

-- grant: delivery_providers.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.delivery_providers TO authenticated;

-- grant: delivery_providers.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.delivery_providers TO service_role;

-- grant: delivery_settings.anon
GRANT SELECT,MAINTAIN ON TABLE public.delivery_settings TO anon;

-- grant: delivery_settings.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.delivery_settings TO authenticated;

-- grant: delivery_settings.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.delivery_settings TO service_role;

-- grant: delivery_zones.anon
GRANT SELECT,MAINTAIN ON TABLE public.delivery_zones TO anon;

-- grant: delivery_zones.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.delivery_zones TO authenticated;

-- grant: delivery_zones.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.delivery_zones TO service_role;

-- grant: disputes.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.disputes TO anon;

-- grant: disputes.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.disputes TO authenticated;

-- grant: disputes.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.disputes TO service_role;

-- grant: favorites.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.favorites TO anon;

-- grant: favorites.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.favorites TO authenticated;

-- grant: favorites.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.favorites TO service_role;

-- grant: feature_flags.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.feature_flags TO anon;

-- grant: feature_flags.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.feature_flags TO authenticated;

-- grant: feature_flags.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.feature_flags TO service_role;

-- grant: featured_placements.anon
GRANT SELECT,MAINTAIN ON TABLE public.featured_placements TO anon;

-- grant: featured_placements.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.featured_placements TO authenticated;

-- grant: featured_placements.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.featured_placements TO service_role;

-- grant: featured_requests.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.featured_requests TO anon;

-- grant: featured_requests.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.featured_requests TO authenticated;

-- grant: featured_requests.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.featured_requests TO service_role;

-- grant: fee_rules.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.fee_rules TO anon;

-- grant: fee_rules.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.fee_rules TO authenticated;

-- grant: fee_rules.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.fee_rules TO service_role;

-- grant: inventory.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.inventory TO anon;

-- grant: inventory.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.inventory TO authenticated;

-- grant: inventory.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.inventory TO service_role;

-- grant: loyalty_accounts.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.loyalty_accounts TO anon;

-- grant: loyalty_accounts.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.loyalty_accounts TO authenticated;

-- grant: loyalty_accounts.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.loyalty_accounts TO service_role;

-- grant: loyalty_transactions.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.loyalty_transactions TO anon;

-- grant: loyalty_transactions.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.loyalty_transactions TO authenticated;

-- grant: loyalty_transactions.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.loyalty_transactions TO service_role;

-- grant: market_cities.anon
GRANT SELECT,MAINTAIN ON TABLE public.market_cities TO anon;

-- grant: market_cities.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.market_cities TO authenticated;

-- grant: market_cities.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.market_cities TO service_role;

-- grant: marketplace_product_cards.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.marketplace_product_cards TO anon;

-- grant: marketplace_product_cards.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.marketplace_product_cards TO authenticated;

-- grant: marketplace_product_cards.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.marketplace_product_cards TO service_role;

-- grant: marketplace_store_cards.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.marketplace_store_cards TO anon;

-- grant: marketplace_store_cards.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.marketplace_store_cards TO authenticated;

-- grant: marketplace_store_cards.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.marketplace_store_cards TO service_role;

-- grant: merchant_ad_campaigns.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.merchant_ad_campaigns TO anon;

-- grant: merchant_ad_campaigns.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.merchant_ad_campaigns TO authenticated;

-- grant: merchant_ad_campaigns.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.merchant_ad_campaigns TO service_role;

-- grant: merchant_identity_documents.anon
GRANT SELECT,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.merchant_identity_documents TO anon;

-- grant: merchant_identity_documents.authenticated
GRANT SELECT,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.merchant_identity_documents TO authenticated;

-- grant: merchant_identity_documents.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.merchant_identity_documents TO service_role;

-- grant: merchant_metrics.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.merchant_metrics TO authenticated;

-- grant: merchant_metrics.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.merchant_metrics TO service_role;

-- grant: merchant_profiles.anon
GRANT SELECT,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.merchant_profiles TO anon;

-- grant: merchant_profiles.authenticated
GRANT SELECT,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.merchant_profiles TO authenticated;

-- grant: merchant_profiles.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.merchant_profiles TO service_role;

-- grant: merchant_reports.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.merchant_reports TO anon;

-- grant: merchant_reports.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.merchant_reports TO authenticated;

-- grant: merchant_reports.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.merchant_reports TO service_role;

-- grant: merchant_reviews.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.merchant_reviews TO anon;

-- grant: merchant_reviews.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.merchant_reviews TO authenticated;

-- grant: merchant_reviews.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.merchant_reviews TO service_role;

-- grant: merchant_settlements.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.merchant_settlements TO anon;

-- grant: merchant_settlements.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.merchant_settlements TO authenticated;

-- grant: merchant_settlements.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.merchant_settlements TO service_role;

-- grant: merchant_trust_scores.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.merchant_trust_scores TO anon;

-- grant: merchant_trust_scores.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.merchant_trust_scores TO authenticated;

-- grant: merchant_trust_scores.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.merchant_trust_scores TO service_role;

-- grant: merchant_verifications.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.merchant_verifications TO anon;

-- grant: merchant_verifications.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.merchant_verifications TO authenticated;

-- grant: merchant_verifications.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.merchant_verifications TO service_role;

-- grant: messages.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.messages TO anon;

-- grant: messages.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.messages TO authenticated;

-- grant: messages.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.messages TO service_role;

-- grant: my_cart_items.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.my_cart_items TO anon;

-- grant: my_cart_items.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.my_cart_items TO authenticated;

-- grant: my_cart_items.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.my_cart_items TO service_role;

-- grant: notification_preferences.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.notification_preferences TO anon;

-- grant: notification_preferences.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.notification_preferences TO authenticated;

-- grant: notification_preferences.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.notification_preferences TO service_role;

-- grant: notifications.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.notifications TO anon;

-- grant: notifications.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.notifications TO authenticated;

-- grant: notifications.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.notifications TO service_role;

-- grant: operational_alerts.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.operational_alerts TO anon;

-- grant: operational_alerts.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.operational_alerts TO authenticated;

-- grant: operational_alerts.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.operational_alerts TO service_role;

-- grant: order_groups.authenticated
GRANT SELECT,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.order_groups TO authenticated;

-- grant: order_groups.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.order_groups TO service_role;

-- grant: order_items.authenticated
GRANT SELECT,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.order_items TO authenticated;

-- grant: order_items.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.order_items TO service_role;

-- grant: order_reviews.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.order_reviews TO anon;

-- grant: order_reviews.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.order_reviews TO authenticated;

-- grant: order_reviews.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.order_reviews TO service_role;

-- grant: order_status_history.authenticated
GRANT SELECT,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.order_status_history TO authenticated;

-- grant: order_status_history.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.order_status_history TO service_role;

-- grant: orders.authenticated
GRANT SELECT,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.orders TO authenticated;

-- grant: orders.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.orders TO service_role;

-- grant: payment_methods.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.payment_methods TO anon;

-- grant: payment_methods.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.payment_methods TO authenticated;

-- grant: payment_methods.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.payment_methods TO service_role;

-- grant: payments.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.payments TO anon;

-- grant: payments.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.payments TO authenticated;

-- grant: payments.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.payments TO service_role;

-- grant: product_images.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.product_images TO anon;

-- grant: product_images.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.product_images TO authenticated;

-- grant: product_images.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.product_images TO service_role;

-- grant: product_reports.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.product_reports TO anon;

-- grant: product_reports.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.product_reports TO authenticated;

-- grant: product_reports.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.product_reports TO service_role;

-- grant: product_variants.anon
GRANT SELECT,MAINTAIN ON TABLE public.product_variants TO anon;

-- grant: product_variants.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.product_variants TO authenticated;

-- grant: product_variants.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.product_variants TO service_role;

-- grant: products.anon
GRANT SELECT,MAINTAIN ON TABLE public.products TO anon;

-- grant: products.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.products TO authenticated;

-- grant: products.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.products TO service_role;

-- grant: profiles.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.profiles TO anon;

-- grant: profiles.authenticated
GRANT SELECT,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.profiles TO authenticated;

-- grant: profiles.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.profiles TO service_role;

-- grant: promotions.anon
GRANT SELECT,MAINTAIN ON TABLE public.promotions TO anon;

-- grant: promotions.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.promotions TO authenticated;

-- grant: promotions.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.promotions TO service_role;

-- grant: push_devices.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE ON TABLE public.push_devices TO authenticated;

-- grant: push_devices.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.push_devices TO service_role;

-- grant: referral_codes.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.referral_codes TO anon;

-- grant: referral_codes.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.referral_codes TO authenticated;

-- grant: referral_codes.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.referral_codes TO service_role;

-- grant: referrals.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.referrals TO anon;

-- grant: referrals.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.referrals TO authenticated;

-- grant: referrals.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.referrals TO service_role;

-- grant: refunds.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.refunds TO anon;

-- grant: refunds.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.refunds TO authenticated;

-- grant: refunds.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.refunds TO service_role;

-- grant: reports.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.reports TO anon;

-- grant: reports.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.reports TO authenticated;

-- grant: reports.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.reports TO service_role;

-- grant: review_reports.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.review_reports TO anon;

-- grant: review_reports.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.review_reports TO authenticated;

-- grant: review_reports.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.review_reports TO service_role;

-- grant: reviews.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.reviews TO anon;

-- grant: reviews.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.reviews TO authenticated;

-- grant: reviews.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.reviews TO service_role;

-- grant: sellers.anon
GRANT SELECT,MAINTAIN ON TABLE public.sellers TO anon;

-- grant: sellers.authenticated
GRANT SELECT,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.sellers TO authenticated;

-- grant: sellers.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.sellers TO service_role;

-- grant: stores.anon
GRANT SELECT,MAINTAIN ON TABLE public.stores TO anon;

-- grant: stores.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.stores TO authenticated;

-- grant: stores.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.stores TO service_role;

-- grant: subscription_plans.anon
GRANT SELECT,MAINTAIN ON TABLE public.subscription_plans TO anon;

-- grant: subscription_plans.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.subscription_plans TO authenticated;

-- grant: subscription_plans.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.subscription_plans TO service_role;

-- grant: subscription_requests.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.subscription_requests TO anon;

-- grant: subscription_requests.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.subscription_requests TO authenticated;

-- grant: subscription_requests.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.subscription_requests TO service_role;

-- grant: subscriptions.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.subscriptions TO anon;

-- grant: subscriptions.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.subscriptions TO authenticated;

-- grant: subscriptions.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.subscriptions TO service_role;

-- grant: support_tickets.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.support_tickets TO anon;

-- grant: support_tickets.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.support_tickets TO authenticated;

-- grant: support_tickets.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.support_tickets TO service_role;

-- grant: system_settings.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.system_settings TO anon;

-- grant: system_settings.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.system_settings TO authenticated;

-- grant: system_settings.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.system_settings TO service_role;

-- grant: ticket_messages.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.ticket_messages TO anon;

-- grant: ticket_messages.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.ticket_messages TO authenticated;

-- grant: ticket_messages.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.ticket_messages TO service_role;

-- grant: user_reports.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.user_reports TO anon;

-- grant: user_reports.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.user_reports TO authenticated;

-- grant: user_reports.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.user_reports TO service_role;

-- grant: wallet_transactions.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.wallet_transactions TO anon;

-- grant: wallet_transactions.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.wallet_transactions TO authenticated;

-- grant: wallet_transactions.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.wallet_transactions TO service_role;

-- grant: wallets.anon
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.wallets TO anon;

-- grant: wallets.authenticated
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.wallets TO authenticated;

-- grant: wallets.service_role
GRANT INSERT,SELECT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER,MAINTAIN ON TABLE public.wallets TO service_role;

-- function_revoke: private.assert_delivery_selection_available
REVOKE ALL ON FUNCTION private.assert_delivery_selection_available(jsonb,jsonb) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: private.audit_trust_support_change
REVOKE ALL ON FUNCTION private.audit_trust_support_change() FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: private.checkout_quote_core
REVOKE ALL ON FUNCTION private.checkout_quote_core(jsonb,jsonb,boolean) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: private.checkout_quote_core_v2
REVOKE ALL ON FUNCTION private.checkout_quote_core_v2(jsonb,jsonb,boolean) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: private.complete_referral_after_order
REVOKE ALL ON FUNCTION private.complete_referral_after_order() FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: private.enforce_checkout_delivery_snapshot
REVOKE ALL ON FUNCTION private.enforce_checkout_delivery_snapshot() FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: private.enqueue_merchant_verification_notification
REVOKE ALL ON FUNCTION private.enqueue_merchant_verification_notification() FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: private.enqueue_new_order_notification
REVOKE ALL ON FUNCTION private.enqueue_new_order_notification() FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: private.enqueue_order_notification
REVOKE ALL ON FUNCTION private.enqueue_order_notification() FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: private.enqueue_stock_alert
REVOKE ALL ON FUNCTION private.enqueue_stock_alert() FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: private.handle_new_user
REVOKE ALL ON FUNCTION private.handle_new_user() FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: private.normalize_checkout_items
REVOKE ALL ON FUNCTION private.normalize_checkout_items(jsonb) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: private.normalize_default_address
REVOKE ALL ON FUNCTION private.normalize_default_address() FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: private.on_order_status_changed
REVOKE ALL ON FUNCTION private.on_order_status_changed() FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: private.protect_merchant_verification_fields
REVOKE ALL ON FUNCTION private.protect_merchant_verification_fields() FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: private.refresh_merchant_metrics
REVOKE ALL ON FUNCTION private.refresh_merchant_metrics(uuid) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: private.refresh_merchant_trust
REVOKE ALL ON FUNCTION private.refresh_merchant_trust(uuid) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: private.refresh_metrics_from_event
REVOKE ALL ON FUNCTION private.refresh_metrics_from_event() FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: private.refresh_metrics_from_order
REVOKE ALL ON FUNCTION private.refresh_metrics_from_order() FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: private.refresh_order_group_status
REVOKE ALL ON FUNCTION private.refresh_order_group_status(uuid) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: private.refresh_product_has_variants
REVOKE ALL ON FUNCTION private.refresh_product_has_variants() FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: private.refresh_trust_from_complaint
REVOKE ALL ON FUNCTION private.refresh_trust_from_complaint() FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: private.refresh_trust_from_merchant_review
REVOKE ALL ON FUNCTION private.refresh_trust_from_merchant_review() FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: private.refresh_trust_from_order
REVOKE ALL ON FUNCTION private.refresh_trust_from_order() FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: private.sync_merchant_delivery_zones
REVOKE ALL ON FUNCTION private.sync_merchant_delivery_zones() FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: private.validate_store_seller_link
REVOKE ALL ON FUNCTION private.validate_store_seller_link() FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: private.write_audit_log
REVOKE ALL ON FUNCTION private.write_audit_log() FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.admin_list_staff
REVOKE ALL ON FUNCTION admin_list_staff() FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.admin_review_featured_request
REVOKE ALL ON FUNCTION admin_review_featured_request(uuid,text,text) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.admin_review_merchant_application
REVOKE ALL ON FUNCTION admin_review_merchant_application(uuid,text,text) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.admin_review_subscription_request
REVOKE ALL ON FUNCTION admin_review_subscription_request(uuid,text,text) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.admin_set_account_active
REVOKE ALL ON FUNCTION admin_set_account_active(uuid,boolean) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.admin_set_category_active
REVOKE ALL ON FUNCTION admin_set_category_active(uuid,boolean) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.admin_set_merchant_status
REVOKE ALL ON FUNCTION admin_set_merchant_status(uuid,text) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.admin_set_product_active
REVOKE ALL ON FUNCTION admin_set_product_active(uuid,boolean,text) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.admin_set_user_role_by_email
REVOKE ALL ON FUNCTION admin_set_user_role_by_email(text,text) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.apply_referral_code
REVOKE ALL ON FUNCTION apply_referral_code(text) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.checkout_create_order_group
REVOKE ALL ON FUNCTION checkout_create_order_group(uuid,text,text,jsonb,text) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.checkout_create_order_group_v2
REVOKE ALL ON FUNCTION checkout_create_order_group_v2(uuid,text,text,jsonb,text,jsonb) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.checkout_create_order_group_v3
REVOKE ALL ON FUNCTION checkout_create_order_group_v3(uuid,text,text,jsonb,text,jsonb,numeric,text) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.checkout_create_order_group_v4
REVOKE ALL ON FUNCTION checkout_create_order_group_v4(uuid,text,text,jsonb,text,jsonb,numeric,text) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.current_user_role
REVOKE ALL ON FUNCTION current_user_role() FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.customer_cancel_order_group
REVOKE ALL ON FUNCTION customer_cancel_order_group(uuid,text) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.delivery_quote
REVOKE ALL ON FUNCTION delivery_quote(uuid,text,text) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.fee_quote
REVOKE ALL ON FUNCTION fee_quote(text,numeric) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.initialize_new_profile
REVOKE ALL ON FUNCTION initialize_new_profile() FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.merchant_dashboard_summary
REVOKE ALL ON FUNCTION merchant_dashboard_summary() FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.merchant_delete_product
REVOKE ALL ON FUNCTION merchant_delete_product(uuid) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.merchant_inventory_health
REVOKE ALL ON FUNCTION merchant_inventory_health() FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.merchant_transition_order_status
REVOKE ALL ON FUNCTION merchant_transition_order_status(uuid,text,text,integer) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.my_merchant_entitlements
REVOKE ALL ON FUNCTION my_merchant_entitlements() FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.my_referral_code
REVOKE ALL ON FUNCTION my_referral_code() FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.place_order
REVOKE ALL ON FUNCTION place_order(text,text,jsonb) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.protect_profile_admin_fields
REVOKE ALL ON FUNCTION protect_profile_admin_fields() FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.protect_profile_authorization_fields
REVOKE ALL ON FUNCTION protect_profile_authorization_fields() FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.quote_cart
REVOKE ALL ON FUNCTION quote_cart(jsonb) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.quote_cart_v2
REVOKE ALL ON FUNCTION quote_cart_v2(jsonb,jsonb) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.quote_cart_v3
REVOKE ALL ON FUNCTION quote_cart_v3(jsonb,jsonb) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.recommended_products
REVOKE ALL ON FUNCTION recommended_products(integer) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.register_my_push_device
REVOKE ALL ON FUNCTION register_my_push_device(text,text,text,text,text,text) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.replace_my_delivery_zones
REVOKE ALL ON FUNCTION replace_my_delivery_zones(jsonb) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.save_my_delivery_configuration
REVOKE ALL ON FUNCTION save_my_delivery_configuration(jsonb,text,boolean) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.search_marketplace_ranked
REVOKE ALL ON FUNCTION search_marketplace_ranked(text,uuid,text,integer,integer) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.search_products
REVOKE ALL ON FUNCTION search_products(text,uuid,numeric,numeric,boolean,text,integer,integer) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.set_app_design_tokens_updated_at
REVOKE ALL ON FUNCTION set_app_design_tokens_updated_at() FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.set_updated_at
REVOKE ALL ON FUNCTION set_updated_at() FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.soft_delete_my_account
REVOKE ALL ON FUNCTION soft_delete_my_account() FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.submit_merchant_application
REVOKE ALL ON FUNCTION submit_merchant_application(text,text,text,text,uuid,text,text,text,text,text,text,numeric,integer,jsonb,text,text,boolean,text) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.submit_merchant_application
REVOKE ALL ON FUNCTION submit_merchant_application(text,text,text,text,uuid,text,text,text,text,text,numeric,integer,text,text,boolean,text) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.sync_my_cart
REVOKE ALL ON FUNCTION sync_my_cart(jsonb) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.sync_my_cart_v2
REVOKE ALL ON FUNCTION sync_my_cart_v2(jsonb) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.transition_order_status
REVOKE ALL ON FUNCTION transition_order_status(uuid,text,text) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.unregister_my_push_device
REVOKE ALL ON FUNCTION unregister_my_push_device(text,text) FROM PUBLIC,anon,authenticated,service_role;

-- function_revoke: public.weekly_marketplace_kpis
REVOKE ALL ON FUNCTION weekly_marketplace_kpis(timestamp with time zone,timestamp with time zone) FROM PUBLIC,anon,authenticated,service_role;

-- function_grant: public.admin_list_staff
GRANT EXECUTE ON FUNCTION admin_list_staff() TO service_role;

-- function_grant: public.admin_list_staff
GRANT EXECUTE ON FUNCTION admin_list_staff() TO authenticated;

-- function_grant: public.admin_review_featured_request
GRANT EXECUTE ON FUNCTION admin_review_featured_request(uuid,text,text) TO authenticated;

-- function_grant: public.admin_review_featured_request
GRANT EXECUTE ON FUNCTION admin_review_featured_request(uuid,text,text) TO service_role;

-- function_grant: public.admin_review_merchant_application
GRANT EXECUTE ON FUNCTION admin_review_merchant_application(uuid,text,text) TO authenticated;

-- function_grant: public.admin_review_merchant_application
GRANT EXECUTE ON FUNCTION admin_review_merchant_application(uuid,text,text) TO service_role;

-- function_grant: public.admin_review_subscription_request
GRANT EXECUTE ON FUNCTION admin_review_subscription_request(uuid,text,text) TO service_role;

-- function_grant: public.admin_review_subscription_request
GRANT EXECUTE ON FUNCTION admin_review_subscription_request(uuid,text,text) TO authenticated;

-- function_grant: public.admin_set_account_active
GRANT EXECUTE ON FUNCTION admin_set_account_active(uuid,boolean) TO service_role;

-- function_grant: public.admin_set_account_active
GRANT EXECUTE ON FUNCTION admin_set_account_active(uuid,boolean) TO authenticated;

-- function_grant: public.admin_set_category_active
GRANT EXECUTE ON FUNCTION admin_set_category_active(uuid,boolean) TO authenticated;

-- function_grant: public.admin_set_category_active
GRANT EXECUTE ON FUNCTION admin_set_category_active(uuid,boolean) TO service_role;

-- function_grant: public.admin_set_merchant_status
GRANT EXECUTE ON FUNCTION admin_set_merchant_status(uuid,text) TO authenticated;

-- function_grant: public.admin_set_merchant_status
GRANT EXECUTE ON FUNCTION admin_set_merchant_status(uuid,text) TO service_role;

-- function_grant: public.admin_set_product_active
GRANT EXECUTE ON FUNCTION admin_set_product_active(uuid,boolean,text) TO authenticated;

-- function_grant: public.admin_set_product_active
GRANT EXECUTE ON FUNCTION admin_set_product_active(uuid,boolean,text) TO service_role;

-- function_grant: public.admin_set_user_role_by_email
GRANT EXECUTE ON FUNCTION admin_set_user_role_by_email(text,text) TO authenticated;

-- function_grant: public.admin_set_user_role_by_email
GRANT EXECUTE ON FUNCTION admin_set_user_role_by_email(text,text) TO service_role;

-- function_grant: public.apply_referral_code
GRANT EXECUTE ON FUNCTION apply_referral_code(text) TO authenticated;

-- function_grant: public.apply_referral_code
GRANT EXECUTE ON FUNCTION apply_referral_code(text) TO service_role;

-- function_grant: public.checkout_create_order_group
GRANT EXECUTE ON FUNCTION checkout_create_order_group(uuid,text,text,jsonb,text) TO service_role;

-- function_grant: public.checkout_create_order_group_v2
GRANT EXECUTE ON FUNCTION checkout_create_order_group_v2(uuid,text,text,jsonb,text,jsonb) TO service_role;

-- function_grant: public.checkout_create_order_group_v3
GRANT EXECUTE ON FUNCTION checkout_create_order_group_v3(uuid,text,text,jsonb,text,jsonb,numeric,text) TO service_role;

-- function_grant: public.checkout_create_order_group_v4
GRANT EXECUTE ON FUNCTION checkout_create_order_group_v4(uuid,text,text,jsonb,text,jsonb,numeric,text) TO authenticated;

-- function_grant: public.checkout_create_order_group_v4
GRANT EXECUTE ON FUNCTION checkout_create_order_group_v4(uuid,text,text,jsonb,text,jsonb,numeric,text) TO service_role;

-- function_grant: public.current_user_role
GRANT EXECUTE ON FUNCTION current_user_role() TO service_role;

-- function_grant: public.current_user_role
GRANT EXECUTE ON FUNCTION current_user_role() TO authenticated;

-- function_grant: public.customer_cancel_order_group
GRANT EXECUTE ON FUNCTION customer_cancel_order_group(uuid,text) TO service_role;

-- function_grant: public.customer_cancel_order_group
GRANT EXECUTE ON FUNCTION customer_cancel_order_group(uuid,text) TO authenticated;

-- function_grant: public.delivery_quote
GRANT EXECUTE ON FUNCTION delivery_quote(uuid,text,text) TO anon;

-- function_grant: public.delivery_quote
GRANT EXECUTE ON FUNCTION delivery_quote(uuid,text,text) TO PUBLIC;

-- function_grant: public.delivery_quote
GRANT EXECUTE ON FUNCTION delivery_quote(uuid,text,text) TO service_role;

-- function_grant: public.delivery_quote
GRANT EXECUTE ON FUNCTION delivery_quote(uuid,text,text) TO authenticated;

-- function_grant: public.fee_quote
GRANT EXECUTE ON FUNCTION fee_quote(text,numeric) TO PUBLIC;

-- function_grant: public.fee_quote
GRANT EXECUTE ON FUNCTION fee_quote(text,numeric) TO anon;

-- function_grant: public.fee_quote
GRANT EXECUTE ON FUNCTION fee_quote(text,numeric) TO authenticated;

-- function_grant: public.fee_quote
GRANT EXECUTE ON FUNCTION fee_quote(text,numeric) TO service_role;

-- function_grant: public.initialize_new_profile
GRANT EXECUTE ON FUNCTION initialize_new_profile() TO service_role;

-- function_grant: public.merchant_dashboard_summary
GRANT EXECUTE ON FUNCTION merchant_dashboard_summary() TO authenticated;

-- function_grant: public.merchant_dashboard_summary
GRANT EXECUTE ON FUNCTION merchant_dashboard_summary() TO service_role;

-- function_grant: public.merchant_delete_product
GRANT EXECUTE ON FUNCTION merchant_delete_product(uuid) TO authenticated;

-- function_grant: public.merchant_delete_product
GRANT EXECUTE ON FUNCTION merchant_delete_product(uuid) TO service_role;

-- function_grant: public.merchant_inventory_health
GRANT EXECUTE ON FUNCTION merchant_inventory_health() TO service_role;

-- function_grant: public.merchant_inventory_health
GRANT EXECUTE ON FUNCTION merchant_inventory_health() TO authenticated;

-- function_grant: public.merchant_transition_order_status
GRANT EXECUTE ON FUNCTION merchant_transition_order_status(uuid,text,text,integer) TO authenticated;

-- function_grant: public.merchant_transition_order_status
GRANT EXECUTE ON FUNCTION merchant_transition_order_status(uuid,text,text,integer) TO service_role;

-- function_grant: public.my_merchant_entitlements
GRANT EXECUTE ON FUNCTION my_merchant_entitlements() TO authenticated;

-- function_grant: public.my_merchant_entitlements
GRANT EXECUTE ON FUNCTION my_merchant_entitlements() TO service_role;

-- function_grant: public.my_referral_code
GRANT EXECUTE ON FUNCTION my_referral_code() TO service_role;

-- function_grant: public.my_referral_code
GRANT EXECUTE ON FUNCTION my_referral_code() TO authenticated;

-- function_grant: public.place_order
GRANT EXECUTE ON FUNCTION place_order(text,text,jsonb) TO service_role;

-- function_grant: public.protect_profile_admin_fields
GRANT EXECUTE ON FUNCTION protect_profile_admin_fields() TO PUBLIC;

-- function_grant: public.protect_profile_admin_fields
GRANT EXECUTE ON FUNCTION protect_profile_admin_fields() TO anon;

-- function_grant: public.protect_profile_admin_fields
GRANT EXECUTE ON FUNCTION protect_profile_admin_fields() TO service_role;

-- function_grant: public.protect_profile_admin_fields
GRANT EXECUTE ON FUNCTION protect_profile_admin_fields() TO authenticated;

-- function_grant: public.protect_profile_authorization_fields
GRANT EXECUTE ON FUNCTION protect_profile_authorization_fields() TO service_role;

-- function_grant: public.quote_cart
GRANT EXECUTE ON FUNCTION quote_cart(jsonb) TO service_role;

-- function_grant: public.quote_cart_v2
GRANT EXECUTE ON FUNCTION quote_cart_v2(jsonb,jsonb) TO service_role;

-- function_grant: public.quote_cart_v3
GRANT EXECUTE ON FUNCTION quote_cart_v3(jsonb,jsonb) TO authenticated;

-- function_grant: public.quote_cart_v3
GRANT EXECUTE ON FUNCTION quote_cart_v3(jsonb,jsonb) TO service_role;

-- function_grant: public.recommended_products
GRANT EXECUTE ON FUNCTION recommended_products(integer) TO service_role;

-- function_grant: public.recommended_products
GRANT EXECUTE ON FUNCTION recommended_products(integer) TO authenticated;

-- function_grant: public.register_my_push_device
GRANT EXECUTE ON FUNCTION register_my_push_device(text,text,text,text,text,text) TO authenticated;

-- function_grant: public.register_my_push_device
GRANT EXECUTE ON FUNCTION register_my_push_device(text,text,text,text,text,text) TO service_role;

-- function_grant: public.replace_my_delivery_zones
GRANT EXECUTE ON FUNCTION replace_my_delivery_zones(jsonb) TO authenticated;

-- function_grant: public.replace_my_delivery_zones
GRANT EXECUTE ON FUNCTION replace_my_delivery_zones(jsonb) TO service_role;

-- function_grant: public.save_my_delivery_configuration
GRANT EXECUTE ON FUNCTION save_my_delivery_configuration(jsonb,text,boolean) TO service_role;

-- function_grant: public.save_my_delivery_configuration
GRANT EXECUTE ON FUNCTION save_my_delivery_configuration(jsonb,text,boolean) TO authenticated;

-- function_grant: public.search_marketplace_ranked
GRANT EXECUTE ON FUNCTION search_marketplace_ranked(text,uuid,text,integer,integer) TO anon;

-- function_grant: public.search_marketplace_ranked
GRANT EXECUTE ON FUNCTION search_marketplace_ranked(text,uuid,text,integer,integer) TO PUBLIC;

-- function_grant: public.search_marketplace_ranked
GRANT EXECUTE ON FUNCTION search_marketplace_ranked(text,uuid,text,integer,integer) TO authenticated;

-- function_grant: public.search_marketplace_ranked
GRANT EXECUTE ON FUNCTION search_marketplace_ranked(text,uuid,text,integer,integer) TO service_role;

-- function_grant: public.search_products
GRANT EXECUTE ON FUNCTION search_products(text,uuid,numeric,numeric,boolean,text,integer,integer) TO authenticated;

-- function_grant: public.search_products
GRANT EXECUTE ON FUNCTION search_products(text,uuid,numeric,numeric,boolean,text,integer,integer) TO service_role;

-- function_grant: public.search_products
GRANT EXECUTE ON FUNCTION search_products(text,uuid,numeric,numeric,boolean,text,integer,integer) TO anon;

-- function_grant: public.set_app_design_tokens_updated_at
GRANT EXECUTE ON FUNCTION set_app_design_tokens_updated_at() TO service_role;

-- function_grant: public.set_updated_at
GRANT EXECUTE ON FUNCTION set_updated_at() TO service_role;

-- function_grant: public.soft_delete_my_account
GRANT EXECUTE ON FUNCTION soft_delete_my_account() TO service_role;

-- function_grant: public.soft_delete_my_account
GRANT EXECUTE ON FUNCTION soft_delete_my_account() TO authenticated;

-- function_grant: public.submit_merchant_application
GRANT EXECUTE ON FUNCTION submit_merchant_application(text,text,text,text,uuid,text,text,text,text,text,numeric,integer,text,text,boolean,text) TO service_role;

-- function_grant: public.submit_merchant_application
GRANT EXECUTE ON FUNCTION submit_merchant_application(text,text,text,text,uuid,text,text,text,text,text,text,numeric,integer,jsonb,text,text,boolean,text) TO authenticated;

-- function_grant: public.submit_merchant_application
GRANT EXECUTE ON FUNCTION submit_merchant_application(text,text,text,text,uuid,text,text,text,text,text,text,numeric,integer,jsonb,text,text,boolean,text) TO service_role;

-- function_grant: public.submit_merchant_application
GRANT EXECUTE ON FUNCTION submit_merchant_application(text,text,text,text,uuid,text,text,text,text,text,numeric,integer,text,text,boolean,text) TO authenticated;

-- function_grant: public.sync_my_cart
GRANT EXECUTE ON FUNCTION sync_my_cart(jsonb) TO service_role;

-- function_grant: public.sync_my_cart_v2
GRANT EXECUTE ON FUNCTION sync_my_cart_v2(jsonb) TO authenticated;

-- function_grant: public.sync_my_cart_v2
GRANT EXECUTE ON FUNCTION sync_my_cart_v2(jsonb) TO service_role;

-- function_grant: public.transition_order_status
GRANT EXECUTE ON FUNCTION transition_order_status(uuid,text,text) TO service_role;

-- function_grant: public.transition_order_status
GRANT EXECUTE ON FUNCTION transition_order_status(uuid,text,text) TO authenticated;

-- function_grant: public.unregister_my_push_device
GRANT EXECUTE ON FUNCTION unregister_my_push_device(text,text) TO service_role;

-- function_grant: public.unregister_my_push_device
GRANT EXECUTE ON FUNCTION unregister_my_push_device(text,text) TO authenticated;

-- function_grant: public.weekly_marketplace_kpis
GRANT EXECUTE ON FUNCTION weekly_marketplace_kpis(timestamp with time zone,timestamp with time zone) TO service_role;

-- function_grant: public.weekly_marketplace_kpis
GRANT EXECUTE ON FUNCTION weekly_marketplace_kpis(timestamp with time zone,timestamp with time zone) TO authenticated;
SET check_function_bodies = true;
