package com.calendarbuddy.api.member;

import com.calendarbuddy.api.account.AccountPrincipal;
import com.calendarbuddy.api.metrics.CalendarMetrics;
import com.calendarbuddy.api.metrics.CalendarMetrics.Operation;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/family-members")
public class FamilyMemberController {
    private final FamilyMemberService service;
    private final CalendarMetrics metrics;

    public FamilyMemberController(FamilyMemberService service, CalendarMetrics metrics) {
        this.service = service;
        this.metrics = metrics;
    }

    @GetMapping
    public List<MemberResponse> list(@AuthenticationPrincipal AccountPrincipal account) {
        return service.list(account.id());
    }

    @GetMapping("/{id}")
    public MemberResponse get(
            @AuthenticationPrincipal AccountPrincipal account, @PathVariable long id) {
        return service.get(account.id(), id);
    }

    @PostMapping
    public ResponseEntity<MemberResponse> create(
            @AuthenticationPrincipal AccountPrincipal account, @RequestBody MemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        metrics.member(
                                Operation.CREATE, () -> service.create(account.id(), request)));
    }

    @PutMapping("/{id}")
    public MemberResponse update(
            @AuthenticationPrincipal AccountPrincipal account,
            @PathVariable long id,
            @RequestBody MemberRequest request) {
        return metrics.member(Operation.UPDATE, () -> service.update(account.id(), id, request));
    }

    @GetMapping("/{id}/removal-impact")
    public RemovalImpact impact(
            @AuthenticationPrincipal AccountPrincipal account, @PathVariable long id) {
        return service.impact(account.id(), id);
    }

    @DeleteMapping("/{id}")
    public RemovalImpact remove(
            @AuthenticationPrincipal AccountPrincipal account, @PathVariable long id) {
        return metrics.member(Operation.DELETE, () -> service.remove(account.id(), id));
    }
}
