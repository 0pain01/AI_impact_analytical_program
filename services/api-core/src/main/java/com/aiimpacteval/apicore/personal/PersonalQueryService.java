package com.aiimpacteval.apicore.personal;

import com.aiimpacteval.apicore.personal.PersonalDtos.OwnPr;
import com.aiimpacteval.apicore.personal.PersonalDtos.PersonalActivityResponse;
import com.aiimpacteval.apicore.personal.PersonalDtos.ReviewGiven;
import com.aiimpacteval.apicore.security.AppUserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Backs the Personal Activity tab (PRD persona table: "Individual Contributor... Self only").
 * Unlike every other query service in this app, there's no {@code scope} parameter at all — the
 * caller's own {@code core.app_user.github_login} (set by an admin) is the only filter, resolved
 * from their JWT subject (email), never from a request param. That's what makes "self only" a
 * real guarantee rather than a UI convention: there's no argument to pass a different identity in.
 */
@Service
public class PersonalQueryService {

    private static final int MAX_ROWS = 20;

    private final JdbcTemplate jdbcTemplate;
    private final AppUserRepository appUserRepository;

    public PersonalQueryService(JdbcTemplate jdbcTemplate, AppUserRepository appUserRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.appUserRepository = appUserRepository;
    }

    public PersonalActivityResponse activity(String callerEmail) {
        String githubLogin = appUserRepository.findByEmail(callerEmail)
                .map(u -> u.githubLogin())
                .orElse(null);

        if (githubLogin == null) {
            // No GitHub identity linked yet — an admin needs to set one. Empty, not an error:
            // this is an expected state for a brand-new IC account.
            return new PersonalActivityResponse(null, List.of(), List.of());
        }

        return new PersonalActivityResponse(githubLogin, openPrs(githubLogin), reviewsGiven(githubLogin));
    }

    private List<OwnPr> openPrs(String githubLogin) {
        return jdbcTemplate.query("""
                SELECT repo, number, title, created_at
                FROM staging.pull_request_state
                WHERE state = 'open' AND number IS NOT NULL AND author = ?
                ORDER BY created_at ASC
                LIMIT ?
                """,
                (rs, rowNum) -> {
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    long ageHours = createdAt == null ? 0
                            : Duration.between(createdAt.toInstant(), Instant.now()).toHours();
                    return new OwnPr("#" + rs.getLong("number"), rs.getString("title"),
                            rs.getString("repo"), ageHours);
                },
                githubLogin, MAX_ROWS);
    }

    private List<ReviewGiven> reviewsGiven(String githubLogin) {
        return jdbcTemplate.query("""
                SELECT repo, pr_number, state, submitted_at
                FROM staging.pull_request_review_state
                WHERE reviewer_login = ?
                ORDER BY submitted_at DESC
                LIMIT ?
                """,
                (rs, rowNum) -> {
                    Timestamp submittedAt = rs.getTimestamp("submitted_at");
                    return new ReviewGiven(rs.getString("repo"), "#" + rs.getLong("pr_number"),
                            rs.getString("state"), submittedAt == null ? null : submittedAt.toInstant().toString());
                },
                githubLogin, MAX_ROWS);
    }
}