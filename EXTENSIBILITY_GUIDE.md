# Dead Air Mod - Extensibility Guide

This guide explains how to add custom music stations and tracks to the Dead Air mod.

## Overview

The Dead Air mod is built with extensibility in mind. You can add custom stations and music tracks through:
1. **Public API** - Programmatic registration
2. **Event System** - Listen to registration events
3. **Automatic Discovery** - Auto-detects music from other mods

## Methods for Adding Stations

### Method 1: Using the Public API

The simplest way to add stations is through the `DeadAirAPI` class:

```java
import uk.creatopia.unbound.dead_air.api.DeadAirAPI;
import net.minecraft.resources.ResourceLocation;
import java.util.Arrays;

// Create a music station with tracks
ResourceLocation stationId = ResourceLocation.fromNamespaceAndPath("mymod", "custom_radio");
List<ResourceLocation> tracks = Arrays.asList(
    ResourceLocation.fromNamespaceAndPath("mymod", "music.track1"),
    ResourceLocation.fromNamespaceAndPath("mymod", "music.track2")
);

RadioStation station = DeadAirAPI.createMusicStation(
    stationId,
    "My Custom Radio",
    "Rock",
    95.5f,  // Frequency in MHz
    tracks
);
```

### Method 2: Using Events

Listen to the `StationRegistrationEvent` to register stations during mod initialization:

```java
@Mod.EventBusSubscriber(modid = "mymod")
public class MyModEvents {
    @SubscribeEvent
    public static void onStationRegistration(StationRegistrationEvent event) {
        RadioStation station = new RadioStation(
            ResourceLocation.fromNamespaceAndPath("mymod", "my_station"),
            "My Station",
            RadioStation.StationType.MUSIC,
            "Jazz",
            92.3f,
            1500,  // Broadcast range
            400     // Min tower spacing
        );
        event.registerStation(station);
    }
}
```

### Method 3: Adding Tracks to Existing Stations

Add tracks to stations that already exist:

```java
// Add a track to the vanilla music station
DeadAirAPI.addTrackToStation(
    StationRegistry.VANILLA_MUSIC_ID,
    ResourceLocation.fromNamespaceAndPath("mymod", "music.new_track")
);
```

### Method 4: Automatic Discovery

The mod automatically discovers music tracks from other mods:
- Scans `ForgeRegistries.SOUND_EVENTS` for tracks
- Creates stations based on mod namespace
- Groups tracks by genre (detected from track name)

To take advantage of this, name your sound events with keywords:
- `music.*` - Will be detected as music
- `ambient.*` - Will be detected as ambient music
- `medieval.*` or `fantasy.*` - Will be grouped as Medieval genre
- `sci.*` or `tech.*` - Will be grouped as Sci-Fi genre
- `horror.*` or `dark.*` - Will be grouped as Horror genre

## Station Types

Available station types:
- `EMERGENCY_BROADCAST` - For airdrop alerts and emergency messages
- `MUSIC` - For music playback
- `LORE` - For story recordings and narrative content
- `CORRUPTED` - For special effects stations (future feature)

## Example: Complete Custom Station

```java
@Mod("mymod")
public class MyMod {
    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Create a custom music station
            ResourceLocation stationId = ResourceLocation.fromNamespaceAndPath("mymod", "rock_radio");
            
            List<ResourceLocation> tracks = Arrays.asList(
                ResourceLocation.fromNamespaceAndPath("mymod", "music.rock1"),
                ResourceLocation.fromNamespaceAndPath("mymod", "music.rock2"),
                ResourceLocation.fromNamespaceAndPath("mymod", "music.rock3")
            );
            
            RadioStation station = DeadAirAPI.createMusicStation(
                stationId,
                "Rock Radio 95.5",
                "Rock",
                95.5f,
                tracks
            );
            
            if (station != null) {
                Dead_air.LOGGER.info("Registered custom station: {}", station.getName());
            }
        });
    }
}
```

## API Reference

### DeadAirAPI Methods

- `registerStation(RadioStation)` - Register a custom station
- `addTrackToStation(ResourceLocation stationId, ResourceLocation trackId)` - Add track to station
- `createMusicStation(...)` - Create and register a music station
- `createLoreStation(...)` - Create and register a lore station
- `getAllStations()` - Get all registered stations
- `getStation(ResourceLocation)` - Get a specific station
- `getStationTracks(ResourceLocation)` - Get all tracks for a station

### MusicStationManager Methods

- `addTrack(ResourceLocation stationId, ResourceLocation trackId)` - Add track
- `removeTrack(ResourceLocation stationId, ResourceLocation trackId)` - Remove track
- `clearTracks(ResourceLocation stationId)` - Clear all tracks
- `getNextTrack(ResourceLocation stationId)` - Get next track (for playback)
- `getRandomTrack(ResourceLocation stationId)` - Get random track

## Best Practices

1. **Use unique station IDs** - Include your mod ID in the station ID to avoid conflicts
2. **Set appropriate frequencies** - Use frequencies between 88.0 and 108.0 MHz
3. **Group related tracks** - Create stations with similar genres/themes
4. **Register during mod init** - Use `FMLCommonSetupEvent` or `StationRegistrationEvent`
5. **Use sound events** - Register your music as `SoundEvent` in Forge's registry

## Future Enhancements

Planned features for future updates:
- Data pack support for JSON-defined stations
- Custom station icons/textures
- Station playlists with shuffle/loop modes
- Station-specific effects (corrupted stations, etc.)
- Network sync for custom stations
