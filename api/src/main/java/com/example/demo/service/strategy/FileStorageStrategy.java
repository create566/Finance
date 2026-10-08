package com.example.demo.service.strategy;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文件存储策略。
 *
 * <p><b>关于归属（重要）</b>：历史版本只有 {@code upload(file, category)} 与
 * {@code delete(fileUrl)}，两者都不带"文件属于谁"的信息，于是
 * {@code DELETE /api/oss/file/remove?url=...} 能删掉任意用户的文件（水平越权）。
 * 现在把归属做成两条硬约束：</p>
 *
 * <ol>
 *   <li>{@link #upload(MultipartFile, String, Long)} 把属主 ID 编进存储路径
 *       （{@code <category>/u<ownerId>/<fileName>}），归属自证，无需额外建表；</li>
 *   <li>{@link #deleteOwned(String, Long)} 删除前严格校验该路径段，
 *       不是本人的文件一律拒绝。</li>
 * </ol>
 *
 * <p>{@link #deleteUnchecked(String)} 是**唯一**不做归属校验的删除入口，故意起这个名字，
 * 让每一处调用都必须解释自己为什么可信。禁止直接暴露给用户请求。</p>
 */
public interface FileStorageStrategy {

    /**
     * 用户上传：把属主写进存储路径，删除时据此校验归属。
     *
     * @param ownerId 文件属主（当前登录用户 ID），不允许为 null
     * @return 形如 {@code /api/knowledge/files/images/u33/images_1712345678_1234.png} 的访问地址
     */
    String upload(MultipartFile file, String category, Long ownerId);

    /**
     * 系统上传（无属主概念，如合同签名存证、知识库文档）。
     * <p>调用方必须自己已完成鉴权 —— 此类文件一律不可通过用户端的删除接口删除。</p>
     */
    String upload(byte[] fileBytes, String originalFileName, String category);

    String uploadWithCustomName(MultipartFile file, String category, String customFileName);

    /**
     * 删除"本人"的文件：校验 {@code fileUrl} 里的属主段是否等于 {@code ownerId}。
     * 校验不通过时抛 403，不做任何删除动作。
     *
     * @throws com.wwfinance.common.exception.ResourceNotFoundException 归属不匹配或地址非法
     */
    void deleteOwned(String fileUrl, Long ownerId);

    /**
     * 【危险】不做任何归属校验的物理删除。
     *
     * <p>仅限服务端内部可信流程调用（例如知识库文档替换时删掉旧文件）。
     * 用户请求链路必须走 {@link #deleteOwned(String, Long)}。</p>
     */
    void deleteUnchecked(String fileUrl);

    boolean exists(String fileUrl);

    Map<String, Object> getFileInfo(String fileUrl);
}
