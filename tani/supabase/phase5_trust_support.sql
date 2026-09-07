-- Tani Phase 5 — Trust & Support
-- Reviews are tied to delivered orders. Support and complaints are private to participants/staff.

create schema if not exists private;
revoke all on schema private from public;

alter table public.reviews
  add column if not exists order_id uuid references public.orders(id) on delete set null,
  add column if not exists status text not null default 'published',
  add column if not exists moderation_reason text,
  add column if not exists updated_at timestamptz not null default now();

do $$ begin
  alter table public.reviews add constraint reviews_status_check check(status in ('published','hidden','removed'));
exception when duplicate_object then null; end $$;
create index if not exists reviews_order_idx on public.reviews(order_id);
create index if not exists reviews_status_product_idx on public.reviews(status,product_id,created_at desc);

create table if not exists public.merchant_reviews (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null references public.orders(id) on delete cascade,
  seller_id uuid not null references public.sellers(id) on delete cascade,
  customer_id uuid not null references auth.users(id) on delete cascade,
  rating integer not null check(rating between 1 and 5),
  comment text not null default '',
  status text not null default 'published' check(status in ('published','hidden','removed')),
  moderation_reason text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(order_id,customer_id)
);
create index if not exists merchant_reviews_seller_idx on public.merchant_reviews(seller_id,status,created_at desc);
create index if not exists merchant_reviews_customer_idx on public.merchant_reviews(customer_id);
alter table public.merchant_reviews enable row level security;

create table if not exists public.order_reviews (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null references public.orders(id) on delete cascade,
  customer_id uuid not null references auth.users(id) on delete cascade,
  rating integer not null check(rating between 1 and 5),
  comment text not null default '',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(order_id,customer_id)
);
create index if not exists order_reviews_order_idx on public.order_reviews(order_id);
create index if not exists order_reviews_customer_idx on public.order_reviews(customer_id);
alter table public.order_reviews enable row level security;

create table if not exists public.review_reports (
  id uuid primary key default gen_random_uuid(),
  review_id uuid not null references public.reviews(id) on delete cascade,
  reporter_id uuid not null references auth.users(id) on delete cascade,
  reason text not null,
  status text not null default 'pending' check(status in ('pending','reviewed','dismissed','actioned')),
  created_at timestamptz not null default now(),
  unique(review_id,reporter_id)
);
create index if not exists review_reports_reporter_idx on public.review_reports(reporter_id);
alter table public.review_reports enable row level security;

create table if not exists public.support_tickets (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  subject text not null,
  description text not null,
  status text not null default 'open' check(status in ('open','in_progress','closed','resolved')),
  priority text not null default 'normal' check(priority in ('low','normal','high','urgent')),
  assigned_to uuid references auth.users(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index if not exists support_tickets_user_idx on public.support_tickets(user_id,created_at desc);
alter table public.support_tickets enable row level security;

create table if not exists public.ticket_messages (
  id uuid primary key default gen_random_uuid(),
  ticket_id uuid not null references public.support_tickets(id) on delete cascade,
  sender_id uuid not null references auth.users(id) on delete cascade,
  body text not null check(char_length(body) between 1 and 2000),
  created_at timestamptz not null default now()
);
create index if not exists ticket_messages_ticket_idx on public.ticket_messages(ticket_id,created_at asc);
create index if not exists ticket_messages_sender_idx on public.ticket_messages(sender_id);
alter table public.ticket_messages enable row level security;

alter table public.complaints
  add column if not exists category text not null default 'order',
  add column if not exists resolution text,
  add column if not exists resolved_by uuid references auth.users(id) on delete set null,
  add column if not exists resolved_at timestamptz;
do $$ begin
  alter table public.complaints add constraint complaints_category_check
    check(category in ('order','availability','merchant_response','delivery_delay','product','fees','cancellation','dispute','other'));
exception when duplicate_object then null; end $$;
create index if not exists complaints_user_status_idx on public.complaints(user_id,status,created_at desc);
create index if not exists complaints_seller_status_idx on public.complaints(seller_id,status,created_at desc);
create index if not exists complaints_order_idx on public.complaints(order_id);

-- Verified-purchase product reviews.
drop policy if exists tani_reviews_own_insert on public.reviews;
drop policy if exists tani_reviews_transaction_insert on public.reviews;
create policy tani_reviews_transaction_insert on public.reviews for insert to authenticated
with check(
  customer_id=(select auth.uid()) and order_id is not null and exists(
    select 1 from public.orders o
    join public.order_items oi on oi.order_id=o.id
    join public.products p on p.id=reviews.product_id
    join public.sellers s on s.id=p.seller_id
    where o.id=reviews.order_id and o.customer_id=(select auth.uid()) and o.status='delivered'
      and oi.product_id=reviews.product_id and s.user_id<>(select auth.uid())
  )
);
drop policy if exists tani_reviews_public_read on public.reviews;
create policy tani_reviews_public_read on public.reviews for select to anon,authenticated using(status='published');
drop policy if exists tani_reviews_own_update on public.reviews;
create policy tani_reviews_own_update on public.reviews for update to authenticated
using(customer_id=(select auth.uid()) and status='published')
with check(customer_id=(select auth.uid()) and status='published');
drop policy if exists tani_reviews_own_delete on public.reviews;
create policy tani_reviews_own_delete on public.reviews for delete to authenticated using(customer_id=(select auth.uid()));
drop policy if exists tani_reviews_admin_read on public.reviews;
create policy tani_reviews_admin_read on public.reviews for select to authenticated using(public.current_user_role()=any(array['admin','support']));
drop policy if exists tani_reviews_admin_moderate on public.reviews;
create policy tani_reviews_admin_moderate on public.reviews for update to authenticated using(public.current_user_role()='admin') with check(public.current_user_role()='admin');

-- Merchant/order ratings.
drop policy if exists tani_merchant_reviews_public_read on public.merchant_reviews;
create policy tani_merchant_reviews_public_read on public.merchant_reviews for select to anon,authenticated using(status='published');
drop policy if exists tani_merchant_reviews_transaction_insert on public.merchant_reviews;
create policy tani_merchant_reviews_transaction_insert on public.merchant_reviews for insert to authenticated
with check(customer_id=(select auth.uid()) and exists(
  select 1 from public.orders o join public.sellers s on s.id=o.seller_id
  where o.id=merchant_reviews.order_id and o.seller_id=merchant_reviews.seller_id
    and o.customer_id=(select auth.uid()) and o.status='delivered' and s.user_id<>(select auth.uid())
));
drop policy if exists tani_merchant_reviews_own_update on public.merchant_reviews;
create policy tani_merchant_reviews_own_update on public.merchant_reviews for update to authenticated
using(customer_id=(select auth.uid()) and status='published') with check(customer_id=(select auth.uid()) and status='published');
drop policy if exists tani_merchant_reviews_own_delete on public.merchant_reviews;
create policy tani_merchant_reviews_own_delete on public.merchant_reviews for delete to authenticated using(customer_id=(select auth.uid()));
drop policy if exists tani_merchant_reviews_admin_read on public.merchant_reviews;
create policy tani_merchant_reviews_admin_read on public.merchant_reviews for select to authenticated using(public.current_user_role()=any(array['admin','support']));
drop policy if exists tani_merchant_reviews_admin_moderate on public.merchant_reviews;
create policy tani_merchant_reviews_admin_moderate on public.merchant_reviews for update to authenticated using(public.current_user_role()='admin') with check(public.current_user_role()='admin');

drop policy if exists tani_order_reviews_owner_read on public.order_reviews;
create policy tani_order_reviews_owner_read on public.order_reviews for select to authenticated using(customer_id=(select auth.uid()) or public.current_user_role()=any(array['admin','support']));
drop policy if exists tani_order_reviews_transaction_insert on public.order_reviews;
create policy tani_order_reviews_transaction_insert on public.order_reviews for insert to authenticated
with check(customer_id=(select auth.uid()) and exists(select 1 from public.orders o where o.id=order_reviews.order_id and o.customer_id=(select auth.uid()) and o.status='delivered'));

-- Reports/support/complaints.
drop policy if exists tani_review_reports_owner_insert on public.review_reports;
create policy tani_review_reports_owner_insert on public.review_reports for insert to authenticated with check(reporter_id=(select auth.uid()));
drop policy if exists tani_review_reports_owner_read on public.review_reports;
create policy tani_review_reports_owner_read on public.review_reports for select to authenticated using(reporter_id=(select auth.uid()) or public.current_user_role()=any(array['admin','support']));
drop policy if exists tani_review_reports_staff_update on public.review_reports;
create policy tani_review_reports_staff_update on public.review_reports for update to authenticated using(public.current_user_role()=any(array['admin','support'])) with check(public.current_user_role()=any(array['admin','support']));

drop policy if exists tani_ticket_owner_insert on public.support_tickets;
create policy tani_ticket_owner_insert on public.support_tickets for insert to authenticated with check(user_id=(select auth.uid()));
drop policy if exists tani_ticket_owner_read on public.support_tickets;
create policy tani_ticket_owner_read on public.support_tickets for select to authenticated using(user_id=(select auth.uid()) or public.current_user_role()=any(array['admin','support']));
drop policy if exists tani_ticket_owner_update on public.support_tickets;
create policy tani_ticket_owner_update on public.support_tickets for update to authenticated using(user_id=(select auth.uid())) with check(user_id=(select auth.uid()) and assigned_to is null and status in ('open','closed'));
drop policy if exists tani_ticket_staff_update on public.support_tickets;
create policy tani_ticket_staff_update on public.support_tickets for update to authenticated using(public.current_user_role()=any(array['admin','support'])) with check(public.current_user_role()=any(array['admin','support']));

drop policy if exists tani_ticket_message_insert on public.ticket_messages;
create policy tani_ticket_message_insert on public.ticket_messages for insert to authenticated with check(
  sender_id=(select auth.uid()) and exists(select 1 from public.support_tickets t where t.id=ticket_messages.ticket_id and (t.user_id=(select auth.uid()) or public.current_user_role()=any(array['admin','support'])))
);
drop policy if exists tani_ticket_message_read on public.ticket_messages;
create policy tani_ticket_message_read on public.ticket_messages for select to authenticated using(
  exists(select 1 from public.support_tickets t where t.id=ticket_messages.ticket_id and (t.user_id=(select auth.uid()) or public.current_user_role()=any(array['admin','support'])))
);

drop policy if exists tani_complaints_owner_insert on public.complaints;
create policy tani_complaints_owner_insert on public.complaints for insert to authenticated with check(
  user_id=(select auth.uid()) and (order_id is null or exists(select 1 from public.orders o where o.id=complaints.order_id and o.customer_id=(select auth.uid())))
);
drop policy if exists tani_complaints_owner_read on public.complaints;
create policy tani_complaints_owner_read on public.complaints for select to authenticated using(
  user_id=(select auth.uid()) or public.current_user_role()=any(array['admin','support'])
  or exists(select 1 from public.sellers s where s.id=complaints.seller_id and s.user_id=(select auth.uid()))
);
drop policy if exists tani_complaints_admin_update on public.complaints;
drop policy if exists tani_complaints_admin_support_update on public.complaints;
create policy tani_complaints_admin_support_update on public.complaints for update to authenticated
using(public.current_user_role()=any(array['admin','support'])) with check(public.current_user_role()=any(array['admin','support']));

create table if not exists public.merchant_trust_scores (
  seller_id uuid primary key references public.sellers(id) on delete cascade,
  trust_level text not null default 'verified' check(trust_level in ('verified','trusted','high_performing','restricted')),
  trust_score numeric(5,2) not null default 50 check(trust_score between 0 and 100),
  completed_orders integer not null default 0,
  cancelled_orders integer not null default 0,
  review_count integer not null default 0,
  average_rating numeric(3,2),
  open_complaints integer not null default 0,
  completion_rate numeric(5,2) not null default 0,
  updated_at timestamptz not null default now()
);
alter table public.merchant_trust_scores enable row level security;
drop policy if exists tani_merchant_trust_public_read on public.merchant_trust_scores;
create policy tani_merchant_trust_public_read on public.merchant_trust_scores for select to anon,authenticated using(true);

create or replace function private.refresh_merchant_trust(p_seller_id uuid)
returns void language plpgsql security definer set search_path=public,pg_temp as $$
declare v_completed int:=0;v_cancelled int:=0;v_reviews int:=0;v_rating numeric:=null;v_open int:=0;v_completion numeric:=0;v_score numeric:=50;v_level text:='verified';
begin
  if p_seller_id is null then return; end if;
  select count(*) filter(where status='delivered'),count(*) filter(where status in ('cancelled','rejected')) into v_completed,v_cancelled from public.orders where seller_id=p_seller_id;
  select count(*),avg(rating)::numeric(3,2) into v_reviews,v_rating from public.merchant_reviews where seller_id=p_seller_id and status='published';
  select count(*) into v_open from public.complaints where seller_id=p_seller_id and status in ('open','in_progress');
  if v_completed+v_cancelled>0 then v_completion:=round(v_completed::numeric/(v_completed+v_cancelled)*100,2); end if;
  v_score:=50+least(v_completion*.25,25)+case when v_rating is null then 0 else greatest(least((v_rating-3)*8,16),-16) end+least(v_completed,20)*.5-least(v_open,10)*4;
  v_score:=greatest(0,least(100,round(v_score,2)));
  if v_open>=5 or v_score<35 then v_level:='restricted'; elsif v_completed>=30 and coalesce(v_rating,0)>=4.5 and v_score>=85 then v_level:='high_performing'; elsif v_completed>=8 and coalesce(v_rating,0)>=4 and v_score>=70 then v_level:='trusted'; end if;
  insert into public.merchant_trust_scores(seller_id,trust_level,trust_score,completed_orders,cancelled_orders,review_count,average_rating,open_complaints,completion_rate,updated_at)
  values(p_seller_id,v_level,v_score,v_completed,v_cancelled,v_reviews,v_rating,v_open,v_completion,now())
  on conflict(seller_id) do update set trust_level=excluded.trust_level,trust_score=excluded.trust_score,completed_orders=excluded.completed_orders,cancelled_orders=excluded.cancelled_orders,review_count=excluded.review_count,average_rating=excluded.average_rating,open_complaints=excluded.open_complaints,completion_rate=excluded.completion_rate,updated_at=now();
end $$;
revoke all on function private.refresh_merchant_trust(uuid) from public,anon,authenticated;

create or replace function private.refresh_trust_from_order() returns trigger language plpgsql security definer set search_path=public,pg_temp as $$ begin perform private.refresh_merchant_trust(coalesce(new.seller_id,old.seller_id));return coalesce(new,old);end $$;
create or replace function private.refresh_trust_from_merchant_review() returns trigger language plpgsql security definer set search_path=public,pg_temp as $$ begin perform private.refresh_merchant_trust(coalesce(new.seller_id,old.seller_id));return coalesce(new,old);end $$;
create or replace function private.refresh_trust_from_complaint() returns trigger language plpgsql security definer set search_path=public,pg_temp as $$ begin perform private.refresh_merchant_trust(coalesce(new.seller_id,old.seller_id));return coalesce(new,old);end $$;
revoke all on function private.refresh_trust_from_order() from public,anon,authenticated;
revoke all on function private.refresh_trust_from_merchant_review() from public,anon,authenticated;
revoke all on function private.refresh_trust_from_complaint() from public,anon,authenticated;
drop trigger if exists tani_refresh_trust_order on public.orders;create trigger tani_refresh_trust_order after insert or delete or update of status on public.orders for each row execute function private.refresh_trust_from_order();
drop trigger if exists tani_refresh_trust_review on public.merchant_reviews;create trigger tani_refresh_trust_review after insert or update or delete on public.merchant_reviews for each row execute function private.refresh_trust_from_merchant_review();
drop trigger if exists tani_refresh_trust_complaint on public.complaints;create trigger tani_refresh_trust_complaint after insert or delete or update of status on public.complaints for each row execute function private.refresh_trust_from_complaint();

grant select,insert,update,delete on public.merchant_reviews to authenticated;
grant select on public.merchant_reviews to anon;
grant select,insert on public.order_reviews to authenticated;
grant select on public.merchant_trust_scores to anon,authenticated;
grant select,insert,update on public.support_tickets to authenticated;
grant select,insert on public.ticket_messages to authenticated;
grant select,insert,update on public.review_reports to authenticated;
grant select,insert,update on public.complaints to authenticated;
