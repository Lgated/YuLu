package com.ityfz.yulu.common.ai.impl;


import com.ityfz.yulu.knowledge.entity.Chunk;
import com.ityfz.yulu.knowledge.service.ChunkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ChunkServiceTest {

    @MockBean
    private QdrantVectorStore qdrantVectorStore; // 屏蔽真实连接

    @Autowired
    private ChunkService chunkService;

    @Test
    void testChunkText_ShortContent() {
        String content = "这是一个短文本";
        List<Chunk> chunks = chunkService.chunkText(content, 500, 50);

        assertEquals(1, chunks.size());
        assertEquals(content, chunks.get(0).getContent());
    }

    @Test
    void testChunkText_LongContent() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("这是第").append(i).append("段内容。");
        }
        String content = sb.toString();

        List<Chunk> chunks = chunkService.chunkText(content, 50, 10);

        assertTrue(chunks.size() > 1);
        // 验证每个 Chunk 的长度不超过 chunkSize
        for (Chunk chunk : chunks) {
            assertTrue(chunk.getContentLength() <= 50);
        }
    }

    @Test
    void testChunkText_Overlap() {
        String content = "第一段内容。第二段内容。第三段内容。";
        List<Chunk> chunks = chunkService.chunkText(content, 20, 5);

        // 验证有重叠
        if (chunks.size() > 1) {
            String firstChunk = chunks.get(0).getContent();
            String secondChunk = chunks.get(1).getContent();
            // 第二个 Chunk 应该包含第一个 Chunk 的结尾部分
            assertTrue(secondChunk.contains(firstChunk.substring(firstChunk.length() - 5)));
        }
    }
}