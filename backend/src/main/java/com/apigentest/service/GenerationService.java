package com.apigentest.service;

import com.apigentest.vo.ConfirmResultVO;
import com.apigentest.vo.GenerationTaskVO;

import java.util.List;

public interface GenerationService {

    String submit(List<Long> apiIds, String businessDesc);

    GenerationTaskVO get(String taskId);

    ConfirmResultVO confirm(String taskId);
}