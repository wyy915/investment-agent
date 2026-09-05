package com.investment.controller.session;

import com.investment.constants.InvestmentConstants;
import com.investment.model.CreateSessionResponse;
import com.investment.service.session.SessionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/investment/sessions")
public class SessionController {
    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    public CreateSessionResponse create(@RequestHeader(value = InvestmentConstants.USER_ID, defaultValue = "1") Long userId) {
        return new CreateSessionResponse(sessionService.createSession(userId));
    }
}
