package com.ityfz.yulu.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger 配置（springdoc-openapi）
 *
 * 访问：
 * - Swagger UI: /swagger-ui.html
 * - OpenAPI JSON: /v3/api-docs
 *
 * 分组策略：
 * - admin-auth/admin-dashboard/admin-ticket/admin-session/admin-user/admin-user-management
 * - customer-auth/customer-chat/customer-faq
 * - knowledge-document/knowledge-search/knowledge-index/knowledge-rag
 * - notify
 */
@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI yuluOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("YuLu 多租户智能客服中台 - API 文档")
                        .description("包含：租户端（ADMIN）、客服端（AGENT）、客户端（USER）接口；支持多租户隔离、RAG 知识库、工单与通知。")
                        .version("v1")
                        .contact(new Contact().name("YuLu").url("https://localhost")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME,
                        new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                ));
    }

    // -------- Admin groups --------
    @Bean
    public GroupedOpenApi adminAuthGroup() {
        return GroupedOpenApi.builder()
                .group("admin-auth")
                .pathsToMatch("/api/admin/auth/**")
                .build();
    }

    @Bean
    public GroupedOpenApi adminDashboardGroup() {
        return GroupedOpenApi.builder()
                .group("admin-dashboard")
                .pathsToMatch("/api/admin/dashboard/**")
                .build();
    }

    @Bean
    public GroupedOpenApi adminTicketGroup() {
        return GroupedOpenApi.builder()
                .group("admin-ticket")
                .pathsToMatch("/api/admin/ticket/**")
                .build();
    }

    @Bean
    public GroupedOpenApi adminSessionGroup() {
        return GroupedOpenApi.builder()
                .group("admin-session")
                .pathsToMatch("/api/admin/session/**")
                .build();
    }

    @Bean
    public GroupedOpenApi adminUserGroup() {
        return GroupedOpenApi.builder()
                .group("admin-user")
                .pathsToMatch("/api/admin/user/**")
                .build();
    }

    @Bean
    public GroupedOpenApi adminUserManagementGroup() {
        return GroupedOpenApi.builder()
                .group("admin-user-management")
                .pathsToMatch("/api/admin/user-management/**")
                .build();
    }

    // -------- Customer groups --------
    @Bean
    public GroupedOpenApi customerAuthGroup() {
        return GroupedOpenApi.builder()
                .group("customer-auth")
                .pathsToMatch("/api/customer/auth/**")
                .build();
    }

    @Bean
    public GroupedOpenApi customerChatGroup() {
        return GroupedOpenApi.builder()
                .group("customer-chat")
                .pathsToMatch("/api/customer/chat/**")
                .build();
    }

    @Bean
    public GroupedOpenApi customerFaqGroup() {
        return GroupedOpenApi.builder()
                .group("customer-faq")
                .pathsToMatch("/api/customer/faq/**")
                .build();
    }

    // -------- Knowledge groups --------
    @Bean
    public GroupedOpenApi knowledgeDocumentGroup() {
        return GroupedOpenApi.builder()
                .group("knowledge-document")
                .pathsToMatch("/api/admin/document/**")
                .build();
    }

    @Bean
    public GroupedOpenApi knowledgeSearchGroup() {
        return GroupedOpenApi.builder()
                .group("knowledge-search")
                .pathsToMatch("/api/admin/knowledge/search/**")
                .build();
    }

    @Bean
    public GroupedOpenApi knowledgeIndexGroup() {
        return GroupedOpenApi.builder()
                .group("knowledge-index")
                .pathsToMatch("/api/admin/knowledge/document/**")
                .build();
    }

    @Bean
    public GroupedOpenApi knowledgeRagGroup() {
        return GroupedOpenApi.builder()
                .group("knowledge-rag")
                .pathsToMatch("/api/admin/knowledge/chat/**")
                .build();
    }

    // -------- Notify group --------
    @Bean
    public GroupedOpenApi notifyGroup() {
        return GroupedOpenApi.builder()
                .group("notify")
                .pathsToMatch("/api/notify/**")
                .build();
    }
}


