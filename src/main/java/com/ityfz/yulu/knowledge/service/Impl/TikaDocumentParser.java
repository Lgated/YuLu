package com.ityfz.yulu.knowledge.service.Impl;


import com.ityfz.yulu.common.enums.ErrorCodes;
import com.ityfz.yulu.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.springframework.stereotype.Service;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Service
@Slf4j
public class TikaDocumentParser {

    public String parse(byte[] fileBytes, String filenameOrExt) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new BizException(ErrorCodes.VALIDATION_ERROR, "文件内容为空");
        }
        // 创建 Tika 三大核心对象
        AutoDetectParser parser = new AutoDetectParser(); // 万能解析器，根据文件魔数自动挑底层实现（PDFBox、POI...）
        BodyContentHandler handler = new BodyContentHandler(-1); // -1 不截断 role: 只拿正文，过滤样式、页眉页脚；-1 表示不限长度（防止大文件被截断）
        Metadata metadata = new Metadata(); // 存元数据（标题、页数、作者...），这里只用来传文件名帮助解析器选型
        if (filenameOrExt != null) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filenameOrExt); //传文件名帮助解析器选型
        }
        ParseContext context = new ParseContext();

        try (InputStream is = new ByteArrayInputStream(fileBytes)) {
            parser.parse(is, handler, metadata, context);
            String text = handler.toString(); //取完正文在 handler 里，转 String 即可
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
