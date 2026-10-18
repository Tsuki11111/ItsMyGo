package com.tsuk1.itsmygo.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    /** @return 可直接放进商品 main_image 的公网 URL */
    String uploadProductImage(MultipartFile file);

    /** @return 可直接放进 users.avatar 的公网 URL。单独一个方法是为了限制大小与目录 */
    String uploadAvatar(MultipartFile file);
}
