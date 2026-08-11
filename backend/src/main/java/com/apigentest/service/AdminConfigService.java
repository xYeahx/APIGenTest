package com.apigentest.service;

import com.apigentest.vo.SysConfigVO;

import java.util.List;
import java.util.Map;

public interface AdminConfigService {

    List<SysConfigVO> list();

    void update(String key, String value);

    /** 用当前已保存的 LLM 配置发一条最小请求，验证连通性；成功返回模型回复 */
    Map<String, String> testLlm();
}