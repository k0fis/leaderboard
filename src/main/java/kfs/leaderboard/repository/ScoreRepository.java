package kfs.leaderboard.repository;

import kfs.leaderboard.model.GameScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScoreRepository extends JpaRepository<GameScore, Long> {

    List<GameScore> findByGameIdOrderByScoreDesc(String gameId);

    @Query("SELECT s FROM GameScore s WHERE s.gameId = :gameId ORDER BY s.score DESC LIMIT :limit")
    List<GameScore> findTopScores(@Param("gameId") String gameId, @Param("limit") int limit);

    @Query("SELECT s FROM GameScore s WHERE s.gameId = :gameId AND s.playerName = :playerName ORDER BY s.score DESC")
    List<GameScore> findByGameIdAndPlayerName(@Param("gameId") String gameId, @Param("playerName") String playerName);

    @Query("SELECT MAX(s.score) FROM GameScore s WHERE s.gameId = :gameId AND s.playerName = :playerName")
    Optional<Integer> findBestScore(@Param("gameId") String gameId, @Param("playerName") String playerName);

    @Query("SELECT COUNT(DISTINCT s.playerName) FROM GameScore s WHERE s.gameId = :gameId AND s.score > :score")
    long countPlayersAbove(@Param("gameId") String gameId, @Param("score") int score);

    @Query("SELECT COUNT(s) FROM GameScore s WHERE s.gameId = :gameId AND s.playerName = :playerName")
    long countGames(@Param("gameId") String gameId, @Param("playerName") String playerName);

    @Query("SELECT MAX(s.score) FROM GameScore s WHERE s.gameId = :gameId")
    Optional<Integer> findTopScoreValue(@Param("gameId") String gameId);
}
