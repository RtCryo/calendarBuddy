package com.calendarbuddy.api.account;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes the signed-in account's settings so the UI uses the same time zone as the API. */
@RestController
public class AccountController {
    public record AccountResponse(String email, String timeZone) {}

    @GetMapping("/api/account")
    public AccountResponse account(@AuthenticationPrincipal AccountPrincipal account) {
        return new AccountResponse(account.email(), account.timeZone());
    }
}
