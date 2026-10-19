package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.ratelimit.RateLimit;
import com.tsuk1.itsmygo.service.StorageService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 商家后台：上传商品图。与平台管理端共用同一个 StorageService。 */
@RestController
@RequestMapping("/api/merchant/files")
public class MerchantFileController {

    private final StorageService storageService;

    public MerchantFileController(StorageService storageService) {
        this.storageService = storageService;
    }

    @RateLimit(scene = "upload", limit = 20, windowSeconds = 60, byUser = true)
    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(storageService.uploadProductImage(file));
    }
}
