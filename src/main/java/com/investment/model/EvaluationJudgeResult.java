package com.investment.model;

public record EvaluationJudgeResult(
        double explanationQuality,
        double naturalness,
        String reason
) {
}
