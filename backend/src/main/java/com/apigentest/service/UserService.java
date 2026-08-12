package com.apigentest.service;

import com.apigentest.dto.LoginDTO;
import com.apigentest.dto.ProfileDTO;
import org.springframework.web.multipart.MultipartFile;
import com.apigentest.dto.RegisterDTO;
import com.apigentest.vo.LoginVO;
import com.apigentest.vo.UserVO;

public interface UserService {

    void register(RegisterDTO dto);

    LoginVO login(LoginDTO dto);

    UserVO getCurrentUser();

    /** 更新个人资料（昵称/邮箱/联系方式） */
    void updateProfile(ProfileDTO dto);

    /** 上传头像，返回可访问的 URL */
    String uploadAvatar(MultipartFile file);
}