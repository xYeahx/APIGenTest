package com.apigentest.service.llm;

import org.springframework.stereotype.Component;

/**
 * 用例生成 Prompt 构建：注入接口定义 + 业务描述，约束严格 JSON 输出
 */
@Component
public class LlmPromptBuilder {

    private static final int SPEC_MAX_LENGTH = 8000;

    /** Prompt 版本号（P2-2 埋点，修改 Prompt 时递增） */
    public static final String PROMPT_VERSION = "v1";

    public static final String SYSTEM_PROMPT = """
            你是接口自动化测试专家。根据给定的 OpenAPI 接口定义，为接口生成可执行的接口测试用例。
            要求：
            1. 覆盖正常（normal）、边界（boundary，如空值/超长/非法类型）、异常（exception，如错误参数/错误 Token）三类场景，每类至少 1 条；
            2. urlTemplate 使用 {{baseUrl}} 作为环境前缀，引用变量使用 {{env:变量名}} 语法；
            3. asserts 支持 type=statusCode（expect 为数字状态码）、type=field（path 为 JSONPath，condition 支持 notEmpty/equal）;
            4. extractVars 用于从响应提取变量（from=response，expr 为 JSONPath，varName 为变量名），登录类接口必须提取 token;
            5. 必须只输出一个 JSON 对象，禁止输出 markdown 代码块、注释或任何解释文字。
            输出格式（严格）：
            {"results":[{"apiId":<接口ID数字>,"cases":[{"name":"用例名","scenarioType":"normal|boundary|exception","method":"POST","urlTemplate":"{{baseUrl}}/api/xxx","headers":{...},"queryParams":{...},"body":{...},"asserts":[{"type":"statusCode","expect":200}],"extractVars":[{"from":"response","expr":"$.data.token","varName":"token"}]}]}]}
            """;

    public String buildUserContent(Long apiId, String apiSummary, String businessDesc, String specJson) {
        StringBuilder sb = new StringBuilder();
        sb.append("接口 ID：").append(apiId).append("\n");
        sb.append("接口名称：").append(apiSummary == null ? "" : apiSummary).append("\n");
        if (businessDesc != null && !businessDesc.isBlank()) {
            sb.append("业务背景：").append(businessDesc).append("\n");
        }
        sb.append("接口 OpenAPI 定义（JSON）：\n");
        sb.append(truncate(specJson));
        return sb.toString();
    }

    private String truncate(String spec) {
        if (spec == null) {
            return "";
        }
        return spec.length() <= SPEC_MAX_LENGTH ? spec : spec.substring(0, SPEC_MAX_LENGTH) + "\n...（定义过长已截断）";
    }
}