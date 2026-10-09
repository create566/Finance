package com.wwfinance.oss.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文件服务。
 *
 * <p><b>2026-10-08 接口调整</b>：上传与删除都强制携带属主 {@code ownerId}。
 * 原先的 {@code upload(file, category)} / {@code delete(url)} 已删除 ——
 * 它们产生的文件没有归属信息，删除时也就无从校验，是 IDOR 的根源。
 * 归属通过「存储路径里的 {@code u<ownerId>} 段」自证，不需要额外建表。</p>
 */
public interface FileService {

    /**
     * 上传文件并登记归属。
     *
     * @param category 一级目录（由 Controller 固定为 images，不接受前端传入）
     * @param ownerId  上传者用户 id，不可为 null
     * @return 可访问的 URL，形如 {@code /api/oss/file/images/u33/xxx.png}
     */
    String upload(MultipartFile file, String category, Long ownerId);

    /**
     * 以指定文件名上传并登记归属（用于签名图等需要固定命名的场景）。
     */
    String uploadWithCustomName(MultipartFile file, String category, String customFileName, Long ownerId);

    /**
     * 删除文件，仅当该文件属于 {@code ownerId} 时允许。
     *
     * @throws com.wwfinance.common.exception.ResourceNotFoundException 403 非本人文件 / 401 未登录
     */
    void deleteOwned(String fileUrl, Long ownerId);

    /** 是否存在（只读，不校验归属） */
    boolean exists(String fileUrl);

    /** 文件信息（只读，不校验归属） */
    Map<String, Object> getFileInfo(String fileUrl);
}
