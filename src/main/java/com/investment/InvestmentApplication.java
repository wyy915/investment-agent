package com.investment;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 银行投资推荐 Agent 应用启动入口。
 * <p>
 * 与 {@code com.agent.AgentApplication} 相互独立：本类只扫描 {@code com.investment} 包下的组件，
 * 运行时使用 {@code com.investment.InvestmentApplication} 作为主类即可启动投资推荐服务。
 * </p>
 */
@MapperScan("com.investment.mapper")
@SpringBootApplication
public class InvestmentApplication {

    public static void main(String[] args) {
        SpringApplication.run(InvestmentApplication.class, args);
    }
}

