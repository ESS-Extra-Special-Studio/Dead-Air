package uk.co.extraspecialstudio.dead_air.survivor;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import uk.co.extraspecialstudio.dead_air.Config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Applies HUD / packet squad orders to all FOLLOWING survivors for a player. */
public final class SurvivorPartyOrders {
    private static final Map<UUID, Long> LAST_ORDER_TICK = new HashMap<>();
    /**
     * Anti-spam only. Keep below the client double-tap window (~280ms / ~6 ticks)
     * so a deliberate quick repeat is not silently dropped.
     */
    private static final int ORDER_COOLDOWN_TICKS = 1;

    private SurvivorPartyOrders() {}

    public static void clearPlayer(UUID id) {
        LAST_ORDER_TICK.remove(id);
    }

    public static void apply(ServerPlayer player, int order) {
        if (!Config.survivorsEnabled) {
            player.sendSystemMessage(Component.translatable("message.dead_air.survivor.disabled")
                .withStyle(ChatFormatting.RED));
            return;
        }
        long nowTick = player.level().getGameTime();
        Long prev = LAST_ORDER_TICK.get(player.getUUID());
        if (prev != null && nowTick - prev < ORDER_COOLDOWN_TICKS) {
            player.displayClientMessage(
                Component.translatable("message.dead_air.survivor.order.cooldown")
                    .withStyle(ChatFormatting.GRAY), true);
            // Correct optimistic client HUD letters after a reject.
            SurvivorPartyEvents.syncPlayerNow(player);
            return;
        }

        List<SurvivorEntity> party = SurvivorParty.listFollowing(player.server, player.getUUID());
        int rosterCount = SurvivorParty.countFollowing(player.server, player.getUUID());
        if (party.isEmpty()) {
            String emptyKey = rosterCount > 0
                ? "message.dead_air.survivor.party_unloaded"
                : "message.dead_air.survivor.party_empty";
            player.sendSystemMessage(Component.translatable(emptyKey).withStyle(ChatFormatting.GRAY));
            SurvivorPartyEvents.syncPlayerNow(player);
            return;
        }

        LivingEntity attackFocus = resolveAttackFocus(player);
        long now = player.level().getGameTime();
        int regrouped = 0;
        int eligible = 0;
        int downed = 0;

        for (SurvivorEntity s : party) {
            if (s.isDowned()) {
                downed++;
                continue;
            }
            eligible++;
            switch (order) {
                case SurvivorParty.ORDER_FOLLOW -> {
                    s.setPartyOrder(SurvivorParty.ORDER_FOLLOW);
                    s.setDuty(SurvivorDuty.NONE);
                    s.clearDefendHold();
                    s.setTarget(null);
                    s.clearRetreat();
                }
                case SurvivorParty.ORDER_DEFEND -> {
                    s.setPartyOrder(SurvivorParty.ORDER_DEFEND);
                    BlockPos at = player.blockPosition().immutable();
                    s.applyPartyDefend(at);
                    s.setTarget(null);
                    s.clearRetreat();
                }
                case SurvivorParty.ORDER_ATTACK -> {
                    s.setPartyOrder(SurvivorParty.ORDER_ATTACK);
                    s.setDuty(SurvivorDuty.NONE);
                    s.clearDefendHold();
                    s.clearRetreat();
                    if (attackFocus != null && attackFocus.isAlive()) {
                        s.setTarget(attackFocus);
                    }
                }
                case SurvivorParty.ORDER_RETREAT -> {
                    s.setPartyOrder(SurvivorParty.ORDER_RETREAT);
                    s.setDuty(SurvivorDuty.NONE);
                    s.clearDefendHold();
                    s.setTarget(null);
                    s.beginRetreat(80);
                }
                case SurvivorParty.ORDER_REGROUP -> {
                    if (!s.tryRegroup(now)) continue;
                    s.setPartyOrder(SurvivorParty.ORDER_FOLLOW);
                    s.setDuty(SurvivorDuty.NONE);
                    s.clearDefendHold();
                    s.setTarget(null);
                    s.clearRetreat();
                    int slot = SurvivorParty.formationIndex(player.server, s);
                    s.softTeleportNear(formationPoint(player, slot));
                    regrouped++;
                }
                default -> {}
            }
        }

        if (eligible == 0) {
            String key = downed > 0
                ? "message.dead_air.survivor.party_all_downed"
                : "message.dead_air.survivor.party_empty";
            player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY));
            SurvivorPartyEvents.syncPlayerNow(player);
            return;
        }
        if (order == SurvivorParty.ORDER_REGROUP && regrouped == 0) {
            player.sendSystemMessage(Component.translatable("message.dead_air.survivor.order.regroup_cooldown")
                .withStyle(ChatFormatting.GRAY));
            SurvivorPartyEvents.syncPlayerNow(player);
            return;
        }

        LAST_ORDER_TICK.put(player.getUUID(), nowTick);
        // Success feedback is the client action-bar from the wheel; avoid duplicate chat.
        // Push an immediate HUD sync so order letters don't lag up to 1s.
        SurvivorPartyEvents.syncPlayerNow(player);
    }

    private static LivingEntity resolveAttackFocus(ServerPlayer player) {
        LivingEntity last = player.getLastHurtMob();
        if (isValidAttackFocus(player, last)) return last;
        last = player.getLastHurtByMob();
        if (isValidAttackFocus(player, last)) return last;
        if (player.getVehicle() instanceof Mob mob) {
            LivingEntity vt = mob.getTarget();
            if (isValidAttackFocus(player, vt)) return vt;
        }
        return null;
    }

    private static boolean isValidAttackFocus(ServerPlayer owner, LivingEntity target) {
        if (target == null || !target.isAlive() || target == owner) return false;
        if (target instanceof Player) return false;
        if (target instanceof SurvivorEntity ally && ally.isOwnedBy(owner)) return false;
        // Monster covers The Listener; Enemy covers other hostiles that aren't Monster subclasses.
        return target instanceof Monster || target instanceof Enemy;
    }

    public static Vec3 formationPoint(ServerPlayer player, int slot) {
        double spacing = Config.partyFormationSpacing;
        int side = (slot % 2 == 0) ? -1 : 1;
        int ring = slot / 2 + 1;
        Vec3 look = player.getLookAngle();
        Vec3 back = new Vec3(-look.x, 0, -look.z);
        if (back.lengthSqr() < 1.0E-4) back = new Vec3(0, 0, 1);
        back = back.normalize();
        Vec3 right = new Vec3(-back.z, 0, back.x).normalize();
        Vec3 offset = back.scale(1.6 + ring * 0.4).add(right.scale(side * spacing * ring * 0.65));
        return player.position().add(offset);
    }
}
