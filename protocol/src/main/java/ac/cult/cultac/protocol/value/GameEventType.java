package ac.cult.cultac.protocol.value;

public enum GameEventType {
    NO_RESPAWN_BLOCK_AVAILABLE,
    START_RAINING,
    STOP_RAINING,
    CHANGE_GAME_MODE,
    WIN_GAME,
    DEMO_EVENT,
    PLAY_ARROW_HIT_SOUND,
    RAIN_LEVEL_CHANGE,
    THUNDER_LEVEL_CHANGE,
    PUFFER_FISH_STING,
    GUARDIAN_ELDER_EFFECT,
    IMMEDIATE_RESPAWN,
    LIMITED_CRAFTING,
    LEVEL_CHUNKS_LOAD_START,
    UNKNOWN;

    public static GameEventType fromId(int id) {
        return switch (id) {
            case 0 -> NO_RESPAWN_BLOCK_AVAILABLE;
            case 1 -> START_RAINING;
            case 2 -> STOP_RAINING;
            case 3 -> CHANGE_GAME_MODE;
            case 4 -> WIN_GAME;
            case 5 -> DEMO_EVENT;
            case 6 -> PLAY_ARROW_HIT_SOUND;
            case 7 -> RAIN_LEVEL_CHANGE;
            case 8 -> THUNDER_LEVEL_CHANGE;
            case 9 -> PUFFER_FISH_STING;
            case 10 -> GUARDIAN_ELDER_EFFECT;
            case 11 -> IMMEDIATE_RESPAWN;
            case 12 -> LIMITED_CRAFTING;
            case 13 -> LEVEL_CHUNKS_LOAD_START;
            default -> UNKNOWN;
        };
    }
}
