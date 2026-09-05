package com.investment.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class SlotBundle {
    private List<String> investmentAmount;
    private List<String> investmentHorizon;
    private List<String> riskPreference;
    private List<String> liquidityNeed;
    private List<String> returnExpectation;
    private List<String> productType;
    private List<String> customerProfile;
    private List<String> restriction;

    public SlotBundle(List<String> investmentAmount,
                      List<String> investmentHorizon,
                      List<String> riskPreference,
                      List<String> liquidityNeed,
                      List<String> returnExpectation,
                      List<String> productType,
                      List<String> customerProfile,
                      List<String> restriction) {
        this.investmentAmount = normalize(investmentAmount);
        this.investmentHorizon = normalize(investmentHorizon);
        this.riskPreference = normalize(riskPreference);
        this.liquidityNeed = normalize(liquidityNeed);
        this.returnExpectation = normalize(returnExpectation);
        this.productType = normalize(productType);
        this.customerProfile = normalize(customerProfile);
        this.restriction = normalize(restriction);
    }

    public static SlotBundle empty() {
        return new SlotBundle(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    public boolean isEmpty() {
        return investmentAmount.isEmpty()
                && investmentHorizon.isEmpty()
                && riskPreference.isEmpty()
                && liquidityNeed.isEmpty()
                && returnExpectation.isEmpty()
                && productType.isEmpty()
                && customerProfile.isEmpty()
                && restriction.isEmpty();
    }

    private static List<String> normalize(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }
}





