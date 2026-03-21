package kfs.leaderboard.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "game_scores", indexes = {
    @Index(name = "idx_game_score", columnList = "gameId, score DESC"),
    @Index(name = "idx_player", columnList = "playerName, gameId")
})
public class GameScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 50)
    private String gameId;

    @NotBlank
    @Size(min = 1, max = 20)
    @Column(nullable = false, length = 20)
    private String playerName;

    @NotNull
    @Column(nullable = false)
    private Integer score;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public GameScore() {}

    public GameScore(String gameId, String playerName, int score) {
        this.gameId = gameId;
        this.playerName = playerName;
        this.score = score;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Transient
    public String getFormattedDate() {
        return createdAt != null ? createdAt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) : "";
    }
}
