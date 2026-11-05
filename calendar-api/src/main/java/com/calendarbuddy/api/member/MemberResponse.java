package com.calendarbuddy.api.member;

import java.time.Instant;

public record MemberResponse(Long id, String name, String color, Instant createdAt) {
    public static MemberResponse from(FamilyMember member) {
        return new MemberResponse(member.getId(), member.getName(), member.getColor(), member.getCreatedAt());
    }
}
