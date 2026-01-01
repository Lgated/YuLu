package com.ityfz.yulu.common.security;

import com.ityfz.yulu.common.exception.BizException;
import com.ityfz.yulu.common.tenant.TenantContextHolder;
import com.ityfz.yulu.common.tenant.UserContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * JWT 认证过滤器。
 * 从请求头中提取 Token，解析后设置租户上下文和用户上下文。
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String TOKEN_PREFIX = "Bearer ";
    private static final String HEADER_NAME = "Authorization";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try{
            //1、从请求头获取token
            String token = extractToken(request);
            //2、如果存在token，解析并设置上下文
            if(StringUtils.hasText(token)){
                try{
                    JwtUtil.LoginUser loginUser = JwtUtil.parseToken(token);
                    //3.设置租户上下文
                    if(loginUser.getTenantId() != null){
                        TenantContextHolder.setTenantId(loginUser.getTenantId());
                        logger.debug("设置租户上下文: tenantId=" + loginUser.getTenantId());
                    } else {
                        logger.warn("Token 解析成功但 tenantId 为 null");
                    }
                    //4.设置用户上下文
                    UserContextHolder.setUser(loginUser);
                    logger.debug("设置用户上下文: userId=" + loginUser.getUserId() + ", username=" + loginUser.getUsername());
                }catch (BizException e){
                    // Token 无效，但不阻止请求继续（可能是公开接口）
                    // 如果需要强制认证，可以在这里返回401
                    logger.warn("Token 解析失败: " + e.getMessage());
                } catch (Exception e) {
                    logger.warn("Token 解析异常: " + e.getMessage(), e);
                }
            } else {
                logger.debug("请求头中未找到 Token: " + request.getRequestURI());
            }
            // 5. 继续过滤器链
            //"我这里的事儿干完了，把请求/响应对象交给下一个过滤器
            //（或者最终进入 Servlet、进入 SpringMVC的DispatcherServlet），让整条链子继续往下走。"
            //如果不写这句，请求就卡死在你这个过滤器里，客户端永远收不到响应，页面会一直转圈。
            filterChain.doFilter(request, response);
        }finally {
            // 6. 请求结束后清除上下文（防止内存泄漏）
            TenantContextHolder.clear();
            UserContextHolder.clear();
        }
    }

    /**
     * 从请求头中提取 Token
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(HEADER_NAME);
        //StringUtils.hasText() : Spring的工具，判断不是 null、不是空串、不只含空白字符
        //如果两者都满足，就把 "Bearer " 这7个字符切掉，剩下的就是纯 JWT 串，直接返回。
        //否则返回 null，表示“没拿到合法令牌”。
        if (!StringUtils.hasText(bearerToken)) {
            return null;
        }
        // 去除首尾空格
        bearerToken = bearerToken.trim();

        // 支持大小写不敏感匹配 "Bearer " 前缀
        String prefixLower = TOKEN_PREFIX.trim().toLowerCase();
        String bearerTokenLower = bearerToken.toLowerCase();

        if (bearerTokenLower.startsWith(prefixLower)) {
            // 提取 token（去掉 "Bearer " 前缀）
            // 使用原始字符串的索引，但前缀长度是固定的
            String token = bearerToken.substring(prefixLower.length()).trim();
            
            // 去除可能的 {} 包裹（Postman 有时会这样显示）
            if (token.startsWith("{") && token.endsWith("}")) {
                token = token.substring(1, token.length() - 1).trim();
                logger.debug("检测到 {} 包裹，已去除");
            }
            
            if (StringUtils.hasText(token)) {
                logger.debug("成功提取 Token，长度: " + token.length() + "，前30字符: " + token.substring(0, Math.min(30, token.length())));
                return token;
            }
        }

        logger.debug("Token 格式不正确: " + bearerToken.substring(0, Math.min(20, bearerToken.length())) + "...");
        return null;
    }
}
