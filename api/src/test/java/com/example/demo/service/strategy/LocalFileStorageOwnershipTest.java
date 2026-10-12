package com.example.demo.service.strategy;

import com.example.demo.service.strategy.impl.LocalFileStorageStrategy;
import com.wwfinance.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文件归属隔离测试。
 *
 * <p>背景：{@code DELETE /api/oss/file/remove?url=...} 曾经只校验 URL 前缀，
 * 任意登录用户拿到他人的签名图 URL（合同详情接口会把 {@code signRecord.signImgUrl}
 * 返回给同合同的另一方）就能删掉对方的合同签署存证与实名材料。</p>
 *
 * <p>修复方式是把属主编进存储路径（{@code images/u<ownerId>/<file>}），删除时严格校验。
 * 本测试锁住五件事：本人可删、<b>跨用户必拒</b>、旧的无属主路径必拒、系统目录（signatures）必拒、
 * 形似但不相等的属主段（u33 vs u330）不能被误判。</p>
 *
 * <p>纯 JUnit + @TempDir，不连数据库、不碰真实 uploads 目录，离线可跑。</p>
 */
@DisplayName("文件归属隔离测试")
class LocalFileStorageOwnershipTest {

    @TempDir
    Path tempDir;

    private LocalFileStorageStrategy strategy;

    private static final String URL_PREFIX = "/api/knowledge/files";

    @BeforeEach
    void setUp() throws Exception {
        strategy = new LocalFileStorageStrategy();
        // 单测不启 Spring 容器，@Value 不生效，手动注入两个配置项
        setField(strategy, "basePath", tempDir.toString());
        setField(strategy, "urlPrefix", URL_PREFIX);
    }

    // ==================== 上传：属主必须落进路径 ====================

    @Test
    @DisplayName("上传后 URL 带属主段，文件落在 images/u33/ 下")
    void upload_putsOwnerIntoPath() throws Exception {
        String url = strategy.upload(png(), "images", 33L);

        assertTrue(url.startsWith(URL_PREFIX + "/images/u33/"), "URL 应含属主段，实际=" + url);
        Path onDisk = tempDir.resolve("images").resolve("u33").resolve(lastSegment(url));
        assertTrue(Files.exists(onDisk), "文件应落在 images/u33/ 下");
    }

    @Test
    @DisplayName("上传不带属主 → 直接拒绝，不落无主文件")
    void upload_withoutOwner_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> strategy.upload(png(), "images", null));
    }

    // ==================== 删除：本人可删 ====================

    @Test
    @DisplayName("本人删除自己的文件 → 成功，磁盘上文件消失")
    void deleteOwned_ownFile_deletes() throws Exception {
        String url = strategy.upload(png(), "images", 33L);
        Path onDisk = tempDir.resolve("images").resolve("u33").resolve(lastSegment(url));

        strategy.deleteOwned(url, 33L);

        assertFalse(Files.exists(onDisk), "本人文件应被真正删除");
    }

    // ==================== 删除：跨用户必拒（本次修复的核心回归点）====================

    @Test
    @DisplayName("删除他人文件 → 403，且文件必须还在")
    void deleteOwned_otherUsersFile_rejectedAndKept() throws Exception {
        String victimUrl = strategy.upload(png(), "images", 33L);
        Path victimFile = tempDir.resolve("images").resolve("u33").resolve(lastSegment(victimUrl));

        // 42 号用户拿到 33 号的 URL 来删
        ResourceNotFoundException e = assertThrows(ResourceNotFoundException.class,
                () -> strategy.deleteOwned(victimUrl, 42L));

        assertEquals(403, e.getCode());
        assertTrue(Files.exists(victimFile), "越权删除必须失败，文件不能被删掉");
    }

    @Test
    @DisplayName("属主段'形似但不等'不能被误判（u33 不能删 u330 的文件）")
    void deleteOwned_similarOwnerSegment_rejected() throws Exception {
        String otherUrl = strategy.upload(png(), "images", 330L);
        Path otherFile = tempDir.resolve("images").resolve("u330").resolve(lastSegment(otherUrl));

        assertThrows(ResourceNotFoundException.class,
                () -> strategy.deleteOwned(otherUrl, 33L));
        assertTrue(Files.exists(otherFile), "前缀相似的属主不能互相删除");
    }

    @Test
    @DisplayName("ownerId 为 null → 401，不放行任何删除")
    void deleteOwned_nullOwner_rejected() throws Exception {
        String url = strategy.upload(png(), "images", 33L);
        Path onDisk = tempDir.resolve("images").resolve("u33").resolve(lastSegment(url));

        ResourceNotFoundException e = assertThrows(ResourceNotFoundException.class,
                () -> strategy.deleteOwned(url, null));

        assertEquals(401, e.getCode());
        assertTrue(Files.exists(onDisk));
    }

    // ==================== 删除：旧路径与系统目录 ====================

    @Test
    @DisplayName("修复前上传的旧文件（无属主段）一律拒绝删除 —— fail-closed")
    void deleteOwned_legacyPathWithoutOwner_rejected() {
        String legacyUrl = URL_PREFIX + "/images/images_1699999999_1234.png";

        assertThrows(ResourceNotFoundException.class, () -> strategy.deleteOwned(legacyUrl, 33L));
    }

    @Test
    @DisplayName("合同签名存证目录（signatures）不可经用户接口删除")
    void deleteOwned_signatureFile_rejected() {
        // 签名图由服务端以「无属主」方式上传，形如 signatures/sign_HT20261008001.png
        String signatureUrl = URL_PREFIX + "/signatures/sign_HT20261008001.png";

        assertThrows(ResourceNotFoundException.class, () -> strategy.deleteOwned(signatureUrl, 33L));
    }

    @Test
    @DisplayName("路径穿越形态被拒（.. 无法凑出合法三段式）")
    void deleteOwned_pathTraversal_rejected() {
        assertThrows(ResourceNotFoundException.class,
                () -> strategy.deleteOwned(URL_PREFIX + "/images/u33/../../../etc/passwd", 33L));
    }

    @Test
    @DisplayName("多套一层目录也不能蒙混过关")
    void deleteOwned_extraNesting_rejected() {
        assertThrows(ResourceNotFoundException.class,
                () -> strategy.deleteOwned(URL_PREFIX + "/images/u33/sub/x.png", 33L));
    }

    @Test
    @DisplayName("前缀不属于本存储策略的地址被拒")
    void deleteOwned_foreignPrefix_rejected() {
        assertThrows(ResourceNotFoundException.class,
                () -> strategy.deleteOwned("http://evil.example.com/images/u33/x.png", 33L));
    }

    // ==================== 内部可信删除通道仍然可用 ====================

    @Test
    @DisplayName("deleteUnchecked 供内部流程使用，能删掉无属主的系统文件")
    void deleteUnchecked_stillWorksForSystemFiles() throws Exception {
        // 模拟知识库 PDF：无属主段的系统文件
        Path systemFile = tempDir.resolve("knowledge").resolve("manual.pdf");
        Files.createDirectories(systemFile.getParent());
        Files.write(systemFile, new byte[]{1, 2, 3});

        strategy.deleteUnchecked(URL_PREFIX + "/knowledge/manual.pdf");

        assertFalse(Files.exists(systemFile), "内部可信通道应仍能删除系统文件");
    }

    // ==================== 辅助 ====================

    private MockMultipartFile png() {
        return new MockMultipartFile("file", "idcard.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G'});
    }

    private static String lastSegment(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private static void setField(Object target, String name, String value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
