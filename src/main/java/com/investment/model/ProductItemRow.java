package com.investment.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProductItemRow {
    private Long id;
    private String sourceType;
    private Long ownerUserId;
    private String scopeType;
    private Long scopeBranchId;
    private Long scopeManagerId;
    private String productName;
    private String productCode;
    private String productType;
    private String riskLevel;
    private String investmentHorizon;
    private String liquidityType;
    private java.math.BigDecimal minAmount;
    private String returnType;
    private String targetCustomer;
    private String tags;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

