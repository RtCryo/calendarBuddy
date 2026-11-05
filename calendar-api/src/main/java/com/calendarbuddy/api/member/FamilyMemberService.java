package com.calendarbuddy.api.member;

import com.calendarbuddy.api.account.Account;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FamilyMemberService {
    private static final Set<String> COLORS = Set.of(
            "indigo", "teal", "coral", "rose", "amber", "sky", "violet", "slate");

    private final FamilyMemberRepository members;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbc;

    public FamilyMemberService(FamilyMemberRepository members, EntityManager entityManager, JdbcTemplate jdbc) {
        this.members = members;
        this.entityManager = entityManager;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> list(UUID accountId) {
        return members.findByAccountIdOrderByCreatedAtAscIdAsc(accountId).stream()
                .map(MemberResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public MemberResponse get(UUID accountId, long id) {
        return MemberResponse.from(requireMember(accountId, id));
    }

    @Transactional
    public MemberResponse create(UUID accountId, MemberRequest request) {
        lockAccount(accountId);
        String name = validName(request.name());
        String color = validColor(request.color());
        if (members.countByAccountId(accountId) >= 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A family can have at most 20 members");
        }
        if (members.existsByAccountIdAndNameIgnoreCase(accountId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Member name already exists");
        }
        try {
            return MemberResponse.from(members.saveAndFlush(new FamilyMember(accountId, name, color)));
        } catch (DataIntegrityViolationException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Member name already exists", error);
        }
    }

    @Transactional
    public MemberResponse update(UUID accountId, long id, MemberRequest request) {
        FamilyMember member = requireMember(accountId, id);
        String name = validName(request.name());
        String color = validColor(request.color());
        member.setName(name);
        member.setColor(color);
        try {
            return MemberResponse.from(members.saveAndFlush(member));
        } catch (DataIntegrityViolationException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Member name already exists", error);
        }
    }

    @Transactional(readOnly = true)
    public RemovalImpact impact(UUID accountId, long id) {
        requireMember(accountId, id);
        return jdbc.queryForObject(
                "WITH affected AS (SELECT appointment_id FROM appointment_participants"
                        + " WHERE account_id=? AND member_id=?), counts AS"
                        + " (SELECT p.appointment_id, COUNT(*) AS participant_count"
                        + " FROM appointment_participants p JOIN affected a"
                        + " ON a.appointment_id=p.appointment_id GROUP BY p.appointment_id)"
                        + " SELECT COUNT(*) FILTER (WHERE participant_count=1) AS delete_count,"
                        + " COUNT(*) FILTER (WHERE participant_count>1) AS unlink_count FROM counts",
                (rs, row) -> new RemovalImpact(rs.getLong("delete_count"), rs.getLong("unlink_count")),
                accountId, id);
    }

    @Transactional
    public RemovalImpact remove(UUID accountId, long id) {
        FamilyMember member = requireMember(accountId, id);
        lockAccount(accountId);
        RemovalImpact impact = impact(accountId, id);
        jdbc.update(
                "DELETE FROM appointments WHERE account_id=? AND id IN"
                        + " (SELECT p.appointment_id FROM appointment_participants p"
                        + " JOIN appointment_participants target"
                        + " ON target.appointment_id=p.appointment_id"
                        + " WHERE target.account_id=? AND target.member_id=?"
                        + " GROUP BY p.appointment_id HAVING COUNT(*)=1)",
                accountId, accountId, id);
        jdbc.update("DELETE FROM appointment_participants WHERE account_id=? AND member_id=?", accountId, id);
        members.delete(member);
        members.flush();
        return impact;
    }

    private FamilyMember requireMember(UUID accountId, long id) {
        return members.findByIdAndAccountId(id, accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
    }

    private void lockAccount(UUID accountId) {
        if (entityManager.find(Account.class, accountId, LockModeType.PESSIMISTIC_WRITE) == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found");
        }
    }

    private String validName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty() || normalized.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name must be 1 to 100 characters");
        }
        return normalized;
    }

    private String validColor(String color) {
        String normalized = color == null ? "" : color.trim().toLowerCase(Locale.ROOT);
        if (!COLORS.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown member color");
        }
        return normalized;
    }
}
