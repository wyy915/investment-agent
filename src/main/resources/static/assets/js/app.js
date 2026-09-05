(function () {
    "use strict";
    const app = document.getElementById("app");
    const toast = document.getElementById("toast");
    const userIdInput = document.getElementById("userIdInput");
    const SLOT_LABELS = {
        investmentAmount: "投资金额",
        investmentHorizon: "投资期限",
        riskPreference: "风险偏好",
        liquidityNeed: "流动性需求",
        returnExpectation: "收益期望",
        productType: "产品类型",
        customerProfile: "客户画像",
        restriction: "限制条件"
    };
    const SCOPE_LABELS = {
        "全行": "全行",
        "分行": "分行",
        "客户经理": "客户经理"
    };
    const SOURCE_MODE_LABELS = {
        PERSONAL: "客户经理产品库",
        PUBLIC: "全行产品库"
    };
    const SUGGESTED_PROMPTS = [
        "客户有5万元工资结余，希望保守，随时可用，收益高于活期，优先现金管理或存款",
        "客户有20万元闲置资金，计划持有3个月，风险偏好稳健，短期可能用钱，优先固定收益类",
        "客户有50万元，6个月内大概率不用，风险偏好稳健，可以接受封闭持有，想要稳健收益",
        "客户有100万元以上，计划持有1年以上，风险偏好平衡，可接受净值波动，想做混合类配置",
        "企业主客户有30万元，期限1年以上，风险偏好进取，可封闭持有，能接受波动换收益",
        "退休客户有10万元，风险偏好保守，3个月内可能用钱，只看低风险产品",
        "客户不买基金，有20万元，计划持有6个月，风险偏好稳健，希望短期可能用钱",
        "20万元怎么做多期限组合配置，客户风险偏好稳健，资金一部分随时可用，一部分持有6个月以上"
    ];
    const INTENTS = [
        "INVESTMENT_RECOMMENDATION",
        "CLARIFY_NEEDED",
        "INVESTMENT_ADJUST",
        "PORTFOLIO_PLAN",
        "RISK_COMPLIANCE",
        "PRODUCT_EXPLANATION",
        "OTHER"
    ];
    const state = {
        home: { loaded: false, personalCount: 0, publicCount: 0 },
        slotOptions: null,
        personalProducts: [],
        publicProducts: [],
        editingProduct: null,
        pendingQuickMessage: "",
        chat: {
            sourceMode: "PERSONAL",
            sessionId: null,
            sending: false,
            messages: [
                {
                    role: "assistant",
                    text: "你好，我可以根据客户资金规模、期限、风险偏好和流动性需求，辅助筛选理财产品并生成合规解释。"
                }
            ]
        },
        traces: {
            rows: [],
            selected: null,
            loading: false,
            filters: defaultTraceFilters()
        },
        evaluation: {
            report: null,
            loading: false,
            form: defaultRangeForm()
        },
        publicProductsLoading: false
    };
    function defaultRangeForm() {
        const end = new Date();
        const start = new Date(end.getTime() - 24 * 60 * 60 * 1000);
        return {
            startAt: toLocalInputValue(start),
            endAt: toLocalInputValue(end),
            limit: 50,
            includeLlmJudge: false
        };
    }
    function defaultTraceFilters() {
        const range = defaultRangeForm();
        return {
            startAt: range.startAt,
            endAt: range.endAt,
            onlyUnlabeled: false,
            limit: 50,
            sessionId: ""
        };
    }
    function toLocalInputValue(date) {
        const pad = (value) => String(value).padStart(2, "0");
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
    }
    function escapeHtml(value) {
        return String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;");
    }
    function safeJson(value) {
        if (value === null || value === undefined || value === "") {
            return "";
        }
        try {
            const parsed = typeof value === "string" ? JSON.parse(value) : value;
            return JSON.stringify(parsed, null, 2);
        } catch (error) {
            return String(value);
        }
    }
    function isPositiveIntegerText(value) {
        return /^[1-9]\d*$/.test(String(value ?? "").trim());
    }
    function showToast(message, type) {
        toast.textContent = message;
        toast.className = `toast show ${type === "error" ? "error" : ""}`;
        window.clearTimeout(showToast.timer);
        showToast.timer = window.setTimeout(() => {
            toast.className = "toast";
        }, 3200);
    }
    function setLoading(button, loadingText) {
        if (!button) {
            return () => {};
        }
        const oldText = button.textContent;
        button.disabled = true;
        button.textContent = loadingText || "处理中...";
        return () => {
            button.disabled = false;
            button.textContent = oldText;
        };
    }
    async function guard(action, successMessage) {
        try {
            const result = await action();
            if (successMessage) {
                showToast(successMessage);
            }
            return result;
        } catch (error) {
            showToast(error.message || "操作失败", "error");
            throw error;
        }
    }
    function currentRoute() {
        return (location.hash || "#/investment").slice(1).split("?")[0] || "/investment";
    }
    function navigate(route) {
        location.hash = route;
    }
    function sourceModeLabel(mode) {
        return SOURCE_MODE_LABELS[mode] || mode || "未知";
    }
    function setActiveNav(route) {
        document.querySelectorAll("[data-nav]").forEach((item) => {
            item.classList.toggle("active", item.dataset.nav === route);
        });
    }
    function render() {
        const route = currentRoute();
        setActiveNav(route);
        if (route === "/investment") {
            renderHome();
        } else if (route === "/investment/chat") {
            renderChat();
        } else if (route === "/investment/products/personal") {
            renderPersonalProducts();
        } else if (route === "/investment/products/public") {
            renderPublicProducts();
        } else if (route === "/admin/traces") {
            renderTraces();
        } else if (route === "/admin/evaluations") {
            renderEvaluations();
        } else {
            navigate("/investment");
        }
        app.focus({ preventScroll: true });
    }
    function renderHome() {
        app.innerHTML = `
            <section class="hero">
                <div class="hero-panel">
                    <span class="badge">多 Agent 理财投顾辅助</span>
                    <h1>银行理财产品辅助筛选工作台</h1>
                    <p>面向银行内部客户经理和理财经理，辅助理解客户投资需求、补齐适当性信息、检索产品库并生成合规解释，支持全行、分行和客户经理三级权限范围。</p>
                    <div class="hero-actions">
                        <a class="btn primary" href="#/investment/chat">进入投顾对话</a>
                        <a class="btn soft" href="#/investment/products/personal">客户经理产品库</a>
                        <a class="btn soft" href="#/investment/products/public">全行产品库</a>
                        <a class="btn ghost" href="#/admin/traces">查看 Trace</a>
                    </div>
                </div>
                <aside class="grid stats">
                    ${statCard("客户经理产品", state.home.loaded ? state.home.personalCount : "加载中", "当前用户可维护的产品池")}
                    ${statCard("全行产品", state.home.loaded ? state.home.publicCount : "加载中", "覆盖全行可见的产品")}
                    ${statCard("当前用户", InvestmentApi.getUserId(), "所有请求会带上 X-User-Id")}
                </aside>
            </section>
            <section class="grid three" style="margin-top: 18px;">
                ${featureCard("投顾对话", "按自然语言输入客户需求，系统返回追问、候选产品和内部解释。", "#/investment/chat")}
                ${featureCard("客户经理产品库", "维护权限范围、风险等级、期限、流动性、起购金额、收益类型和客户标签。", "#/investment/products/personal")}
                ${featureCard("全行产品库", "浏览全行可见的示例产品，快速切换到 PUBLIC 模式体验推荐。", "#/investment/products/public")}
            </section>
            <section class="section" style="margin-top: 18px;">
                <div class="card-title">
                    <div>
                        <h2>常用入口</h2>
                        <p>首页直接进入最常用的两个工作面。</p>
                    </div>
                </div>
                <div class="hero-actions" style="margin-top: 0;">
                    <a class="btn primary" href="#/investment/chat">去对话</a>
                    <a class="btn soft" href="#/investment/products/personal">看客户经理产品库</a>
                    <a class="btn soft" href="#/investment/products/public">看全行产品库</a>
                    <a class="btn ghost" href="#/admin/evaluations">进入评估后台</a>
                </div>
            </section>
            <section class="section" style="margin-top: 18px;">
                <div class="card-title">
                    <div>
                        <h2>推荐提问方式</h2>
                        <p>尽量同时说清金额、期限、风险偏好、流动性和偏好的产品类型，命中率会高很多。</p>
                    </div>
                </div>
                <div class="chips">
                    ${SUGGESTED_PROMPTS.slice(0, 6).map((text) => `<a class="chip selected" href="#/investment/chat" data-action="quick-message" data-message="${escapeHtml(text)}">${escapeHtml(text)}</a>`).join("")}
                </div>
            </section>
            <section class="section" style="margin-top: 18px;">
                <div class="card-title">
                    <div>
                        <h2>应用案例</h2>
                        <p>用一个真实工作场景看看怎么更高效地用它。</p>
                    </div>
                </div>
                <div class="grid two">
                    <article class="card">
                        <h3>支行客户经理的日常筛选</h3>
                        <p class="muted">客户有 20 万闲置资金，6 个月内可能要用，希望稳一点、流动性别太差。</p>
                        <p class="muted">做法：切到“客户经理产品库”，确认产品范围、风险等级和期限后，直接在对话框输入需求，让系统先追问缺失信息，再给出候选产品和合规解释。</p>
                    </article>
                    <article class="card">
                        <h3>更好的用法</h3>
                        <p class="muted">先用产品维护页把全行、分行、客户经理三个范围梳理清楚，再用 Trace 页回看每次推荐为什么命中或没命中。</p>
                        <p class="muted">这样能把系统从“一个聊天机器人”变成“标准化的内部筛选台”，减少重复搜索，也更方便做质检和复盘。</p>
                    </article>
                </div>
            </section>
        `;
        loadHomeStats();
    }
    function statCard(label, value, desc) {
        return `
            <div class="stat-card">
                <span class="muted">${escapeHtml(label)}</span>
                <strong>${escapeHtml(value)}</strong>
                <p class="muted">${escapeHtml(desc)}</p>
            </div>
        `;
    }
    function featureCard(title, desc, href) {
        return `
            <article class="card">
                <div class="card-title">
                    <div>
                        <h3>${escapeHtml(title)}</h3>
                        <p>${escapeHtml(desc)}</p>
                    </div>
                </div>
                <a class="btn soft" href="${href}">进入</a>
            </article>
        `;
    }
    async function loadHomeStats() {
        if (state.home.loaded) {
            return;
        }
        try {
            const [personal, publicProducts] = await Promise.all([
                InvestmentApi.listPersonalProducts(),
                InvestmentApi.listPublicProducts()
            ]);
            state.home = {
                loaded: true,
                personalCount: personal.length,
                publicCount: publicProducts.length
            };
            if (currentRoute() === "/investment") {
                renderHome();
            }
        } catch (error) {
            showToast(error.message || "首页数据加载失败", "error");
        }
    }
    function renderChat() {
        app.innerHTML = `
            <section class="chat-layout">
                <div class="section chat-window">
                    <div class="card-title">
                        <div>
                            <h2>投顾对话</h2>
                            <p>当前会话：${state.chat.sessionId ? escapeHtml(state.chat.sessionId) : "尚未创建，发送消息时自动创建"} · 当前数据源：<span class="badge">${escapeHtml(sourceModeLabel(state.chat.sourceMode))}</span></p>
                        </div>
                        <div class="inline-actions">
                            <button class="btn ${state.chat.sourceMode === "PERSONAL" ? "soft" : "ghost"}" data-action="set-source" data-source="PERSONAL">客户经理产品库</button>
                            <button class="btn ${state.chat.sourceMode === "PUBLIC" ? "soft" : "ghost"}" data-action="set-source" data-source="PUBLIC">全行产品库</button>
                            <button class="btn ghost" data-action="new-session">新会话</button>
                        </div>
                    </div>
                    <div id="messages" class="messages">${state.chat.messages.map(renderMessage).join("")}</div>
                    <form id="chatForm" class="composer">
                        <textarea name="message" placeholder="例如：客户有20万闲置资金，半年内可能用到，希望风险低一点" required></textarea>
                        <button class="btn primary" type="submit">${state.chat.sending ? "发送中..." : "发送"}</button>
                    </form>
                </div>
                <aside class="grid">
                    <div class="card">
                        <div class="card-title">
                            <div>
                            <h3>快捷输入</h3>
                                <p>点击后可直接填入输入框。</p>
                            </div>
                        </div>
                        <div class="chips">
                            ${SUGGESTED_PROMPTS.map((text) => `<button class="chip" data-action="quick-message" data-message="${escapeHtml(text)}">${escapeHtml(text)}</button>`).join("")}
                        </div>
                    </div>
                    <div class="card">
                        <h3>边界提示</h3>
                        <p class="muted">输出仅用于银行内部辅助参考，具体推荐需结合客户风险测评、产品说明书、风险揭示和适当性要求。</p>
                        <div class="button-row">
                            <a class="btn soft" href="#/investment/products/personal">维护产品</a>
                            <a class="btn ghost" href="#/investment/products/public">看全行产品库</a>
                        </div>
                    </div>
                </aside>
            </section>
        `;
        if (state.pendingQuickMessage) {
            const input = document.querySelector("#chatForm textarea[name=message]");
            if (input) {
                input.value = state.pendingQuickMessage;
                input.focus();
                state.pendingQuickMessage = "";
            }
        }
        scrollMessagesToBottom();
    }
    function renderMessage(message) {
        const productCards = (message.products || []).map((product) => renderProductCard(product, { feedback: true, sessionId: message.sessionId })).join("");
        const missingSlots = message.missingSlots && message.missingSlots.length
            ? `<div class="chips">${message.missingSlots.map((slot) => `<span class="chip selected">${escapeHtml(SLOT_LABELS[slot] || slot)}</span>`).join("")}</div>`
            : "";
        const trace = message.traceId
            ? `<span>traceId：<a href="#/admin/traces" data-action="open-trace" data-trace-id="${escapeHtml(message.traceId)}">${escapeHtml(message.traceId)}</a></span>`
            : "";
        return `
            <article class="message ${message.role}">
                <div class="bubble">${escapeHtml(message.text)}</div>
                ${missingSlots}
                ${productCards ? `<div class="grid">${productCards}</div>` : ""}
                ${trace ? `<div class="message-meta">${trace}</div>` : ""}
            </article>
        `;
    }
    function scrollMessagesToBottom() {
        const messages = document.getElementById("messages");
        if (messages) {
            messages.scrollTop = messages.scrollHeight;
        }
    }
    async function submitChat(form) {
        const messageInput = form.elements.message;
        const message = messageInput.value.trim();
        if (!message || state.chat.sending) {
            return;
        }
        state.chat.messages.push({ role: "user", text: message });
        messageInput.value = "";
        state.chat.sending = true;
        renderChat();
        try {
            if (!state.chat.sessionId) {
                const session = await InvestmentApi.createSession();
                state.chat.sessionId = session.sessionId;
            }
            const response = await InvestmentApi.chat({
                sessionId: state.chat.sessionId,
                message,
                sourceMode: state.chat.sourceMode,
                context: {}
            });
            state.chat.sessionId = response.sessionId || state.chat.sessionId;
            state.chat.messages.push({
                role: "assistant",
                text: response.clarifyQuestion || response.speechText || "我已经处理完这轮请求。",
                responseType: response.responseType,
                products: response.displayBlocks || [],
                missingSlots: response.missingSlots || [],
                traceId: response.traceId,
                sessionId: response.sessionId || state.chat.sessionId
            });
        } catch (error) {
            showToast(error.message || "聊天请求失败", "error");
            state.chat.messages.push({ role: "assistant", text: "这轮请求失败了，请稍后重试。" });
        } finally {
            state.chat.sending = false;
            renderChat();
        }
    }
    function resetChat() {
        state.chat.sessionId = null;
        state.chat.messages = [
            {
                role: "assistant",
                text: "已开启新会话。告诉我客户的资金规模、期限、风险偏好或流动性要求，我来辅助筛选理财产品。"
            }
        ];
        renderChat();
    }
    async function renderPersonalProducts() {
        if (!state.slotOptions) {
            app.innerHTML = `<section class="section"><div class="empty">标签字典加载中...</div></section>`;
            await ensureSlotOptions();
            if (currentRoute() !== "/investment/products/personal") {
                return;
            }
        }
        await ensurePersonalProducts();
        if (currentRoute() !== "/investment/products/personal") {
            return;
        }
        app.innerHTML = `
            <section class="split">
                <div class="section">
                    <div class="card-title">
                        <div>
                            <h2>客户经理产品库</h2>
                            <p>维护理财产品的权限范围、风险等级、期限、流动性和适配标签。</p>
                        </div>
                        <button class="btn primary" data-action="new-product">新增产品</button>
                    </div>
                    <div id="personalProductList">${renderProductList(state.personalProducts, { editable: true })}</div>
                </div>
                <aside class="section">
                    ${renderProductForm()}
                </aside>
            </section>
        `;
    }
    function renderProductForm() {
        const product = state.editingProduct || emptyProduct();
        const title = product.id ? "编辑产品" : "新增产品";
        return `
            <div class="card-title">
                <div>
                    <h3>${title}</h3>
                    <p>权限范围、产品类型、风险等级、期限和流动性为必选项。</p>
                </div>
            </div>
            <form id="productForm" class="form-grid">
                <input type="hidden" name="productId" value="${escapeHtml(product.id || "")}">
                <div class="field full">
                    <label for="productName">产品名称</label>
                    <input id="productName" name="name" value="${escapeHtml(product.name || "")}" placeholder="例如：示例稳健固收产品" required>
                </div>
                <div class="field">
                    <label for="productCode">产品代码</label>
                    <input id="productCode" name="productCode" value="${escapeHtml(product.productCode || "")}" placeholder="例如：DEMO-FI-180">
                </div>
                <div class="field">
                    <label for="scopeType">权限范围</label>
                    <select id="scopeType" name="scopeType" required>
                        ${Object.keys(SCOPE_LABELS).map((scopeType) => `<option value="${scopeType}" ${(product.scopeType || "客户经理") === scopeType ? "selected" : ""}>${SCOPE_LABELS[scopeType]}</option>`).join("")}
                    </select>
                </div>
                <div class="field">
                    <label for="scopeBranchId">分行 ID</label>
                    <input id="scopeBranchId" name="scopeBranchId" type="number" min="1" step="1" value="${escapeHtml(product.scopeBranchId || "")}" placeholder="仅分行范围需要">
                </div>
                <div class="field">
                    <label for="scopeManagerId">客户经理 ID</label>
                    <input id="scopeManagerId" name="scopeManagerId" type="number" min="1" step="1" value="${escapeHtml(product.scopeManagerId || "")}" placeholder="客户经理范围可留空">
                </div>
                <p class="field-hint full">全行范围不需要填写分行或客户经理 ID；客户经理范围可留空，系统默认当前用户；分行范围需要填写实际分行 ID。</p>
                <div class="field">
                    <label for="minAmount">起购金额</label>
                    <input id="minAmount" name="minAmount" type="number" min="0" step="0.01" value="${escapeHtml(product.minAmount || "")}" placeholder="例如：10000">
                </div>
                <div class="field">
                    <label for="returnType">收益类型</label>
                    <input id="returnType" name="returnType" value="${escapeHtml(product.returnType || "")}" placeholder="例如：稳健收益">
                </div>
                <div class="field">
                    <label for="status">产品状态</label>
                    <select id="status" name="status">
                        ${["AVAILABLE", "ON_SALE", "OFF_SHELF"].map((status) => `<option value="${status}" ${(product.status || "AVAILABLE") === status ? "selected" : ""}>${status}</option>`).join("")}
                    </select>
                </div>
                <p class="field-hint full">标签下拉框支持多选：Windows 按住 Ctrl，Mac 按住 Command 点击可多项选择。</p>
                ${Object.entries(SLOT_LABELS).map(([key, label]) => renderSlotPicker(key, label, selectedSlotValues(product, key))).join("")}
                <div class="field full">
                    <div class="button-row">
                        <button class="btn primary" type="submit">${product.id ? "保存修改" : "创建产品"}</button>
                        <button class="btn ghost" type="button" data-action="cancel-edit">清空</button>
                    </div>
                </div>
            </form>
        `;
    }
    function renderSlotPicker(key, label, selected) {
        const options = state.slotOptions && state.slotOptions[key] ? state.slotOptions[key] : [];
        const selectedSet = new Set(selected || []);
        const required = ["productType", "riskPreference", "investmentHorizon", "liquidityNeed"].includes(key);
        return `
            <div class="field">
                <label for="slot-${escapeHtml(key)}">${escapeHtml(label)}${required ? "（必选）" : ""}</label>
                <select
                    id="slot-${escapeHtml(key)}"
                    class="slot-select"
                    name="${escapeHtml(key)}"
                    multiple
                    size="5"
                    ${required ? "required" : ""}
                >
                    ${options.map((option) => {
                        const isSelected = selectedSet.has(option);
                        return `<option value="${escapeHtml(option)}" ${isSelected ? "selected" : ""}>${escapeHtml(option)}</option>`;
                    }).join("")}
                </select>
            </div>
        `;
    }
    function emptyProduct() {
        return {
            name: "",
            productCode: "",
            scopeType: "客户经理",
            scopeBranchId: "",
            scopeManagerId: "",
            minAmount: "",
            returnType: "",
            status: "AVAILABLE",
            investmentAmount: [],
            investmentHorizon: [],
            riskPreference: [],
            liquidityNeed: [],
            returnExpectation: [],
            productType: [],
            customerProfile: [],
            restriction: []
        };
    }
    function renderProductList(products, options) {
        if (!products.length) {
            return `<div class="empty">暂无产品。可以先新增可用于内部筛选的理财产品。</div>`;
        }
        return `<div class="grid two">${products.map((product) => renderProductCard(product, options || {})).join("")}</div>`;
    }
    function renderProductCard(product, options) {
        const editable = options && options.editable;
        const feedback = options && options.feedback;
        return `
            <article class="product-card">
                <header>
                    <div>
                        <h3>${escapeHtml(product.name)}</h3>
                        <p class="muted">${escapeHtml([product.productCode, product.productType, product.riskLevel, product.status].filter(Boolean).join(" · ") || product.sourceType || "")}</p>
                        <p class="muted">${escapeHtml(scopeSummary(product))}</p>
                    </div>
                    ${product.matchScore ? `<span class="score">匹配 ${Math.round(product.matchScore * 100)}%</span>` : ""}
                </header>
                <p class="muted">${escapeHtml([product.investmentHorizon && `期限 ${product.investmentHorizon}`, product.liquidityType && `流动性 ${product.liquidityType}`, product.minAmount !== undefined && product.minAmount !== null && `起购 ${product.minAmount}`, product.returnType && `收益类型 ${product.returnType}`].filter(Boolean).join(" · "))}</p>
                <div class="chips">${productTags(product).map((tag) => `<span class="chip selected">${escapeHtml(tag)}</span>`).join("")}</div>
                ${editable ? `
                    <div class="button-row">
                        <button class="btn soft" data-action="edit-product" data-id="${escapeHtml(product.id)}">编辑</button>
                        <button class="btn ghost" data-action="delete-product" data-id="${escapeHtml(product.id)}">删除</button>
                    </div>
                ` : ""}
                ${feedback ? `
                    <div class="button-row">
                        <button class="btn soft" data-action="feedback" data-action-value="ADOPT" data-product-id="${escapeHtml(product.id)}" data-session-id="${escapeHtml(options.sessionId || "")}">采纳</button>
                        <button class="btn ghost" data-action="feedback" data-action-value="NEED_MANUAL_ADJUST" data-product-id="${escapeHtml(product.id)}" data-session-id="${escapeHtml(options.sessionId || "")}">需人工调整</button>
                        <button class="btn ghost" data-action="feedback" data-action-value="UNSUITABLE" data-product-id="${escapeHtml(product.id)}" data-session-id="${escapeHtml(options.sessionId || "")}">不适合</button>
                    </div>
                ` : ""}
            </article>
        `;
    }
    function selectedSlotValues(product, key) {
        const listValue = (value) => Array.isArray(value) ? value : (value ? [value] : []);
        if (key === "investmentAmount") {
            return listValue(product.investmentAmount);
        }
        if (key === "investmentHorizon") {
            return listValue(product.investmentHorizon);
        }
        if (key === "riskPreference") {
            return listValue(product.riskPreference || product.riskLevel);
        }
        if (key === "liquidityNeed") {
            return listValue(product.liquidityNeed || product.liquidityType);
        }
        if (key === "returnExpectation") {
            return listValue(product.returnExpectation || product.returnType);
        }
        if (key === "productType") {
            return listValue(product.productType);
        }
        if (key === "customerProfile") {
            return listValue(product.customerProfile);
        }
        if (key === "restriction") {
            return listValue(product.restriction);
        }
        return [];
    }
    function productTags(product) {
        const tags = [];
        const addMany = (label, value) => {
            const values = Array.isArray(value) ? value : (value ? [value] : []);
            values.forEach((item) => {
                tags.push(`${label}：${item}`);
            });
        };
        addMany(SLOT_LABELS.investmentAmount, product.investmentAmount);
        addMany(SLOT_LABELS.investmentHorizon, product.investmentHorizon);
        addMany(SLOT_LABELS.riskPreference, product.riskLevel || product.riskPreference);
        addMany(SLOT_LABELS.liquidityNeed, product.liquidityType || product.liquidityNeed);
        addMany(SLOT_LABELS.returnExpectation, product.returnType || product.returnExpectation);
        addMany(SLOT_LABELS.productType, product.productType);
        addMany(SLOT_LABELS.customerProfile, product.customerProfile);
        addMany(SLOT_LABELS.restriction, product.restriction);
        return tags;
    }

    function scopeSummary(product) {
        const scopeType = product.scopeType || "全行";
        const pieces = [`范围 ${SCOPE_LABELS[scopeType] || scopeType}`];
        if (scopeType === "分行" && product.scopeBranchId) {
            pieces.push(`分行ID ${product.scopeBranchId}`);
        }
        if (scopeType === "客户经理" && product.scopeManagerId) {
            pieces.push(`客户经理ID ${product.scopeManagerId}`);
        }
        return pieces.join(" · ");
    }
    async function ensurePersonalProducts(force) {
        if (!force && state.personalProducts.length) {
            return;
        }
        try {
            state.personalProducts = await InvestmentApi.listPersonalProducts();
            state.home.loaded = false;
            if (currentRoute() === "/investment/products/personal") {
                document.getElementById("personalProductList").innerHTML = renderProductList(state.personalProducts, { editable: true });
            }
        } catch (error) {
            showToast(error.message || "客户经理产品加载失败", "error");
        }
    }
    async function ensureSlotOptions() {
        if (state.slotOptions) {
            return;
        }
        try {
            state.slotOptions = await InvestmentApi.slotOptions();
        } catch (error) {
            showToast(error.message || "槽位字典加载失败", "error");
            throw error;
        }
    }
    async function saveProduct(form) {
        const { id, payload } = productPayloadFromForm(form);
        if (!payload.name) {
            showToast("请填写产品名称", "error");
            return;
        }
        if (!payload.productType.length || !payload.riskPreference.length || !payload.investmentHorizon.length || !payload.liquidityNeed.length) {
            showToast("请至少选择产品类型、风险等级、期限和流动性", "error");
            return;
        }
        if (payload.scopeType === "分行" && !payload.scopeBranchId) {
            showToast("选择分行范围时，请填写分行 ID", "error");
            return;
        }
        if (payload.scopeBranchId && !isPositiveIntegerText(payload.scopeBranchId)) {
            showToast("分行 ID 必须是正整数", "error");
            return;
        }
        if (payload.scopeManagerId && !isPositiveIntegerText(payload.scopeManagerId)) {
            showToast("客户经理 ID 必须是正整数", "error");
            return;
        }
        const submitPayload = {
            ...payload,
            scopeBranchId: payload.scopeBranchId ? Number(payload.scopeBranchId) : null,
            scopeManagerId: payload.scopeManagerId ? Number(payload.scopeManagerId) : null
        };
        if (submitPayload.scopeType === "客户经理" && submitPayload.scopeManagerId == null) {
            submitPayload.scopeManagerId = Number(InvestmentApi.getUserId());
        }
        if (submitPayload.scopeType === "全行") {
            submitPayload.scopeBranchId = null;
            submitPayload.scopeManagerId = null;
        }
        const restore = setLoading(form.querySelector("button[type=submit]"), "保存中...");
        try {
            await guard(async () => {
                if (id) {
                    return InvestmentApi.updatePersonalProduct(id, submitPayload);
                }
                return InvestmentApi.createPersonalProduct(submitPayload);
            }, id ? "产品已更新" : "产品已创建");
            state.editingProduct = null;
            await ensurePersonalProducts(true);
            renderPersonalProducts();
        } finally {
            restore();
        }
    }
    function productPayloadFromForm(form) {
        const formData = new FormData(form);
        const payload = {
            name: String(formData.get("name") || "").trim(),
            productCode: String(formData.get("productCode") || "").trim(),
            scopeType: String(formData.get("scopeType") || "客户经理").trim(),
            scopeBranchId: String(formData.get("scopeBranchId") || "").trim(),
            scopeManagerId: String(formData.get("scopeManagerId") || "").trim(),
            minAmount: formData.get("minAmount") ? Number(formData.get("minAmount")) : null,
            returnType: String(formData.get("returnType") || "").trim(),
            status: String(formData.get("status") || "AVAILABLE").trim()
        };
        Object.keys(SLOT_LABELS).forEach((key) => {
            payload[key] = formData.getAll(key).filter(Boolean);
        });
        return {
            id: String(formData.get("productId") || "").trim(),
            payload
        };
    }
    function editProduct(id) {
        const product = state.personalProducts.find((item) => String(item.id) === String(id));
        if (!product) {
            showToast("没有找到要编辑的产品", "error");
            return;
        }
        state.editingProduct = JSON.parse(JSON.stringify(product));
        renderPersonalProducts();
    }
    async function deleteProduct(id) {
        const product = state.personalProducts.find((item) => String(item.id) === String(id));
        if (!product || !window.confirm(`确定删除“${product.name}”？`)) {
            return;
        }
        await guard(async () => {
            await InvestmentApi.deletePersonalProduct(id);
            await ensurePersonalProducts(true);
            renderPersonalProducts();
        }, "产品已删除");
    }
    function renderPublicProducts() {
        const loading = state.publicProductsLoading;
        const empty = !loading && !state.publicProducts.length;
        app.innerHTML = `
            <section class="section">
                <div class="card-title">
                    <div>
                        <h2>全行产品库</h2>
                        <p>${loading ? "公共产品正在加载，请稍候。" : (empty ? "当前没有公共产品，请先导入种子数据或刷新列表。" : `系统当前已加载 ${state.publicProducts.length} 条公共产品，只读展示，可在聊天页切换到 PUBLIC 模式体验。`)}</p>
                    </div>
                    <div class="inline-actions">
                        <button class="btn soft" data-action="refresh-public-products">刷新列表</button>
                        <a class="btn primary" href="#/investment/chat">去投顾对话</a>
                    </div>
                </div>
                <div id="publicProductList">${loading ? `<div class="empty">公共产品加载中...</div>` : (empty ? `<div class="empty">当前没有公共产品，请先导入种子数据。</div>` : renderProductList(state.publicProducts, {}))}</div>
            </section>
        `;
        ensurePublicProducts();
    }
    async function ensurePublicProducts(force) {
        if (!force && state.publicProducts.length) {
            return;
        }
        state.publicProductsLoading = true;
        if (currentRoute() === "/investment/products/public") {
            const list = document.getElementById("publicProductList");
            if (list) {
                list.innerHTML = `<div class="empty">公共产品加载中...</div>`;
            }
        }
        try {
            state.publicProducts = await InvestmentApi.listPublicProducts();
            state.home.loaded = false;
            if (currentRoute() === "/investment/products/public") {
                document.getElementById("publicProductList").innerHTML = renderProductList(state.publicProducts, {});
            }
        } catch (error) {
            showToast(error.message || "全行产品加载失败", "error");
            if (currentRoute() === "/investment/products/public") {
                const list = document.getElementById("publicProductList");
                if (list && !state.publicProducts.length) {
                    list.innerHTML = `<div class="empty">公共产品加载失败，请检查数据库种子或接口返回。</div>`;
                }
            }
        } finally {
            state.publicProductsLoading = false;
        }
    }
    function renderTraces() {
        const selected = state.traces.selected;
        app.innerHTML = `
            <section class="split">
                <div class="section">
                    <div class="card-title">
                        <div>
                            <h2>Trace 调试</h2>
                            <p>按时间范围或会话查询请求链路，查看意图修正、槽位、产品检索和合规事件。</p>
                        </div>
                    </div>
                    <form id="traceFilterForm" class="form-grid">
                        <div class="field">
                            <label>开始时间</label>
                            <input type="datetime-local" name="startAt" value="${escapeHtml(state.traces.filters.startAt)}" required>
                        </div>
                        <div class="field">
                            <label>结束时间</label>
                            <input type="datetime-local" name="endAt" value="${escapeHtml(state.traces.filters.endAt)}" required>
                        </div>
                        <div class="field">
                            <label>会话 ID（可选）</label>
                            <input name="sessionId" value="${escapeHtml(state.traces.filters.sessionId)}" placeholder="填写后按会话查询">
                        </div>
                        <div class="field">
                            <label>数量上限</label>
                            <input type="number" min="1" max="500" name="limit" value="${escapeHtml(state.traces.filters.limit)}">
                        </div>
                        <div class="field">
                            <label>标注状态</label>
                            <select name="onlyUnlabeled">
                                <option value="false" ${!state.traces.filters.onlyUnlabeled ? "selected" : ""}>全部</option>
                                <option value="true" ${state.traces.filters.onlyUnlabeled ? "selected" : ""}>仅未标注</option>
                            </select>
                        </div>
                        <div class="field">
                            <span>&nbsp;</span>
                            <button class="btn primary" type="submit">${state.traces.loading ? "查询中..." : "查询 Trace"}</button>
                        </div>
                    </form>
                    <div class="subtle-divider"></div>
                    ${renderTraceTable()}
                </div>
                <aside class="section">
                    ${selected ? renderTraceDetail(selected) : `<div class="empty">选择一条 Trace 查看详情和标注表单。</div>`}
                </aside>
            </section>
        `;
    }
    function renderTraceTable() {
        if (!state.traces.rows.length) {
            return `<div class="empty">暂无 Trace 数据。可以先在聊天页发起几轮对话。</div>`;
        }
        return `
            <div class="table-wrap">
                <table>
                    <thead>
                        <tr>
                            <th>Trace ID</th>
                            <th>会话</th>
                            <th>状态</th>
                            <th>事件</th>
                            <th>耗时</th>
                            <th>创建时间</th>
                            <th>标注</th>
                            <th>操作</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${state.traces.rows.map((row) => `
                            <tr>
                                <td>${escapeHtml(row.traceId)}</td>
                                <td>${escapeHtml(row.sessionId)}</td>
                                <td>${escapeHtml(row.status || "-")}</td>
                                <td>${escapeHtml(row.eventCount ?? "-")}</td>
                                <td>${row.durationMs ? `${escapeHtml(row.durationMs)} ms` : "-"}</td>
                                <td>${escapeHtml(row.createdAt || "-")}</td>
                                <td>${row.expectedIntent ? `<span class="badge">${escapeHtml(row.expectedIntent)}</span>` : "<span class=\"muted\">未标注</span>"}</td>
                                <td><button class="btn soft" data-action="select-trace" data-trace-id="${escapeHtml(row.traceId)}">查看</button></td>
                            </tr>
                        `).join("")}
                    </tbody>
                </table>
            </div>
        `;
    }
    function renderTraceDetail(trace) {
        return `
            <div class="card-title">
                <div>
                    <h3>Trace 详情</h3>
                    <p>${escapeHtml(trace.traceId)}</p>
                </div>
            </div>
            <div class="grid">
                <div>
                    <span class="badge">${escapeHtml(trace.status || "UNKNOWN")}</span>
                    <p class="muted">Session：${escapeHtml(trace.sessionId || "-")} · Events：${escapeHtml(trace.eventCount ?? "-")} · Duration：${escapeHtml(trace.durationMs ?? "-")} ms</p>
                </div>
                <details open>
                    <summary>Trace JSON</summary>
                    <pre class="json-box">${escapeHtml(safeJson(trace.traceJson))}</pre>
                </details>
                <form id="traceLabelForm" class="form-grid">
                    <input type="hidden" name="traceId" value="${escapeHtml(trace.traceId)}">
                    <div class="field">
                        <label>预期意图</label>
                        <select name="expectedIntent">
                            <option value="">不标注</option>
                            ${INTENTS.map((intent) => `<option value="${intent}" ${trace.expectedIntent === intent ? "selected" : ""}>${intent}</option>`).join("")}
                        </select>
                    </div>
                    <div class="field">
                        <label>澄清动作</label>
                        <select name="expectedClarifyAction">
                            <option value="">不标注</option>
                            <option value="ASK" ${trace.expectedClarifyAction === "ASK" ? "selected" : ""}>ASK</option>
                            <option value="READY" ${trace.expectedClarifyAction === "READY" ? "selected" : ""}>READY</option>
                        </select>
                    </div>
                    <div class="field full">
                        <label>预期槽位 JSON</label>
                        <textarea name="expectedSlots" placeholder='{"investmentAmount":["20万-100万"],"riskPreference":["稳健"]}'>${escapeHtml(safeJson(trace.expectedSlots))}</textarea>
                    </div>
                    <div class="field">
                        <label>合规结果</label>
                        <select name="expectedComplianceResult">
                            <option value="">不标注</option>
                            <option value="true" ${trace.expectedComplianceResult === true ? "selected" : ""}>通过</option>
                            <option value="false" ${trace.expectedComplianceResult === false ? "selected" : ""}>不通过</option>
                        </select>
                    </div>
                    <div class="field">
                        <label>期望产品 ID</label>
                        <input name="expectedProductIds" value="${escapeHtml(formatIdList(trace.expectedProductIds))}" placeholder="例如：1,2,3">
                    </div>
                    <div class="field full">
                        <label>备注</label>
                        <textarea name="labelNote" placeholder="标注说明">${escapeHtml(trace.labelNote || "")}</textarea>
                    </div>
                    <div class="field full">
                        <button class="btn primary" type="submit">保存标注</button>
                    </div>
                </form>
            </div>
        `;
    }
    async function searchTraces(form) {
        const formData = new FormData(form);
        state.traces.filters = {
            startAt: formData.get("startAt"),
            endAt: formData.get("endAt"),
            sessionId: formData.get("sessionId").trim(),
            onlyUnlabeled: formData.get("onlyUnlabeled") === "true",
            limit: Number(formData.get("limit") || 50)
        };
        state.traces.loading = true;
        renderTraces();
        try {
            if (state.traces.filters.sessionId) {
                state.traces.rows = await InvestmentApi.listSessionTraces(state.traces.filters.sessionId, state.traces.filters.limit);
            } else {
                state.traces.rows = await InvestmentApi.listTraces({
                    startAt: state.traces.filters.startAt,
                    endAt: state.traces.filters.endAt,
                    onlyUnlabeled: state.traces.filters.onlyUnlabeled,
                    limit: state.traces.filters.limit
                });
            }
            state.traces.selected = state.traces.rows[0] || null;
        } catch (error) {
            showToast(error.message || "Trace 查询失败", "error");
        } finally {
            state.traces.loading = false;
            renderTraces();
        }
    }
    async function selectTrace(traceId) {
        await guard(async () => {
            state.traces.selected = await InvestmentApi.getTrace(traceId);
            renderTraces();
        });
    }
    async function saveTraceLabel(form) {
        const formData = new FormData(form);
        const traceId = formData.get("traceId");
        const slotsText = formData.get("expectedSlots").trim();
        let expectedSlots = null;
        if (slotsText) {
            try {
                expectedSlots = JSON.parse(slotsText);
            } catch (error) {
                showToast("预期槽位必须是合法 JSON", "error");
                return;
            }
        }
        const payload = {
            expectedIntent: formData.get("expectedIntent") || null,
            expectedSlots,
            expectedClarifyAction: formData.get("expectedClarifyAction") || null,
            expectedComplianceResult: formData.get("expectedComplianceResult") === "" ? null : formData.get("expectedComplianceResult") === "true",
            expectedProductIds: parseIdList(formData.get("expectedProductIds")),
            labelNote: formData.get("labelNote").trim()
        };
        await guard(async () => {
            await InvestmentApi.labelTrace(traceId, payload);
            state.traces.selected = await InvestmentApi.getTrace(traceId);
            const index = state.traces.rows.findIndex((row) => row.traceId === traceId);
            if (index >= 0) {
                state.traces.rows[index] = state.traces.selected;
            }
            renderTraces();
        }, "Trace 标注已保存");
    }
    function formatIdList(value) {
        if (Array.isArray(value)) {
            return value.join(",");
        }
        try {
            const parsed = typeof value === "string" ? JSON.parse(value) : value;
            return Array.isArray(parsed) ? parsed.join(",") : "";
        } catch (error) {
            return String(value || "");
        }
    }
    function parseIdList(value) {
        const text = String(value || "").trim();
        if (!text) {
            return null;
        }
        return text.split(/[,，\s]+/)
            .map((item) => Number(item))
            .filter((item) => Number.isFinite(item) && item > 0);
    }
    function renderEvaluations() {
        app.innerHTML = `
            <section class="section">
                <div class="card-title">
                    <div>
                        <h2>评估报告</h2>
                        <p>基于已落库 Trace 生成规则评分、可选 LLM Judge 和反馈归因指标。</p>
                    </div>
                </div>
                <form id="evaluationForm" class="form-grid">
                    <div class="field">
                        <label>开始时间</label>
                        <input type="datetime-local" name="startAt" value="${escapeHtml(state.evaluation.form.startAt)}" required>
                    </div>
                    <div class="field">
                        <label>结束时间</label>
                        <input type="datetime-local" name="endAt" value="${escapeHtml(state.evaluation.form.endAt)}" required>
                    </div>
                    <div class="field">
                        <label>数量上限</label>
                        <input type="number" min="1" max="500" name="limit" value="${escapeHtml(state.evaluation.form.limit)}">
                    </div>
                    <div class="field">
                        <label>LLM Judge</label>
                        <select name="includeLlmJudge">
                            <option value="false" ${!state.evaluation.form.includeLlmJudge ? "selected" : ""}>关闭</option>
                            <option value="true" ${state.evaluation.form.includeLlmJudge ? "selected" : ""}>开启</option>
                        </select>
                    </div>
                    <div class="field full">
                        <button class="btn primary" type="submit">${state.evaluation.loading ? "评估中..." : "生成评估报告"}</button>
                    </div>
                </form>
            </section>
            <section class="section" style="margin-top: 18px;">
                ${renderEvaluationReport()}
            </section>
        `;
    }
    function renderEvaluationReport() {
        const report = state.evaluation.report;
        if (!report) {
            return `<div class="empty">暂无报告。选择时间范围后生成评估。</div>`;
        }
        return `
            <div class="grid three">
                ${statCard("Trace 总数", report.totalTraces, "本次纳入评估的请求数")}
                ${statCard("已标注", report.labeledTraces, "有人工标签的 Trace 数")}
                ${statCard("平均分", report.avgScore === null || report.avgScore === undefined ? "-" : Number(report.avgScore).toFixed(2), "综合评分")}
            </div>
            <div class="subtle-divider"></div>
            <div class="grid two">
                <div>
                    <h3>指标均值</h3>
                    ${renderMetrics(report.metricAverages)}
                </div>
                <div>
                    <h3>报告范围</h3>
                    <p class="muted">${escapeHtml(report.startAt)} 至 ${escapeHtml(report.endAt)}</p>
                </div>
            </div>
            <div class="subtle-divider"></div>
            ${renderEvaluationTable(report.traceResults || [])}
        `;
    }
    function renderMetrics(metrics) {
        const entries = Object.entries(metrics || {});
        if (!entries.length) {
            return `<div class="empty">暂无指标</div>`;
        }
        return `<div class="chips">${entries.map(([key, value]) => `<span class="chip selected">${escapeHtml(key)}：${Number(value).toFixed(2)}</span>`).join("")}</div>`;
    }
    function renderEvaluationTable(rows) {
        if (!rows.length) {
            return `<div class="empty">暂无 Trace 明细</div>`;
        }
        return `
            <div class="table-wrap">
                <table>
                    <thead>
                        <tr>
                            <th>Trace ID</th>
                            <th>会话</th>
                            <th>综合分</th>
                            <th>规则分</th>
                            <th>LLM 分</th>
                            <th>反馈分</th>
                            <th>指标 / 明细</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${rows.map((row) => `
                            <tr>
                                <td>${escapeHtml(row.traceId)}</td>
                                <td>${escapeHtml(row.sessionId)}</td>
                                <td>${formatScore(row.score)}</td>
                                <td>${formatScore(row.ruleScore)}</td>
                                <td>${formatScore(row.llmJudgeScore)}</td>
                                <td>${formatScore(row.userFeedbackScore)}</td>
                                <td>
                                    <details>
                                        <summary>查看 JSON</summary>
                                        <pre class="json-box">${escapeHtml(JSON.stringify({ metrics: row.metrics, detail: row.detail }, null, 2))}</pre>
                                    </details>
                                </td>
                            </tr>
                        `).join("")}
                    </tbody>
                </table>
            </div>
        `;
    }
    function formatScore(value) {
        return value === null || value === undefined ? "-" : Number(value).toFixed(2);
    }
    async function runEvaluation(form) {
        const formData = new FormData(form);
        state.evaluation.form = {
            startAt: formData.get("startAt"),
            endAt: formData.get("endAt"),
            limit: Number(formData.get("limit") || 50),
            includeLlmJudge: formData.get("includeLlmJudge") === "true"
        };
        state.evaluation.loading = true;
        renderEvaluations();
        try {
            state.evaluation.report = await InvestmentApi.evaluate(state.evaluation.form);
        } catch (error) {
            showToast(error.message || "评估失败", "error");
        } finally {
            state.evaluation.loading = false;
            renderEvaluations();
        }
    }
    async function saveFeedback(button) {
        await guard(async () => {
            await InvestmentApi.saveFeedback({
                sessionId: button.dataset.sessionId || state.chat.sessionId,
                productId: Number(button.dataset.productId || button.dataset.itemId),
                action: button.dataset.actionValue,
                rating: button.dataset.actionValue === "UNSUITABLE" ? 2 : (button.dataset.actionValue === "NEED_MANUAL_ADJUST" ? 3 : 5),
                reason: ""
            });
        }, "反馈已记录");
    }
    function handleClick(event) {
        const target = event.target.closest("[data-action]");
        if (!target) {
            return;
        }
        const action = target.dataset.action;
        if (action === "set-source") {
            state.chat.sourceMode = target.dataset.source;
            resetChat();
            showToast(`已切换到${sourceModeLabel(state.chat.sourceMode)}`);
        } else if (action === "new-session") {
            resetChat();
        } else if (action === "quick-message") {
            const input = document.querySelector("#chatForm textarea[name=message]");
            if (input) {
                input.value = target.dataset.message;
                input.focus();
            } else {
                state.pendingQuickMessage = target.dataset.message || "";
            }
        } else if (action === "feedback") {
            saveFeedback(target);
        } else if (action === "new-product") {
            state.editingProduct = emptyProduct();
            renderPersonalProducts();
        } else if (action === "edit-product") {
            editProduct(target.dataset.id);
        } else if (action === "delete-product") {
            deleteProduct(target.dataset.id);
        } else if (action === "cancel-edit") {
            state.editingProduct = null;
            renderPersonalProducts();
        } else if (action === "select-trace") {
            selectTrace(target.dataset.traceId);
        } else if (action === "open-trace") {
            state.traces.filters.sessionId = "";
            navigate("/admin/traces");
            selectTrace(target.dataset.traceId);
        } else if (action === "refresh-public-products") {
            state.publicProducts = [];
            state.publicProductsLoading = true;
            renderPublicProducts();
            ensurePublicProducts(true);
            showToast("正在刷新全行产品库");
        }
    }
    function handleSubmit(event) {
        const form = event.target;
        if (form.id === "chatForm") {
            event.preventDefault();
            submitChat(form);
        } else if (form.id === "productForm") {
            event.preventDefault();
            if (!form.checkValidity()) {
                form.reportValidity();
                return;
            }
            saveProduct(form);
        } else if (form.id === "traceFilterForm") {
            event.preventDefault();
            searchTraces(form);
        } else if (form.id === "traceLabelForm") {
            event.preventDefault();
            saveTraceLabel(form);
        } else if (form.id === "evaluationForm") {
            event.preventDefault();
            runEvaluation(form);
        }
    }
    function initUserField() {
        userIdInput.value = InvestmentApi.setUserId(InvestmentApi.getUserId());
        userIdInput.addEventListener("change", () => {
            InvestmentApi.setUserId(userIdInput.value);
            state.home.loaded = false;
            state.personalProducts = [];
            state.publicProducts = [];
            state.traces.rows = [];
            state.traces.selected = null;
            resetChat();
            showToast("用户 ID 已切换");
            render();
        });
    }
    window.addEventListener("hashchange", render);
    app.addEventListener("click", handleClick);
    app.addEventListener("submit", handleSubmit);
    initUserField();
    if (!location.hash) {
        navigate("/investment");
    } else {
        render();
    }
})();



