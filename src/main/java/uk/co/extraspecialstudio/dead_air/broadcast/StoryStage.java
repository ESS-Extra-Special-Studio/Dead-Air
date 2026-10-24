package uk.co.extraspecialstudio.dead_air.broadcast;

/** Story stages for The Listener, stored per player in {@link BroadcastProgress}. */
public final class StoryStage {
    /** No story broadcast heard yet. */
    public static final int NONE = 0;
    /** Following relay coordinates. */
    public static final int RELAYS = 1;
    /** Final broadcast heard: the hidden frequency is known and he can be summoned. */
    public static final int FINAL_BROADCAST = 2;
    /** First encounter survived; Boss Mode unlocked. */
    public static final int FIRST_SURVIVED = 3;
    /** Final encounter won. */
    public static final int DEFEATED = 4;

    public static String name(int stage) {
        return switch (stage) {
            case NONE -> "none";
            case RELAYS -> "relays";
            case FINAL_BROADCAST -> "final_broadcast";
            case FIRST_SURVIVED -> "first_survived";
            case DEFEATED -> "defeated";
            default -> "unknown(" + stage + ")";
        };
    }

    private StoryStage() {}
}
