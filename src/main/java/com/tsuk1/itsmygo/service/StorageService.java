package com.tsuk1.itsmygo.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    /** @return 可直接放进商品 main_image 的公网 URL */
    String uploadProductImage(MultipartFile file);

    /** @return 可直接放进 users.avatar 的公网 URL。单独一个方法是为了限制大小与目录 */
    String uploadAvatar(MultipartFile file);

    /** @return 可直接放进 merchants.logo 的公网 URL。入驻申请人尚未有商家角色，需独立入口 */
    String uploadMerchantLogo(MultipartFile file);

    /** @return 聊天图片的公网 URL，固定放 chat/ 前缀 */
    String uploadChatImage(MultipartFile file);

    /** 是否为本系统 chat/ 前缀的图片 URL —— 拒绝聊天消息里夹带外链 */
    boolean isChatImageUrl(String url);
}
