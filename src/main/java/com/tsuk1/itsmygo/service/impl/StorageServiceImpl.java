package com.tsuk1.itsmygo.service.impl;

import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.service.StorageService;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class StorageServiceImpl implements StorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "gif", "image/gif",
            "webp", "image/webp");

    private final MinioClient minioClient;
    private final String bucket;
    private final String publicPrefix;

    private volatile boolean bucketReady = false;

    public StorageServiceImpl(MinioClient minioClient,
                              @Value("${itsmygo.minio.bucket}") String bucket,
                              @Value("${itsmygo.minio.endpoint}") String endpoint) {
        this.minioClient = minioClient;
        this.bucket = bucket;
        this.publicPrefix = endpoint.endsWith("/") ? endpoint + bucket : endpoint + "/" + bucket;
    }

    /** 头像单独限 2MB —— 商品图有 5MB 的 multipart 上限兜着，头像不需要那么大 */
    private static final long AVATAR_MAX_BYTES = 2L * 1024 * 1024;

    @Override
    public String uploadProductImage(MultipartFile file) {
        return upload(file, "products/", 0);
    }

    @Override
    public String uploadAvatar(MultipartFile file) {
        return upload(file, "avatars/", AVATAR_MAX_BYTES);
    }

    /**
     * @param prefix   对象名前缀 —— 商品图和头像分开存，别都堆在 products/ 里
     * @param maxBytes 0 表示不限（仍受 multipart 的 5MB 上限约束）
     */
    private String upload(MultipartFile file, String prefix, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "文件为空");
        }
        if (maxBytes > 0 && file.getSize() > maxBytes) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "图片不能超过 " + (maxBytes / 1024 / 1024) + "MB");
        }
        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        extension = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BizException(ResultCode.BAD_REQUEST, "只允许上传 jpg / jpeg / png / gif / webp 图片");
        }

        ensureBucket();

        String objectName = prefix + UUID.randomUUID() + "." + extension;
        try (var in = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(in, file.getSize(), -1)
                    .contentType(CONTENT_TYPES.getOrDefault(extension, "application/octet-stream"))
                    .build());
        } catch (Exception e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "图片上传失败");
        }
        return publicPrefix + "/" + objectName;
    }

    /** 懒初始化：MinIO 没起来时不影响应用启动，上传时才报错 */
    private void ensureBucket() {
        if (bucketReady) {
            return;
        }
        synchronized (this) {
            if (bucketReady) {
                return;
            }
            try {
                if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                    log.info("创建 MinIO bucket: {}", bucket);
                }
                // 商品图需要能被浏览器直接访问
                minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                        .bucket(bucket)
                        .config(publicReadPolicy(bucket))
                        .build());
            } catch (Exception e) {
                throw new BizException(ResultCode.INTERNAL_ERROR, "对象存储不可用：" + e.getMessage());
            }
            bucketReady = true;
        }
    }

    private String publicReadPolicy(String bucketName) {
        return """
                {
                  "Version": "2012-10-17",
                  "Statement": [{
                    "Effect": "Allow",
                    "Principal": {"AWS": ["*"]},
                    "Action": ["s3:GetObject"],
                    "Resource": ["arn:aws:s3:::%s/*"]
                  }]
                }
                """.formatted(bucketName);
    }
}
