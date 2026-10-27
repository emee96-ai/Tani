-- product_images_public_read was added from the dashboard and is not in any migration. It is a SELECT policy open to
-- everyone on product-images, so anyone can still list that bucket after 20261022091000. Files stay readable through
-- /object/public/ without it.
DROP POLICY IF EXISTS product_images_public_read ON storage.objects;
