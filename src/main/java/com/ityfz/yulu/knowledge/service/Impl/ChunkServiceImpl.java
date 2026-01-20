package com.ityfz.yulu.knowledge.service.Impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ityfz.yulu.knowledge.entity.Chunk;
import com.ityfz.yulu.knowledge.mapper.ChunkMapper;
import com.ityfz.yulu.knowledge.service.ChunkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ChunkServiceImpl implements ChunkService {

    @Autowired
    private ChunkMapper chunkMapper;

    // 从配置文件读取默认值
    @Value("${rag.chunk.size:500}")
    private int defaultChunkSize;

    @Value("${rag.chunk.overlap:50}")
    private int defaultOverlapSize;

    @Value("${rag.chunk.min-size:50}")
    private int minChunkSize;

    @Override
    public List<Chunk> chunkText(String content, int chunkSize, int overlapSize) {

        // 如果内容为空
        if (content == null || content.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // 预处理：统一换行符，清理多余空白
        String normalizedContent = normalizeContent(content);

        List<Chunk> chunks = new ArrayList<>();
        int contentLength = normalizedContent.length();

        // 如果内容长度小于 chunkSize，直接返回一个 Chunk
        if (contentLength <= chunkSize) {
            Chunk chunk = new Chunk();
            chunk.setContent(normalizedContent);
            chunk.setContentLength(normalizedContent.length());
            chunk.setChunkIndex(0);
            chunks.add(chunk);
            return chunks;
        }

        // 切分逻辑  ：  按规定大小切分，然后再细化切分点即不直接切，先找到最佳切分点
        //             切完，下一段chunk再往回倒贴overlapSize长度，保证语义/关键词不会丢失，也方便向量检索时召回相邻信息
        int start = 0;
        int chunkIndex = 0;


        while (start < contentLength) {
            // 计算当前 Chunk 的结束位置
            int end = Math.min(start + chunkSize, contentLength);

            // 如果不是最后一个 Chunk，尝试在句子边界切分
            if (end < contentLength) {
                end = findBestSplitPoint(normalizedContent, start, end);
            }

            // 提取 Chunk 内容
            String chunkContent = normalizedContent.substring(start, end).trim();

            // 如果 Chunk 内容不为空，创建 Chunk
            if (!chunkContent.isEmpty()) {
                Chunk chunk = new Chunk();
                chunk.setContent(chunkContent);
                chunk.setContentLength(chunkContent.length());
                chunk.setChunkIndex(chunkIndex);
                chunk.setCreateTime(LocalDateTime.now());
                chunks.add(chunk);
                chunkIndex++;
            }

            // 计算下一个 Chunk 的起始位置（考虑重叠）
            start = end - overlapSize;
            if (start < 0) {
                start = 0;
            }

            // 如果已经到达末尾，退出循环
            if (end >= contentLength) {
                break;
            }
        }
        // 处理最后一个 Chunk：如果太小，合并到前一个
        if (chunks.size() > 1) {
            Chunk lastChunk = chunks.get(chunks.size() - 1);
            if (lastChunk.getContentLength() < minChunkSize) {
                Chunk prevChunk = chunks.get(chunks.size() - 2);
                prevChunk.setContent(prevChunk.getContent() + "\n" + lastChunk.getContent());
                prevChunk.setContentLength(prevChunk.getContent().length());
                chunks.remove(chunks.size() - 1);
            }
        }

        log.debug("[ChunkService] 切分完成: 原文长度={}, Chunk数量={}", contentLength, chunks.size());
        return chunks;
    }


    @Override
    @Transactional
    public List<Chunk> chunkAndSave(Long documentId, Long tenantId, String content, int chunkSize, int overlapSize) {
        // 1. 切分文档
        List<Chunk> chunks = chunkText(content, chunkSize, overlapSize);

        // 2. 设置文档ID和租户ID，保存到数据库
        for (Chunk chunk : chunks) {
            chunk.setDocumentId(documentId);
            chunk.setTenantId(tenantId);
            chunkMapper.insert(chunk);
        }

        log.info("[ChunkService] 文档切分并保存: documentId={}, chunkCount={}", documentId, chunks.size());
        return chunks;
    }

    @Override
    public List<Chunk> getChunksByDocumentId(Long documentId) {
        return chunkMapper.selectList(
                        Wrappers.<Chunk>lambdaQuery()
                        .eq(Chunk::getDocumentId, documentId)
                        .orderByAsc(Chunk::getChunkIndex)
        );
    }

    @Override
    @Transactional
    public void deleteChunksByDocumentId(Long documentId) {
        chunkMapper.delete(
                        Wrappers.<Chunk>lambdaQuery()
                        .eq(Chunk::getDocumentId, documentId)
        );
        log.info("[ChunkService] 删除文档的所有 Chunk: documentId={}", documentId);

    }


    // 规范化内容：统一换行符，清理多余空白
    private String normalizeContent(String content) {
        if (content == null) {
            return "";
        }
        // 统一换行符为 \n
        String normalized = content.replaceAll("\r\n", "\n").replaceAll("\r", "\n");

        // 清理连续的空行（保留单个换行）
        normalized = normalized.replaceAll("\n{3,}", "\n\n");
        return normalized.trim();
    }


    /**
     * 寻找最佳切分点（尽量在句子边界）
     */
    private int findBestSplitPoint(String content, int start, int end) {
        // 在 end 位置向前查找，寻找句子边界（句号、问号、感叹号、换行符）
        int searchStart = Math.max(start, end - 100); // 最多向前查找 100 个字符

        for (int i = end - 1; i >= searchStart; i--) {
            char c = content.charAt(i);
            // 如果遇到句子结束符，在此处切分
            if (c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?') {
                return i + 1;
            }

            // 如果遇到换行符，也可以在此处切分
            if (c == '\n') {
                return i + 1;
            }
        }

        // 如果没找到合适的切分点，返回原始 end
        return end;
    }
}
