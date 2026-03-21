# KFS Leaderboard

High score server pro KFS Games (Space Invaders, River Raid, ...).

- **Tech:** Spring Boot 3.4.3, Java 17, H2 database, Thymeleaf
- **Repo:** https://github.com/k0fis/leaderboard.git

## Lokální vývoj

```bash
mvn spring-boot:run          # spustí na http://localhost:8080
mvn test                     # 23 E2E testů
mvn package -DskipTests      # build JAR
```

## CI/CD

Push do `main` → GitHub Actions buildne + vytvoří release s:
- `kfsLeaderboard.jar` - aplikace
- `kfs-leaderboard.sh` - launcher + watchdog

## Deploy na server

Script běží **na serveru** (kofis.eu). Stáhne poslední release z GitHubu.

```bash
ssh kofis@kofis.eu
cd ~/leaderboard
./deploy-kfs-leaderboard.sh              # stáhne JAR + launcher z GH, restartuje
./deploy-kfs-leaderboard.sh download     # jen stáhne, nespouští
```

Deploy script:
1. Zjistí poslední release z GitHub API
2. Stáhne `kfsLeaderboard.jar` a `kfs-leaderboard.sh`
3. Zastaví běžící server
4. Zálohuje starý JAR → `.bak`
5. Prohodí soubory, spustí server + watchdog

### První instalace

```bash
ssh kofis@kofis.eu
mkdir -p ~/leaderboard && cd ~/leaderboard
curl -sL https://api.github.com/repos/k0fis/leaderboard/releases/latest \
  | grep browser_download_url | cut -d '"' -f 4 \
  | xargs -n1 curl -sLO
chmod +x deploy-kfs-leaderboard.sh kfs-leaderboard.sh
./deploy-kfs-leaderboard.sh
```

### Správa na serveru

```bash
cd ~/leaderboard
./kfs-leaderboard.sh start       # spustí server + watchdog
./kfs-leaderboard.sh stop        # zastaví obojí
./kfs-leaderboard.sh restart     # restart
./kfs-leaderboard.sh status      # stav + health check
./kfs-leaderboard.sh log         # tail leaderboard.log
```

### Watchdog

- Kontroluje `/api/health` každých 60s
- Po 3 selháních restartuje server
- Automaticky spustí server pokud proces spadne
- Loguje do `leaderboard.log`

### Struktura na serveru

```
/home/kofis/leaderboard/
├── kfsLeaderboard.jar        # aplikace
├── kfsLeaderboard.jar.bak    # záloha předchozí verze
├── kfs-leaderboard.sh        # launcher + watchdog
├── leaderboard.log           # log
├── leaderboard.pid           # PID serveru
├── watchdog.pid              # PID watchdogu
└── data/
    └── leaderboard.mv.db     # H2 databáze (persistentní)
```

---

## REST API

### POST /api/scores

Odeslání score po skončení hry.

```
POST /api/scores
Content-Type: application/json

{ "gameId": "space-invaders", "playerName": "KUB", "score": 4250 }
```

```json
{ "rank": 3, "personalBest": 5100, "isNewRecord": false }
```

- `rank` - pozice tohoto score v celkovém žebříčku (1 = nejlepší)
- `personalBest` - nejlepší score tohoto hráče v této hře
- `isNewRecord` - true pokud tento score je nový osobní rekord

### GET /api/scores/{gameId}/top?limit=10

Top žebříček. `limit` volitelný (default 10, max 100).

```json
[
  { "id": 4, "gameId": "space-invaders", "playerName": "BOB", "score": 6000, "createdAt": "..." },
  { "id": 2, "gameId": "space-invaders", "playerName": "KUB", "score": 5100, "createdAt": "..." }
]
```

### GET /api/scores/{gameId}/player/{playerName}

Statistiky hráče.

```json
{ "bestScore": 5100, "totalGames": 3, "rank": 2, "recentScores": [...] }
```

### GET /api/scores/games

Přehled všech her.

```json
[
  { "gameId": "space-invaders", "displayName": "Space Invaders", "topScore": 6000 },
  { "gameId": "river-raid", "displayName": "River Raid", "topScore": 12500 },
  { "gameId": "arkanoid", "displayName": "Arkanoid", "topScore": null }
]
```

### GET /api/health

Health check pro watchdog.

```json
{ "status": "UP", "uptime": "5m 32.451s", "totalScores": 42 }
```

Vrací 200 (UP) nebo 503 (DOWN).

### Validace

- Neznámý `gameId` → 400 `{ "error": "Unknown gameId: xxx" }`
- Záporné score → 400 `{ "error": "Score must be >= 0" }`
- Prázdné jméno → 400
- CORS povolený pro `*` na `/api/**`

### Platné gameId

| gameId | Hra |
|--------|-----|
| `space-invaders` | Space Invaders |
| `river-raid` | River Raid |
| `boulder-dash` | Boulder Dash |
| `arkanoid` | Arkanoid |
| `sokoban` | Sokoban |

---

## Integrace do her (LibGDX + TeaVM)

Hry používají LibGDX `Gdx.net` API - funguje cross-platform (desktop i web).
Veškerý kód jde do `core/` modulu. **Žádné extra závislosti.**

### ScoreClient

Přidat do `core/src/main/java/kfs/<game>/ScoreClient.java`:

```java
package kfs.invaders;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.net.HttpRequestBuilder;

public class ScoreClient {

    private static final String BASE_URL = "http://localhost:8080";  // TODO: produkční URL
    private static final String GAME_ID = "space-invaders";          // změnit per hra

    public interface SubmitCallback {
        void onSuccess(long rank, int personalBest, boolean isNewRecord);
        void onError(String message);
    }

    public interface TopScoresCallback {
        void onSuccess(String jsonResponse);
        void onError(String message);
    }

    public static void submitScore(String playerName, int score, SubmitCallback callback) {
        String json = "{\"gameId\":\"" + GAME_ID + "\","
                     + "\"playerName\":\"" + escapeJson(playerName) + "\","
                     + "\"score\":" + score + "}";

        Net.HttpRequest request = new HttpRequestBuilder()
            .newRequest()
            .method(Net.HttpMethods.POST)
            .url(BASE_URL + "/api/scores")
            .header("Content-Type", "application/json")
            .content(json)
            .build();

        Gdx.net.sendHttpRequest(request, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse httpResponse) {
                String body = httpResponse.getResultAsString();
                long rank = parseLong(body, "rank");
                int best = (int) parseLong(body, "personalBest");
                boolean isNew = body.contains("\"isNewRecord\":true");
                Gdx.app.postRunnable(() -> callback.onSuccess(rank, best, isNew));
            }

            @Override
            public void failed(Throwable t) {
                Gdx.app.postRunnable(() -> callback.onError(t.getMessage()));
            }

            @Override
            public void cancelled() {
                Gdx.app.postRunnable(() -> callback.onError("Cancelled"));
            }
        });
    }

    public static void getTopScores(int limit, TopScoresCallback callback) {
        Net.HttpRequest request = new HttpRequestBuilder()
            .newRequest()
            .method(Net.HttpMethods.GET)
            .url(BASE_URL + "/api/scores/" + GAME_ID + "/top?limit=" + limit)
            .build();

        Gdx.net.sendHttpRequest(request, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse httpResponse) {
                String body = httpResponse.getResultAsString();
                Gdx.app.postRunnable(() -> callback.onSuccess(body));
            }

            @Override
            public void failed(Throwable t) {
                Gdx.app.postRunnable(() -> callback.onError(t.getMessage()));
            }

            @Override
            public void cancelled() {
                Gdx.app.postRunnable(() -> callback.onError("Cancelled"));
            }
        });
    }

    private static long parseLong(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return 0;
        idx += search.length();
        StringBuilder sb = new StringBuilder();
        while (idx < json.length() && (Character.isDigit(json.charAt(idx)) || json.charAt(idx) == '-')) {
            sb.append(json.charAt(idx++));
        }
        return sb.length() > 0 ? Long.parseLong(sb.toString()) : 0;
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
```

### GameOverScreen úprava

V `GameOverScreen` přidat po zobrazení score:
1. `TextField` pro jméno hráče (default "AAA")
2. `TextButton` SUBMIT → volá `ScoreClient.submitScore()`
3. `Label` pro zobrazení ranku / "NEW RECORD!"

### Flow

```
Hráč umře → GameOverScreen(score)
  → "GAME OVER" + score
  → TextField pro jméno + SUBMIT
  → POST /api/scores → { rank, personalBest, isNewRecord }
  → "RANK #3" nebo "RANK #1 - NEW RECORD!"
  → PLAY AGAIN / MENU
```

### Web dashboard

- `http://server:8080/` - přehled všech her s top score
- `http://server:8080/leaderboard/{gameId}` - žebříček konkrétní hry
