package com.apigentest.service;

import com.apigentest.dto.CaseDTO;
import com.apigentest.dto.CaseQuery;
import com.apigentest.vo.CaseVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

public interface TestCaseService {

    Page<CaseVO> list(Long projectId, CaseQuery query, long page, long size);

    CaseVO getDetail(Long id);

    CaseVO create(CaseDTO dto);

    CaseVO update(Long id, CaseDTO dto);

    void delete(Long id);

    void batchStatus(List<Long> ids, Integer status);

    void batchDelete(List<Long> ids);
}