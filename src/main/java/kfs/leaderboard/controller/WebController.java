package kfs.leaderboard.controller;

import kfs.leaderboard.model.GameId;
import kfs.leaderboard.service.ScoreService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class WebController {

    private final ScoreService scoreService;

    public WebController(ScoreService scoreService) {
        this.scoreService = scoreService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("games", scoreService.getAllGamesOverview());
        return "index";
    }

    @GetMapping("/leaderboard/{gameId}")
    public String leaderboard(@PathVariable String gameId, Model model) {
        GameId game = GameId.fromId(gameId);
        if (game == null) return "redirect:/";

        model.addAttribute("gameName", game.getDisplayName());
        model.addAttribute("scores", scoreService.getTopScores(gameId, 50));
        return "leaderboard";
    }
}
