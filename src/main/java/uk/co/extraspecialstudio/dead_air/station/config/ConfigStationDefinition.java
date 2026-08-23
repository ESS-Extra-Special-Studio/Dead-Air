package uk.co.extraspecialstudio.dead_air.station.config;

import java.util.List;

/**
 * JSON station definition loaded from /config/dead_air/stations/*.json.
 *
 * Kept intentionally minimal; extra fields can be added later without breaking older packs.
 */
@SuppressWarnings("null")
public class ConfigStationDefinition {
    public String station_id;
    public String display_name;
    public String type; // MUSIC / LORE / CORRUPTED / EMERGENCY_BROADCAST / STREAM (future)
    public String genre;
    public Float frequency;
    public Integer range;
    public Integer min_tower_spacing;
    public Double weight;
    public List<String> tracks;
    public String stream_url; // future
}

