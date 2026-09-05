package com.investment.service.orchestrator;

import com.investment.exception.InvestmentException;
import com.investment.service.intent.IntentAgentService;
import com.investment.service.intent.IntentReviseService;
import com.investment.service.risk.RiskGuardService;
import com.investment.enums.ClarifyAction;
import com.investment.model.ClarifyResult;
import com.investment.model.RiskGuardResult;
import com.investment.enums.Intent;
import com.investment.model.IntentResult;
import com.investment.model.ProductItem;
import com.investment.model.ProductRankRequest;
import com.investment.model.ProductSearchRequest;
import com.investment.model.ChatRequest;
import com.investment.model.ChatResponse;
import com.investment.model.RecommendResult;
import com.investment.model.ResponseResult;
import com.investment.enums.SessionPhase;
import com.investment.model.SessionState;
import com.investment.model.SlotBundle;
import com.investment.enums.SourceMode;
import com.investment.service.clarify.ClarifyAgentService;
import com.investment.service.product.ProductRankService;
import com.investment.service.product.ProductSearchService;
import com.investment.service.product.ProductService;
import com.investment.service.plan.ProductPlanService;
import com.investment.service.plan.PlanResponseAgentService;
import com.investment.service.recommend.RecommendResponseAgentService;
import com.investment.service.session.SessionService;
import com.investment.service.session.SessionStateService;
import com.investment.service.slot.SlotMergeService;
import com.investment.service.trace.AgentTraceService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 银行理财投顾辅助多 Agent 编排服务（Orchestrator）。
 * <p>
 * 一轮 {@link #investmentChat} 的主链路：加载会话 → Trace → 加锁 → 记录消息 → 意图识别 → 路由 → 推荐/澄清/固定回复 → 落库。
 */
@Service
public class InvestmentOrchestratorService {

    /**
     * 闲聊时的固定引导文案，不额外调用 LLM。
     */
    private static final String CHITCHAT_REPLY = "我主要负责银行理财投顾辅助。你可以描述客户资金规模、期限、风险偏好和流动性需求，我会先做适当性筛选参考。";

    /**
     * 会话消息落库服务，写入 investment_messages 表。
     */
    private final SessionService sessionService;

    /**
     * 会话状态服务，读写 phase、slots、lastRecommendations 等到 investment_sessions 表。
     */
    private final SessionStateService sessionStateService;

    /**
     * 意图识别 Agent 服务，调用 LLM 识别 intent + slots。
     */
    private final IntentAgentService intentAgentService;

    /**
     * 意图矫正规则服务，用历史状态二次修正 LLM 输出。
     */
    private final IntentReviseService intentReviseService;

    /**
     * 槽位合并服务，多轮对话中合并历史槽位与本轮槽位。
     */
    private final SlotMergeService slotMergeService;

    /**
     * 澄清 Agent 服务，槽位不足时生成追问文案。
     */
    private final ClarifyAgentService clarifyAgentService;

    /**
     * 产品检索服务，按 sourceMode + slots 从 DB 召回候选。
     */
    private final ProductSearchService productSearchService;

    /**
     * 产品重排服务，对候选按槽位命中二次打分排序。
     */
    private final ProductRankService productRankService;

    /**
     * 推荐应答 Agent 服务，一次 LLM 调用生成推荐理由 + 口语回复。
     */
    private final RecommendResponseAgentService recommendResponseAgentService;

    /**
     * 组合配置服务：解析期限篮子并拆分检索重排。
     */
    private final ProductPlanService productPlanService;

    /**
     * 组合配置应答 Agent：按期限篮子生成理由与口语回复。
     */
    private final PlanResponseAgentService planResponseAgentService;

    /**
     * 产品服务，用于 PERSONAL 模式空库前置检查。
     */
    private final ProductService productService;

    /**
     * 金融合规守卫，拦截收益承诺、风险弱化等高风险表述。
     */
    private final RiskGuardService riskGuardService;

    /**
     * 链路追踪服务，记录状态机事件和 Agent 调用到 agent_traces 表。
     */
    private final AgentTraceService agentTraceService;

    /**
     * 会话级锁 Map，key=sessionId，value=锁对象，保证同 session 串行写状态。
     */
    private final Map<String, Object> sessionLocks = new ConcurrentHashMap<>();

    /**
     * Spring 构造器注入全部依赖。
     */
    public InvestmentOrchestratorService(
            SessionService sessionService,
            SessionStateService sessionStateService,
            IntentAgentService intentAgentService,
            IntentReviseService intentReviseService,
            SlotMergeService slotMergeService,
            ClarifyAgentService clarifyAgentService,
            ProductSearchService productSearchService,
            ProductRankService productRankService,
            RecommendResponseAgentService recommendResponseAgentService,
            ProductPlanService productPlanService,
            PlanResponseAgentService planResponseAgentService,
            ProductService productService,
            RiskGuardService riskGuardService,
            AgentTraceService agentTraceService
    ) {
        this.sessionService = sessionService;                           // 注入消息落库服务
        this.sessionStateService = sessionStateService;                 // 注入会话状态服务
        this.intentAgentService = intentAgentService;                   // 注入意图识别服务
        this.intentReviseService = intentReviseService;                 // 注入意图矫正服务
        this.slotMergeService = slotMergeService;                       // 注入槽位合并服务
        this.clarifyAgentService = clarifyAgentService;                 // 注入澄清 Agent 服务
        this.productSearchService = productSearchService;                     // 注入产品检索服务
        this.productRankService = productRankService;                         // 注入产品重排服务
        this.recommendResponseAgentService = recommendResponseAgentService; // 注入推荐应答 Agent 服务
        this.productPlanService = productPlanService;                         // 注入组合配置服务
        this.planResponseAgentService = planResponseAgentService;       // 注入规划应答 Agent 服务
        this.productService = productService;                                 // 注入产品服务
        this.riskGuardService = riskGuardService;             // 注入合规守卫
        this.agentTraceService = agentTraceService;                     // 注入链路追踪服务
    }

    /**
     * 同步处理一轮用户输入并返回完整推荐结果（HTTP 入口对应方法）。
     */
    public ChatResponse investmentChat(Long userId, ChatRequest request) {
        // 生成本轮唯一 traceId，格式 trace_<32位hex>，贯穿整轮请求的所有 Trace 事件
        String traceId = "trace_" + UUID.randomUUID().toString().replace("-", "");
        // 校验 request 非空且 message 非空白，否则抛业务异常
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new InvestmentException("用户问题不能为空");
        }
        // 校验 sourceMode 必填（PERSONAL 个人库 / PUBLIC 公共库），否则无法检索
        if (request.sourceMode() == null) {
            throw new InvestmentException("sourceMode 不能为空，请选择 PERSONAL 或 PUBLIC");
        }

        // 从 DB 加载已有会话状态，或按 sessionId/userId 创建新会话，得到 slots/phase/lastRecommendations 等
        SessionState initialState = sessionStateService.loadOrCreate(request.sessionId(), userId, request.sourceMode());

        // 开启 Trace 上下文；try-with-resources 结束时 TraceScope#close 会将整轮事件写入 agent_traces 表
        try (AgentTraceService.TraceScope ignored = agentTraceService.openTrace(traceId, initialState.sessionId(), userId)) {
            try {
                // 记录请求开始时间（纳秒），用于最后计算整轮耗时
                long startedAt = System.nanoTime();
                // Trace 事件：REQUEST_RECEIVED | 阶段 HTTP | 输入=ChatRequest | 输出=初始 SessionState
                agentTraceService.recordEvent("REQUEST_RECEIVED", "HTTP", request, initialState);

                // 获取或创建该 sessionId 对应的锁对象，保证同一 session 并发请求串行执行
                Object lock = sessionLocks.computeIfAbsent(initialState.sessionId(), key -> new Object());
                synchronized (lock) {
                    // 在锁内执行完整状态机，处理本轮用户输入
                    ChatResponse response = handleTurn(userId, request, traceId, initialState);
                    // Trace 事件：REQUEST_FINISHED | 阶段 HTTP | 输入=ChatRequest | 输出=ChatResponse | 耗时 ms
                    agentTraceService.recordEvent("REQUEST_FINISHED", "HTTP", request, response, elapsedMs(startedAt));
                    // 将最终响应返回给 Controller
                    return response;
                }
            } catch (RuntimeException error) {
                // Trace 事件：REQUEST_FAILED | 阶段 HTTP | 输入=ChatRequest | 记录异常并将 Trace 标记 FAILED
                agentTraceService.recordError("REQUEST_FAILED", "HTTP", request, error);
                // 继续向上抛出，由全局异常处理器返回错误响应
                throw error;
            }
        }
    }

    /**
     * 在会话锁内执行完整状态机：记消息 → 前置校验 → 意图识别 → 路由分发。
     */
    private ChatResponse handleTurn(Long userId, ChatRequest request, String traceId, SessionState state) {
        // 从会话状态中取出 sessionId，后续落库和 Agent 调用都依赖它
        String sessionId = state.sessionId();
        // 从会话状态中取出数据源模式（PERSONAL / PUBLIC）
        SourceMode sourceMode = state.sourceMode();

        // 将用户消息 INSERT 到 investment_messages 表，role=user，intent=null，关联 traceId
        sessionService.appendMessage(sessionId, "user", request.message(), null, traceId);

        // Trace 事件：USER_MESSAGE_RECORDED | 阶段 SESSION | 输入=用户原文 | 输出=sessionId+sourceMode
        agentTraceService.recordEvent("USER_MESSAGE_RECORDED", "SESSION", request.message(), Map.of("sessionId", sessionId, "sourceMode", sourceMode));

        // PERSONAL 模式且用户尚未录入任何个人产品时，提前返回引导文案，跳过后续检索
        if (sourceMode == SourceMode.PERSONAL && !productService.hasPersonalProducts(userId)) {
            ResponseResult response = ResponseResult.textOnly("当前个人产品库还没有可用产品。可以先录入理财产品，或切换到公共示例产品库体验。");
            // Trace 事件：PERSONAL_LIBRARY_EMPTY | 阶段 ROUTE | 输入=userId | 输出=引导文案
            agentTraceService.recordEvent("PERSONAL_LIBRARY_EMPTY", "ROUTE", Map.of("userId", userId), response);
            // 走纯文本完成分支：保存状态 + 落库助手消息 + 返回 ChatResponse
            return completeTextOnly(sessionId, traceId, state, Intent.INVESTMENT_RECOMMENDATION, response);
        }

        // 意图识别：调用 IntentAgent：传入 sessionId、userId、用户原文、历史槽位、最近 3 条对话摘要
        IntentResult rawIntent = intentAgentService.recognize(sessionId, userId, request.message(), state.slots(), sessionService.recentConversationTurns(sessionId, userId, 3));
        // Trace 事件：INTENT_RECOGNIZED | 阶段 INTENT | 输入=用户原文 | 输出=IntentResult（intent/slots/confidence）
        agentTraceService.recordEvent("INTENT_RECOGNIZED", "INTENT", request.message(), rawIntent);

        // 调用 IntentReviseService，结合历史 phase/slots/lastRecommendations 二次矫正意图
        IntentResult intent = intentReviseService.revise(state, rawIntent, request.message());
        // Trace 事件：INTENT_REVISED | 阶段 INTENT | 输入=矫正前 rawIntent | 输出=矫正后 intent
        agentTraceService.recordEvent("INTENT_REVISED", "INTENT", rawIntent, intent);

        // Trace 事件：ROUTE_SELECTED | 阶段 ROUTE | 输入=最终 intent | 输出=路由目标 intent 枚举名
        agentTraceService.recordEvent("ROUTE_SELECTED", "ROUTE", intent, Map.of("route", intent.intent()));

        // 按最终意图枚举分发到对应分支处理器
        return switch (intent.intent()) {
            // 推荐或需澄清：走推荐主链路（澄清由 ClarifyAgent 内部决定）
            case INVESTMENT_RECOMMENDATION, CLARIFY_NEEDED ->
                    handleRecommendation(sessionId, userId, request.message(), traceId, state, intent);
            // 调整上轮推荐：排除已推荐 ID，重跑推荐流水线
            case INVESTMENT_ADJUST -> handleAdjust(sessionId, userId, request.message(), traceId, state, intent);
            // 组合配置：按期限篮子拆分检索后统一包装
            case PORTFOLIO_PLAN -> handlePlan(sessionId, userId, request.message(), traceId, state, intent);
            // 合规风险：返回保守提示，不走推荐
            case RISK_COMPLIANCE -> handleHealthRisk(sessionId, traceId, state);
            case PRODUCT_EXPLANATION ->
                    handleRecommendation(sessionId, userId, request.message(), traceId, state, intent);
            // 其他无关内容：返回固定引导文案
            case OTHER -> handleChitchat(sessionId, traceId, state);
        };
    }

    /**
     * 推荐主链路：合并槽位 → ClarifyAgent 判追问 → 槽位足够则进入 completeRecommendation。
     */
    private ChatResponse handleRecommendation(String sessionId, Long userId, String userInput, String traceId, SessionState state, IntentResult intent) {
        // 将历史 slots 与 IntentAgent 本轮识别的 slots 合并（本轮非空覆盖，本轮空保留历史）
        SlotBundle mergedSlots = slotMergeService.merge(state.slots(), intent.slots());

        // Trace 事件：SLOTS_MERGED | 阶段 SLOT | 输入=stateSlots+intentSlots | 输出=mergedSlots
        agentTraceService.recordEvent("SLOTS_MERGED", "SLOT", Map.of("stateSlots", state.slots(), "intentSlots", intent.slots()), mergedSlots);

        // 基于合并槽位构建工作态：意图固定为 INVESTMENT_RECOMMENDATION
        SessionState workingState = state.withIntent(Intent.INVESTMENT_RECOMMENDATION).withSlots(mergedSlots);

        // 【重要】不能完全依靠agent的意图识别,在进入推荐之前,规则层面上也需要判断是否有足够的信息
        // 调用 ClarifyAgent：规则层先判缺失槽位，不足则 LLM 生成追问文案
        ClarifyResult clarify = clarifyAgentService.decide(sessionId, userInput, mergedSlots);
        // Trace 事件：CLARIFY_DECISION | 阶段 CLARIFY | 输入=mergedSlots | 输出=ClarifyResult（ASK/READY）
        agentTraceService.recordEvent("CLARIFY_DECISION", "CLARIFY", mergedSlots, clarify);

        // 若 ClarifyResult.action == ASK，说明槽位不足，需要追问用户
        if (clarify.action() == ClarifyAction.ASK) {
            // 直接返回追问，不进入检索推荐
            return completeAsk(sessionId, traceId, workingState, clarify);
        }
        // 槽位足够：phase 切 RECOMMEND，excludeProductIds 为空
        return completeRecommendation(sessionId, userId, userInput, traceId, workingState.withPhase(SessionPhase.RECOMMEND), List.of());
    }

    private ChatResponse completeAsk(String sessionId, String traceId, SessionState workingState, ClarifyResult clarify) {
        // 将会话 phase 切换为 CLARIFY，表示当前处于澄清等待用户回复状态
        SessionState clarifyState = workingState.withPhase(SessionPhase.CLARIFY);
        // 将澄清态会话状态 UPDATE 到 investment_sessions 表
        sessionStateService.save(clarifyState);

        // 将助手追问消息 INSERT 到 investment_messages，intent=CLARIFY_NEEDED
        sessionService.appendMessage(sessionId, "assistant", clarify.questionToAsk(), Intent.CLARIFY_NEEDED.name(), traceId);

        // 构造澄清型 ChatResponse，携带 missingSlots 供前端展示
        ChatResponse response = ChatResponse.clarify(sessionId, traceId, clarify.questionToAsk(), clarify.missingSlots());

        // Trace 事件：RESPONSE_READY | 阶段 CLARIFY | 输入=clarify | 输出=ChatResponse
        agentTraceService.recordEvent("RESPONSE_READY", "CLARIFY", clarify, response);

        // 直接返回追问，不进入检索推荐
        return response;
    }

    /**
     * 调整链路：合并槽位 → 取 excludeProductIds → 重跑推荐流水线。
     */
    private ChatResponse handleAdjust(String sessionId, Long userId, String userInput, String traceId, SessionState state, IntentResult intent) {
        // 合并历史槽位与本轮 IntentAgent 识别的槽位
        SlotBundle mergedSlots = slotMergeService.merge(state.slots(), intent.slots());

        List<Long> excludeProductIds = state.lastRecommendations() == null ? List.of() : state.lastRecommendations();

        agentTraceService.recordEvent("ADJUST_CONTEXT_RESOLVED", "ADJUST", intent, traceMap("mergedSlots", mergedSlots, "excludeProductIds", excludeProductIds));

        // 构建调整态工作会话：意图=INVESTMENT_ADJUST，phase=RECOMMEND
        SessionState workingState = state.withIntent(Intent.INVESTMENT_ADJUST)
                .withSlots(mergedSlots)
                .withPhase(SessionPhase.RECOMMEND);

        // 进入推荐流水线，仅排除已推荐产品，实现换一批
        return completeRecommendation(sessionId, userId, userInput, traceId, workingState, excludeProductIds);
    }

    /**
     * 组合配置链路：合并槽位 → 解析期限篮子 → 按期限拆分检索重排 → 规划应答包装。
     */
    private ChatResponse handlePlan(String sessionId, Long userId, String userInput, String traceId, SessionState state, IntentResult intent) {
        // 合并历史槽位与本轮槽位，组合配置时按期限篮子覆盖 investmentHorizon。
        SlotBundle mergedSlots = slotMergeService.merge(state.slots(), intent.slots());
        List<String> planInvestmentHorizons = productPlanService.resolveInvestmentHorizons(mergedSlots);
        // 规划态 slots 显式写入目标期限篮子，便于后续轮次与 Trace 观察
        SlotBundle planSlots = new SlotBundle(
                mergedSlots.investmentAmount(),
                planInvestmentHorizons,
                mergedSlots.riskPreference(),
                mergedSlots.liquidityNeed(),
                mergedSlots.returnExpectation(),
                mergedSlots.productType(),
                mergedSlots.customerProfile(),
                mergedSlots.restriction()
        );
        // Trace 事件：PLAN_CONTEXT_RESOLVED | 阶段 PLAN | 输入=intent | 输出=planSlots+planInvestmentHorizons
        agentTraceService.recordEvent(
                "PLAN_CONTEXT_RESOLVED",
                "PLAN",
                intent,
                traceMap("mergedSlots", mergedSlots, "planInvestmentHorizons", planInvestmentHorizons, "planSlots", planSlots)
        );

        SessionState workingState = state.withIntent(Intent.PORTFOLIO_PLAN).withSlots(planSlots).withPhase(SessionPhase.PLAN);
        return completePlan(sessionId, userId, userInput, traceId, workingState, planInvestmentHorizons);
    }

    /**
     * 组合配置流水线：按期限篮子 search/rank 各取一款 → PlanResponseAgent → Guard → 落库。
     */
    private ChatResponse completePlan(String sessionId,
                                      Long userId,
                                      String userInput,
                                      String traceId,
                                      SessionState state,
                                      List<String> planInvestmentHorizons) {
        List<ProductPlanService.PlannedProduct> plannedProducts = productPlanService.planProducts(
                state.sourceMode(), userId, state.slots(), planInvestmentHorizons);

        List<Map<String, Object>> planTrace = new ArrayList<>();
        for (ProductPlanService.PlannedProduct planned : plannedProducts) {
            planTrace.add(traceMap(
                    "investmentHorizon", planned.investmentHorizon(),
                    "matched", planned.matched(),
                    "productId", planned.matched() ? planned.product().id() : null,
                    "productName", planned.matched() ? planned.product().name() : null
            ));
        }
        agentTraceService.recordEvent(
                "PRODUCT_PORTFOLIO_SEARCHED",
                "PLAN",
                Map.of("planInvestmentHorizons", planInvestmentHorizons, "slots", state.slots()),
                Map.of("plannedCount", plannedProducts.size(), "plannedProducts", planTrace)
        );

        boolean anyMatched = plannedProducts.stream().anyMatch(ProductPlanService.PlannedProduct::matched);
        if (!anyMatched) {
            ResponseResult empty = ResponseResult.textOnly(state.sourceMode() == SourceMode.PERSONAL
                    ? "当前个人产品库暂时拼不出组合配置参考，可以补充更多可售产品，或切换到公共示例产品库。"
                    : "公共示例产品库暂时拼不出组合配置参考，可以补充期限、风险偏好或流动性要求后再试。");
            agentTraceService.recordEvent("NO_PRODUCT_PORTFOLIO_MATCHED", "PLAN", state, empty);
            return completeTextOnly(sessionId, traceId, state, Intent.PORTFOLIO_PLAN, empty);
        }

        RecommendResponseAgentService.Result merged = planResponseAgentService.planAndRespond(
                sessionId, userInput, state.sourceMode(), state.slots(), plannedProducts);

        RecommendResult recommend = merged.recommend();
        agentTraceService.recordEvent(
                "PLAN_RESULT_BUILT",
                "PLAN",
                Map.of("strategy", Intent.PORTFOLIO_PLAN.name(), "plannedProducts", planTrace),
                recommend
        );

        ResponseResult response = merged.response();
        agentTraceService.recordEvent("PLAN_RESPONSE_AGENT_RESULT", "RESPONSE", recommend, response);

        RiskGuardResult guard = riskGuardService.check(userInput, Intent.PORTFOLIO_PLAN, recommend, response);
        agentTraceService.recordEvent(
                "COMPLIANCE_GUARD_CHECKED",
                "GUARD",
                Map.of("intent", Intent.PORTFOLIO_PLAN, "response", response),
                guard
        );

        if (!guard.passed()) {
            response = ResponseResult.textOnly(guard.rewriteSuggestion());
            agentTraceService.recordEvent("COMPLIANCE_GUARD_REWRITTEN", "GUARD", guard, response);
        } else {
            agentTraceService.recordEvent("COMPLIANCE_GUARD_REWRITTEN", "GUARD", null, response);
        }

        List<Long> lastIds = recommend.recommendations().stream().map(option -> option.productId()).toList();
        SessionState savedState = state.appendLastRecommendations(lastIds);
        sessionStateService.save(savedState);
        sessionService.appendMessage(sessionId, "assistant", response.speechText(), Intent.PORTFOLIO_PLAN.name(), traceId);

        ChatResponse chatResponse = ChatResponse.answer(
                sessionId, traceId, response.speechText(), response.displayBlocks(), response.nextAction());
        agentTraceService.recordEvent("RESPONSE_READY", "RESPONSE", savedState, chatResponse);
        return chatResponse;
    }

    /**
     * 合规风险分支：返回保守提示，不走推荐链路。
     */
    private ChatResponse handleHealthRisk(String sessionId, String traceId, SessionState state) {
        // 构造纯文本响应，内容为 conservativeMessage 固定文案
        ResponseResult response = ResponseResult.textOnly(riskGuardService.conservativeMessage());
        // 走纯文本完成分支，intent 标记为 RISK_COMPLIANCE
        return completeTextOnly(sessionId, traceId, state, Intent.RISK_COMPLIANCE, response);
    }

    /**
     * 闲聊分支：返回固定引导文案，不调用 LLM。
     */
    private ChatResponse handleChitchat(String sessionId, String traceId, SessionState state) {
        // 构造纯文本响应，内容为 CHITCHAT_REPLY 常量
        ResponseResult response = ResponseResult.textOnly(CHITCHAT_REPLY);
        // 走纯文本完成分支，intent 标记为 CHITCHAT
        return completeTextOnly(sessionId, traceId, state, Intent.OTHER, response);
    }

    /**
     * 完整推荐流水线：检索 → 重排 → LLM 生成理由与口语回复 → Guard 审查 → 持久化并返回。
     */
    private ChatResponse completeRecommendation(String sessionId, Long userId, String userInput, String traceId, SessionState state, List<Long> excludeProductIds) {
        // 构造检索请求：sourceMode + userId + 当前 slots + excludeProductIds（检索层暂不使用 exclude，在 Rank 层过滤）
        List<ProductItem> candidates = productSearchService.search(new ProductSearchRequest(state.sourceMode(), userId, state.slots(), excludeProductIds));
        // Trace 事件：PRODUCT_SEARCHED | 阶段 SEARCH | 输入=slots | 输出=候选数量+candidates 列表
        agentTraceService.recordEvent("PRODUCT_SEARCHED", "SEARCH", state.slots(), Map.of("candidateCount", candidates.size(), "candidates", candidates));

        // 构造排序请求：候选列表 + slots + excludeProductIds，返回 top10
        List<ProductItem> ranked = productRankService.rank(new ProductRankRequest(candidates, state.slots(), excludeProductIds));
        // Trace 事件：PRODUCT_RANKED | 阶段 RANK | 输入=excludeProductIds | 输出=重排后数量+ranked 列表
        agentTraceService.recordEvent("PRODUCT_RANKED", "RANK", Map.of("excludeProductIds", excludeProductIds), Map.of("rankedCount", ranked.size(), "ranked", ranked));

        // 结果为空时，按 sourceMode 返回不同的空库提示文案
        if (ranked.isEmpty()) {
            // PERSONAL 模式提示补充产品或切 PUBLIC；PUBLIC 模式提示补充槽位
            ResponseResult empty = ResponseResult.textOnly(state.sourceMode() == SourceMode.PERSONAL
                    ? "当前个人产品库里暂时没有适配的可售产品，可以补充更多产品，或切换到公共示例产品库。"
                    : "公共示例产品库里暂时没有适配结果，可以补充投资金额、期限、风险偏好或流动性要求。");
            // Trace 事件：NO_PRODUCT_MATCHED | 阶段 RECOMMEND | 输入=state | 输出=空结果提示文案
            agentTraceService.recordEvent("NO_PRODUCT_MATCHED", "RECOMMEND", state, empty);
            // 走纯文本完成分支，intent 保持当前 state.currentIntent()
            return completeTextOnly(sessionId, traceId, state, state.currentIntent(), empty);
        }

        // 调用 RecommendResponseAgent：top3 候选 + 用户原文 + slots → 推荐理由 + speechText + 卡片
        RecommendResponseAgentService.Result merged = recommendResponseAgentService.recommendAndRespond(
                sessionId, userInput, state.sourceMode(), state.slots(), ranked);

        // 从结果中取出 RecommendResult（含 recommendations 列表和 needDisclaimer 标记）
        RecommendResult recommend = merged.recommend();
        // Trace 事件：RECOMMEND_RESULT_BUILT | 阶段 RECOMMEND | 输入=strategy+ranked | 输出=RecommendResult
        String strategy = state.currentIntent() == null ? Intent.INVESTMENT_RECOMMENDATION.name() : state.currentIntent().name();
        agentTraceService.recordEvent("RECOMMEND_RESULT_BUILT", "RECOMMEND", Map.of("strategy", strategy, "ranked", ranked), recommend);

        // 从结果中取出 ResponseResult（含 speechText、displayBlocks、nextAction）
        ResponseResult response = merged.response();
        // Trace 事件：RESPONSE_AGENT_RESULT | 阶段 RESPONSE | 输入=recommend | 输出=ResponseResult
        agentTraceService.recordEvent("RESPONSE_AGENT_RESULT", "RESPONSE", recommend, response);

        // 调用 riskGuardService 检查用户输入 + 最终回复是否含金融合规风险关键词
        RiskGuardResult guard = riskGuardService.check(userInput, state.currentIntent(), recommend, response);
        // Trace 事件：COMPLIANCE_GUARD_CHECKED | 阶段 GUARD | 输入=intent+response | 输出=GuardResult（passed/reasons）
        agentTraceService.recordEvent("COMPLIANCE_GUARD_CHECKED", "GUARD", Map.of("intent", state.currentIntent(), "response", response), guard);

        // Guard 未通过时，用 conservativeMessage 替换 speechText，丢弃原 LLM 回复
        if (!guard.passed()) {
            // 重建纯文本 ResponseResult，内容为 guard.rewriteSuggestion()
            response = ResponseResult.textOnly(guard.rewriteSuggestion());
            // Trace 事件：COMPLIANCE_GUARD_REWRITTEN | 阶段 GUARD | 输入=guard | 输出=替换后的 response
            agentTraceService.recordEvent("COMPLIANCE_GUARD_REWRITTEN", "GUARD", guard, response);
        } else {
            // Guard 通过时也记录事件，表示未改写（COMPLIANCE_GUARD_REWRITTEN 为历史命名，语义=合规检查通过）
            agentTraceService.recordEvent("COMPLIANCE_GUARD_REWRITTEN", "GUARD", null, response);
        }

        // 从推荐结果中提取本轮推荐的 productId 列表，追加到 lastRecommendations 供下轮调整累积排除
        List<Long> lastIds = recommend.recommendations().stream().map(option -> option.productId()).toList();
        // 基于当前 state 累积更新 lastRecommendations 字段
        SessionState savedState = state.appendLastRecommendations(lastIds);

        // 将更新后的会话状态 UPDATE 到 investment_sessions 表
        sessionStateService.save(savedState);

        // 将助手回复 INSERT 到 investment_messages，intent=当前意图名，content=speechText
        sessionService.appendMessage(sessionId, "assistant", response.speechText(), state.currentIntent().name(), traceId);

        // 构造最终 ChatResponse：含 speechText、产品卡片 displayBlocks、nextAction=WAIT_USER
        ChatResponse chatResponse = ChatResponse.answer(sessionId, traceId, response.speechText(), response.displayBlocks(), response.nextAction());

        // Trace 事件：RESPONSE_READY | 阶段 RESPONSE | 输入=savedState | 输出=ChatResponse
        agentTraceService.recordEvent("RESPONSE_READY", "RESPONSE", savedState, chatResponse);

        // 返回带推荐卡片的完整响应
        return chatResponse;
    }

    /**
     * 纯文本分支的统一收尾：更新 intent → 保存状态 → 落库消息 → 返回 ChatResponse。
     */
    private ChatResponse completeTextOnly(String sessionId, String traceId, SessionState state, Intent intent, ResponseResult response) {
        // 将会话 currentIntent 更新为传入的 intent 枚举
        SessionState savedState = state.withIntent(intent);
        // 将更新后的会话状态 UPDATE 到 investment_sessions 表
        sessionStateService.save(savedState);

        // 将助手纯文本回复 INSERT 到 investment_messages
        sessionService.appendMessage(sessionId, "assistant", response.speechText(), intent.name(), traceId);

        // 构造 ChatResponse（无产品卡片，displayBlocks 为空）
        ChatResponse chatResponse = ChatResponse.answer(sessionId, traceId, response.speechText(), response.displayBlocks(), response.nextAction());

        // Trace 事件：RESPONSE_READY | 阶段 RESPONSE | 输入=intent+savedState | 输出=ChatResponse
        agentTraceService.recordEvent("RESPONSE_READY", "RESPONSE", Map.of("intent", intent, "state", savedState), chatResponse);

        // 返回纯文本响应
        return chatResponse;
    }

    /**
     * 将纳秒级开始时间戳转为毫秒耗时。
     */
    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    /**
     * 构造 Trace payload Map，支持 key-value 交替传入，允许 value 为 null。
     */
    private Map<String, Object> traceMap(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        // 每两个元素为一组 key-value，步长 2 遍历
        for (int i = 0; i + 1 < entries.length; i += 2) {
            // 将 key 转 String 后与 value 放入 Map
            result.put(String.valueOf(entries[i]), entries[i + 1]);
        }
        return result;
    }
}

