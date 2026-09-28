-- Rebind the delivery-zone sync trigger to the phase-5 implementation.
drop trigger if exists tani_sync_merchant_delivery_zones on public.merchant_profiles;
create trigger tani_sync_merchant_delivery_zones
after insert or update of delivery_zones,verification_status,seller_id on public.merchant_profiles
for each row execute function private.sync_merchant_delivery_zones();
