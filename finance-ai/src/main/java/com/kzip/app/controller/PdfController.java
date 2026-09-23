package com.kzip.app.controller;

import com.kzip.app.repository.ChatHistoryRepository;
import com.kzip.app.service.PdfRagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Slf4j
@RestController
@RequestMapping("/ai/pdf")
@RequiredArgsConstructor
public class PdfRagController {

    private final PdfRagService pdfRagService;
    private final ChatClient pdfChatClient;
    private final ChatHistoryRepository chatHistoryRepository;
    private final com.kzip.app.repository.FileRepository fileRepository;


    /**
     * 1、PDF上传接口
     * POST /ai/pdf/upload/{chatId}
     */
    @PostMapping("/upload/{chatId}")
    public Result<String> uploadPdf(@PathVariable String chatId,
                                    @RequestParam("file") MultipartFile file) {
        // 参数校验 Controller只做校验
        if (!Objects.equals(file.getContentType(), "application/pdf")) {
            return Result.fail("只能上传PDF文件");
        }
        // 直接调用service，业务逻辑全部交给service
        boolean ok = pdfRagService.uploadPdfAndStoreVector(chatId, file.getResource());
        if (ok) {
            return Result.success("上传并且向量入库成功");
        } else {
            return Result.fail("处理PDF失败");
        }
    }


    /**
     * 2、RAG流式对话接口 SSE
     */
    @RequestMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestParam String prompt, @RequestParam String chatId) {
        chatHistoryRepository.save("pdf", chatId);
        Resource fileResource = fileRepository.getFile(chatId);
        String filename = fileResource.getFilename();

        // QuestionAnswerAdvisor会自动调用VectorStore(ChromaDB)做向量检索
        return pdfChatClient.prompt(prompt)
                .advisors(a -> a.param("chat_memory_conversation_id", chatId))
                .advisors(a -> a.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, "file_name == '" + filename + "'"))
                .stream()
                .content();
    }

    /**
     * 3、同步RAG接口
     */
    @GetMapping("/chat‑sync")
    public Result<String> chatSync(@RequestParam String prompt, @RequestParam String chatId) {
        chatHistoryRepository.save("pdf", chatId);
        Resource fileResource = fileRepository.getFile(chatId);
        String filename = fileResource.getFilename();
        String answer = pdfChatClient.prompt(prompt)
                .advisors(a -> a.param("chat_memory_conversation_id", chatId))
                .advisors(a -> a.param(QuestionAnswerAdvisor.FILTER_EXPRESSION, "file_name == '" + filename + "'"))
                .call()
                .content();
        return Result.success(answer);
    }


    /**
     * 文件下载接口，这个属于controller层，保留
     */
    @GetMapping("/file/{chatId}")
    public ResponseEntity<Resource> download(@PathVariable String chatId) throws Exception {
        Resource resource = fileRepository.getFile(chatId);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        String fileName = URLEncoder.encode(Objects.requireNonNull(resource.getFilename()), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header("Content‑Disposition", "attachment;filename=\"" + fileName + "\"")
                .body(resource);
    }

    /**
     * 获取会话列表
     */
    @GetMapping("/history")
    public Result<List<String>> getHistory() {
        return Result.success(chatHistoryRepository.getChatIds("pdf"));
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/history/{chatId}")
    public Result<String> delHistory(@PathVariable String chatId) {
        chatHistoryRepository.delete("pdf", chatId);
        return Result.success("删除成功");
    }
}