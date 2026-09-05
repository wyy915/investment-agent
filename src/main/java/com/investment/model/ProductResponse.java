package com.investment.model;

import java.util.List;

import com.investment.enums.SourceMode;
import com.fasterxml.jackson.annotation.JsonAutoDetect;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Accessors(fluent = true)
@AllArgsConstructor
@NoArgsConstructor
public class ProductResponse {
    private Long id;
    private SourceMode sourceType;
    private String name;
    private String scopeType;
    private Long scopeBranchId;
    private Long scopeManagerId;
    private String productCode;
    private String productType;
    private String riskLevel;
    private String investmentHorizon;
    private String liquidityType;
    private java.math.BigDecimal minAmount;
    private String returnType;
    private String targetCustomer;
    private String status;
    private List<String> investmentAmount;
    private List<String> riskPreference;
    private List<String> liquidityNeed;
    private List<String> returnExpectation;
    private List<String> customerProfile;
    private List<String> restriction;
    private double matchScore;

    public static ProductResponse from(ProductItem item) {
        SlotBundle slots = item.slots();
        return new ProductResponse(
                item.id(),
                item.sourceType(),
                item.name(),
                item.scopeType(),
                item.scopeBranchId(),
                item.scopeManagerId(),
                item.productCode(),
                item.productType(),
                item.riskLevel(),
                item.investmentHorizon(),
                item.liquidityType(),
                item.minAmount(),
                item.returnType(),
                item.targetCustomer(),
                item.status(),
                slots.investmentAmount(),
                slots.riskPreference(),
                slots.liquidityNeed(),
                slots.returnExpectation(),
                slots.customerProfile(),
                slots.restriction(),
                item.matchScore()
        );
    }
}





