package com.ityfz.yulu.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.ityfz.yulu.common.tenant.TenantContextHolder;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;



/**
 * MyBatis Plus 配置类（分页插件 + 多租户插件）
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 需要自动添加 tenant_id 条件的表名列表
     */
    private static final List<String> TENANT_TABLES = Arrays.asList(
            "user",
            "chat_session",
            "chat_message"
            // 可以根据需要添加其他表
    );

    /**
     * MyBatis Plus 拦截器（分页 + 多租户）
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 1. 多租户插件（必须在分页插件之前）
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler()));

        // 2. 分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));

        return interceptor;
    }

    /**
     * 多租户处理器
     */
    public static class TenantLineHandler implements com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler {

        /**
         * 获取租户ID（从 ThreadLocal 中读取）
         */
        @Override
        public Expression getTenantId() {
            Long tenantId = TenantContextHolder.getTenantId();
            if (tenantId == null) {
                // 如果没有租户上下文，返回一个默认值或抛出异常
                // 这里返回 null，表示不添加租户条件（适用于系统级查询）
                return null;
            }
            return new LongValue(tenantId);
        }

        /**
         * 获取租户字段名
         */
        @Override
        public String getTenantIdColumn() {
            return "tenant_id";
        }

        /**
         * 判断表是否需要多租户隔离
         */
        @Override
        public boolean ignoreTable(String tableName) {
            // 返回 true 表示忽略该表（不添加租户条件）
            // 返回 false 表示需要添加租户条件

            // tenant 表本身不需要租户隔离
            if ("tenant".equalsIgnoreCase(tableName)) {
                return true;
            }

            // 检查是否在需要租户隔离的表列表中
            return !TENANT_TABLES.contains(tableName.toLowerCase());
        }
    }
}

