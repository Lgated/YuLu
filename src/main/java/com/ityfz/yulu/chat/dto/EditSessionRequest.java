package com.ityfz.yulu.chat.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 编辑会话请求
 * <p>
 * 注意：Jackson 反序列化需要无参构造器，因此这里使用 @NoArgsConstructor，
 * 不再使用 Lombok 的 @NonNull 构造方式，字段必填通过 @NotNull/@NotBlank 校验。
 */
@Data
@NoArgsConstructor
public class EditSessionRequest {

    /** 会话ID */
    @NotNull(message = "会话ID不能为空")
    private Long id;

    /** 新的会话标题 */
    @NotBlank(message = "会话标题不能为空")
    private String newTitle;
}
