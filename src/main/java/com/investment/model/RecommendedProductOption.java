package com.investment.model;

import com.investment.enums.SourceMode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * RecommendAgent 生成理由后的单个推荐项。
 * matchScore 由 Java 重排层生成，Agent 只能引用，不能自行改分。
 */
@Data
@Accessors(fluent = true)
@AllArgsConstructor
public class RecommendedProductOption {
    /** 产品 ID，必须来自数据库候选。 */
    private Long productId;
    private SourceMode sourceType;
    private String scopeType;
    private Long scopeBranchId;
    private Long scopeManagerId;
    private String name;
    private String productCode;
    private String productType;
    private String riskLevel;
    private String investmentHorizon;
    private String liquidityType;
    private java.math.BigDecimal minAmount;
    private String returnType;
    private String targetCustomer;
    private String status;
    private String reason;
    private double matchScore;
    private SlotBundle matchedSlots;
}





