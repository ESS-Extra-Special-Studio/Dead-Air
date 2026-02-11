package uk.creatopia.unbound.dead_air.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.creatopia.unbound.dead_air.walkie.WalkieTalkieManager;

/**
 * Mixin to detect when players use walkie-talkies.
 */
@Mixin(Player.class)
@SuppressWarnings("null")
public class PlayerMixin {
    
    @Inject(method = "tick", at = @At("TAIL"))
    private void onPlayerTick(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        
        // Check if player is holding walkie-talkie
        if (WalkieTalkieManager.isHoldingWalkieTalkie(player)) {
            // Player is holding walkie-talkie - state is managed by WalkieTalkieManager
            // This mixin allows us to hook into player tick for additional processing
        }
    }
    
    // Removed getMainHandItem mixin - not needed, detection happens in WalkieTalkieManager
}
