-- 一次性迁移：把历史图片的绝对 URL 改为同源相对路径。
--
-- 背景：图片地址曾经以 http://<宿主机>:9000/its-my-go-product-images/... 的完整形式
-- 写进数据库。宿主机是 DHCP，IP 一变，订单快照、头像、SKU 图就全部指向不存在的主机。
-- 现在前后端同源反代 /its-my-go-product-images/ 到 MinIO，数据库只存相对路径。
--
-- 按需扩展 host 列表；新部署没有旧数据，执行本脚本无副作用。

UPDATE users
SET avatar = REPLACE(REPLACE(avatar,
        'http://10.76.196.106:9000/its-my-go-product-images', '/its-my-go-product-images'),
        'http://10.184.171.106:9000/its-my-go-product-images', '/its-my-go-product-images')
WHERE avatar LIKE 'http://%:9000/its-my-go-product-images/%';

UPDATE merchants
SET logo = REPLACE(REPLACE(logo,
        'http://10.76.196.106:9000/its-my-go-product-images', '/its-my-go-product-images'),
        'http://10.184.171.106:9000/its-my-go-product-images', '/its-my-go-product-images')
WHERE logo LIKE 'http://%:9000/its-my-go-product-images/%';

UPDATE products
SET main_image = REPLACE(REPLACE(main_image,
        'http://10.76.196.106:9000/its-my-go-product-images', '/its-my-go-product-images'),
        'http://10.184.171.106:9000/its-my-go-product-images', '/its-my-go-product-images'),
    thumb_image = REPLACE(REPLACE(thumb_image,
        'http://10.76.196.106:9000/its-my-go-product-images', '/its-my-go-product-images'),
        'http://10.184.171.106:9000/its-my-go-product-images', '/its-my-go-product-images'),
    detail = REPLACE(REPLACE(detail,
        'http://10.76.196.106:9000/its-my-go-product-images', '/its-my-go-product-images'),
        'http://10.184.171.106:9000/its-my-go-product-images', '/its-my-go-product-images')
WHERE main_image LIKE 'http://%:9000/its-my-go-product-images/%'
   OR thumb_image LIKE 'http://%:9000/its-my-go-product-images/%'
   OR detail LIKE 'http://%:9000/its-my-go-product-images/%';

UPDATE product_skus
SET image = REPLACE(REPLACE(image,
        'http://10.76.196.106:9000/its-my-go-product-images', '/its-my-go-product-images'),
        'http://10.184.171.106:9000/its-my-go-product-images', '/its-my-go-product-images'),
    thumb_image = REPLACE(REPLACE(thumb_image,
        'http://10.76.196.106:9000/its-my-go-product-images', '/its-my-go-product-images'),
        'http://10.184.171.106:9000/its-my-go-product-images', '/its-my-go-product-images')
WHERE image LIKE 'http://%:9000/its-my-go-product-images/%'
   OR thumb_image LIKE 'http://%:9000/its-my-go-product-images/%';

UPDATE order_items
SET product_image = REPLACE(REPLACE(product_image,
        'http://10.76.196.106:9000/its-my-go-product-images', '/its-my-go-product-images'),
        'http://10.184.171.106:9000/its-my-go-product-images', '/its-my-go-product-images')
WHERE product_image LIKE 'http://%:9000/its-my-go-product-images/%';

UPDATE messages
SET card_data = REPLACE(REPLACE(card_data,
        'http://10.76.196.106:9000/its-my-go-product-images', '/its-my-go-product-images'),
        'http://10.184.171.106:9000/its-my-go-product-images', '/its-my-go-product-images')
WHERE card_data LIKE 'http://%:9000/its-my-go-product-images/%';
