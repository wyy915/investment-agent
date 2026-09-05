package com.investment.service.plan;

import com.investment.agent.factory.AgentFactory;
import com.investment.enums.SourceMode;
import com.investment.model.*;
import com.investment.service.recommend.RecommendResponseAgentService;
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
 * 组合配置应答 Agent：按期限篮子生成理由与结构化口语回复。
 */
@Service
public class PlanResponseAgentService {

    private final AgentFactory agentFactory;
    private final LlmJsonService llmJsonService;
    private final AgentTraceService agentTraceService;
    private final String modelName;

    public PlanResponseAgentService(
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
     * 将按期限篮子选出的方案包装为 RecommendResult + ResponseResult。
     */
    public RecommendResponseAgentService.Result planAndRespond(
            String sessionId,
            String userInput,
            SourceMode sourceMode,
            SlotBundle sharedSlots,
            List<ProductPlanService.PlannedProduct> plannedProducts
    ) {
        List<ProductPlanService.PlannedProduct> safePlans = plannedProducts == null ? List.of() : plannedProducts;
        List<ProductPlanService.PlannedProduct> matched = safePlans.stream().filter(ProductPlanService.PlannedProduct::matched).toList();
        boolean needDisclaimer = needsDisclaimer(sharedSlots);

        if (matched.isEmpty()) {
            RecommendResult empty = RecommendResult.empty();
            return new RecommendResponseAgentService.Result(
                    empty,
            ResponseResult.textOnly("暂时没有拼出组合配置参考，可以补充投资金额、期限、风险偏好或切换产品库后再试。")
            );
        }

        try {
            ReActAgent agent = agentFactory.get(sessionId).planResponse();
            agent.getMemory().clear();
            Msg response = agentTraceService.callAgent(
                    sessionId,
                    "PlanResponseAgent",
                    modelName,
                    agent,
                    buildUserPrompt(userInput, sourceMode, sharedSlots, safePlans)
            );
            ParsedOutput parsed = parseOutput(response.getTextContent(), safePlans, sharedSlots);
            RecommendResult recommend = new RecommendResult(parsed.options(), needDisclaimer);
            ResponseResult responseResult = new ResponseResult(parsed.speechText(), toDisplayBlocks(recommend), "WAIT_USER");
            return new RecommendResponseAgentService.Result(recommend, responseResult);
        } catch (Exception ignored) {
            RecommendResult recommend = new RecommendResult(templateOptions(safePlans, sharedSlots), needDisclaimer);
            return new RecommendResponseAgentService.Result(
                    recommend,
                    new ResponseResult(templateSpeech(safePlans, recommend), toDisplayBlocks(recommend), "WAIT_USER")
            );
        }
    }

    private String buildUserPrompt(
            String userInput,
            SourceMode sourceMode,
            SlotBundle sharedSlots,
            List<ProductPlanService.PlannedProduct> plannedProducts
    ) {
        StringBuilder productSection = new StringBuilder();
        for (ProductPlanService.PlannedProduct planned : plannedProducts) {
            productSection.append("\n- 期限篮子=").append(planned.investmentHorizon());
            if (planned.matched()) {
                ProductItem product = planned.product();
                productSection.append("，候选=[productId=").append(product.id())
                        .append(", name=").append(product.name())
                        .append(", score=").append(product.matchScore())
                        .append("]");
            } else {
                productSection.append("，候选=[]（暂无匹配）");
            }
        }
        return """
                用户原话：%s
                数据源模式：%s
                共享槽位：%s
                各期限篮子候选：%s
                请输出 JSON，包含 productPlans 数组（每项 investmentHorizon + productId + reason）和 speechText；productId 必须来自对应候选。
                """.formatted(userInput, sourceMode, sharedSlots, productSection);
    }

    private ParsedOutput parseOutput(String content, List<ProductPlanService.PlannedProduct> plannedProducts, SlotBundle sharedSlots) {
        JsonNode root = llmJsonService.parseObject(content);
        Map<String, String> reasonsByHorizon = new LinkedHashMap<>();
        JsonNode plansNode = root.path("productPlans");
        if (plansNode.isArray()) {
            plansNode.forEach(node -> {
                String investmentHorizon = node.path("investmentHorizon").asText("").trim();
                String reason = node.path("reason").asText("").trim();
                if (!investmentHorizon.isBlank() && !reason.isBlank()) {
                    reasonsByHorizon.put(investmentHorizon, reason);
                }
            });
        }

        List<RecommendedProductOption> options = new ArrayList<>();
        for (ProductPlanService.PlannedProduct planned : plannedProducts) {
            if (!planned.matched()) {
                continue;
            }
            ProductItem product = planned.product();
            String reason = reasonsByHorizon.getOrDefault(planned.investmentHorizon(), templateReason(planned, sharedSlots));
            options.add(toOption(product, reason, planned.querySlots()));
        }

        String speechText = root.path("speechText").asText("").trim();
        if (speechText.isBlank()) {
            speechText = templateSpeech(plannedProducts, new RecommendResult(options, needsDisclaimer(sharedSlots)));
        }
        return new ParsedOutput(options, speechText);
    }

    private List<RecommendedProductOption> templateOptions(List<ProductPlanService.PlannedProduct> plannedProducts, SlotBundle sharedSlots) {
        List<RecommendedProductOption> options = new ArrayList<>();
        for (ProductPlanService.PlannedProduct planned : plannedProducts) {
            if (!planned.matched()) {
                continue;
            }
            options.add(toOption(planned.product(), templateReason(planned, sharedSlots), planned.querySlots()));
        }
        return options;
    }

    private RecommendedProductOption toOption(ProductItem product, String reason, SlotBundle querySlots) {
        SlotBundle displaySlots = querySlots != null ? querySlots : product.slots();
        return new RecommendedProductOption(
                product.id(), product.sourceType(), product.scopeType(), product.scopeBranchId(), product.scopeManagerId(),
                product.name(), product.productCode(), product.productType(),
                product.riskLevel(), product.investmentHorizon(), product.liquidityType(), product.minAmount(),
                product.returnType(), product.targetCustomer(), product.status(), reason, product.matchScore(), displaySlots
        );
    }

    private String templateReason(ProductPlanService.PlannedProduct planned, SlotBundle sharedSlots) {
        String name = planned.product().name();
        if (sharedSlots != null && !sharedSlots.riskPreference().isEmpty()) {
            return name + "风险等级与客户" + String.join("、", sharedSlots.riskPreference()) + "偏好较接近，可作为" + planned.investmentHorizon() + "期限篮子的内部参考。";
        }
        if (sharedSlots != null && !sharedSlots.liquidityNeed().isEmpty()) {
            return name + "的流动性安排较贴近客户" + String.join("、", sharedSlots.liquidityNeed()) + "需求。";
        }
        return name + "与客户当前投资需求匹配度较高，可作为内部组合筛选参考。";
    }

    private String templateSpeech(List<ProductPlanService.PlannedProduct> plannedProducts, RecommendResult recommendResult) {
        StringBuilder builder = new StringBuilder("基于当前信息，先按期限篮子给出组合筛选参考：");
        for (ProductPlanService.PlannedProduct planned : plannedProducts) {
            builder.append("\n- ").append(planned.investmentHorizon()).append("：");
            if (planned.matched()) {
                String reason = recommendResult.recommendations().stream()
                        .filter(option -> option.productId().equals(planned.product().id()))
                        .map(RecommendedProductOption::reason)
                        .findFirst()
                        .orElse(planned.product().name());
                builder.append(planned.product().name()).append("（").append(reason).append("）");
            } else {
                builder.append("暂时没有适配产品");
            }
        }
        builder.append("\n这不是最终销售建议，仍需结合客户风险测评、产品说明书和适当性要求确认。");
        if (recommendResult.needDisclaimer()) {
            builder.append("\n如需调整，可继续说明更稳、期限更短或流动性更高。");
        }
        return builder.toString();
    }

    private List<ProductResponse> toDisplayBlocks(RecommendResult recommendResult) {
        if (recommendResult == null || recommendResult.recommendations() == null) {
            return List.of();
        }
        return recommendResult.recommendations().stream()
                .map(option -> new ProductResponse(
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
                ))
                .toList();
    }

    private boolean needsDisclaimer(SlotBundle slots) {
        return true;
    }

    private record ParsedOutput(List<RecommendedProductOption> options, String speechText) {
    }
}

