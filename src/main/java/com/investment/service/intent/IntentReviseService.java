package com.investment.service.intent;

import com.investment.enums.Intent;
import com.investment.model.IntentResult;
import com.investment.model.SessionState;
import com.investment.model.SlotBundle;
import org.springframework.stereotype.Service;

/**
 * 意图后处理服务。
 * LLM 意图识别可能误判，Orchestrator 在路由前用历史 SessionState 做二次矫正。
 */
@Service
public class IntentReviseService {

    /** 低于该阈值时，推荐意图降级为澄清，避免低确定性结果直接进入推荐。 */
    private static final double LOW_CONFIDENCE_THRESHOLD = 0.4;

    /**
     * 根据会话状态矫正 IntentAgent 输出。
     * 由 Orchestrator#handleTurn 在 INTENT_RECOGNIZED 之后调用。
     */
    public IntentResult revise(SessionState state, IntentResult result, String userInput) {
        // result 为 null 时构造 CLARIFY_NEEDED + 空槽位，防止 NPE
        IntentResult safeResult = result == null ? IntentResult.clarify(SlotBundle.empty()) : result;

        if (safeResult.intent() == Intent.RISK_COMPLIANCE || containsComplianceRiskKeyword(userInput)) {
            return new IntentResult(Intent.RISK_COMPLIANCE, safeSlots(safeResult), safeResult.confidence());
        }

        if (safeResult.intent() == Intent.INVESTMENT_ADJUST && !hasLastRecommendations(state)) {
            return new IntentResult(Intent.INVESTMENT_RECOMMENDATION, safeSlots(safeResult), safeResult.confidence());
        }

        if (containsPortfolioKeyword(userInput)
                && safeResult.intent() != Intent.INVESTMENT_ADJUST
                && safeResult.intent() != Intent.PORTFOLIO_PLAN) {
            return new IntentResult(Intent.PORTFOLIO_PLAN, safeSlots(safeResult), safeResult.confidence());
        }

        if (safeResult.intent() == Intent.INVESTMENT_RECOMMENDATION && safeResult.confidence() < LOW_CONFIDENCE_THRESHOLD) {
            return new IntentResult(Intent.CLARIFY_NEEDED, safeSlots(safeResult), safeResult.confidence());
        }

        // 无矫正规则命中，原样返回 LLM 结果
        return safeResult;
    }

    /** 判断会话是否已有可用于“换一批”的上轮推荐结果。 */
    private boolean hasLastRecommendations(SessionState state) {
        return state != null && state.lastRecommendations() != null && !state.lastRecommendations().isEmpty();
    }

    /** slots 为空时使用空槽位，避免后续合并逻辑出现 NPE。 */
    private SlotBundle safeSlots(IntentResult result) {
        return result.slots() == null ? SlotBundle.empty() : result.slots();
    }

    private boolean containsComplianceRiskKeyword(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return false;
        }
        return containsAny(userInput, "保本高收益", "保证收益", "一定赚钱", "零风险", "不会亏", "一定不能亏", "替客户下单", "绕过风险测评");
    }

    private boolean containsPortfolioKeyword(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return false;
        }
        return containsAny(userInput, "组合", "资产配置", "怎么分配", "配置方案", "分散配置", "多产品");
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

