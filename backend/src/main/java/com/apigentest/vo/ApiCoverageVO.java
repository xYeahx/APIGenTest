package com.apigentest.vo;

import lombok.Data;

import java.util.List;

/**
 * 接口覆盖率统计：已生成用例的接口数 / 接口总数，按 tag 分组
 */
@Data
public class ApiCoverageVO {

    /** 接口总数 */
    private long totalApis;

    /** 已生成用例的接口数 */
    private long coveredApis;

    /** 覆盖率（0-100，保留一位小数） */
    private double rate;

    /** 按 tag 分组统计 */
    private List<TagCoverage> byTag;

    @Data
    public static class TagCoverage {
        private String tag;
        private long total;
        private long covered;
        private double rate;
    }
}