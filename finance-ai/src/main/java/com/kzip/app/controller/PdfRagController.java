package com.kzip.app.controller;

import com.kzip.app.repository.ChatHistoryRepository;
import com.kzip.app.repository.FileRepository;
import com.kzip.app.service.PdfRagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@RestController
@RequestMapping("/ai/pdf")
public class PdfRagController {

    private static final Logger log = LoggerFactory.getLogger(PdfRagController.class);

    private final PdfRagService pdfRagService;
    private final ChatHistoryRepository chatHistoryRepository;
    private final FileRepository fileRepository;

    public PdfRagController(PdfRagService pdfRagService,
                            ChatHistoryRepository chatHistoryRepository,
                            FileRepository fileRepository) {
        this.pdfRagService = pdfRagService;
        this.chatHistoryRepository = chatHistoryRepository;
        this.fileRepository = fileRepository;
    }

    @PostMapping("/upload/{chatId}")
    public Result<String> uploadPdf(@PathVariable String chatId,
                                    @RequestParam("file") MultipartFile file) {
        // 空文件/未传文件
        if (file == null || file.isEmpty()) {
            return Result.fail("文件不能为空");
        }
        String originalName = file.getOriginalFilename();
        // 1) MIME 类型校验（客户端声明，可伪造，仅作第一道）
        if (!Objects.equals(file.getContentType(), "application/pdf")) {
            return Result.fail("只能上传PDF文件");
        }
        // 2) 扩展名校验（可伪造，仅作第二道）
        if (originalName == null || !originalName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            return Result.fail("文件名必须以 .pdf 结尾");
        }
        // 3) 内容魔数校验（真实格式判定：PDF 文件头固定为 %PDF-）
        if (!isPdfMagic(file)) {
            return Result.fail("文件内容不是有效的PDF（缺少 %PDF- 文件头）");
        }
        boolean ok = pdfRagService.uploadPdfAndStoreVector(chatId, file.getResource());
        if (ok) {
            chatHistoryRepository.save("pdf", chatId);
            return Result.success("上传并且向量入库成功");
        } else {
            return Result.fail("处理PDF失败");
        }
    }

    /**
     * 基于文件内容（魔数）判断是否为真实 PDF，而不是依赖可伪造的扩展名/Content-Type。
     * PDF 规范要求文件头为字节序列 {@code %PDF-}（0x25 0x50 0x44 0x46 0x2D）。
     */
    private boolean isPdfMagic(MultipartFile file) {
        byte[] header = new byte[5];
        try (InputStream in = file.getInputStream()) {
            int read = in.read(header);
            return read >= 5
                    && header[0] == '%' && header[1] == 'P'
                    && header[2] == 'D' && header[3] == 'F' && header[4] == '-';
        } catch (IOException e) {
            log.warn("读取PDF文件头失败: {}", e.getMessage());
            return false;
        }
    }

    @GetMapping("/file/{chatId}")
    public ResponseEntity<Resource> download(@PathVariable String chatId) throws Exception {
        Resource resource = fileRepository.getFile(chatId);
        // 修复：未知 chatId 返回 404 而不是 NPE/500
        if (resource == null || !resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        String fileName = URLEncoder.encode(Objects.requireNonNull(resource.getFilename()), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment;filename=\"" + fileName + "\"")
                .body(resource);
    }

    @GetMapping("/history")
    public Result<List<String>> getHistory() {
        return Result.success(chatHistoryRepository.getChatIds("pdf"));
    }

    @DeleteMapping("/history/{chatId}")
    public Result<String> delHistory(@PathVariable String chatId) {
        chatHistoryRepository.delete("pdf", chatId);
        return Result.success("删除成功");
    }
}