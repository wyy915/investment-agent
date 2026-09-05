package com.investment.service.slot;

import com.investment.model.SlotBundle;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 槽位合并服务。
 * 多轮对话中，本轮非空槽位覆盖历史槽位，本轮空槽位不清空历史槽位。
 */
@Service
public class SlotMergeService {

    /**
     * 合并历史槽位与本轮槽位。
     * 由 Orchestrator/IntentRevise 调用，7 维字段各自独立合并。
     */
    public SlotBundle merge(SlotBundle history, SlotBundle current) {
        // history 为 null 时用空 SlotBundle
        SlotBundle safeHistory = history == null ? SlotBundle.empty() : history;
        // current 为 null 时用空 SlotBundle
        SlotBundle safeCurrent = current == null ? SlotBundle.empty() : current;
        return new SlotBundle(
                choose(safeHistory.investmentAmount(), safeCurrent.investmentAmount()),
                choose(safeHistory.investmentHorizon(), safeCurrent.investmentHorizon()),
                choose(safeHistory.riskPreference(), safeCurrent.riskPreference()),
                choose(safeHistory.liquidityNeed(), safeCurrent.liquidityNeed()),
                choose(safeHistory.returnExpectation(), safeCurrent.returnExpectation()),
                choose(safeHistory.productType(), safeCurrent.productType()),
                choose(safeHistory.customerProfile(), safeCurrent.customerProfile()),
                choose(safeHistory.restriction(), safeCurrent.restriction())
        );
    }

    /** 本轮 current 非空则用本轮，否则保留 history。 */
    private List<String> choose(List<String> history, List<String> current) {
        return current == null || current.isEmpty() ? history : current;
    }
}

