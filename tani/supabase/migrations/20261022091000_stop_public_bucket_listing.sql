-- Public buckets serve files through /object/public/ without any SELECT policy. A SELECT policy
-- open to everyone also lets anyone list the bucket, exposing every user id used as a folder.
-- Owners keep SELECT on their own folder because upsert (x-upsert) needs it.
DROP POLICY IF EXISTS tani_avatars_public_read ON storage.objects;
DROP POLICY IF EXISTS tani_product_images_storage_public_read ON storage.objects;
DROP POLICY IF EXISTS tani_store_assets_public_read ON storage.objects;

DROP POLICY IF EXISTS tani_public_media_owner_read ON storage.objects;
CREATE POLICY tani_public_media_owner_read ON storage.objects FOR SELECT TO authenticated
USING (
 bucket_id IN ('avatars','product-images','store-assets')
 AND name LIKE (SELECT auth.uid())::text || '/%'
);
