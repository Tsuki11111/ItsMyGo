-- 商品/SKU 缩略图：列表页用 400×400 缩略图，详情页仍用原图
ALTER TABLE products ADD COLUMN thumb_image VARCHAR(512) NOT NULL DEFAULT '' AFTER main_image;
ALTER TABLE product_skus ADD COLUMN thumb_image VARCHAR(512) NOT NULL DEFAULT '' AFTER image;
