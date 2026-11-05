package com.calendarbuddy.api.member;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyMemberRepository extends JpaRepository<FamilyMember, Long> {
    List<FamilyMember> findByAccountIdOrderByCreatedAtAscIdAsc(UUID accountId);
    Optional<FamilyMember> findByIdAndAccountId(Long id, UUID accountId);
    boolean existsByIdAndAccountId(Long id, UUID accountId);
    long countByAccountId(UUID accountId);
    boolean existsByAccountIdAndNameIgnoreCase(UUID accountId, String name);
}
