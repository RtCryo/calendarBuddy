package com.calendarbuddy.api.appointment;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

public record RecurrenceRequest(
        String frequency,
        Integer interval,
        Set<DayOfWeek> weekdays,
        LocalDate until,
        Integer count) {}
