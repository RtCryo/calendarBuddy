package com.calendarbuddy.api.appointment;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AppointmentResponse(
        long appointmentId,
        LocalDate occurrenceDate,
        Long seriesId,
        boolean recurring,
        String title,
        String location,
        String notes,
        boolean allDay,
        Instant startAt,
        Instant endAt,
        LocalDate startDate,
        LocalDate endDate,
        List<Long> participantIds,
        RecurrenceRequest recurrence,
        long version) {}
