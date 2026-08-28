package com.apigentest.service.impl;

import com.apigentest.common.BusinessException;
import com.apigentest.common.ErrorCode;
import com.apigentest.common.Roles;
import com.apigentest.common.JwtUtil;
import com.apigentest.common.UserContext;
import com.apigentest.dto.LoginDTO;
import com.apigentest.dto.ProfileDTO;
import com.apigentest.dto.RegisterDTO;
import com.apigentest.entity.SysConfig;
import com.apigentest.entity.User;
import com.apigentest.mapper.SysConfigMapper;
import com.apigentest.mapper.UserMapper;
import com.apigentest.service.UserService;
import com.apigentest.vo.LoginVO;
import com.apigentest.vo.UserVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {

    private static final long MAX_AVATAR_SIZE = 2 * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private final UserMapper userMapper;
    private final SysConfigMapper sysConfigMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    public UserServiceImpl(UserMapper userMapper, SysConfigMapper sysConfigMapper,
                            BCryptPasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.sysConfigMapper = sysConfigMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void register(RegisterDTO dto) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "用户名已存在");
        }
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname() == null || dto.getNickname().isBlank()
                ? dto.getUsername() : dto.getNickname());
        user.setRole(resolveRegisterRole(dto.getInviteCode()));
        user.setStatus(1);
        userMapper.insert(user);
    }

    /**
     * 注册角色判定：填写且匹配系统配置中的超级管理员注册码则注册为超级管理员，否则普通用户。
     * 填了注册码但未配置 / 不匹配时拒绝注册，避免暴破试探。
     */
    private int resolveRegisterRole(String inviteCode) {
        if (inviteCode == null || inviteCode.isBlank()) {
            return Roles.USER;
        }
        SysConfig config = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, "super_admin_invite_code"));
        String expected = config == null ? null : config.getConfigValue();
        if (expected == null || expected.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "系统暂未开放超级管理员注册");
        }
        if (!constantTimeEquals(expected.trim(), inviteCode.trim())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "注册码不正确");
        }
        return Roles.SUPER_ADMIN;
    }

    /** 常量时间比较，避免时序攻击 */
    private boolean constantTimeEquals(String a, String b) {
        try {
            return MessageDigest.isEqual(
                    a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "账号已被禁用，请联系管理员");
        }
        LoginVO vo = new LoginVO();
        vo.setToken(jwtUtil.generateToken(user.getId(), user.getUsername()));
        vo.setUser(toVO(user));
        return vo;
    }

    @Override
    public UserVO getCurrentUser() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在");
        }
        return toVO(user);
    }

    @Override
    public void updateProfile(ProfileDTO dto) {
        User user = requireCurrentUser();
        if (dto.getNickname() != null) {
            user.setNickname(dto.getNickname().isBlank() ? user.getUsername() : dto.getNickname().trim());
        }
        user.setEmail(dto.getEmail() == null || dto.getEmail().isBlank() ? null : dto.getEmail().trim());
        user.setPhone(dto.getPhone() == null || dto.getPhone().isBlank() ? null : dto.getPhone().trim());
        userMapper.updateById(user);
    }

    @Override
    public String uploadAvatar(MultipartFile file) {
        User user = requireCurrentUser();
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请选择图片文件");
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "头像大小不能超过 2MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "仅支持 jpg / png / webp / gif 图片");
        }
        String ext = switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "jpg";
        };
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.IO_ERROR, "图片读取失败");
        }
        if (!matchMagic(data, ext)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "文件内容不是有效的图片");
        }
        try {
            String sub = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            Path dir = Paths.get(uploadDir, sub);
            Files.createDirectories(dir);
            String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            Files.write(dir.resolve(filename), data);
            String url = "/uploads/" + sub + "/" + filename;
            user.setAvatarUrl(url);
            userMapper.updateById(user);
            return url;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.IO_ERROR, "头像保存失败：" + e.getMessage());
        }
    }

    private boolean matchMagic(byte[] head, String ext) {
        if (head.length < 4) {
            return false;
        }
        return switch (ext) {
            case "jpg" -> (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
            case "png" -> (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G';
            case "gif" -> head[0] == 'G' && head[1] == 'I' && head[2] == 'F' && head[3] == '8';
            case "webp" -> head.length >= 12
                    && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                    && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P';
            default -> false;
        };
    }

    private User requireCurrentUser() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在");
        }
        return user;
    }

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setRole(user.getRole());
        vo.setCreatedAt(user.getCreatedAt());
        vo.setUpdatedAt(user.getUpdatedAt());
        return vo;
    }
}