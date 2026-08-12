package com.apigentest.service;

import com.apigentest.vo.ApiCoverageVO;
import com.apigentest.vo.ApiInfoVO;
import com.apigentest.vo.ImportResultVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ApiService {

    ImportResultVO importFromFile(Long projectId, MultipartFile file);

    ImportResultVO importFromUrl(Long projectId, String url);

    Page<ApiInfoVO> listApis(Long projectId, long page, long size, String keyword, String tag);

    ApiInfoVO getApiDetail(Long apiId);

    /** 接口覆盖率统计（已生成用例的接口数 / 接口总数，按 tag 分组） */
    ApiCoverageVO coverage(Long projectId);

    void batchDelete(List<Long> ids);
}