package uk.co.extraspecialstudio.dead_air.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.co.extraspecialstudio.dead_air.radio.RadioManager;

/**
 * Mixin to detect when players use radios.
 */
@Mixin(Player.class)
@SuppressWarnings("null")
public class PlayerMixin {
    
    @Inject(method = "tick", at = @At("TAIL"))
    private void onPlayerTick(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        
        // Check if player is holding radio
        if (RadioManager.isHoldingRadio(player)) {
            // Player is holding radio - state is managed by RadioManager
            // This mixin allows us to hook into player tick for additional processing
        }
    }
    
    // Removed getMainHandItem mixin - not needed, detection happens in RadioManager
}
