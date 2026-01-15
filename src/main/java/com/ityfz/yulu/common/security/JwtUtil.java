package com.ityfz.yulu.common.security;

import cn.hutool.jwt.JWTPayload;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.ityfz.yulu.common.enums.ErrorCodes;
import com.ityfz.yulu.common.exception.BizException;
import lombok.Data;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类：负责 LoginUser -> Token 的生成与解析。
 */
public class JwtUtil {

    // 为了简单演示，先写死；后续可以通过配置注入
    private static final String SECRET = "demo-secret-key";
    private static final long EXPIRE_HOURS = 12L;

    private JwtUtil() {
    }

    /**
     * 登录用户信息，会被写入 JWT 中。
     */
    @Data
    public static class LoginUser {
        private Long userId;
        private Long tenantId;
        private String username;
        private String role;
    }

    /**
     * 生成 JWT Token。
     */
    public static String generateToken(LoginUser user) {
        Map<String, Object> payload = new HashMap<>();
        long nowMillis = System.currentTimeMillis();
        payload.put(JWTPayload.ISSUED_AT, nowMillis);
        payload.put(JWTPayload.NOT_BEFORE, nowMillis);
        payload.put(JWTPayload.EXPIRES_AT, nowMillis + Duration.ofHours(EXPIRE_HOURS).toMillis());
        payload.put("userId", user.getUserId());
        payload.put("tenantId", user.getTenantId());
        payload.put("role", user.getRole());
        payload.put("username", user.getUsername());
        return JWTUtil.createToken(payload, JWTSignerUtil.hs256(SECRET.getBytes()));
    }

    /**
     * 解析并验证 JWT Token，成功则还原为 LoginUser。
     */
    public static LoginUser parseToken(String token) {
        try {
            //用封装好的解析token工具
            JWT jwt = JWTUtil.parseToken(token);
            boolean verified = jwt.verify(JWTSignerUtil.hs256(SECRET.getBytes()));
            if (!verified) {
                throw new BizException(ErrorCodes.TOKEN_INVALID, "Token 验证失败：签名不匹配");
            }
            JWTPayload payload = jwt.getPayload();
            LoginUser user = new LoginUser();
            Object userId = payload.getClaim("userId");
            Object tenantId = payload.getClaim("tenantId");
            user.setUserId(userId == null ? null : Long.valueOf(userId.toString()));
            user.setTenantId(tenantId == null ? null : Long.valueOf(tenantId.toString()));
            user.setRole((String) payload.getClaim("role"));
            user.setUsername((String) payload.getClaim("username"));
            return user;
        } catch (BizException e) {
            // 重新抛出业务异常
            throw e;
        } catch (Exception e) {
            // 记录原始异常信息，便于排查
            String errorMsg = "Token 不合法或已过期: " + e.getClass().getSimpleName() + " - " + e.getMessage();
            throw new BizException(ErrorCodes.TOKEN_INVALID, errorMsg);
        }
    }
}

