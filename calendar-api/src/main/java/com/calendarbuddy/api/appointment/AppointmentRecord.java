package com.calendarbuddy.api.appointment;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AppointmentRecord(
        long id,
        UUID accountId,
        String title,
        String location,
        String notes,
        boolean allDay,
        Instant startAt,
        Instant endAt,
        LocalDate startDate,
        LocalDate endDate,
        String recurrenceFreq,
        Integer recurrenceInterval,
        String recurrenceWeekdays,
        LocalDate recurrenceUntil,
        Integer recurrenceCount,
        Long seriesId,
        LocalDate originalDate,
        long version,
        Instant createdAt,
        Instant updatedAt) {}
