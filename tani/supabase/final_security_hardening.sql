-- Final security/performance hardening. Safe after phases 0–8.
create schema if not exists extensions;
do $$ begin
  if exists(select 1 from pg_extension e join pg_namespace n on n.oid=e.extnamespace where e.extname='pg_trgm' and n.nspname='public') then
    alter extension pg_trgm set schema extensions;
  end if;
end $$;

do $$ begin
  if to_regprocedure('public.set_updated_at()') is not null then
    execute 'revoke execute on function public.set_updated_at() from anon,authenticated';
  end if;
  if to_regprocedure('public.set_app_design_tokens_updated_at()') is not null then
    execute 'revoke execute on function public.set_app_design_tokens_updated_at() from anon,authenticated';
  end if;
end $$;

create or replace function public.soft_delete_my_account()
returns boolean language plpgsql security definer set search_path=public,pg_temp as $$
declare v_uid uuid:=(select auth.uid());
begin
  if v_uid is null then raise exception 'authentication required'; end if;
  perform set_config('tani.merchant_workflow','submit',true);

  update public.products p set is_active=false,updated_at=now()
  where exists(select 1 from public.sellers s where s.id=p.seller_id and s.user_id=v_uid);

  update public.stores st set is_active=false,is_open=false,updated_at=now()
  where exists(select 1 from public.sellers s where s.id=st.seller_id and s.user_id=v_uid)
     or exists(select 1 from public.merchant_profiles mp where mp.id=st.merchant_id and mp.user_id=v_uid);

  update public.merchant_profiles
  set verification_status='suspended',suspended_at=coalesce(suspended_at,now()),trust_badge=false,
      review_note='Account deleted by owner',updated_at=now()
  where user_id=v_uid;

  update public.sellers set verification_status='suspended' where user_id=v_uid;

  update public.profiles
  set is_active=false,deleted_at=coalesce(deleted_at,now()),name='محذوف',phone='deleted-'||id::text,
      avatar_url=null,updated_at=now()
  where id=v_uid;

  insert into public.audit_logs(actor_id,action,entity_type,entity_id,source)
  values(v_uid,'soft_delete','profile',v_uid,'account');
  return found;
end $$;
revoke all on function public.soft_delete_my_account() from public,anon;
grant execute on function public.soft_delete_my_account() to authenticated;

-- High-value FK indexes used in RLS / operational reads.
create index if not exists complaints_order_idx on public.complaints(order_id);
create index if not exists featured_placements_product_idx on public.featured_placements(product_id);
create index if not exists featured_placements_seller_idx on public.featured_placements(seller_id);
create index if not exists featured_placements_request_idx on public.featured_placements(request_id);
create index if not exists featured_requests_product_idx on public.featured_requests(product_id);
create index if not exists merchant_ad_campaigns_product_idx on public.merchant_ad_campaigns(product_id);
create index if not exists merchant_reviews_customer_idx on public.merchant_reviews(customer_id);
create index if not exists order_groups_address_idx on public.order_groups(address_id);
create index if not exists order_reviews_customer_idx on public.order_reviews(customer_id);
create index if not exists review_reports_reporter_idx on public.review_reports(reporter_id);
create index if not exists subscription_requests_user_idx on public.subscription_requests(user_id);
create index if not exists subscription_requests_plan_idx on public.subscription_requests(plan_id);
create index if not exists subscriptions_plan_idx on public.subscriptions(plan_id);
create index if not exists support_tickets_user_idx on public.support_tickets(user_id);
create index if not exists ticket_messages_ticket_idx on public.ticket_messages(ticket_id);
create index if not exists ticket_messages_sender_idx on public.ticket_messages(sender_id);
