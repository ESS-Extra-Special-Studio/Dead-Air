package uk.co.extraspecialstudio.dead_air.survivor;

/** Extra AI mode while SETTLED (or following after defend order). */
public enum SurvivorDuty {
    NONE,
    DEFEND,
    PATROL;

    public static SurvivorDuty byIndex(int i) {
        SurvivorDuty[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}
