package com.tsuk1.itsmygo;

import com.tsuk1.itsmygo.service.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 商品图上传：走真实 MinIO（复用本机共用实例），因此 @Transactional 不会回滚对象存储。
 * 更关键的是验证 bucket 公开读策略生效 —— 上传完的 URL 要能被浏览器直接打开。
 *
 * ⚠️ **CI 上会跳过本类**（workflow 里设了 SKIP_MINIO_TESTS=true）：
 * MinIO 在 2025 年停止了官方镜像与二进制的公开分发
 * （Docker Hub 上 minio/minio 返回 denied、dl.min.io 返回 410），
 * GitHub Actions 里起不了实例。本地有共用 MinIO 容器，所以照常跑。
 * 代价是 CI 覆盖率少这几条，**这是已知的、被记录下来的缺口，不是遗漏**。
 */
@DisabledIfEnvironmentVariable(
        named = "SKIP_MINIO_TESTS",
        matches = "true",
        disabledReason = "CI 环境没有可用的 MinIO（官方镜像与二进制已停止公开分发）")
class StorageServiceTest extends ApiTestBase {

    private static final String BUCKET_PREFIX = "http://localhost:9000/its-my-go-product-images/";

    @Autowired
    private StorageService storageService;

    private String upload(String adminToken, MockMultipartFile file) throws Exception {
        String body = mockMvc.perform(multipart("/api/admin/files/upload")
                        .file(file)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").asText();
    }

    @Test
    void adminUploadsImageAndUrlIsPubliclyReadable() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A});

        String url = upload(admin, file);

        assertThat(url).startsWith(BUCKET_PREFIX);
        assertThat(url).endsWith(".png");
        assertThat(url).contains("/products/");

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void uploadGeneratesSquareThumbnail() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.png", "image/png", samplePng());

        String url = upload(admin, file);
        String thumbUrl = storageService.ensureProductThumbnail(url);

        assertThat(thumbUrl).contains("/products/thumbs/").endsWith(".jpg");
        HttpResponse<byte[]> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(thumbUrl)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        BufferedImage thumb = ImageIO.read(new ByteArrayInputStream(response.body()));
        assertThat(thumb.getWidth()).isEqualTo(400);
        assertThat(thumb.getHeight()).isEqualTo(400);
    }

    private byte[] samplePng() throws Exception {
        BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(220, 130, 160));
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    void rejectsNonImageExtension() throws Exception {
        String admin = adminToken("adm" + System.nanoTime());
        MockMultipartFile file = new MockMultipartFile(
                "file", "evil.sh", "text/plain", "#!/bin/sh".getBytes());

        mockMvc.perform(multipart("/api/admin/files/upload")
                        .file(file)
                        .header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("只允许上传 jpg / jpeg / png / gif / webp 图片"));
    }

    @Test
    void customerCannotUpload() throws Exception {
        String customer = customerToken("cust" + System.nanoTime());
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/admin/files/upload")
                        .file(file)
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isForbidden());
    }
}
