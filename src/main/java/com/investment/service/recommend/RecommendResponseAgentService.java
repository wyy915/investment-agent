package com.investment.service.recommend;

import com.investment.agent.factory.AgentFactory;
import com.investment.enums.SourceMode;
import com.investment.model.ProductItem;
import com.investment.model.ProductResponse;
import com.investment.model.RecommendResult;
import com.investment.model.RecommendedProductOption;
import com.investment.model.ResponseResult;
import com.investment.model.SlotBundle;
import com.investment.service.trace.AgentTraceService;
import com.investment.util.LlmJsonService;
import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RecommendResponseAgent 服务（Orchestrator 推荐流水线第三层）。
 * 一次 LLM 调用同时生成 top3 推荐理由和面向用户的口语 speechText。
 */
@Service
public class RecommendResponseAgentService {

    /**
     * 按 sessionId 提供 RecommendResponseAgent 实例的工厂。
     */
    private final AgentFactory agentFactory;

    /**
     * 从 LLM 输出文本中提取 JSON 对象的工具。
     */
    private final LlmJsonService llmJsonService;

    /**
     * 链路追踪服务，callAgent 内部记录 AGENT_CALL 事件。
     */
    private final AgentTraceService agentTraceService;

    /**
     * RecommendResponseAgent 使用的主模型名，来自配置 investment.llm.main-model。
     */
    private final String modelName;

    /**
     * 构造器注入依赖。
     */
    public RecommendResponseAgentService(
            AgentFactory agentFactory,
            LlmJsonService llmJsonService,
            AgentTraceService agentTraceService,
            @Value("${investment.llm.main-model:qwen-max}") String modelName
    ) {
        this.agentFactory = agentFactory;
        this.llmJsonService = llmJsonService;
        this.agentTraceService = agentTraceService;
        this.modelName = modelName;
    }

    /**
     * 合并推荐链路输出：RecommendResult + ResponseResult。
     * 由 Orchestrator#completeRecommendation 在 PRODUCT_RANKED 之后调用。
     */
    public Result recommendAndRespond(
            String sessionId,
            String userInput,
            SourceMode sourceMode,
            SlotBundle slots,
            List<ProductItem> rankedProducts) {
        List<ProductItem> topProducts = rankedProducts == null ? List.of() : rankedProducts.stream().limit(3).toList();

        if (topProducts.isEmpty()) {
            RecommendResult empty = RecommendResult.empty();
            return new Result(empty, ResponseResult.textOnly("暂时没有找到适配的可售产品，可以先补充客户投资金额、期限、风险偏好和流动性要求。"));
        }

        boolean needDisclaimer = needsDisclaimer(slots);
        try {
            // 从 AgentFactory 获取 RecommendResponseAgent 实例
            ReActAgent agent = agentFactory.get(sessionId).recommendResponse();
            // 清空 Agent 内存
            agent.getMemory().clear();
            // 调用 Agent：内部走 agentTraceService.callAgent，记录 AGENT_CALL（RecommendResponseAgent + main-model）
            Msg response = agentTraceService.callAgent(
                    sessionId,
                    "RecommendResponseAgent",
                    modelName,
                    agent,
                    buildUserPrompt(userInput, sourceMode, slots, topProducts)
            );
            // 解析 Agent JSON 输出为 recommendations + speechText
            ParsedOutput parsed = parseOutput(response.getTextContent(), topProducts, slots);

            // 构造 RecommendResult：推荐项列表 + strategy + needDisclaimer
            RecommendResult recommend = new RecommendResult(parsed.options(), needDisclaimer);

            // 构造 ResponseResult：speechText + 前端卡片 displayBlocks + nextAction=WAIT_USER
            ResponseResult responseResult = new ResponseResult(parsed.speechText(), toDisplayBlocks(recommend), "WAIT_USER");
            return new Result(recommend, responseResult);

        } catch (Exception ignored) {
            // LLM 异常时用模板理由 + 模板 speechText 兜底
            RecommendResult recommend = new RecommendResult(templateOptions(topProducts, slots), needDisclaimer);
            return new Result(recommend, new ResponseResult(templateSpeech(recommend), toDisplayBlocks(recommend), "WAIT_USER"));
        }
    }

    /**
     * 构造 RecommendResponseAgent 的输入 prompt。
     */
    private String buildUserPrompt(String userInput, SourceMode sourceMode, SlotBundle slots, List<ProductItem> topProducts) {
        return """
                用户原话：%s
                数据源模式：%s
                本轮槽位：%s
                候选理财产品：%s
                请输出 JSON，包含 recommendations 数组（每项 productId + reason）和 speechText，不要编造候选之外的产品。
                """.formatted(userInput, sourceMode, slots, topProducts);
    }

    /**
     * 解析 Agent 返回的 JSON 为 ParsedOutput。
     */
    private ParsedOutput parseOutput(String content, List<ProductItem> topProducts, SlotBundle slots) {
        JsonNode root = llmJsonService.parseObject(content);                              // 提取 JSON 根节点
        List<RecommendedProductOption> options = parseOptions(root.path("recommendations"), topProducts, slots); // 解析推荐数组
        String speechText = root.path("speechText").asText("").trim();                    // 读取口语回复
        if (speechText.isBlank()) {
            speechText = templateSpeech(new RecommendResult(options, needsDisclaimer(slots))); // 空则用模板
        }
        return new ParsedOutput(options, speechText);
    }

    /**
     * 解析 recommendations JSON 数组，只保留 topProducts 中存在的 productId。
     */
    private List<RecommendedProductOption> parseOptions(JsonNode recommendationsNode, List<ProductItem> topProducts, SlotBundle slots) {
        Map<Long, ProductItem> byId = new LinkedHashMap<>();
        topProducts.forEach(product -> byId.put(product.id(), product));           // productId → ProductItem 索引
        Map<Long, String> reasons = new LinkedHashMap<>();
        if (recommendationsNode.isArray()) {
            recommendationsNode.forEach(node -> {
                long productId = node.path("productId").asLong();
                String reason = node.path("reason").asText("");
                // 只采纳候选内且 reason 非空的项
                if (byId.containsKey(productId) && !reason.isBlank()) {
                    reasons.put(productId, reason);
                }
            });
        }
        List<RecommendedProductOption> result = new ArrayList<>();
        // 按 topProducts 顺序输出，缺失 reason 时用 templateReason 兜底
        for (ProductItem product : topProducts) {
            result.add(toOption(product, reasons.getOrDefault(product.id(), templateReason(product, slots))));
        }
        return result;
    }

    /**
     * LLM 失败时为 topProducts 生成模板推荐理由列表。
     */
    private List<RecommendedProductOption> templateOptions(List<ProductItem> topProducts, SlotBundle slots) {
        return topProducts.stream()
                .map(product -> toOption(product, templateReason(product, slots)))
                .toList();
    }

    /**
     * ProductItem + reason 转为 RecommendedProductOption。
     */
    private RecommendedProductOption toOption(ProductItem product, String reason) {
        return new RecommendedProductOption(
                product.id(), product.sourceType(), product.scopeType(), product.scopeBranchId(), product.scopeManagerId(),
                product.name(), product.productCode(), product.productType(),
                product.riskLevel(), product.investmentHorizon(), product.liquidityType(), product.minAmount(),
                product.returnType(), product.targetCustomer(), product.status(), reason, product.matchScore(), product.slots()
        );
    }

    /**
     * 根据 slots 生成单条模板推荐理由。
     */
    private String templateReason(ProductItem product, SlotBundle slots) {
        if (slots != null && !slots.riskPreference().isEmpty()) {
            return product.name() + "的风险等级与客户" + String.join("、", slots.riskPreference()) + "偏好较接近，仍需以正式风险测评和产品说明书为准。";
        }
        if (slots != null && !slots.investmentHorizon().isEmpty()) {
            return product.name() + "的期限与客户" + String.join("、", slots.investmentHorizon()) + "资金安排较匹配。";
        }
        return product.name() + "与本轮客户投资需求匹配度较高，可作为内部筛选参考。";
    }

    /**
     * 将 RecommendResult 转为前端展示用的 ProductResponse 卡片列表。
     */
    private List<ProductResponse> toDisplayBlocks(RecommendResult recommendResult) {
        if (recommendResult == null || recommendResult.recommendations() == null) {
            return List.of();
        }
        return recommendResult.recommendations().stream()
                .map(this::toProductResponse)
                .toList();
    }

    /**
     * RecommendedProductOption 转为 ProductResponse（含各维 slots 标签）。
     */
    private ProductResponse toProductResponse(RecommendedProductOption option) {
        return new ProductResponse(
                option.productId(),
                option.sourceType(),
                option.name(),
                option.scopeType(),
                option.scopeBranchId(),
                option.scopeManagerId(),
                option.productCode(),
                option.productType(),
                option.riskLevel(),
                option.investmentHorizon(),
                option.liquidityType(),
                option.minAmount(),
                option.returnType(),
                option.targetCustomer(),
                option.status(),
                option.matchedSlots().investmentAmount(),
                option.matchedSlots().riskPreference(),
                option.matchedSlots().liquidityNeed(),
                option.matchedSlots().returnExpectation(),
                option.matchedSlots().customerProfile(),
                option.matchedSlots().restriction(),
                option.matchScore()
        );
    }

    /**
     * LLM 失败时的模板口语回复，含可选免责声明。
     */
    private String templateSpeech(RecommendResult recommendResult) {
        if (recommendResult == null || recommendResult.recommendations().isEmpty()) {
            return "暂时没有找到适配的理财产品，可以补充投资金额、期限、风险偏好或流动性要求。";
        }
        StringBuilder builder = new StringBuilder("基于当前信息，可先把这几款作为内部筛选参考：");
        for (RecommendedProductOption option : recommendResult.recommendations()) {
            builder.append("\n- ").append(option.name()).append("：").append(option.reason());
        }
        if (recommendResult.needDisclaimer()) {
            builder.append("\n以上内容仅用于理财经理内部辅助，具体推荐需结合客户风险测评、产品说明书和适当性要求确认。");
        }
        return builder.toString();
    }

    /**
     * 理财推荐默认附加合规边界提示。
     */
    private boolean needsDisclaimer(SlotBundle slots) {
        return true;
    }

    /**
     * recommendAndRespond 的返回结构：RecommendResult + ResponseResult。
     */
    public record Result(RecommendResult recommend, ResponseResult response) {
    }

    /**
     * parseOutput 的中间结构。
     */
    private record ParsedOutput(List<RecommendedProductOption> options, String speechText) {
    }
}

