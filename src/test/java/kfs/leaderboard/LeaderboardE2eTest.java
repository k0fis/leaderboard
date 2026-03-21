package kfs.leaderboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import kfs.leaderboard.repository.ScoreRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LeaderboardE2eTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ScoreRepository repo;

    @Autowired
    private ObjectMapper json;

    @BeforeEach
    void cleanDb() {
        repo.deleteAll();
    }

    // --- Submit scores ---

    @Test
    void submitScore_returnsRankAndPersonalBest() throws Exception {
        mvc.perform(post("/api/scores")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "gameId", "space-invaders",
                    "playerName", "KUB",
                    "score", 4250))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rank").value(1))
            .andExpect(jsonPath("$.personalBest").value(4250))
            .andExpect(jsonPath("$.isNewRecord").value(true));
    }

    @Test
    void submitScore_secondScore_tracksPersonalBest() throws Exception {
        submitScore("space-invaders", "KUB", 4250);
        submitScore("space-invaders", "KUB", 5100);

        mvc.perform(post("/api/scores")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "gameId", "space-invaders",
                    "playerName", "KUB",
                    "score", 3000))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.personalBest").value(5100))
            .andExpect(jsonPath("$.isNewRecord").value(false));
    }

    @Test
    void submitScore_rankCalculation() throws Exception {
        submitScore("space-invaders", "BOB", 6000);
        submitScore("space-invaders", "AAA", 3000);

        // KUB with 5000 should be rank 2 (BOB above)
        mvc.perform(post("/api/scores")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "gameId", "space-invaders",
                    "playerName", "KUB",
                    "score", 5000))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rank").value(2));
    }

    // --- Validation ---

    @Test
    void submitScore_unknownGameId_returns400() throws Exception {
        mvc.perform(post("/api/scores")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "gameId", "unknown-game",
                    "playerName", "KUB",
                    "score", 100))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("Unknown gameId")));
    }

    @Test
    void submitScore_negativeScore_returns400() throws Exception {
        mvc.perform(post("/api/scores")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "gameId", "space-invaders",
                    "playerName", "KUB",
                    "score", -5))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("Score must be >= 0")));
    }

    @Test
    void submitScore_emptyPlayerName_returns400() throws Exception {
        mvc.perform(post("/api/scores")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"gameId\":\"space-invaders\",\"playerName\":\"\",\"score\":100}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void submitScore_missingFields_returns400() throws Exception {
        mvc.perform(post("/api/scores")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    // --- Top scores ---

    @Test
    void getTopScores_returnsSortedByScoreDesc() throws Exception {
        submitScore("space-invaders", "AAA", 3000);
        submitScore("space-invaders", "BOB", 6000);
        submitScore("space-invaders", "KUB", 5100);

        mvc.perform(get("/api/scores/space-invaders/top")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(3)))
            .andExpect(jsonPath("$[0].playerName").value("BOB"))
            .andExpect(jsonPath("$[0].score").value(6000))
            .andExpect(jsonPath("$[1].playerName").value("KUB"))
            .andExpect(jsonPath("$[1].score").value(5100))
            .andExpect(jsonPath("$[2].playerName").value("AAA"))
            .andExpect(jsonPath("$[2].score").value(3000));
    }

    @Test
    void getTopScores_respectsLimit() throws Exception {
        submitScore("space-invaders", "A", 100);
        submitScore("space-invaders", "B", 200);
        submitScore("space-invaders", "C", 300);

        mvc.perform(get("/api/scores/space-invaders/top")
                .param("limit", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].score").value(300))
            .andExpect(jsonPath("$[1].score").value(200));
    }

    @Test
    void getTopScores_emptyGame_returnsEmptyList() throws Exception {
        mvc.perform(get("/api/scores/arkanoid/top"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getTopScores_unknownGameId_returns400() throws Exception {
        mvc.perform(get("/api/scores/fake-game/top"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("Unknown gameId")));
    }

    @Test
    void getTopScores_isolatedPerGame() throws Exception {
        submitScore("space-invaders", "KUB", 5000);
        submitScore("river-raid", "KUB", 12000);

        mvc.perform(get("/api/scores/space-invaders/top"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].score").value(5000));

        mvc.perform(get("/api/scores/river-raid/top"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].score").value(12000));
    }

    // --- Player stats ---

    @Test
    void getPlayerStats_returnsCorrectStats() throws Exception {
        submitScore("space-invaders", "KUB", 4250);
        submitScore("space-invaders", "KUB", 5100);
        submitScore("space-invaders", "KUB", 3000);
        submitScore("space-invaders", "BOB", 6000);

        mvc.perform(get("/api/scores/space-invaders/player/KUB"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bestScore").value(5100))
            .andExpect(jsonPath("$.totalGames").value(3))
            .andExpect(jsonPath("$.rank").value(2))
            .andExpect(jsonPath("$.recentScores", hasSize(3)))
            .andExpect(jsonPath("$.recentScores[0].score").value(5100));
    }

    @Test
    void getPlayerStats_unknownPlayer_returnsZeros() throws Exception {
        mvc.perform(get("/api/scores/space-invaders/player/NOBODY"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bestScore").value(0))
            .andExpect(jsonPath("$.totalGames").value(0))
            .andExpect(jsonPath("$.rank").value(0));
    }

    @Test
    void getPlayerStats_unknownGameId_returns400() throws Exception {
        mvc.perform(get("/api/scores/fake-game/player/KUB"))
            .andExpect(status().isBadRequest());
    }

    // --- Games overview ---

    @Test
    void listGames_returnsAllGamesWithTopScores() throws Exception {
        submitScore("space-invaders", "KUB", 5100);
        submitScore("river-raid", "JAN", 8900);

        mvc.perform(get("/api/scores/games"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(5)))
            .andExpect(jsonPath("$[?(@.gameId == 'space-invaders')].topScore").value(5100))
            .andExpect(jsonPath("$[?(@.gameId == 'river-raid')].topScore").value(8900))
            .andExpect(jsonPath("$[?(@.gameId == 'arkanoid')].topScore").value(everyItem(nullValue())));
    }

    // --- Web pages ---

    @Test
    void webIndex_returnsHtml() throws Exception {
        mvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andExpect(content().string(containsString("KFS Games Leaderboard")));
    }

    @Test
    void webLeaderboard_returnsHtmlWithScores() throws Exception {
        submitScore("space-invaders", "BOB", 6000);
        submitScore("space-invaders", "KUB", 5100);

        mvc.perform(get("/leaderboard/space-invaders"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andExpect(content().string(containsString("Space Invaders")))
            .andExpect(content().string(containsString("BOB")))
            .andExpect(content().string(containsString("6000")));
    }

    @Test
    void webLeaderboard_unknownGame_redirectsToIndex() throws Exception {
        mvc.perform(get("/leaderboard/fake-game"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/"));
    }

    @Test
    void webLeaderboard_emptyGame_showsEmptyMessage() throws Exception {
        mvc.perform(get("/leaderboard/arkanoid"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("No scores yet")));
    }

    // --- Health ---

    @Test
    void health_returnsUpStatus() throws Exception {
        mvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.uptime").isString())
            .andExpect(jsonPath("$.totalScores").isNumber());
    }

    // --- CORS ---

    @Test
    void corsHeaders_presentOnApiResponses() throws Exception {
        mvc.perform(options("/api/scores")
                .header("Origin", "https://kofis.eu")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isOk())
            .andExpect(header().exists("Access-Control-Allow-Origin"));
    }

    // --- Full flow ---

    @Test
    void fullFlow_submitAndRetrieve() throws Exception {
        // 1. Games overview - empty
        mvc.perform(get("/api/scores/games"))
            .andExpect(jsonPath("$[?(@.gameId == 'space-invaders')].topScore").value(everyItem(nullValue())));

        // 2. Submit first score
        mvc.perform(post("/api/scores")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "gameId", "space-invaders", "playerName", "KUB", "score", 4250))))
            .andExpect(jsonPath("$.rank").value(1))
            .andExpect(jsonPath("$.isNewRecord").value(true));

        // 3. Another player beats it
        mvc.perform(post("/api/scores")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "gameId", "space-invaders", "playerName", "BOB", "score", 6000))))
            .andExpect(jsonPath("$.rank").value(1));

        // 4. KUB submits again, lower - rank 3 (BOB:6000, KUB:4250, this:3000)
        mvc.perform(post("/api/scores")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "gameId", "space-invaders", "playerName", "KUB", "score", 3000))))
            .andExpect(jsonPath("$.rank").value(3))
            .andExpect(jsonPath("$.personalBest").value(4250))
            .andExpect(jsonPath("$.isNewRecord").value(false));

        // 5. Top scores reflect all entries
        mvc.perform(get("/api/scores/space-invaders/top"))
            .andExpect(jsonPath("$", hasSize(3)))
            .andExpect(jsonPath("$[0].playerName").value("BOB"))
            .andExpect(jsonPath("$[0].score").value(6000));

        // 6. Player stats for KUB
        mvc.perform(get("/api/scores/space-invaders/player/KUB"))
            .andExpect(jsonPath("$.bestScore").value(4250))
            .andExpect(jsonPath("$.totalGames").value(2))
            .andExpect(jsonPath("$.rank").value(2));

        // 7. Games overview updated
        mvc.perform(get("/api/scores/games"))
            .andExpect(jsonPath("$[?(@.gameId == 'space-invaders')].topScore").value(6000));

        // 8. Web leaderboard shows data
        mvc.perform(get("/leaderboard/space-invaders"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("BOB")))
            .andExpect(content().string(containsString("6000")));
    }

    // --- Helper ---

    private void submitScore(String gameId, String playerName, int score) throws Exception {
        mvc.perform(post("/api/scores")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                    "gameId", gameId,
                    "playerName", playerName,
                    "score", score))))
            .andExpect(status().isOk());
    }
}
