# 文档管理与 Apache Tika 接入实现方案（基于现有代码逐行对应）

> 适用分支：当前代码结构  
> 目标：不直接改代码，提供可对照落地的步骤、代码片段与路径。

---

## 1. 现状梳理（基于你代码）

- 文档服务接口：`com.ityfz.yulu.knowledge.service.DocumentService`
  - 入口方法：`uploadDocument(...)`、`parseDocument(...)`、`listDocuments(...)`、`getDocument(...)`、`deleteDocument(...)`、`updateDocumentStatus(...)`
- 实现：`com.ityfz.yulu.knowledge.service.Impl.DocumentServiceImpl`
  - `parseDocument` 目前按扩展名 switch，`pdf/doc/docx` 未实现直接抛错。
  - `uploadDocument` 负责：校验 → 解析 → `DocumentMapper.insert` → `ChunkService.chunkAndSave`。
- 切分服务：`com.ityfz.yulu.knowledge.service.Impl.ChunkServiceImpl`
  - 已有：规范化内容、智能分句切分、overlap、尾部小块合并、`chunkAndSave` 入库。
- Controller：缺失“文档管理”对外接口（上传/列表/详情/删除）。

痛点：
1) 解析只支持 txt/md，pdf/doc/docx/其他格式不可用。  
2) 没有对外 HTTP 接口，前端/运营无法管理知识库。

---

## 2. 方案总览

1) **引入 Apache Tika**：用统一解析器替换 `parseDocument` 的手写分支，支持常见办公格式。  
2) **新增解析适配层**：`TikaDocumentParser`（新类），对 DocumentServiceImpl 提供统一的 `parse(byte[] fileBytes, String filenameOrExt)`。  
3) **改造 DocumentServiceImpl.parseDocument**：去掉 switch，委托给 Tika。  
4) **补齐文档管理 Controller**：基于你现有的多租户/鉴权/返回体风格，提供上传文件、上传文本、列表、详情、删除接口。  
5) **DTO 与路径**：统一放在 admin 侧（推荐 B 端运营使用）。

---

## 3. 依赖变更（pom.xml）

在 `<dependencies>` 中新增（版本可按需调整，这里用 2.9.2）：

```xml
<!-- Apache Tika -->
<dependency>
  <groupId>org.apache.tika</groupId>
  <artifactId>tika-core</artifactId>
  <version>2.9.2</version>
</dependency>
<dependency>
  <groupId>org.apache.tika</groupId>
  <artifactId>tika-parsers-standard-package</artifactId>
  <version>2.9.2</version>
</dependency>
```

执行 `mvn -DskipTests compile` 确认能拉齐依赖。

---

## 4. 新增解析适配层：TikaDocumentParser

**文件路径（建议）**：`src/main/java/com/ityfz/yulu/knowledge/service/Impl/TikaDocumentParser.java`  
> 说明：你现有包是大写 `Impl`，保持一致，避免注入问题。

**职责**：接收 `byte[]` + 文件名/扩展名，返回纯文本；异常统一转为 `BizException`。

**核心代码片段（可直接抄到新类）**：

```java
package com.ityfz.yulu.knowledge.service.Impl;

import com.ityfz.yulu.common.enums.ErrorCodes;
import com.ityfz.yulu.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Service
@Slf4j
public class TikaDocumentParser {

    public String parse(byte[] fileBytes, String filenameOrExt) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new BizException(ErrorCodes.VALIDATION_ERROR, "文件内容为空");
        }

        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(-1); // -1 不截断
        Metadata metadata = new Metadata();
        if (filenameOrExt != null) {
            metadata.set(Metadata.RESOURCE_NAME_KEY, filenameOrExt);
        }
        ParseContext context = new ParseContext();

        try (InputStream is = new ByteArrayInputStream(fileBytes)) {
            parser.parse(is, handler, metadata, context);
            String text = handler.toString();
            if (text == null || text.trim().isEmpty()) {
                log.warn("[Tika] 解析结果为空: name={}", filenameOrExt);
                throw new BizException(ErrorCodes.DOCUMENT_PARSE_FAILED, "文档内容解析为空");
            }
            String normalized = text.trim();
            log.debug("[Tika] 解析完成: name={}, length={}", filenameOrExt, normalized.length());
            return normalized;
        } catch (Exception e) {
            log.error("[Tika] 文档解析失败: name={}", filenameOrExt, e);
            throw new BizException(ErrorCodes.DOCUMENT_PARSE_FAILED, "文档解析失败: " + e.getMessage());
        }
    }
}
```

---

## 5. 改造 DocumentServiceImpl.parseDocument

**文件**：`src/main/java/com/ityfz/yulu/knowledge/service/Impl/DocumentServiceImpl.java`

### 5.1 构造器注入 Tika 解析器

```java
private final TikaDocumentParser tikaDocumentParser;

public DocumentServiceImpl(DocumentMapper documentMapper,
                           ChunkService chunkService,
                           TikaDocumentParser tikaDocumentParser) {
    this.documentMapper = documentMapper;
    this.chunkService = chunkService;
    this.tikaDocumentParser = tikaDocumentParser;
}
```

> 你原构造器只有两个参数，添加后要同步修改调用处（Spring 自动注入即可）。

### 5.2 重写 parseDocument

替换原 switch 分支为 Tika 调用：

```java
@Override
public String parseDocument(byte[] fileBytes, String fileType) {
    if (fileBytes == null || fileBytes.length == 0) {
        throw new BizException(ErrorCodes.VALIDATION_ERROR, "文件内容为空");
    }
    // fileType 可以传原始文件名或扩展名，Tika 会结合内容自动判别
    String text = tikaDocumentParser.parse(fileBytes, fileType);
    return text;
}
```

> 若你想保留 txt/md 的特殊处理，可在解析前做一个简单分支，但主路径应走 Tika。

### 5.3 uploadDocument 里保持不变的要点

- 仍然使用 `getFileType(file.getOriginalFilename())` 存库。  
- 解析时直接调用新的 `parseDocument`，即可支持 pdf/doc/docx/html 等。

---

## 6. 补齐文档管理 Controller（B 端）

**推荐路径**：`src/main/java/com/ityfz/yulu/admin/controller/AdminDocumentController.java`  
**鉴权**：沿用你现有的 B 端注解（如 `@RequireRole({Roles.ADMIN, Roles.AGENT})`）；tenantId 从你现有的上下文工具获取（如 `UserContextHolder`）。

### 6.1 上传文件接口

- `POST /api/admin/knowledge/document/upload`
- `Content-Type: multipart/form-data`
- 参数：`file` (MultipartFile, 必填)，`title` (可选)，`source` (可选)

示例代码片段：

```java
@PostMapping("/upload")
public ApiResponse<Long> upload(@RequestPart("file") MultipartFile file,
                                @RequestParam(value = "title", required = false) String title,
                                @RequestParam(value = "source", required = false) String source) {
    Long tenantId = SecurityUtil.getCurrentTenantId(); // 按你项目实际方法替换
    Long docId = documentService.uploadDocument(tenantId, title, file, null, source);
    return ApiResponse.success(docId);
}
```

### 6.2 上传纯文本接口

- `POST /api/admin/knowledge/document/upload-text`
- `Content-Type: application/json`

DTO 示例（放在 `com.ityfz.yulu.admin.dto`）：

```java
@Data
public class DocumentUploadTextRequest {
    private String title;
    private String content;
    private String source;
}
```

Controller 片段：

```java
@PostMapping("/upload-text")
public ApiResponse<Long> uploadText(@RequestBody @Validated DocumentUploadTextRequest req) {
    Long tenantId = SecurityUtil.getCurrentTenantId();
    Long docId = documentService.uploadDocument(tenantId, req.getTitle(), null, req.getContent(), req.getSource());
    return ApiResponse.success(docId);
}
```

### 6.3 文档列表接口

- `GET /api/admin/knowledge/document/list?pageNum=1&pageSize=10`

返回 DTO（`DocumentListItemResponse`）：

```java
@Data
public class DocumentListItemResponse {
    private Long id;
    private String title;
    private String source;
    private String fileType;
    private Long fileSize;
    private Integer status;
    private LocalDateTime createTime;
}
```

Controller 片段：

```java
@GetMapping("/list")
public ApiResponse<List<DocumentListItemResponse>> list(
        @RequestParam(value = "pageNum", required = false) Integer pageNum,
        @RequestParam(value = "pageSize", required = false) Integer pageSize) {
    Long tenantId = SecurityUtil.getCurrentTenantId();
    List<Document> docs = documentService.listDocuments(tenantId, pageNum, pageSize);
    List<DocumentListItemResponse> resp = docs.stream().map(d -> {
        DocumentListItemResponse r = new DocumentListItemResponse();
        r.setId(d.getId());
        r.setTitle(d.getTitle());
        r.setSource(d.getSource());
        r.setFileType(d.getFileType());
        r.setFileSize(d.getFileSize());
        r.setStatus(d.getStatus());
        r.setCreateTime(d.getCreateTime());
        return r;
    }).toList();
    return ApiResponse.success(resp);
}
```

### 6.4 文档详情接口

- `GET /api/admin/knowledge/document/{id}`

返回 DTO（`DocumentDetailResponse`）：

```java
@Data
public class DocumentDetailResponse {
    private Long id;
    private String title;
    private String source;
    private String fileType;
    private Long fileSize;
    private Integer status;
    private LocalDateTime indexedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String contentPreview; // 建议截取前 500~1000 字符
}
```

Controller 片段：

```java
@GetMapping("/{id}")
public ApiResponse<DocumentDetailResponse> detail(@PathVariable("id") Long id) {
    Long tenantId = SecurityUtil.getCurrentTenantId();
    Document d = documentService.getDocument(id, tenantId);
    DocumentDetailResponse r = new DocumentDetailResponse();
    r.setId(d.getId());
    r.setTitle(d.getTitle());
    r.setSource(d.getSource());
    r.setFileType(d.getFileType());
    r.setFileSize(d.getFileSize());
    r.setStatus(d.getStatus());
    r.setIndexedAt(d.getIndexedAt());
    r.setCreateTime(d.getCreateTime());
    r.setUpdateTime(d.getUpdateTime());
    String content = d.getContent();
    if (content != null && content.length() > 1000) {
        r.setContentPreview(content.substring(0, 1000) + "...");
    } else {
        r.setContentPreview(content);
    }
    return ApiResponse.success(r);
}
```

### 6.5 删除接口

- `DELETE /api/admin/knowledge/document/{id}`

Controller 片段：

```java
@DeleteMapping("/{id}")
public ApiResponse<Void> delete(@PathVariable("id") Long id) {
    Long tenantId = SecurityUtil.getCurrentTenantId();
    documentService.deleteDocument(id, tenantId);
    return ApiResponse.success();
}
```

> `deleteDocument` 内部已调用 `chunkService.deleteChunksByDocumentId(id)`，会级联删 chunk。

---

## 7. 测试建议

### 7.1 单测（解析层）

- 新建 `TikaDocumentParserTest`，放少量样例（txt/pdf/docx）到 `src/test/resources`：
  - 读取 bytes → `parse` → 断言非空。
  - 遇到空文件断言抛 `BizException`。

### 7.2 集成测试（接口层）

- 使用 `@SpringBootTest` + `MockMvc`：
  - 先模拟登录获取 token（复用你已有的登录接口）。
  - `multipart` 调用 `/api/admin/knowledge/document/upload`，断言 200 且返回 docId。
  - 调 `/list` 看是否包含。
  - 调 `/detail/{id}` 验证 `contentPreview`。
  - 调 `/delete/{id}`，再查列表确认已删除。

---

## 8. 实施顺序 Checklist（对照执行）

1) `pom.xml` 增加 Tika 依赖，`mvn compile` 验证。  
2) 新建 `TikaDocumentParser`，代码如 §4。  
3) 修改 `DocumentServiceImpl` 构造器 + `parseDocument`，委托 Tika。  
4) 新增 DTO：`DocumentUploadTextRequest`、`DocumentListItemResponse`、`DocumentDetailResponse`。  
5) 新增 `AdminDocumentController`，实现上传文件/文本、列表、详情、删除。  
6) 写基础单测/集成测试验证解析与接口链路。  

完成后，你的“上传 → 解析(Tika) → 入库 → 切 chunk → 管理接口”链路即闭环，可继续下一步“向量化入库（Qdrant）+ RAG 检索接口”。

---

## 9. 后续可拓展

- 解析结果清洗：可在 `TikaDocumentParser` 返回后统一做正则清洗、空行压缩。  
- 大文件切分策略：配置 `rag.chunk.size/overlap/min-size`，已有 `ChunkServiceImpl` 支持。  
- 入库后异步向量化：文档状态 0 → 1/2，可在上传后投递队列，异步调 `EmbeddingService` + `QdrantVectorStore` 完成索引。  
- 控制台观测：在切分/解析处增加关键日志，便于定位文件问题。  

---

以上内容均基于你当前代码目录和已有类名逐一对应，可直接按节落地。若还需我补充“向量化入库与检索接口的实现方案”，告诉我即可。***
















