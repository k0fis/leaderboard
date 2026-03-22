package kfs.leaderboard.model;

import java.util.Map;

/**
 * Registry of known games with their metadata.
 */
public enum GameId {

    SPACE_INVADERS("space-invaders", "Space Invaders"),
    RIVER_RAID("river-raid", "River Raid"),
    BOULDER_DASH("boulder-dash", "Boulder Dash"),
    ARKANOID("arkanoid", "Arkanoid"),
    SOKOBAN("sokoban", "Sokoban"),
    KFS_TETRIS("kfs-tetris", "Tetris");

    private final String id;
    private final String displayName;

    GameId(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }

    private static final Map<String, GameId> BY_ID;

    static {
        var map = new java.util.HashMap<String, GameId>();
        for (GameId g : values()) {
            map.put(g.id, g);
        }
        BY_ID = Map.copyOf(map);
    }

    public static GameId fromId(String id) {
        return BY_ID.get(id);
    }

    public static boolean isValid(String id) {
        return BY_ID.containsKey(id);
    }
}
