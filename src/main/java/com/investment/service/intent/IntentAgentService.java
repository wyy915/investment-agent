package com.investment.service.intent;

import com.investment.agent.factory.AgentFactory;
import com.investment.model.ConversationTurn;
import com.investment.enums.Intent;
import com.investment.model.IntentResult;
import com.investment.model.SlotBundle;
import com.investment.service.slot.SlotOptionService;
import com.investment.service.trace.AgentTraceService;
import com.investment.util.LlmJsonService;
import com.investment.util.SlotJsonPicker;
import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

/**
 * IntentAgent 调用服务。
 * 负责调用 LLM 识别 intent + slots，解析 JSON，失败时关键词兜底；不直接写 SessionState。
 */
@Service
public class IntentAgentService {

    /** 按 sessionId 提供 IntentAgent 实例的工厂。 */
    private final AgentFactory agentFactory;

    /** 从 LLM 输出文本中提取 JSON 对象的工具。 */
    private final LlmJsonService llmJsonService;

    /** 槽位字典服务，校验 LLM 输出的标签是否在合法候选值内。 */
    private final SlotOptionService slotOptionService;

    /** 链路追踪服务，callAgent 内部会记录 AGENT_CALL 事件。 */
    private final AgentTraceService agentTraceService;

    /** IntentAgent 使用的轻量模型名，来自配置 investment.llm.light-model。 */
    private final String modelName;

    /** 构造器注入全部依赖。 */
    public IntentAgentService(
            AgentFactory agentFactory,
            LlmJsonService llmJsonService,
            SlotOptionService slotOptionService,
            AgentTraceService agentTraceService,
            @Value("${investment.llm.light-model:qwen-turbo}") String modelName
    ) {
        this.agentFactory = agentFactory;
        this.llmJsonService = llmJsonService;
        this.slotOptionService = slotOptionService;
        this.agentTraceService = agentTraceService;
        this.modelName = modelName;
    }

    /**
     * 调用 IntentAgent 识别本轮意图和槽位。
     * 由 Orchestrator#handleTurn 调用，返回 IntentResult 供路由和槽位合并。
     */
    public IntentResult recognize(String sessionId, Long userId, String userInput, SlotBundle knownSlots, List<ConversationTurn> recentHistory) {
        try {
            // 加载全部投资槽位字段的合法候选值 Map，并把槽位字典传入 prompt。
            Map<String, List<String>> slotOptions = slotOptionService.findAllOptions();
            
            // 从 AgentFactory 获取当前 session 绑定的 IntentAgent ReActAgent 实例
            ReActAgent agent = agentFactory.get(sessionId).intent();
            // 清空 Agent 内存，避免上一轮对话污染本轮意图识别
            agent.getMemory().clear();
            // 调用 Agent：内部走 agentTraceService.callAgent，记录 AGENT_CALL 事件（含 input/output/latency）
            Msg response = agentTraceService.callAgent(sessionId, "IntentAgent", modelName,
                    agent, buildUserPrompt(userId, sessionId, userInput, knownSlots, recentHistory, slotOptions));
            // 解析 Agent 返回的 JSON 文本为 IntentResult（intent + slots + confidence）
            return parseResult(response.getTextContent(), userInput, slotOptions);
        } catch (Exception ignored) {
            // LLM 超时/JSON 解析失败时不抛异常，走关键词 fallback 保证 Orchestrator 可继续
            return fallback(userInput);
        }
    }

    /** 构造传给 IntentAgent 的用户 prompt，包含上下文和输出格式约束。 */
    private String buildUserPrompt(Long userId, String sessionId, String userInput, SlotBundle knownSlots, List<ConversationTurn> recentHistory, Map<String, List<String>> slotOptions) {
        return """
                userId: %s
                sessionId: %s
                recentHistory: %s
                knownSlots: %s
                slotOptions: %s
                当前这一句: %s
                请输出 JSON，字段为 intent、slots、confidence。
                slots 必须从 slotOptions 对应字段的候选值中选择；无法映射则输出 null 或空数组，不要创造标签。
                """.formatted(userId, sessionId, recentHistory, knownSlots, slotOptions, userInput);
    }

    /** 将 Agent 返回的 JSON 文本解析为 IntentResult。 */
    private IntentResult parseResult(String content, String userInput, Map<String, List<String>> slotOptions) {
        // 从 LLM 输出中提取 JSON 根节点（可能包裹在 markdown 代码块中）
        JsonNode root = llmJsonService.parseObject(content);

        // 读取 intent 字段并解析为 Intent 枚举，失败时走关键词兜底
        Intent intent = parseIntent(root.path("intent").asText(null), userInput);

        // 若 slots 是嵌套对象则取 slots 节点，否则直接用 root（兼容扁平 JSON）
        JsonNode slotsNode = root.path("slots").isObject() ? root.path("slots") : root;

        // 将 JSON slots 各字段映射为 SlotBundle，并过滤非法字典值
        SlotBundle slots = parseSlots(slotsNode, slotOptions);

        // 读取 confidence 字段，缺省 0.5
        double confidence = root.path("confidence").asDouble(0.5);

        // 组装并返回 IntentResult
        return new IntentResult(intent, slots, confidence);
    }

    /** 将 JSON 中的 intent 字符串解析为 Intent 枚举。 */
    private Intent parseIntent(String rawIntent, String userInput) {
        try {
            // rawIntent 为 null 时走关键词兜底；否则 Intent.valueOf 解析
            return rawIntent == null ? fallbackIntent(userInput) : Intent.valueOf(rawIntent);
        } catch (Exception ignored) {
            // 非法枚举名时走关键词兜底
            return fallbackIntent(userInput);
        }
    }

    /** 将 JSON slots 节点各字段转为 SlotBundle，通过 SlotJsonPicker 过滤非法标签。 */
    private SlotBundle parseSlots(JsonNode node, Map<String, List<String>> options) {
        return new SlotBundle(
                SlotJsonPicker.pick(node, "investmentAmount", options),
                SlotJsonPicker.pick(node, "investmentHorizon", options),
                SlotJsonPicker.pick(node, "riskPreference", options),
                SlotJsonPicker.pick(node, "liquidityNeed", options),
                SlotJsonPicker.pick(node, "returnExpectation", options),
                SlotJsonPicker.pick(node, "productType", options),
                SlotJsonPicker.pick(node, "customerProfile", options),
                SlotJsonPicker.pick(node, "restriction", options)
        );
    }

    /** LLM 完全失败时的保守兜底 IntentResult，confidence 固定 0.2。 */
    private IntentResult fallback(String userInput) {
        return new IntentResult(
                fallbackIntent(userInput),                                                          // 关键词推断意图
                SlotBundle.empty(),                                                                 // 槽位置空
                0.2                                                                                 // 低置信度
        );
    }

    /** 关键词规则推断意图，按优先级依次匹配。 */
    private Intent fallbackIntent(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return Intent.CLARIFY_NEEDED;  // 空输入 → 需要澄清
        }
        if (containsAny(userInput, "保本高收益", "保证收益", "一定赚钱", "零风险", "不会亏", "一定不能亏", "替客户下单")) {
            return Intent.RISK_COMPLIANCE;
        }
        if (containsAny(userInput, "换一批", "换个", "更稳", "风险低一点", "期限短一点", "流动性好一点")) {
            return Intent.INVESTMENT_ADJUST;
        }
        if (containsAny(userInput, "组合", "怎么分配", "资产配置", "配置方案", "分散")) {
            return Intent.PORTFOLIO_PLAN;
        }
        if (containsAny(userInput, "解释", "特点", "说明", "产品怎么样", "风险是什么")) {
            return Intent.PRODUCT_EXPLANATION;
        }
        if (containsAny(userInput, "你是谁", "你是 AI", "你好")) {
            return Intent.OTHER;
        }
        if (containsAny(userInput, "理财", "投资", "推荐", "产品", "闲置资金", "收益", "风险", "期限")) {
            return Intent.INVESTMENT_RECOMMENDATION;
        }
        return Intent.CLARIFY_NEEDED;
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

