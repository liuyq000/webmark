package com.cloud.self.webmark.middleware;

import com.cloud.self.webmark.entity.User;
import com.cloud.self.webmark.security.JwtUtil;
import com.cloud.self.webmark.service.UserService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.util.Map;

public class JwtAuthMiddleware {

    public static void handle(Context ctx, JwtUtil jwtUtil, UserService userService) {
        String authHeader = ctx.header("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            ctx.json(Map.of("success", false, "message", "未登录或会话已过期"));
            return;
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.validateToken(token) || !"access".equals(jwtUtil.getTokenType(token))) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            ctx.json(Map.of("success", false, "message", "Token 无效或已过期"));
            return;
        }

        String username = jwtUtil.getUsername(token);
        User user = userService.findByUserName(username);
        if (user == null) {
            ctx.status(HttpStatus.UNAUTHORIZED);
            ctx.json(Map.of("success", false, "message", "用户不存在"));
            return;
        }

        ctx.attribute("user", user);
        ctx.attribute("username", username);
        ctx.attribute("role", user.getRole());
    }
}
