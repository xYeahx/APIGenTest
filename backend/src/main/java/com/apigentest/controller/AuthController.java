package com.apigentest.controller;

import com.apigentest.common.Result;
import com.apigentest.dto.LoginDTO;
import com.apigentest.dto.RegisterDTO;
import com.apigentest.service.UserService;
import com.apigentest.vo.LoginVO;
import com.apigentest.vo.UserVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证模块：注册 / 登录 / 当前用户 / 退出
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO dto) {
        userService.register(dto);
        return Result.ok();
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.ok(userService.login(dto));
    }

    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(userService.getCurrentUser());
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        // 无状态 JWT：前端清除本地 token 即可，此处仅作约定占位
        return Result.ok();
    }
}