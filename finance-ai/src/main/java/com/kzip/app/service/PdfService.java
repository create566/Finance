package com.kzip.app.service;

import com.kzip.app.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PdfRagService {

    private final FileRepository fileRepository;
    private final VectorStore vectorStore;

    /**
     * 上传PDF完整逻辑：保存磁盘 → 段落读取PDF → 元数据赋值 →语义切割 →分批Embedding存入ChromaDB
     * @param chatId 会话id
     * @param pdfResource pdf文件资源
     * @return true成功 false失败
     */
    public boolean uploadPdfAndStoreVector(String chatId, Resource pdfResource) {
        // 步骤1：保存PDF原始文件到本地磁盘
        boolean saveFileOk = fileRepository.save(chatId, pdfResource);
        if (!saveFileOk) {
            return false;
        }

        String fileName = pdfResource.getFilename();
        LocalDateTime now = LocalDateTime.now();

        try {
            // 步骤2：使用ParagraphPdfDocumentReader 按段落读取PDF
            PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                    .withPageTopMargin(0)
                    .withPageBottomMargin(0)
                    .withPagesPerDocument(1)
                    .build();

            ParagraphPdfDocumentReader paragraphReader = new ParagraphPdfDocumentReader(pdfResource, config);
            List<Document> originDocumentList = paragraphReader.get();

            // 步骤3：给原始文档设置元数据
            for (Document doc : originDocumentList) {
                doc.getMetadata().put("file_name", fileName);
                doc.getMetadata().put("chatId", chatId);
                doc.getMetadata().put("upload_time", now.toString());
            }

            // 步骤4：TokenTextSplitter做二次语义切割
            // 参数：chunkSize=500，重叠50，maxChunkSize=1000，minChunkSize=0
            TokenTextSplitter splitter = new TokenTextSplitter(500, 50, 1000, 0);
            List<Document> chunkDocumentList = splitter.apply(originDocumentList);

            // 步骤5：分批写入向量库，DashScope embedding单次最多10条
            int batchSize = 10;
            for (int i = 0; i < chunkDocumentList.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, chunkDocumentList.size());
                List<Document> batchList = chunkDocumentList.subList(i, endIndex);
                vectorStore.add(batchList);
            }
            return true;
        } catch (Exception e) {
            // PDF解析、向量化出现任何异常返回false，controller收到返回失败
            e.printStackTrace();
            return false;
        }
    }


    /**
     * 向量检索：RAG查询，根据问题，在chromadb检索相关文档片段
     * @param question 用户提问
     * @param fileName 需要过滤的pdf文件名
     * @return 检索出来的文档片段
     */
    public List<Document> searchRelevantDoc(String question, String fileName) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(question)
                .topK(2)
                .similarityThreshold(0.5d)
                .filterExpression("file_name == '" + fileName + "'")
                .build();
        return vectorStore.similaritySearch(searchRequest);
    }
}