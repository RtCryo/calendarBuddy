package com.calendarbuddy.api.appointment;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;

public record AppointmentRequest(
        String title,
        String location,
        String notes,
        boolean allDay,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        LocalDate startDate,
        LocalDate endDate,
        Set<Long> participantIds,
        RecurrenceRequest recurrence,
        Long version) {}
