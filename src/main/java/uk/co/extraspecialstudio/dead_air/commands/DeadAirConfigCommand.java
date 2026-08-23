package uk.co.extraspecialstudio.dead_air.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;

@EventBusSubscriber(modid = Dead_air.MODID)
public final class DeadAirConfigCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
            Commands.literal("dead_air")
                .then(Commands.literal("config")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.literal("dump").executes(ctx -> dump(ctx.getSource())))
                    .then(Commands.literal("applytest").executes(ctx -> applyTest(ctx.getSource())))
                    .then(Commands.literal("restoredefaults").executes(ctx -> restore(ctx.getSource())))
                    .then(Commands.literal("checklist").executes(ctx -> checklist(ctx.getSource()))))
        );
    }

    private static void line(CommandSourceStack src, String text) {
        src.sendSuccess(() -> Component.literal(text), false);
    }

    private static int dump(CommandSourceStack src) {
        line(src, "=== Dead Air live config ===");
        line(src, "emergencyBroadcastRange=" + Config.emergencyBroadcastRange);
        line(src, "musicStationRange=" + Config.musicStationRange);
        line(src, "minTowerSpacing=" + Config.minTowerSpacing);
        line(src, "enableLineOfSight=" + Config.enableLineOfSight);
        line(src, "enableWeatherEffects=" + Config.enableWeatherEffects);
        line(src, "maxVolume=" + Config.maxVolume + " minVolume=" + Config.minVolume);
        line(src, "radioAlwaysOn=" + Config.radioAlwaysOn);
        line(src, "musicPlaysWithoutTower=" + Config.musicPlaysWithoutTower);
        line(src, "autoDiscoverModMusic=" + Config.autoDiscoverModMusic);
        line(src, "overlayCorner=" + Config.overlayCorner
            + " offset=" + Config.overlayOffsetX + "," + Config.overlayOffsetY);
        line(src, "customMusicPath=" + (Config.customMusicPath.isEmpty() ? "(empty)" : Config.customMusicPath));
        return 1;
    }

    private static int applyTest(CommandSourceStack src) {
        Config.applyTestProfile();
        line(src, "[Dead Air] Test profile written to dead_air-common.toml.");
        return checklist(src);
    }

    private static int restore(CommandSourceStack src) {
        Config.restoreDefaults();
        line(src, "[Dead Air] Defaults restored to dead_air-common.toml.");
        return dump(src);
    }

    private static int checklist(CommandSourceStack src) {
        line(src, "=== Dead Air checks (current values) ===");
        line(src, "1. HUD is " + Config.overlayCorner + " inset "
            + Config.overlayOffsetX + "," + Config.overlayOffsetY + " — look at walkie overlay.");
        line(src, "2. radioAlwaysOn=" + Config.radioAlwaysOn
            + " — " + (Config.radioAlwaysOn ? "music should play from inventory." : "music ONLY while walkie is held."));
        line(src, "3. musicStationRange=" + Config.musicStationRange
            + " — walk away; bars should drop by ~75-block steps. At 75, you lose signal almost immediately.");
        line(src, "4. emergencyBroadcastRange=" + Config.emergencyBroadcastRange
            + " — Emergency station dies much sooner if this is 250.");
        line(src, "5. musicPlaysWithoutTower=" + Config.musicPlaysWithoutTower
            + " — " + (Config.musicPlaysWithoutTower ? "can keep playing with no powered tower." : "must have a powered tower in range."));
        line(src, "6. Volume max=" + Config.maxVolume + " min=" + Config.minVolume
            + " — 5/5 loud, 1/5 very quiet (0 min = almost silent).");
        line(src, "7. Weather/LOS " + Config.enableWeatherEffects + "/" + Config.enableLineOfSight
            + " — rain or a wall should weaken bars when true.");
        return 1;
    }

    private DeadAirConfigCommand() {}
}
