package com.investment.service.clarify;

import com.investment.model.SlotBundle;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

/**
 * 澄清规则服务。
 * 是否追问由 Java 规则决定，避免 LLM 随机性影响状态机；ClarifyAgent 只负责生成追问文案。
 */
@Service
public class ClarifyRuleService {

    /** 判断当前槽位是否足够进入推荐（missingSlots 为空即足够）。 */
    public boolean hasEnoughSlots(SlotBundle slots) {
        return missingSlots(slots).isEmpty();
    }

    /**
     * 计算当前缺失的关键投资槽位列表。
     * 必填：投资金额、投资期限、风险偏好、流动性需求。
     */
    public List<String> missingSlots(SlotBundle slots) {
        // slots 为 null 时用空 SlotBundle 代替
        SlotBundle safeSlots = slots == null ? SlotBundle.empty() : slots;
        List<String> missing = new ArrayList<>();
        if (safeSlots.investmentAmount().isEmpty()) {
            missing.add("investmentAmount");
        }
        if (safeSlots.investmentHorizon().isEmpty()) {
            missing.add("investmentHorizon");
        }
        if (safeSlots.riskPreference().isEmpty()) {
            missing.add("riskPreference");
        }
        if (safeSlots.liquidityNeed().isEmpty()) {
            missing.add("liquidityNeed");
        }
        return missing;
    }

    /** LLM 澄清失败或返回空时的模板追问文案，按 missingSlots 内容选择。 */
    public String fallbackQuestion(List<String> missingSlots) {
        if (missingSlots == null || missingSlots.isEmpty()) {
            return "我再确认一下客户的投资金额、期限、风险偏好和用钱安排，方便先做适当性筛选。";
        }
        if (missingSlots.contains("investmentAmount")) {
            return "客户这笔资金大概准备投入多少？";
        }
        if (missingSlots.contains("investmentHorizon")) {
            return "客户这笔钱预计可以持有多久，短期会不会用到？";
        }
        if (missingSlots.contains("riskPreference")) {
            return "客户风险偏好或风险测评结果大致是哪一档？";
        }
        if (missingSlots.contains("liquidityNeed")) {
            return "客户对流动性有什么要求，是随时可能用钱，还是可以封闭持有一段时间？";
        }
        return "我还需要补齐关键适当性信息，再做产品筛选。";
    }
}

