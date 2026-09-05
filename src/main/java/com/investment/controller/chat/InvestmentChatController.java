package com.investment.controller.chat;

import com.investment.constants.InvestmentConstants;
import com.investment.model.ChatRequest;
import com.investment.model.ChatResponse;
import com.investment.service.orchestrator.InvestmentOrchestratorService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 投资推荐对话 HTTP 入口。
 * 本层只做参数透传，完整状态机由 {@link InvestmentOrchestratorService#investmentChat} 驱动。
 */
@RestController
@RequestMapping("/api/v1/investment")
public class InvestmentChatController {

    /** 多 Agent 编排服务，注入后用于处理每轮对话。 */
    private final InvestmentOrchestratorService orchestratorService;

    /** Spring 构造器注入 Orchestrator。 */
    public InvestmentChatController(InvestmentOrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    /**
     * POST /api/v1/investment/chat — 同步对话接口。
     * 接收用户消息，返回澄清追问或推荐结果（含产品卡片）。
     */
    @PostMapping("/chat")
    public ChatResponse investmentChat(
            // 从请求头 X-User-Id 读取用户 ID，缺省为 1 便于本地调试
            @RequestHeader(value = InvestmentConstants.USER_ID, defaultValue = "1") Long userId,
            // 从请求体反序列化 ChatRequest（sessionId、message、sourceMode）
            @RequestBody ChatRequest request
    ) {
        // 委托 Orchestrator 执行完整状态机，直接返回 ChatResponse
        return orchestratorService.investmentChat(userId, request);
    }
}

