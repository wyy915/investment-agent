package com.investment.service.plan;

import com.investment.enums.SourceMode;
import com.investment.model.ProductItem;
import com.investment.model.ProductRankRequest;
import com.investment.model.ProductSearchRequest;
import com.investment.model.SlotBundle;
import com.investment.service.product.ProductRankService;
import com.investment.service.product.ProductSearchService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 组合配置服务：按期限偏好拆分检索/重排后各取一款候选产品。
 */
@Service
public class ProductPlanService {

    public static final List<String> DEFAULT_PLAN_HORIZONS = List.of("随时可用", "3个月", "6个月");

    private static final String AGGREGATE_PORTFOLIO = "组合配置";

    private final ProductSearchService productSearchService;
    private final ProductRankService productRankService;

    public ProductPlanService(ProductSearchService productSearchService, ProductRankService productRankService) {
        this.productSearchService = productSearchService;
        this.productRankService = productRankService;
    }

    /**
     * 从合并后的槽位解析组合配置的期限篮子。
     */
    public List<String> resolveInvestmentHorizons(SlotBundle slots) {
        List<String> raw = slots == null || slots.investmentHorizon() == null ? List.of() : slots.investmentHorizon();
        if (raw.isEmpty() || raw.stream().anyMatch(AGGREGATE_PORTFOLIO::equals)) {
            return DEFAULT_PLAN_HORIZONS;
        }
        List<String> specific = raw.stream()
                .filter(value -> value != null && !value.isBlank())
                .filter(value -> !AGGREGATE_PORTFOLIO.equals(value))
                .distinct()
                .toList();
        if (specific.size() >= 2) {
            return specific;
        }
        return DEFAULT_PLAN_HORIZONS;
    }

    /**
     * 复制共享槽位，仅替换 investmentHorizon 为单一期限。
     */
    public SlotBundle slotsForInvestmentHorizon(SlotBundle base, String investmentHorizon) {
        SlotBundle safe = base == null ? SlotBundle.empty() : base;
        return new SlotBundle(
                safe.investmentAmount(),
                List.of(investmentHorizon),
                safe.riskPreference(),
                safe.liquidityNeed(),
                safe.returnExpectation(),
                safe.productType(),
                safe.customerProfile(),
                safe.restriction()
        );
    }

    /**
     * 按期限篮子依次检索重排，每个篮子取 top1，跨篮子排除已选产品。
     */
    public List<PlannedProduct> planProducts(SourceMode sourceMode, Long userId, SlotBundle baseSlots, List<String> investmentHorizons) {
        List<String> targets = investmentHorizons == null || investmentHorizons.isEmpty() ? DEFAULT_PLAN_HORIZONS : investmentHorizons;
        List<PlannedProduct> planned = new ArrayList<>();
        Set<Long> usedIds = new LinkedHashSet<>();

        for (String investmentHorizon : targets) {
            SlotBundle querySlots = slotsForInvestmentHorizon(baseSlots, investmentHorizon);
            List<Long> excludeIds = List.copyOf(usedIds);
            List<ProductItem> candidates = productSearchService.search(
                    new ProductSearchRequest(sourceMode, userId, querySlots, excludeIds));
            List<ProductItem> ranked = productRankService.rank(
                    new ProductRankRequest(candidates, querySlots, excludeIds));
            ProductItem picked = ranked.stream()
                    .filter(item -> item != null && item.id() != null && !usedIds.contains(item.id()))
                    .findFirst()
                    .orElse(null);
            if (picked != null) {
                usedIds.add(picked.id());
                planned.add(new PlannedProduct(investmentHorizon, picked, querySlots));
            } else {
                planned.add(new PlannedProduct(investmentHorizon, null, querySlots));
            }
        }
        return planned;
    }

    /**
     * 单个期限篮子的规划结果：期限 + 命中产品（可能为空）+ 该篮子检索用槽位。
     */
    public record PlannedProduct(String investmentHorizon, ProductItem product, SlotBundle querySlots) {
        public boolean matched() {
            return product != null && product.id() != null;
        }
    }
}

