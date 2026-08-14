package com.aiimpacteval.metrics;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal trigger for on-demand recompute (smoke tests, post-backfill); not exposed publicly. */
@RestController
public class RecomputeController {

    private final MetricsRecomputeService recomputeService;

    public RecomputeController(MetricsRecomputeService recomputeService) {
        this.recomputeService = recomputeService;
    }

    @PostMapping("/internal/recompute")
    public ResponseEntity<?> recompute() {
        MetricsRecomputeService.RecomputeResult result = recomputeService.recomputeAll();
        if (result == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Recompute already in progress (scheduled or manual) — try again shortly.");
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/internal/recompute/status")
    public MetricsRecomputeService.RecomputeStatus status() {
        return recomputeService.currentStatus();
    }
}