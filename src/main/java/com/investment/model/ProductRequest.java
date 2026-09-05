package com.investment.model;

import java.util.List;

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
public class ProductRequest {
    private String name;
    private String productCode;
    private String scopeType;
    private Long scopeBranchId;
    private Long scopeManagerId;
    private List<String> investmentAmount;
    private List<String> investmentHorizon;
    private List<String> riskPreference;
    private List<String> liquidityNeed;
    private List<String> returnExpectation;
    private List<String> productType;
    private List<String> customerProfile;
    private List<String> restriction;
    private java.math.BigDecimal minAmount;
    private String returnType;
    private String status;

    public SlotBundle toSlots() {
        return new SlotBundle(investmentAmount, investmentHorizon, riskPreference, liquidityNeed, returnExpectation, productType, customerProfile, restriction);
    }
}





