package com.aiimpacteval.apicore.personal;

import java.util.List;

/**
 * Response DTOs for GET /api/v1/personal/activity — the Personal Activity tab (PRD persona
 * table: "Individual Contributor... Self only"). Deliberately smaller/friendlier than
 * CodeReviewDtos — this is one person's own view of their own work, not an analytics table.
 */
public final class PersonalDtos {

    public record PersonalActivityResponse(String githubLogin, List<OwnPr> openPrs,
                                           List<ReviewGiven> recentReviewsGiven) {
    }

    public record OwnPr(String id, String title, String repo, long ageHours) {
    }

    public record ReviewGiven(String repo, String prId, String state, String submittedAt) {
    }

    private PersonalDtos() {
    }
}