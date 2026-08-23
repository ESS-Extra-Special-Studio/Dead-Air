package uk.co.extraspecialstudio.dead_air.events;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.item.PlaceableDecorations;

/**
 * Decorations never auto-vacuum into the inventory. Punch, right-click, or swing-look to retrieve.
 */
@EventBusSubscriber(modid = Dead_air.MODID)
public final class DecorRetrieveEvents {
    private DecorRetrieveEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPickup(ItemEntityPickupEvent.Pre event) {
        if (PlaceableDecorations.isDecorStack(event.getItemEntity().getItem())) {
            event.setCanPickup(net.neoforged.neoforge.common.util.TriState.FALSE);
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getTarget() instanceof ItemEntity item)) return;
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (PlaceableDecorations.tryRetrieve(player, item)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof ItemEntity item)) return;
        if (event.getLevel().isClientSide()) return;
        if (PlaceableDecorations.tryRetrieve(event.getEntity(), item)) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (tryRaycastRetrieve(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** When the player starts a swing, pick up a looked-at decor item (covers punching in open air). */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (!player.swinging || player.swingTime != 0) return;
        tryRaycastRetrieve(player);
    }

    private static boolean tryRaycastRetrieve(Player player) {
        double reach = player.blockInteractionRange();
        Vec3 eye = player.getEyePosition(1f);
        Vec3 look = player.getViewVector(1f);
        Vec3 end = eye.add(look.scale(reach));
        AABB box = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0);

        EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
            player.level(), player, eye, end, box,
            e -> e instanceof ItemEntity ie
                && PlaceableDecorations.isDecorStack(ie.getItem())
                && !ie.isRemoved());

        if (hit == null || hit.getType() != HitResult.Type.ENTITY) return false;
        if (!(hit.getEntity() instanceof ItemEntity item)) return false;
        return PlaceableDecorations.tryRetrieve(player, item);
    }
}
