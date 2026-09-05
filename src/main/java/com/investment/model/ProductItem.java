package com.investment.model;

import com.investment.enums.SourceMode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(fluent = true)
@AllArgsConstructor
public class ProductItem {
    private Long id;
    private SourceMode sourceType;
    private Long ownerUserId;
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
    private SlotBundle slots;
    private double matchScore;

    public double matchScore() {
        return matchScore;
    }
}





