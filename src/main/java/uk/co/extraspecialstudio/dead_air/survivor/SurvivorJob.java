package uk.co.extraspecialstudio.dead_air.survivor;

/** Assigned role while settled at a base. Distinct from spawn profession skin. */
public enum SurvivorJob {
    IDLE("idle"),
    GUARD("guard"),
    SCAVENGER("scavenger"),
    MEDIC("medic"),
    RADIO_OP("radio_op");

    public final String id;

    SurvivorJob(String id) {
        this.id = id;
    }

    public static SurvivorJob byIndex(int i) {
        SurvivorJob[] v = values();
        return v[Math.floorMod(i, v.length)];
    }

    public static SurvivorJob byId(String id) {
        for (SurvivorJob j : values()) {
            if (j.id.equalsIgnoreCase(id)) return j;
        }
        return IDLE;
    }

    public SurvivorJob next() {
        return byIndex(ordinal() + 1);
    }
}
