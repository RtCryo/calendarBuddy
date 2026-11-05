package com.calendarbuddy.api.appointment;

import com.calendarbuddy.api.account.AccountPrincipal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {
    private final AppointmentService service;
    public AppointmentController(AppointmentService service) { this.service = service; }

    @GetMapping
    public List<AppointmentResponse> range(@AuthenticationPrincipal AccountPrincipal account,
                                           @RequestParam LocalDate from, @RequestParam LocalDate to,
                                           @RequestParam(required = false) Long memberId) {
        return service.range(account.id(), from, to, memberId);
    }

    @GetMapping("/{id}")
    public AppointmentResponse get(@AuthenticationPrincipal AccountPrincipal account, @PathVariable long id) {
        return service.get(account.id(), id);
    }

    @PostMapping
    public ResponseEntity<AppointmentResponse> create(@AuthenticationPrincipal AccountPrincipal account,
                                                      @RequestBody AppointmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(account.id(), request));
    }

    @PutMapping("/{id}")
    public AppointmentResponse update(@AuthenticationPrincipal AccountPrincipal account, @PathVariable long id,
                                      @RequestParam(required = false) String scope,
                                      @RequestParam(required = false) LocalDate occurrenceDate,
                                      @RequestBody AppointmentRequest request) {
        return service.update(account.id(), id, request, scope, occurrenceDate);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AccountPrincipal account, @PathVariable long id,
                                       @RequestParam long version,
                                       @RequestParam(required = false) String scope,
                                       @RequestParam(required = false) LocalDate occurrenceDate) {
        service.delete(account.id(), id, version, scope, occurrenceDate);
        return ResponseEntity.noContent().build();
    }
}
