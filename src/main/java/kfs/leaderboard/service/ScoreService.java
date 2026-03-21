package kfs.leaderboard.service;

import kfs.leaderboard.model.GameId;
import kfs.leaderboard.model.GameScore;
import kfs.leaderboard.repository.ScoreRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ScoreService {

    private final ScoreRepository repo;

    public ScoreService(ScoreRepository repo) {
        this.repo = repo;
    }

    public record SubmitResult(long rank, int personalBest, boolean isNewRecord) {}

    public record PlayerStats(int bestScore, long totalGames, long rank, List<GameScore> recentScores) {}

    public record GameOverview(String gameId, String displayName, Integer topScore) {}

    public SubmitResult submitScore(String gameId, String playerName, int score) {
        GameScore entry = new GameScore(gameId, playerName, score);
        repo.save(entry);

        int personalBest = repo.findBestScore(gameId, playerName).orElse(score);
        boolean isNewRecord = score >= personalBest;
        long rank = repo.countPlayersAbove(gameId, score) + 1;

        return new SubmitResult(rank, personalBest, isNewRecord);
    }

    public List<GameScore> getTopScores(String gameId, int limit) {
        return repo.findTopScores(gameId, limit);
    }

    public PlayerStats getPlayerStats(String gameId, String playerName) {
        int best = repo.findBestScore(gameId, playerName).orElse(0);
        long total = repo.countGames(gameId, playerName);
        long rank = best > 0 ? repo.countPlayersAbove(gameId, best) + 1 : 0;
        List<GameScore> recent = repo.findByGameIdAndPlayerName(gameId, playerName);
        if (recent.size() > 10) {
            recent = recent.subList(0, 10);
        }
        return new PlayerStats(best, total, rank, recent);
    }

    public List<GameOverview> getAllGamesOverview() {
        List<GameOverview> result = new ArrayList<>();
        for (GameId g : GameId.values()) {
            Integer top = repo.findTopScoreValue(g.getId()).orElse(null);
            result.add(new GameOverview(g.getId(), g.getDisplayName(), top));
        }
        return result;
    }
}
