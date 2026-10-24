package uk.co.extraspecialstudio.dead_air.music;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import uk.co.extraspecialstudio.dead_air.Dead_air;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Expansion pack namespaces this world has seen, so their stations stay registered across restarts. */
@SuppressWarnings("null")
public class ExpansionStationsStorage extends SavedData {
    private static final String DATA_NAME = Dead_air.MODID + "_expansion_stations";
    private final Set<String> namespaces = new LinkedHashSet<>();

    public ExpansionStationsStorage() {}

    public static ExpansionStationsStorage load(CompoundTag nbt) {
        ExpansionStationsStorage s = new ExpansionStationsStorage();
        ListTag list = nbt.getList("namespaces", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) s.namespaces.add(list.getString(i));
        return s;
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        ListTag list = new ListTag();
        for (String ns : namespaces) list.add(StringTag.valueOf(ns));
        nbt.put("namespaces", list);
        return nbt;
    }

    public Set<String> getNamespaces() {
        return Collections.unmodifiableSet(namespaces);
    }

    public void add(String namespace) {
        if (namespaces.add(namespace)) setDirty();
    }

    public static ExpansionStationsStorage get(MinecraftServer server) {
        if (server == null) return null;
        ServerLevel overworld = server.overworld();
        if (overworld == null) return null;
        return overworld.getDataStorage().computeIfAbsent(ExpansionStationsStorage::load, ExpansionStationsStorage::new, DATA_NAME);
    }
}
