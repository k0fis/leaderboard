package kfs.leaderboard.controller;

import kfs.leaderboard.repository.ScoreRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Map;

@RestController
public class HealthController {

    private final ScoreRepository repo;

    public HealthController(ScoreRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/api/health")
    public ResponseEntity<?> health() {
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
        String uptimeStr = Duration.ofMillis(uptime).toString()
            .substring(2).replaceAll("(\\d[HMS])(?!$)", "$1 ").toLowerCase();

        boolean dbOk;
        long totalScores;
        try {
            totalScores = repo.count();
            dbOk = true;
        } catch (Exception e) {
            totalScores = -1;
            dbOk = false;
        }

        String status = dbOk ? "UP" : "DOWN";

        return ResponseEntity
            .status(dbOk ? 200 : 503)
            .body(Map.of(
                "status", status,
                "uptime", uptimeStr,
                "totalScores", totalScores
            ));
    }
}
