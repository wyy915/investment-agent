package com.investment.service.slot;

import com.investment.exception.InvestmentException;
import com.investment.mapper.SlotOptionMapper;
import com.investment.model.SlotBundle;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SlotOptionService {
    public static final List<String> SLOT_NAMES = List.of(
            "investmentAmount",
            "investmentHorizon",
            "riskPreference",
            "liquidityNeed",
            "returnExpectation",
            "productType",
            "customerProfile",
            "restriction"
    );

    private final SlotOptionMapper slotOptionMapper;

    public SlotOptionService(SlotOptionMapper slotOptionMapper) {
        this.slotOptionMapper = slotOptionMapper;
    }

    public Map<String, List<String>> findAllOptions() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String slotName : SLOT_NAMES) {
            result.put(slotName, slotOptionMapper.findEnabledValues(slotName));
        }
        return result;
    }

    public void validate(SlotBundle slots) {
        Map<String, List<String>> options = findAllOptions();
        validateSlot("investmentAmount", slots.investmentAmount(), options);
        validateSlot("investmentHorizon", slots.investmentHorizon(), options);
        validateSlot("riskPreference", slots.riskPreference(), options);
        validateSlot("liquidityNeed", slots.liquidityNeed(), options);
        validateSlot("returnExpectation", slots.returnExpectation(), options);
        validateSlot("productType", slots.productType(), options);
        validateSlot("customerProfile", slots.customerProfile(), options);
        validateSlot("restriction", slots.restriction(), options);
    }

    private void validateSlot(String slotName, List<String> values, Map<String, List<String>> options) {
        if (values == null || values.isEmpty()) {
            return;
        }
        Set<String> allowed = Set.copyOf(options.getOrDefault(slotName, List.of()));
        for (String value : values) {
            if (!allowed.contains(value)) {
                throw new InvestmentException("非法槽位标签: " + slotName + "=" + value);
            }
        }
    }
}

