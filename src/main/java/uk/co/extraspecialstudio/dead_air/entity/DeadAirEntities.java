package uk.co.extraspecialstudio.dead_air.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import uk.co.extraspecialstudio.dead_air.Dead_air;

@Mod.EventBusSubscriber(modid = Dead_air.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DeadAirEntities {
    public static final DeferredRegister<EntityType<?>> REGISTER =
        DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Dead_air.MODID);

    public static final RegistryObject<EntityType<ListenerEntity>> THE_LISTENER = REGISTER.register(
        "the_listener",
        () -> EntityType.Builder.of(ListenerEntity::new, MobCategory.MONSTER)
            .sized(1.4f, 5.7f)
            .clientTrackingRange(10)
            .fireImmune()
            .build("the_listener"));

    public static final RegistryObject<EntityType<SignalAnchorEntity>> SIGNAL_ANCHOR = REGISTER.register(
        "signal_anchor",
        () -> EntityType.Builder.of(SignalAnchorEntity::new, MobCategory.MISC)
            .sized(0.9f, 2.0f)
            .clientTrackingRange(10)
            .fireImmune()
            .build("signal_anchor"));

    public static final RegistryObject<EntityType<uk.co.extraspecialstudio.dead_air.survivor.SurvivorEntity>> SURVIVOR = REGISTER.register(
        "survivor",
        () -> EntityType.Builder.of(uk.co.extraspecialstudio.dead_air.survivor.SurvivorEntity::new, MobCategory.CREATURE)
            .sized(0.6f, 1.8f)
            .clientTrackingRange(10)
            .build("survivor"));

    public static final RegistryObject<EntityType<uk.co.extraspecialstudio.dead_air.survivor.AuctionBrokerEntity>> AUCTION_BROKER = REGISTER.register(
        "auction_broker",
        () -> EntityType.Builder.of(uk.co.extraspecialstudio.dead_air.survivor.AuctionBrokerEntity::new, MobCategory.CREATURE)
            .sized(0.6f, 1.95f)
            .clientTrackingRange(10)
            .build("auction_broker"));

    public static final RegistryObject<EntityType<SeatEntity>> SEAT = REGISTER.register(
        "seat",
        () -> EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC)
            .sized(0.4f, 0.4f)
            .clientTrackingRange(8)
            .updateInterval(3)
            .noSummon()
            .fireImmune()
            .build("seat"));

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(THE_LISTENER.get(), ListenerEntity.createAttributes().build());
        event.put(SIGNAL_ANCHOR.get(), SignalAnchorEntity.createAttributes().build());
        event.put(SURVIVOR.get(), uk.co.extraspecialstudio.dead_air.survivor.SurvivorEntity.createAttributes().build());
        event.put(AUCTION_BROKER.get(), uk.co.extraspecialstudio.dead_air.survivor.AuctionBrokerEntity.createAttributes().build());
    }

    private DeadAirEntities() {}
}
