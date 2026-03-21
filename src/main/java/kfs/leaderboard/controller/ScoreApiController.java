package kfs.leaderboard.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kfs.leaderboard.model.GameId;
import kfs.leaderboard.model.GameScore;
import kfs.leaderboard.service.ScoreService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/scores")
@CrossOrigin
public class ScoreApiController {

    private final ScoreService scoreService;

    public ScoreApiController(ScoreService scoreService) {
        this.scoreService = scoreService;
    }

    public record SubmitRequest(
        @NotBlank String gameId,
        @NotBlank @Size(min = 1, max = 20) String playerName,
        @NotNull Integer score
    ) {}

    @PostMapping
    public ResponseEntity<?> submitScore(@Valid @RequestBody SubmitRequest req) {
        if (!GameId.isValid(req.gameId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown gameId: " + req.gameId()));
        }
        if (req.score() < 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Score must be >= 0"));
        }

        ScoreService.SubmitResult result = scoreService.submitScore(req.gameId(), req.playerName(), req.score());
        return ResponseEntity.ok(Map.of(
            "rank", result.rank(),
            "personalBest", result.personalBest(),
            "isNewRecord", result.isNewRecord()
        ));
    }

    @GetMapping("/{gameId}/top")
    public ResponseEntity<?> getTopScores(
            @PathVariable String gameId,
            @RequestParam(defaultValue = "10") int limit) {
        if (!GameId.isValid(gameId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown gameId: " + gameId));
        }
        List<GameScore> scores = scoreService.getTopScores(gameId, Math.min(limit, 100));
        return ResponseEntity.ok(scores);
    }

    @GetMapping("/{gameId}/player/{playerName}")
    public ResponseEntity<?> getPlayerStats(
            @PathVariable String gameId,
            @PathVariable String playerName) {
        if (!GameId.isValid(gameId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown gameId: " + gameId));
        }
        ScoreService.PlayerStats stats = scoreService.getPlayerStats(gameId, playerName);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/games")
    public ResponseEntity<?> listGames() {
        return ResponseEntity.ok(scoreService.getAllGamesOverview());
    }
}
