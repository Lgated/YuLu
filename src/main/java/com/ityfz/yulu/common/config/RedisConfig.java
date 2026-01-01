package com.ityfz.yulu.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> tpl = new RedisTemplate<>();
        tpl.setConnectionFactory(factory);

        // Key 序列化
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        tpl.setKeySerializer(stringSerializer);
        tpl.setHashKeySerializer(stringSerializer);

        // Value 可根据需要换成 Jackson 序列化
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        tpl.setValueSerializer(jsonSerializer);
        tpl.setHashValueSerializer(jsonSerializer);

        tpl.afterPropertiesSet();
        return tpl;
    }

    // 仅用于 String 类型数据操作的 RedisTemplate
    //如只想存 JSON 字符串，可直接注入 StringRedisTemplate。
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
