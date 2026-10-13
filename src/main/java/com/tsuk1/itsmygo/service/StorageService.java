package com.tsuk1.itsmygo.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    /** @return 可直接放进商品 main_image 的公网 URL */
    String uploadProductImage(MultipartFile file);
}
