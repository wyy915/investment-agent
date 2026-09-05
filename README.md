# Investment Agent

银行理财投顾辅助系统，面向银行内部理财经理、客户经理的客户需求分析与产品筛选场景。系统通过多 Agent 工作流完成意图识别、槽位抽取、槽位澄清、产品召回重排、合规解释生成、反馈记录、Trace 排查和离线评估，帮助业务人员把自然语言客户需求转化为可追踪、可解释、可复盘的产品推荐参考。

> 本项目中的产品均为示例数据，不代表真实银行产品，不构成投资建议。正式推荐应结合客户风险测评、产品说明书、风险揭示书和银行适当性管理要求。

## 核心能力

- 对话式投资推荐：根据投资金额、投资期限、风险偏好、流动性需求、收益期望、产品类型、客户画像和限制条件生成候选产品。
- 多轮槽位澄清：客户信息不足时，系统自动追问关键缺失槽位，避免直接给出低质量推荐。
- 产品权限模型：支持全行、分行、客户经理三个产品范围，便于模拟银行内部产品可见性。
- 规则召回与重排：基于风险、期限、流动性、收益类型、客户画像等字段进行产品筛选和打分排序。
- 合规兜底：对收益承诺、风险弱化、适当性不匹配等表述进行拦截或降级处理。
- Trace 全链路观测：记录请求、Agent 调用、状态流转、推荐结果和错误信息，支持排查和回放。
- 离线评估闭环：基于 Trace、规则评分、Judge Agent 和用户反馈生成评估报告。
- 前端演示页面：支持投顾对话、客户经理产品库、全行产品库、Trace 查看和批量评估。

## 技术栈

- Java 21
- Spring Boot 3.3.13
- MyBatis
- MySQL 8
- AgentScope Spring Boot Starter
- DashScope Chat Model
- Reactor
- Lombok
- 原生 HTML / CSS / JavaScript

## 工作流

一次推荐请求的主要链路如下：

1. 前端调用 `/api/v1/investment/chat`，提交客户自然语言需求和数据源模式。
2. `InvestmentOrchestratorService` 加载或创建会话状态，写入用户消息并开启 Trace。
3. `IntentAgentService` 调用意图识别 Agent，输出 intent、slots、confidence。
4. `IntentReviseService` 根据历史会话状态和规则修正低置信或上下文相关意图。
5. `SlotMergeService` 合并历史槽位和本轮槽位。
6. 若关键槽位不足，`ClarifyAgentService` 生成追问。
7. 若进入推荐链路，`ProductSearchService` 按权限和槽位召回产品。
8. `ProductRankService` 根据风险、期限、流动性、金额等规则打分重排。
9. `RiskGuardService` 做合规风险检查。
10. `RecommendResponseAgentService` 或 `PlanResponseAgentService` 生成推荐解释和展示块。
11. 系统写入会话状态、助手消息和 Trace，返回前端展示。

## 目录结构

```text
src/main/java/com/investment
  agent/          Agent 构建、工厂和 Prompt 加载
  config/         AgentScope 与模型配置
  controller/     HTTP 接口
  mapper/         MyBatis Mapper
  model/          请求、响应、会话和产品模型
  service/        编排、意图、澄清、推荐、产品、Trace、评估等服务
  util/           JSON 与槽位工具

src/main/resources
  db/             初始化、迁移、示例产品 SQL
  investment/     Agent Prompt
  mapper/         MyBatis XML
  static/         前端页面和静态资源
```

## 数据库初始化

默认数据库为 `investment_db`，配置在：

```text
src/main/resources/application.yml
```

首次初始化可执行：

```powershell
Get-Content .\src\main\resources\db\investment_db.sql | & "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" --default-character-set=utf8mb4 -h localhost -P 3306 -u root -p你的密码 investment_db
```

如果数据库还不存在，可以先创建：

```powershell
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" --default-character-set=utf8mb4 -h localhost -P 3306 -u root -p你的密码 -e "CREATE DATABASE IF NOT EXISTS investment_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

补充示例产品：

```powershell
Get-Content .\src\main\resources\db\add_sample_investment_products.sql | & "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" --default-character-set=utf8mb4 -h localhost -P 3306 -u root -p你的密码 investment_db

Get-Content .\src\main\resources\db\add_more_public_branch_products.sql | & "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" --default-character-set=utf8mb4 -h localhost -P 3306 -u root -p你的密码 investment_db
```

## 本地启动

确认 `application.yml` 中的数据库账号、密码和 `agentscope.dashscope.api-key` 已配置。

使用 Maven：

```bash
mvn spring-boot:run
```

或在 IntelliJ IDEA 中运行主类：

```text
com.investment.InvestmentApplication
```

启动后访问：

```text
http://localhost:8080/
```

前端路由：

- `#/investment`：首页
- `#/investment/chat`：投顾对话
- `#/investment/products/personal`：客户经理产品库
- `#/investment/products/public`：全行产品库
- `#/admin/traces`：Trace 排查
- `#/admin/evaluations`：离线评估

## 接口清单

基础前缀：

```text
/api/v1/investment
```

常用接口：

- `POST /chat`：对话推荐
- `POST /sessions`：创建会话
- `GET /slot-options`：查询槽位选项
- `GET /products/public`：查询全行和分行产品
- `GET /products/personal`：查询客户经理产品
- `POST /products/personal`：新增客户经理产品
- `PUT /products/personal/{productId}`：更新客户经理产品
- `DELETE /products/personal/{productId}`：删除客户经理产品
- `POST /feedback`：保存推荐反馈
- `GET /debug/traces`：查询 Trace 列表
- `GET /debug/traces/{traceId}`：查询 Trace 详情
- `PUT /debug/traces/{traceId}/label`：人工标注 Trace
- `POST /evaluations`：批量评估

所有接口默认使用请求头：

```text
X-User-Id: 1
```

## 示例请求

```http
POST /api/v1/investment/chat
X-User-Id: 1
Content-Type: application/json

{
  "message": "客户有5万元工资结余，希望保守，随时可用，收益高于活期，优先现金管理或存款",
  "sourceMode": "PUBLIC",
  "context": {}
}
```

推荐在前端测试这些问题：

```text
客户有5万元工资结余，希望保守，随时可用，收益高于活期，优先现金管理或存款
客户有20万元闲置资金，计划持有3个月，风险偏好稳健，短期可能用钱，优先固定收益类
客户有50万元，6个月内大概率不用，风险偏好稳健，可以接受封闭持有，想要稳健收益
客户有100万元以上，计划持有1年以上，风险偏好平衡，可接受净值波动，想做混合类配置
企业主客户有30万元，期限1年以上，风险偏好进取，可封闭持有，能接受波动换收益
退休客户有10万元，风险偏好保守，3个月内可能用钱，只看低风险产品
20万元怎么做多期限组合配置，客户风险偏好稳健，资金一部分随时可用，一部分持有6个月以上
```

## 数据表

核心表：

- `bank_branch`：分行信息
- `bank_employee`：客户经理信息
- `investment_slot_option`：投资槽位选项
- `investment_product`：投资产品库
- `investment_sessions`：会话状态
- `investment_messages`：会话消息
- `investment_request_trace`：请求 Trace
- `recommend_feedback`：推荐反馈

## 合规说明

系统输出仅作为内部产品筛选和投顾辅助参考，不承诺收益，不保证本金，不替代正式销售流程。面向客户展示或销售前，应完成客户风险测评、产品适当性匹配、风险揭示、产品说明书核验和必要的双录流程。
