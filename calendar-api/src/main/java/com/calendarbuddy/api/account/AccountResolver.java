package com.calendarbuddy.api.account;

import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AccountResolver {
    private final AccountRepository accounts;
    private final TransactionTemplate transactions;

    public AccountResolver(AccountRepository accounts, PlatformTransactionManager manager) {
        this.accounts = accounts;
        this.transactions = new TransactionTemplate(manager);
    }

    public AccountPrincipal resolve(Jwt jwt) {
        UUID id;
        try {
            id = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new BadCredentialsException("Invalid account subject", error);
        }
        String claimEmail = jwt.getClaimAsString("email");
        if (claimEmail == null || claimEmail.isBlank()) {
            throw new BadCredentialsException("Missing account email");
        }
        String email = claimEmail.trim().toLowerCase(Locale.ROOT);
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                Account account = transactions.execute(status -> resolveOnce(id, email));
                return new AccountPrincipal(account.getId(), account.getEmail(), account.getTimeZone());
            } catch (DataIntegrityViolationException error) {
                if (attempt == 2) {
                    throw new BadCredentialsException("Account provisioning conflict", error);
                }
            }
        }
        throw new IllegalStateException("Unreachable account resolution state");
    }

    private Account resolveOnce(UUID id, String email) {
        Account account = accounts.findById(id).orElse(null);
        if (account == null) {
            return accounts.saveAndFlush(new Account(id, email));
        }
        if (!account.getEmail().equals(email)) {
            account.setEmail(email);
            return accounts.saveAndFlush(account);
        }
        return account;
    }
}
