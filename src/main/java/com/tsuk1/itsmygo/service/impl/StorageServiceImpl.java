package com.tsuk1.itsmygo.service.impl;

import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.service.StorageService;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import io.minio.StatObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

    private static final String PRODUCT_PREFIX = "products/";
    private static final String THUMB_PREFIX = "products/thumbs/";
    private static final int THUMB_SIZE = 400;
    private static final float THUMB_QUALITY = 0.82f;


    private final MinioClient minioClient;
    private final String bucket;
    private final String publicPrefix;

    private volatile boolean bucketReady = false;

    public StorageServiceImpl(MinioClient minioClient,
                              @Value("${itsmygo.minio.bucket}") String bucket,
                              @Value("${itsmygo.minio.public-endpoint}") String publicEndpoint) {
        this.minioClient = minioClient;
        this.bucket = bucket;
        this.publicPrefix = publicEndpoint.endsWith("/")
                ? publicEndpoint + bucket : publicEndpoint + "/" + bucket;
    }

    /** 头像和店铺 Logo 单独限 2MB —— 商品图有 5MB 上限兜着 */
    private static final long PRODUCT_IMAGE_MAX_BYTES = 5L * 1024 * 1024;
    private static final long AVATAR_MAX_BYTES = 2L * 1024 * 1024;
    private static final long MERCHANT_LOGO_MAX_BYTES = 2L * 1024 * 1024;
    private static final long CHAT_IMAGE_MAX_BYTES = 5L * 1024 * 1024;

    @Override
    public String uploadProductImage(MultipartFile file) {
        String url = upload(file, PRODUCT_PREFIX, PRODUCT_IMAGE_MAX_BYTES);
        // 上传即生成缩略图；生成失败只记日志，不影响上传结果
        ensureProductThumbnail(url);
        return url;
    }

    @Override
    public String ensureProductThumbnail(String imageUrl) {
        String objectName = productObjectName(imageUrl);
        if (objectName == null || objectName.startsWith(THUMB_PREFIX)) {
            return objectName == null ? "" : imageUrl;
        }
        String thumbObject = thumbnailObjectName(objectName);
        String thumbUrl = publicPrefix + "/" + thumbObject;
        try {
            // 上传时已生成过就复用；对象名由原图决定，不会串图
            minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(thumbObject).build());
            return thumbUrl;
        } catch (Exception ignored) {
            // 不存在就往下生成
        }
        try (GetObjectResponse source = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucket).object(objectName).build())) {
            BufferedImage image = ImageIO.read(source);
            if (image == null) {
                log.warn("缩略图生成失败，无法解码：{}", imageUrl);
                return "";
            }
            byte[] jpeg = renderThumbnail(image);
            ensureBucket();
            try (var in = new ByteArrayInputStream(jpeg)) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(thumbObject)
                        .stream(in, jpeg.length, -1)
                        .contentType("image/jpeg")
                        .build());
            }
            return thumbUrl;
        } catch (Exception e) {
            log.warn("缩略图生成失败：{}（{}）", imageUrl, e.getMessage());
            return "";
        }
    }

    @Override
    public String uploadAvatar(MultipartFile file) {
        return upload(file, "avatars/", AVATAR_MAX_BYTES);
    }

    @Override
    public String uploadMerchantLogo(MultipartFile file) {
        return upload(file, "merchants/", MERCHANT_LOGO_MAX_BYTES);
    }

    @Override
    public String uploadChatImage(MultipartFile file) {
        return upload(file, "chat/", CHAT_IMAGE_MAX_BYTES);
    }

    @Override
    public boolean isChatImageUrl(String url) {
        return url != null && url.startsWith(publicPrefix + "/chat/");
    }

    @Override
    public String publicUrl(String objectKey) {
        String key = objectKey == null ? "" : objectKey.replaceAll("^/+", "");
        return publicPrefix + (key.isEmpty() ? "" : "/" + key);
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

    /** 只接受本系统商品的公网 URL，避免把任意外链当图片下载 */
    private String productObjectName(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return null;
        }
        String prefix = publicPrefix + "/";
        if (!imageUrl.startsWith(prefix)) {
            return null;
        }
        String object = imageUrl.substring(prefix.length());
        int query = object.indexOf('?');
        if (query >= 0) {
            object = object.substring(0, query);
        }
        return object.startsWith(PRODUCT_PREFIX) ? object : null;
    }

    /** products/seed/1722.<ext> → products/thumbs/seed/1722.jpg */
    private String thumbnailObjectName(String objectName) {
        String relative = objectName.substring(PRODUCT_PREFIX.length());
        int dot = relative.lastIndexOf('.');
        if (dot > 0) {
            relative = relative.substring(0, dot);
        }
        return THUMB_PREFIX + relative + ".jpg";
    }

    /** 中心裁成正方形再缩到 400×400，和列表卡片的 object-fit: cover 视觉一致 */
    private byte[] renderThumbnail(BufferedImage source) throws IOException {
        BufferedImage thumb = new BufferedImage(THUMB_SIZE, THUMB_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = thumb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, THUMB_SIZE, THUMB_SIZE);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int side = Math.min(source.getWidth(), source.getHeight());
            int sx = (source.getWidth() - side) / 2;
            int sy = (source.getHeight() - side) / 2;
            g.drawImage(source, 0, 0, THUMB_SIZE, THUMB_SIZE,
                    sx, sy, sx + side, sy + side, null);
        } finally {
            g.dispose();
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(THUMB_QUALITY);
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(thumb, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
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
