package com.investment.service.risk;

import com.investment.model.RiskGuardResult;
import com.investment.enums.Intent;
import com.investment.model.RecommendResult;
import com.investment.model.ResponseResult;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * 金融合规与适当性风险守卫。
 * 在 Orchestrator#completeRecommendation 中，LLM 生成回复后做最后一道合规检查。
 */
@Component
public class RiskGuardService {

    /**
     * 检查用户输入 + 最终回复是否含收益承诺、风险弱化或替代人工投顾判断等表述。
     * 命中任一规则返回 block，Orchestrator 用 conservativeMessage 替换 speechText。
     */
    public RiskGuardResult check(String userInput, Intent intent, RecommendResult recommendResult, ResponseResult responseResult) {
        List<String> reasons = new ArrayList<>();
        // 拼接用户原文和助手回复，统一扫描关键词
        String allText = (userInput == null ? "" : userInput) + " " + (responseResult == null ? "" : responseResult.speechText());
        if (intent == Intent.RISK_COMPLIANCE) {
            reasons.add("命中 RISK_COMPLIANCE 意图");
        }
        if (containsAny(allText, "保证收益", "一定赚钱", "稳赚", "包赚", "保本高收益")) {
            reasons.add("涉及收益承诺或保本高收益表达");
        }
        if (containsAny(allText, "零风险", "没有风险", "不会亏", "一定不能亏")) {
            reasons.add("涉及风险弱化或绝对化安全表达");
        }
        if (containsAny(allText, "替代投顾", "自动下单", "直接买", "不用看说明书", "不用风险测评")) {
            reasons.add("涉及替代人工投顾或忽略适当性流程");
        }
        if (containsAny(allText, "超出风险承受能力", "风险测评不够也可以买")) {
            reasons.add("涉及诱导购买超风险承受能力产品");
        }
        // 无命中规则 → 通过
        if (reasons.isEmpty()) {
            return RiskGuardResult.pass();
        }
        // 有命中 → 拦截，返回 reasons 和 conservativeMessage 作为 rewriteSuggestion
        return RiskGuardResult.block(reasons, conservativeMessage());
    }

    /** 高风险场景的保守固定提示文案。 */
    public String conservativeMessage() {
        return "该内容仅用于理财经理内部辅助参考，不能替代人工投顾判断或客户风险测评。具体推荐需结合客户风险承受能力、产品说明书、风险揭示和银行适当性要求，由具备资质的人员确认后再向客户展示。";
    }

    /** 判断 text 是否包含 keywords 中任一子串。 */
    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}


