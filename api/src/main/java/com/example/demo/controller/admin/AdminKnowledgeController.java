package com.example.demo.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.demo.entity.KnowledgeBase;
import com.example.demo.entity.KnowledgeDoc;
import com.example.demo.service.KnowledgeBaseService;
import com.example.demo.service.KnowledgeDocService;
import com.wwfinance.common.result.PccAjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@Api(tags = "后台知识库管理")
@RestController
@RequestMapping("/admin/core/knowledge")
public class AdminKnowledgeController {

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private KnowledgeDocService knowledgeDocService;

    @ApiOperation("知识库列表（分页）")
    @GetMapping("/base/page")
    public PccAjaxResult basePage(@RequestParam(defaultValue = "1") Integer pageNum,
                                   @RequestParam(defaultValue = "10") Integer pageSize,
                                   @RequestParam(required = false) String name,
                                   @RequestParam(required = false) Integer status) {
        IPage<KnowledgeBase> result = knowledgeBaseService.pageList(pageNum, pageSize, name, status);
        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getRecords());
        data.put("total", result.getTotal());
        return new PccAjaxResult(200, "查询成功", data);
    }

    @ApiOperation("获取所有知识库（不分页）")
    @GetMapping("/base/list")
    public PccAjaxResult baseList() {
        return new PccAjaxResult(200, "查询成功", knowledgeBaseService.list());
    }

    @ApiOperation("根据ID获取知识库详情")
    @GetMapping("/base/{id}")
    public PccAjaxResult baseGetById(@PathVariable Long id) {
        KnowledgeBase kb = knowledgeBaseService.getById(id);
        if (kb == null) {
            return new PccAjaxResult(404, "知识库不存在", null);
        }
        return new PccAjaxResult(200, "查询成功", kb);
    }

    @ApiOperation("新增知识库")
    @PostMapping("/base")
    public PccAjaxResult baseAdd(@RequestBody KnowledgeBase knowledgeBase) {
        boolean ok = knowledgeBaseService.saveWithCheck(knowledgeBase);
        return ok ? new PccAjaxResult(200, "新增成功", knowledgeBase.getId())
                  : new PccAjaxResult(500, "新增失败", null);
    }

    @ApiOperation("修改知识库")
    @PutMapping("/base")
    public PccAjaxResult baseUpdate(@RequestBody KnowledgeBase knowledgeBase) {
        boolean ok = knowledgeBaseService.updateWithCheck(knowledgeBase);
        return ok ? new PccAjaxResult(200, "修改成功", null)
                  : new PccAjaxResult(500, "修改失败", null);
    }

    @ApiOperation("删除知识库（级联删除所有文档）")
    @DeleteMapping("/base/{id}")
    public PccAjaxResult baseDelete(@PathVariable Long id) {
        boolean ok = knowledgeBaseService.removeCascade(id);
        return ok ? new PccAjaxResult(200, "删除成功", null)
                  : new PccAjaxResult(500, "删除失败", null);
    }

    @ApiOperation("文档列表（分页，按知识库）")
    @GetMapping("/doc/page")
    public PccAjaxResult docPage(@RequestParam Long kbId,
                                  @RequestParam(defaultValue = "1") Integer pageNum,
                                  @RequestParam(defaultValue = "10") Integer pageSize,
                                  @RequestParam(required = false) String title,
                                  @RequestParam(required = false) String source,
                                  @RequestParam(required = false) Integer status) {
        IPage<KnowledgeDoc> result = knowledgeDocService.pageList(kbId, pageNum, pageSize, title, source, status);
        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getRecords());
        data.put("total", result.getTotal());
        return new PccAjaxResult(200, "查询成功", data);
    }

    @ApiOperation("获取某个知识库下所有文档（不分页）")
    @GetMapping("/doc/list")
    public PccAjaxResult docList(@RequestParam Long kbId) {
        return new PccAjaxResult(200, "查询成功", knowledgeDocService.list());
    }

    @ApiOperation("根据ID获取文档详情")
    @GetMapping("/doc/{id}")
    public PccAjaxResult docGetById(@PathVariable Long id) {
        KnowledgeDoc doc = knowledgeDocService.getById(id);
        if (doc == null) {
            return new PccAjaxResult(404, "文档不存在", null);
        }
        return new PccAjaxResult(200, "查询成功", doc);
    }

    @ApiOperation("添加文本文档（自动触发向量化）")
    @PostMapping("/doc/text")
    public PccAjaxResult docAddText(@RequestBody KnowledgeDoc doc) {
        KnowledgeDoc savedDoc = knowledgeDocService.saveTextDoc(doc);
        return new PccAjaxResult(200, "添加成功，正在向量化处理...", savedDoc.getId());
    }

    @ApiOperation("上传PDF文档（独立存储目录，自动触发向量化）")
    @PostMapping("/doc/upload")
    public PccAjaxResult docUploadPdf(@RequestParam("kbId") Long kbId,
                                       @RequestParam("title") String title,
                                       @RequestParam("file") MultipartFile file) {
        Map<String, Object> result = knowledgeDocService.savePdfDoc(kbId, title, file);
        return new PccAjaxResult(200, "PDF上传成功", result);
    }

    @ApiOperation("删除文档")
    @DeleteMapping("/doc/{id}")
    public PccAjaxResult docDelete(@PathVariable Long id) {
        boolean ok = knowledgeDocService.removeWithFile(id);
        return ok ? new PccAjaxResult(200, "删除成功", null)
                  : new PccAjaxResult(500, "删除失败", null);
    }

    @ApiOperation("AI模块回调：更新文档向量化状态")
    @PostMapping("/doc/{docId}/embedding-status")
    public PccAjaxResult updateEmbeddingStatus(@PathVariable Long docId,
                                                @RequestBody Map<String, Object> statusInfo) {
        Integer status = (Integer) statusInfo.get("status");
        Integer chunkCount = statusInfo.get("chunkCount") != null ?
                             ((Number) statusInfo.get("chunkCount")).intValue() : null;
        String message = (String) statusInfo.get("message");

        knowledgeDocService.updateEmbeddingStatus(docId, status, chunkCount, message);

        Map<String, Object> data = new HashMap<>();
        data.put("docId", docId);
        data.put("status", status);
        data.put("chunkCount", chunkCount);

        return new PccAjaxResult(200, "文档状态已更新", data);
    }

    @ApiOperation("健康检查（供AI模块调用）")
    @GetMapping("/health")
    public Map<String, Object> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "api-module");
        health.put("timestamp", System.currentTimeMillis());
        return health;
    }
}