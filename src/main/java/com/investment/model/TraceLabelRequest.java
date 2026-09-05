package com.investment.model;
import com.investment.enums.ClarifyAction;
import com.investment.enums.Intent;
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
public class TraceLabelRequest {
    private Intent expectedIntent;
    private SlotBundle expectedSlots;
    private ClarifyAction expectedClarifyAction;
    private Boolean expectedComplianceResult;
    private java.util.List<Long> expectedProductIds;
    private String labelNote;
}

