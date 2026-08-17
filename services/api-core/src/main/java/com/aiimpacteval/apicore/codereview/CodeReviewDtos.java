package com.aiimpacteval.apicore.codereview;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response DTOs for GET /api/v1/metrics/code-review. Field names mirror
 * {@code frontend/src/mock/mockData.ts}'s {@code codeReview} export exactly, so swapping the
 * frontend from the mock to this endpoint is a data-source change only, not a shape change.
 */
public final class CodeReviewDtos {

    public record CodeReviewResponse(String windowLabel, List<PrCycleStage> cycleStages,
                                     List<ReviewerLoad> reviewLoad, AgingPrsPage agingPrs) {
    }

    public record PrCycleStage(String stage, BigDecimal hoursP50) {
    }

    public record ReviewerLoad(String reviewer, int reviews) {
    }

    public record AgingPrsPage(List<AgingPr> items, int page, int pageSize, long totalCount) {
    }

    /**
     * {@code sizeLines} is always null here — PR size (additions/deletions) isn't in the bulk
     * "List pull requests" payload the connector fetches, and adding a second per-PR API call
     * just for that field wasn't judged worth the extra GitHub rate-limit cost. Known gap, not
     * faked with a placeholder value. (Sorting by "size" is accepted by the API but currently
     * falls back to age ordering for the same reason.)
     */
    public record AgingPr(String id, String title, String repo, String author, long ageHours,
                          List<String> reviewers, Integer sizeLines) {
    }

    private CodeReviewDtos() {
    }
}