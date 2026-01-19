package com.ityfz.yulu.common.ai;


import java.util.List;

/**
 * Embedding 服务接口
 * 用于将文本转换为向量
 */
public interface EmbeddingService {

    /**
     * 将单个文本转换为向量
     *
     * @param text 输入文本
     * @return 向量（Float 列表）
     */
    List<Float> embed(String text);

    /**
     * 批量将文本转换为向量
     *
     * @param texts 输入文本列表
     * @return 向量列表
     */
    List<List<Float>> embedBatch(List<String> texts);

    /**
     * 获取向量维度
     *
     * @return 向量维度
     */
    int getDimension();

}
