package com.apigentest.controller;

import com.apigentest.common.Result;
import com.apigentest.dto.ProfileDTO;
import com.apigentest.service.UserService;
import com.apigentest.vo.UserVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 个人资料：查看/更新资料、上传头像
 */
@RestController
@RequestMapping("/api/user")
public class UserProfileController {

    private final UserService userService;

    public UserProfileController(UserService userService) {
        this.userService = userService;
    }

    /** 当前用户资料 */
    @GetMapping("/profile")
    public Result<UserVO> profile() {
        return Result.ok(userService.getCurrentUser());
    }

    /** 更新昵称 / 邮箱 / 联系方式 */
    @PutMapping("/profile")
    public Result<Void> update(@Valid @RequestBody ProfileDTO dto) {
        userService.updateProfile(dto);
        return Result.ok();
    }

    /** 上传头像（multipart，字段名 file） */
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Result.ok(userService.uploadAvatar(file));
    }
}