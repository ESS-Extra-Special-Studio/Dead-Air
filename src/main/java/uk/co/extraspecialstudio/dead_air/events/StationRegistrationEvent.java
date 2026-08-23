package uk.co.extraspecialstudio.dead_air.events;

import net.neoforged.bus.api.Event;
import uk.co.extraspecialstudio.dead_air.radio.RadioStation;

import java.util.ArrayList;
import java.util.List;

/**
 * Event fired when stations are being registered.
 * Other mods can listen to this event to register custom stations.
 */
@SuppressWarnings("null")
public class StationRegistrationEvent extends Event {
    private final List<RadioStation> stationsToRegister = new ArrayList<>();
    
    /**
     * Register a custom station during this event.
     * 
     * @param station The station to register
     */
    public void registerStation(RadioStation station) {
        stationsToRegister.add(station);
    }
    
    /**
     * Get all stations that were registered during this event.
     * 
     * @return List of stations to register
     */
    public List<RadioStation> getStationsToRegister() {
        return stationsToRegister;
    }
}
