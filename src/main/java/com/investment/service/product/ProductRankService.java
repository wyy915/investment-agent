package com.investment.service.product;

import com.investment.model.ProductItem;
import com.investment.model.ProductRankRequest;
import com.investment.model.SlotBundle;
import org.springframework.stereotype.Service;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 理财产品重排服务（Orchestrator 推荐流水线第二层）。
 * 消费投资槽位、excludeProductIds，对检索候选二次打分排序。
 */
@Service
public class ProductRankService {
    public List<ProductItem> rank(ProductRankRequest request) {
        Set<Long> excludeIds = new HashSet<>(request.excludeProductIds() == null ? List.of() : request.excludeProductIds());
        return request.candidates().stream()
                .filter(item -> item != null && !excludeIds.contains(item.id()))
                .filter(item -> "AVAILABLE".equalsIgnoreCase(item.status()) || "ON_SALE".equalsIgnoreCase(item.status()))
                .filter(item -> riskAllowed(item, request.slots()))
                .filter(item -> amountAllowed(item, request.slots()))
                .map(item -> withRankScore(item, request.slots()))
                .sorted(Comparator.comparingDouble((ProductItem item) -> item.matchScore()).reversed())
                .limit(10)
                .toList();
    }

    private ProductItem withRankScore(ProductItem item, SlotBundle query) {
        double slotScore = slotScore(item.slots(), query);
        return new ProductItem(
                item.id(), item.sourceType(), item.ownerUserId(),
                item.scopeType(), item.scopeBranchId(), item.scopeManagerId(),
                item.name(), item.productCode(),
                item.productType(), item.riskLevel(), item.investmentHorizon(), item.liquidityType(),
                item.minAmount(), item.returnType(), item.targetCustomer(), item.status(), item.slots(), slotScore
        );
    }

    private double slotScore(SlotBundle item, SlotBundle query) {
        SlotBundle safeQuery = query == null ? SlotBundle.empty() : query;
        double total = weightedOverlap(item.riskPreference(), safeQuery.riskPreference(), 2.0)
                + weightedOverlap(item.investmentHorizon(), safeQuery.investmentHorizon(), 1.5)
                + weightedOverlap(item.liquidityNeed(), safeQuery.liquidityNeed(), 1.5)
                + weightedOverlap(item.productType(), safeQuery.productType(), 1.0)
                + weightedOverlap(item.returnExpectation(), safeQuery.returnExpectation(), 0.8)
                + weightedOverlap(item.customerProfile(), safeQuery.customerProfile(), 0.6)
                + restrictionScore(item.restriction(), safeQuery.restriction());
        return clamp(total / 8.4);
    }

    private double weightedOverlap(List<String> itemValues, List<String> queryValues, double weight) {
        return overlap(itemValues, queryValues) * weight;
    }

    private double restrictionScore(List<String> itemValues, List<String> queryValues) {
        if (queryValues == null || queryValues.isEmpty()) {
            return 1.0;
        }
        Set<String> itemSet = Set.copyOf(itemValues == null ? List.of() : itemValues);
        boolean conflict = queryValues.stream().anyMatch(value ->
                itemSet.contains(value) || (value.contains("不买基金") && itemSet.contains("基金")));
        return conflict ? 0.0 : 1.0;
    }

    private double overlap(List<String> itemValues, List<String> queryValues) {
        if (queryValues == null || queryValues.isEmpty()) {
            return 0;
        }
        Set<String> itemSet = Set.copyOf(itemValues == null ? List.of() : itemValues);
        long hits = queryValues.stream().filter(itemSet::contains).count();
        return hits * 1.0 / queryValues.size();
    }

    private boolean riskAllowed(ProductItem item, SlotBundle query) {
        if (query == null || query.riskPreference().isEmpty()) {
            return true;
        }
        int customerRisk = query.riskPreference().stream().mapToInt(this::riskRank).max().orElse(5);
        int productRisk = riskRank(item.riskLevel());
        return productRisk <= customerRisk;
    }

    private int riskRank(String value) {
        if (value == null) {
            return 5;
        }
        String text = value.trim().toUpperCase();
        return switch (text) {
            case "R1", "PR1", "低风险", "保守" -> 1;
            case "R2", "PR2", "中低风险", "稳健" -> 2;
            case "R3", "PR3", "中风险", "平衡" -> 3;
            case "R4", "PR4", "中高风险", "进取" -> 4;
            case "R5", "PR5", "高风险", "激进" -> 5;
            default -> 5;
        };
    }

    private boolean amountAllowed(ProductItem item, SlotBundle query) {
        if (item.minAmount() == null || query == null || query.investmentAmount().isEmpty()) {
            return true;
        }
        java.math.BigDecimal amount = query.investmentAmount().stream()
                .map(this::parseAmount)
                .filter(java.util.Objects::nonNull)
                .max(java.math.BigDecimal::compareTo)
                .orElse(null);
        return amount == null || item.minAmount().compareTo(amount) <= 0;
    }

    private java.math.BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim().replace(",", "");
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(text);
        if (!matcher.find()) {
            return null;
        }
        java.math.BigDecimal amount = new java.math.BigDecimal(matcher.group(1));
        if (text.contains("万")) {
            amount = amount.multiply(new java.math.BigDecimal("10000"));
        }
        return amount;
    }

    /** 将分数约束在 [0, 1] 区间。 */
    private double clamp(double score) {
        return Math.max(0, Math.min(1, score));
    }
}

