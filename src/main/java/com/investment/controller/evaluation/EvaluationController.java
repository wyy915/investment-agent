package com.investment.controller.evaluation;

import com.investment.constants.InvestmentConstants;
import com.investment.model.EvaluationReport;
import com.investment.model.EvaluationRequest;
import com.investment.service.evaluation.EvaluationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/investment/evaluations")
public class EvaluationController {
    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping
    public EvaluationReport evaluate(
            @RequestHeader(value = InvestmentConstants.USER_ID, defaultValue = "1") Long userId,
            @RequestBody EvaluationRequest request
    ) {
        return evaluationService.evaluate(userId, request);
    }
}





