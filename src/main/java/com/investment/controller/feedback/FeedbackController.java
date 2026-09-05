package com.investment.controller.feedback;

import com.investment.constants.InvestmentConstants;
import com.investment.model.FeedbackRequest;
import com.investment.service.feedback.FeedbackService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/investment/feedback")
public class FeedbackController {
    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public void save(
            @RequestHeader(value = InvestmentConstants.USER_ID, defaultValue = "1") Long userId,
            @RequestBody FeedbackRequest request
    ) {
        feedbackService.save(userId, request);
    }
}





