package com.calendarbuddy.api.account;

import java.util.UUID;

public record AccountPrincipal(UUID id, String email, String timeZone) {}
