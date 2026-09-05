package com.investment.service.feedback;

import com.investment.exception.InvestmentException;
import com.investment.mapper.FeedbackMapper;
import com.investment.model.FeedbackRequest;
import org.springframework.stereotype.Service;

@Service
public class FeedbackService {
    private final FeedbackMapper feedbackMapper;

    public FeedbackService(FeedbackMapper feedbackMapper) {
        this.feedbackMapper = feedbackMapper;
    }

    public void save(Long userId, FeedbackRequest request) {
        if (request == null || request.sessionId() == null || request.sessionId().isBlank()) {
            throw new InvestmentException("反馈 sessionId 不能为空");
        }
        if (request.action() == null || request.action().isBlank()) {
            throw new InvestmentException("反馈 action 不能为空");
        }
        String action = request.action().trim().toUpperCase();
        feedbackMapper.insert(
                userId,
                request.sessionId(),
                request.productId(),
                action,
                request.rating(),
                request.reason()
        );
    }
}

