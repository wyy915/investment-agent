package com.investment.agent.builder;

import com.investment.agent.loader.PromptLoader;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.Model;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * PlanResponseAgent 构建器：多餐规划理由与口语包装。
 */
@Component
public class PlanResponseAgentBuilder {

    private final Model mainModel;
    private final PromptLoader promptLoader;

    public PlanResponseAgentBuilder(@Qualifier("InvestmentMainChatModel") Model mainModel, PromptLoader promptLoader) {
        this.mainModel = mainModel;
        this.promptLoader = promptLoader;
    }

    public ReActAgent build() {
        return ReActAgent.builder()
                .name("investment_plan_response_agent")
                .model(mainModel)
                .sysPrompt(promptLoader.load("investment/prompts/plan-response.txt"))
                .memory(new InMemoryMemory())
                .build();
    }
}
