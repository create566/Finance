package com.kzip.app.service;

import com.kzip.app.repository.FileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class PdfRagService {

    private static final Logger log = LoggerFactory.getLogger(PdfRagService.class);

    private final FileRepository fileRepository;
    private final VectorStore vectorStore;

    public PdfRagService(FileRepository fileRepository, VectorStore vectorStore) {
        this.fileRepository = fileRepository;
        this.vectorStore = vectorStore;
    }

    /**
     * 上传PDF完整逻辑：保存磁盘 → 段落读取PDF → 元数据赋值 →语义切割 →分批Embedding存入ChromaDB
     * @param chatId 会话id
     * @param pdfResource pdf文件资源
     * @return true成功 false失败
     */
    public boolean uploadPdfAndStoreVector(String chatId, Resource pdfResource) {
        String fileName = pdfResource.getFilename();
        LocalDateTime now = LocalDateTime.now();

        // 步骤1：保存PDF原始文件到本地磁盘
        boolean saveFileOk = fileRepository.save(chatId, pdfResource);
        if (!saveFileOk) {
            return false;
        }

        // 步骤2：从磁盘重新读取，避免 MultipartFile InputStream 被重复消费
        Resource savedResource = fileRepository.getFile(chatId);
        if (savedResource == null || !savedResource.exists()) {
            log.error("PDF保存后无法从磁盘读取: chatId={}", chatId);
            return false;
        }

        try {
            // 步骤3：使用ParagraphPdfDocumentReader 按段落读取PDF
            PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                    .withPageTopMargin(0)
                    .withPageBottomMargin(0)
                    .withPagesPerDocument(1)
                    .build();

            PagePdfDocumentReader pageReader = new PagePdfDocumentReader(savedResource, config);
            List<Document> originDocumentList = pageReader.get();

            // 步骤4：给原始文档设置元数据
            for (Document doc : originDocumentList) {
                doc.getMetadata().put("file_name", fileName);
                doc.getMetadata().put("chatId", chatId);
                doc.getMetadata().put("upload_time", now.toString());
            }

            // 步骤5：TokenTextSplitter做二次语义切割
            TokenTextSplitter splitter = new TokenTextSplitter(500, 50, 1000, 10000, false);
            List<Document> chunkDocumentList = splitter.apply(originDocumentList);
            log.info("PDF解析完成: 原始文档数={}, 切割后chunks={}, chatId={}, fileName={}",
                    originDocumentList.size(), chunkDocumentList.size(), chatId, fileName);

            // 切割后无有效文本（典型原因：图片型PDF没有文本层），明确失败而非静默"成功"
            if (chunkDocumentList.isEmpty()) {
                log.warn("PDF未提取到有效文本，可能是图片型PDF: chatId={}, fileName={}", chatId, fileName);
                return false;
            }

            // 步骤6：分批写入向量库，DashScope embedding单次最多10条
            int batchSize = 10;
            for (int i = 0; i < chunkDocumentList.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, chunkDocumentList.size());
                List<Document> batchList = chunkDocumentList.subList(i, endIndex);
                vectorStore.add(batchList);
            }
            return true;
        } catch (Exception e) {
            log.error("PDF解析/向量化失败: chatId={}, fileName={}", chatId, fileName, e);
            return false;
        }
    }


    /**
     * 向量检索：RAG查询，根据问题，在chromadb检索相关文档片段。
     *
     * <p>阈值修复：ChromaDB 内部用 L2 距离衡量相似度，<b>距离越小越相似</b>，
     * 与 Spring AI 默认的 similarityThreshold（越大越相似）语义相反，
     * 直接设 0.5 会失真。这里改为：不依赖阈值，先取 topK 候选，
     * 再按 metadata["distance"] 升序（近到远）排序，取最相似的前 2 条。
     *
     * @param question 用户提问
     * @param fileName 需要过滤的pdf文件名
     * @return 检索出来的文档片段（已按相似度降序，最多 2 条）
     */
    public List<Document> searchRelevantDoc(String question, String fileName) {
        // 使用 FilterExpressionBuilder 类型安全构建过滤条件，
        // 避免字符串拼接（文件名含引号/空格会语法错误，且存在注入风险）
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        SearchRequest searchRequest = SearchRequest.builder()
                .query(question)
                .topK(10)   // 多取候选，本地排序截断
                .filterExpression(b.eq("file_name", fileName).build())
                .build();
        List<Document> docs = new ArrayList<>(vectorStore.similaritySearch(searchRequest));
        // 按 distance 升序（L2 距离越小越相似）；缺省距离视为最远
        docs.sort(Comparator.comparingDouble(d -> {
            Object v = d.getMetadata().get("distance");
            if (v == null) {
                return Double.MAX_VALUE;
            }
            try {
                return Double.parseDouble(String.valueOf(v));
            } catch (NumberFormatException e) {
                return Double.MAX_VALUE;
            }
        }));
        if (docs.size() > 2) {
            docs = new ArrayList<>(docs.subList(0, 2));
        }
        return docs;
    }
}