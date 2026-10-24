package uk.co.extraspecialstudio.dead_air.survivor;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.api.DeadAirAPI;

import javax.annotation.Nullable;

/**
 * Party role combat / loot hooks: radio music damage buff, scavenger kill loot, military damage,
 * and survivor XP awards on kills / assists.
 */
@Mod.EventBusSubscriber(modid = Dead_air.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SurvivorPartyRoleEvents {
    private SurvivorPartyRoleEvents() {}

    private static final float RADIO_MUSIC_DAMAGE = 1.10f;
    /** Flat damage mult — no Strength potion (avoids stacking). */
    private static final float MILITARY_DAMAGE = 1.12f;
    private static final float SCAVENGER_LOOT_CHANCE = 0.35f;
    private static final double ASSIST_RANGE = 16.0;

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        var source = event.getSource().getEntity();
        if (source instanceof ServerPlayer player) {
            // Require a listening radio (on+tuned), not merely a MUSIC station id in state.
            if (hasFollowingProfession(player, SurvivorEntity.Profession.RADIO_OPERATOR)
                && uk.co.extraspecialstudio.dead_air.radio.RadioManager.isListeningToRadio(player)
                && DeadAirAPI.isPlayerPlayingMusic(player)) {
                event.setAmount(event.getAmount() * RADIO_MUSIC_DAMAGE);
            }
            return;
        }
        if (source instanceof SurvivorEntity survivor
            && survivor.getProfession() == SurvivorEntity.Profession.MILITARY
            && (survivor.getSurvivorState() == SurvivorEntity.FOLLOWING
                || survivor.getDuty() == SurvivorDuty.DEFEND)) {
            float skillBonus = 1f + 0.02f * Math.max(0, survivor.getIdentity().skillCombat);
            event.setAmount(event.getAmount() * MILITARY_DAMAGE * skillBonus);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        LivingEntity victim = event.getEntity();
        if (victim instanceof SurvivorEntity) return; // no XP farming allies
        var killerEntity = event.getSource().getEntity();
        if (killerEntity instanceof SurvivorEntity killer
            && killer.getOwnerUuid() != null
            && !killer.isDowned()
            && (killer.getSurvivorState() == SurvivorEntity.FOLLOWING
                || killer.getSurvivorState() == SurvivorEntity.SETTLED)) {
            SurvivorProgress.awardXp(killer, 10, SurvivorProgress.SkillBias.COMBAT);
            return;
        }
        if (!(killerEntity instanceof ServerPlayer player)) return;
        double bestDist = ASSIST_RANGE * ASSIST_RANGE;
        for (SurvivorEntity s : SurvivorParty.listFollowing(player.server, player.getUUID())) {
            if (s.isDowned() || s.level() != victim.level()) continue;
            double d = s.distanceToSqr(victim);
            if (d <= bestDist) {
                SurvivorProgress.awardXp(s, 4, SurvivorProgress.SkillBias.COMBAT);
            }
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (event.getEntity().level().isClientSide) return;
        SurvivorEntity scav = resolveScavengerForLoot(event);
        if (scav == null) return;
        float chance = SCAVENGER_LOOT_CHANCE + 0.03f * Math.max(0, scav.getIdentity().skillScavenge);
        if (scav.getRandom().nextFloat() > Math.min(0.55f, chance)) return;
        // Extra roll: duplicate one random drop stack (slight loot bump).
        var drops = event.getDrops();
        if (drops.isEmpty()) return;
        ItemEntity pick = null;
        int i = 0;
        int want = scav.getRandom().nextInt(drops.size());
        for (ItemEntity d : drops) {
            if (i++ == want) {
                pick = d;
                break;
            }
        }
        if (pick == null) return;
        ItemStack extra = pick.getItem().copy();
        if (extra.isEmpty()) return;
        if (extra.getCount() > 1) extra.setCount(1 + scav.getRandom().nextInt(Math.min(2, extra.getCount())));
        ItemEntity spawned = new ItemEntity(event.getEntity().level(),
            event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(), extra);
        drops.add(spawned);
    }

    /**
     * Bonus when a scavenger lands the kill, or a following scavenger is nearby when the owner does.
     */
    @Nullable
    private static SurvivorEntity resolveScavengerForLoot(LivingDropsEvent event) {
        var killerEntity = event.getSource().getEntity();
        if (killerEntity instanceof SurvivorEntity scav
            && scav.getProfession() == SurvivorEntity.Profession.SCAVENGER
            && scav.getOwnerUuid() != null
            && !scav.isDowned()) {
            int state = scav.getSurvivorState();
            if (state == SurvivorEntity.FOLLOWING || state == SurvivorEntity.SETTLED) {
                return scav;
            }
        }
        if (!(killerEntity instanceof ServerPlayer player)) return null;
        SurvivorEntity best = null;
        double bestDist = ASSIST_RANGE * ASSIST_RANGE;
        for (SurvivorEntity s : SurvivorParty.listFollowing(player.server, player.getUUID())) {
            if (s.getProfession() != SurvivorEntity.Profession.SCAVENGER || s.isDowned()) continue;
            if (s.level() != event.getEntity().level()) continue;
            double d = s.distanceToSqr(event.getEntity());
            if (d <= bestDist) {
                bestDist = d;
                best = s;
            }
        }
        return best;
    }

    private static boolean hasFollowingProfession(ServerPlayer player, SurvivorEntity.Profession profession) {
        for (SurvivorEntity s : SurvivorParty.listFollowing(player.server, player.getUUID())) {
            if (s.getProfession() == profession && !s.isDowned()) return true;
        }
        return false;
    }
}
