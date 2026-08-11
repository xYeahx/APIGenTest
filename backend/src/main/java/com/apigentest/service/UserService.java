package com.apigentest.service;

import com.apigentest.dto.LoginDTO;
import com.apigentest.dto.RegisterDTO;
import com.apigentest.vo.LoginVO;
import com.apigentest.vo.UserVO;

public interface UserService {

    void register(RegisterDTO dto);

    LoginVO login(LoginDTO dto);

    UserVO getCurrentUser();
}