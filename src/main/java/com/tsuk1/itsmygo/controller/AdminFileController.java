package com.tsuk1.itsmygo.controller;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.service.StorageService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 管理端：上传商品图，需 ADMIN */
@RestController
@RequestMapping("/api/admin/files")
public class AdminFileController {

    private final StorageService storageService;

    public AdminFileController(StorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(storageService.uploadProductImage(file));
    }
}
