package uk.creatopia.unbound.dead_air.tower;

/**
 * Types of radio towers from Apocalypse Structures: Radio Towers and Airdrops mod.
 */
public enum ApocalypseTowerType {
    /**
     * Standard tower - always broadcasting, no activation needed.
     */
    STANDARD,
    
    /**
     * Fenced tower - 20% start broadcasting, 80% need activation via Radio Panel.
     */
    FENCED,
    
    /**
     * Overrun tower - always needs activation via Radio Panel (starts off).
     */
    OVERRUN,
    
    /**
     * Unknown tower type (fallback).
     */
    UNKNOWN
}
