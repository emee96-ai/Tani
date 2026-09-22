-- Tani notification UX v2: clearer merchant order notifications.
create or replace function private.enqueue_new_order_notification()
returns trigger
language plpgsql
security definer
set search_path=public,pg_temp
as $$
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
$$;
revoke all on function private.enqueue_new_order_notification() from public,anon,authenticated;

drop trigger if exists tani_new_order_notification on public.orders;
create trigger tani_new_order_notification
after insert on public.orders
for each row execute function private.enqueue_new_order_notification();

create or replace function private.enqueue_order_notification()
returns trigger
language plpgsql
security definer
set search_path=public,pg_temp
as $$
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
$$;
revoke all on function private.enqueue_order_notification() from public,anon,authenticated;

drop trigger if exists tani_order_status_notifications on public.order_status_history;
create trigger tani_order_status_notifications
after insert on public.order_status_history
for each row execute function private.enqueue_order_notification();
