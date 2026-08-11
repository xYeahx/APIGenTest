package com.apigentest.config;

import com.apigentest.common.BusinessException;
import com.apigentest.common.JwtUtil;
import com.apigentest.common.UserContext;
import com.apigentest.entity.User;
import com.apigentest.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截器：校验 Bearer Token，并注入当前用户上下文
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    public AuthInterceptor(JwtUtil jwtUtil, UserMapper userMapper) {
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw new BusinessException(401, "未登录或登录已过期");
        }
        try {
            Claims claims = jwtUtil.parse(auth.substring(7));
            Long userId = Long.valueOf(claims.getSubject());
            User user = userMapper.selectById(userId);
            if (user == null) {
                throw new BusinessException(401, "用户不存在");
            }
            if (user.getStatus() != null && user.getStatus() == 0) {
                throw new BusinessException(403, "账号已被禁用");
            }
            UserContext.set(user.getId(), user.getUsername(), user.getRole());
            return true;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(401, "登录状态无效，请重新登录");
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}