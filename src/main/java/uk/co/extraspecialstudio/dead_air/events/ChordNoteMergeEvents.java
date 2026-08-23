package uk.co.extraspecialstudio.dead_air.events;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.item.DeadAirItems;
import uk.co.extraspecialstudio.dead_air.item.PlaceableDecorations;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Throw Static Notes into a pile on the ground: 9 notes merge into one Resonant Chord.
 */
@Mod.EventBusSubscriber(modid = Dead_air.MODID)
public final class ChordNoteMergeEvents {
    private static final int NOTES_REQUIRED = 9;
    private static final double CLUSTER_RADIUS = 1.25;
    private static final int CHECK_INTERVAL_TICKS = 10;

    private ChordNoteMergeEvents() {}

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.level.isClientSide()) return;
        if (!(event.level instanceof ServerLevel level)) return;
        if (level.getGameTime() % CHECK_INTERVAL_TICKS != 0) return;

        List<ItemEntity> notes = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (var player : level.players()) {
            List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class,
                player.getBoundingBox().inflate(48.0),
                e -> !e.isRemoved()
                    && e.getItem().is(DeadAirItems.STATIC_NOTE.get())
                    && e.getItem().getCount() > 0
                    && e.onGround()
                    && !PlaceableDecorations.isDecorStack(e.getItem()));
            for (ItemEntity item : nearby) {
                if (seen.add(item.getId())) {
                    notes.add(item);
                }
            }
        }
        if (notes.isEmpty()) return;

        Set<ItemEntity> consumed = new HashSet<>();
        for (ItemEntity seed : notes) {
            if (consumed.contains(seed) || seed.isRemoved()) continue;

            List<ItemEntity> cluster = new ArrayList<>();
            int total = 0;
            Vec3 center = seed.position();
            for (ItemEntity other : notes) {
                if (consumed.contains(other) || other.isRemoved()) continue;
                if (PlaceableDecorations.isDecorStack(other.getItem())) continue;
                if (other.distanceToSqr(center) > CLUSTER_RADIUS * CLUSTER_RADIUS) continue;
                cluster.add(other);
                total += other.getItem().getCount();
            }
            if (total < NOTES_REQUIRED) continue;

            int need = NOTES_REQUIRED;
            double sumX = 0, sumY = 0, sumZ = 0;
            int weighed = 0;
            for (ItemEntity member : cluster) {
                if (need <= 0) break;
                ItemStack stack = member.getItem();
                int take = Math.min(need, stack.getCount());
                if (take <= 0) continue;
                sumX += member.getX() * take;
                sumY += member.getY() * take;
                sumZ += member.getZ() * take;
                weighed += take;
                stack.shrink(take);
                need -= take;
                if (stack.isEmpty()) {
                    member.discard();
                    consumed.add(member);
                } else {
                    member.setItem(stack);
                }
            }
            if (need > 0 || weighed <= 0) continue;

            double x = sumX / weighed;
            double y = sumY / weighed;
            double z = sumZ / weighed;
            ItemEntity chord = new ItemEntity(level, x, y + 0.1, z, new ItemStack(DeadAirItems.RESONANT_CHORD.get()));
            chord.setDefaultPickUpDelay();
            chord.setGlowingTag(true);
            level.addFreshEntity(chord);
            level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9f, 1.2f);
            level.sendParticles(ParticleTypes.END_ROD, x, y + 0.3, z, 16, 0.25, 0.2, 0.25, 0.02);
            level.sendParticles(ParticleTypes.ENCHANT, x, y + 0.2, z, 24, 0.35, 0.25, 0.35, 0.5);
        }
    }
}
