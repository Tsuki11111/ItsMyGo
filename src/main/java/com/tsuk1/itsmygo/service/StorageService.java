package com.tsuk1.itsmygo.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    /** @return 可直接放进商品 main_image 的公网 URL；上传时会顺带生成列表缩略图 */
    String uploadProductImage(MultipartFile file);

    /**
     * 为商品/SKU 图片生成 400×400 列表缩略图（中心裁切）。
     * 只处理本系统 MinIO 里的 products/ 对象；空图、外链、生成失败都返回空串，
     * 由调用方回落到原图 URL —— 缩略图失败不能影响商品保存。
     */
    String ensureProductThumbnail(String imageUrl);

    /** @return 可直接放进 users.avatar 的公网 URL。单独一个方法是为了限制大小与目录 */
    String uploadAvatar(MultipartFile file);

    /** @return 可直接放进 merchants.logo 的公网 URL。入驻申请人尚未有商家角色，需独立入口 */
    String uploadMerchantLogo(MultipartFile file);

    /** @return 聊天图片的公网 URL，固定放 chat/ 前缀 */
    String uploadChatImage(MultipartFile file);

    /** 是否为本系统 chat/ 前缀的图片 URL —— 拒绝聊天消息里夹带外链 */
    boolean isChatImageUrl(String url);

    /** 把 bucket 内的对象 key 拼成浏览器可访问的公网 URL（品牌 Logo 等静态素材用） */
    String publicUrl(String objectKey);
}
