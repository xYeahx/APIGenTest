package com.apigentest.service;

import com.apigentest.dto.CaseDTO;
import com.apigentest.dto.CaseQuery;
import com.apigentest.vo.CaseVO;
import com.apigentest.vo.ImportResultVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface TestCaseService {

    Page<CaseVO> list(Long projectId, CaseQuery query, long page, long size);

    CaseVO getDetail(Long id);

    CaseVO create(CaseDTO dto);

    CaseVO update(Long id, CaseDTO dto);

    void delete(Long id);

    void batchStatus(List<Long> ids, Integer status);

    void batchDelete(List<Long> ids);

    /** 导出用例：format = json / postman / openapi */
    ExportFile exportCases(Long projectId, String format, List<Long> caseIds);

    /** 从 Postman Collection / 平台 JSON 导出文件导入用例 */
    ImportResultVO importCases(Long projectId, MultipartFile file);

    /** 导出可独立运行的 pytest + requests 脚本 */
    String exportPytest(Long projectId, Long environmentId, List<Long> caseIds);

    /** 导出文件载体 */
    class ExportFile {
        public final String filename;
        public final byte[] content;

        public ExportFile(String filename, byte[] content) {
            this.filename = filename;
            this.content = content;
        }
    }
}