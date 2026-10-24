package uk.co.extraspecialstudio.dead_air.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import uk.co.extraspecialstudio.dead_air.block.DeadAirBlocks;
import uk.co.extraspecialstudio.dead_air.block.EbDecorBlock;
import uk.co.extraspecialstudio.dead_air.block.FoldingChairBlock;

import javax.annotation.Nullable;

/** Invisible mount used so players / survivors / villagers can sit on folding chairs. */
public class SeatEntity extends Entity {
    /**
     * Mount Y above the block. After ~1.18× enlarge, cushion top is ~8.08/16 (0.51).
     * Sit pose parks hips ~0.55 above the entity origin, so mount below the cushion.
     */
    public static final double SEAT_Y = -0.05;

    private BlockPos sourcePos = BlockPos.ZERO;

    public SeatEntity(EntityType<? extends SeatEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setInvulnerable(true);
    }

    public SeatEntity(Level level, BlockPos pos, Direction facing) {
        this(DeadAirEntities.SEAT.get(), level);
        this.sourcePos = pos.immutable();
        setPos(pos.getX() + 0.5, pos.getY() + SEAT_Y, pos.getZ() + 0.5);
        setYRot(facing.toYRot());
        setXRot(0);
    }

    /** Try to seat {@code rider} on the chair at {@code pos}. Returns false if occupied/invalid. */
    public static boolean trySit(Level level, BlockPos pos, BlockState state, LivingEntity rider) {
        if (level.isClientSide || rider == null || !rider.isAlive()) return false;
        if (!(state.getBlock() instanceof FoldingChairBlock)) return false;
        if (rider.isPassenger()) return false;
        // Players who are already a vehicle (rare) — skip. Mobs never host passengers here.
        if (rider instanceof Player player && player.isVehicle()) return false;
        if (isOccupied(level, pos)) return false;

        Direction facing = state.getValue(EbDecorBlock.FACING);
        SeatEntity seat = new SeatEntity(level, pos, facing);
        if (!level.addFreshEntity(seat)) return false;
        // Force=true so PathfinderMobs (survivors / villagers) actually mount.
        boolean mounted = rider.startRiding(seat, true);
        if (!mounted) {
            seat.discard();
            return false;
        }
        return true;
    }

    public static boolean isOccupied(Level level, BlockPos pos) {
        AABB box = new AABB(pos).inflate(0.15);
        // Any live seat at this chair counts as occupied — empty seats discard next tick,
        // but treating them as free caused double-mount races in the same tick.
        return !level.getEntitiesOfClass(SeatEntity.class, box,
            s -> pos.equals(s.sourcePos) && s.isAlive()).isEmpty();
    }

    /** Eject riders and discard every seat bound to this chair block. */
    public static void ejectAt(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        AABB box = new AABB(pos).inflate(0.25);
        for (SeatEntity seat : level.getEntitiesOfClass(SeatEntity.class, box,
            s -> pos.equals(s.sourcePos) && s.isAlive())) {
            seat.ejectPassengers();
            seat.discard();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        BlockState state = level().getBlockState(sourcePos);
        if (!state.is(DeadAirBlocks.FOLDING_CHAIR.get())) {
            ejectPassengers();
            discard();
            return;
        }
        if (getPassengers().isEmpty()) {
            discard();
            return;
        }
        // Keep yaw locked to the chair facing so sitters look forward.
        Direction facing = state.getValue(EbDecorBlock.FACING);
        setYRot(facing.toYRot());
        setPos(sourcePos.getX() + 0.5, sourcePos.getY() + SEAT_Y, sourcePos.getZ() + 0.5);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        sourcePos = new BlockPos(tag.getInt("Sx"), tag.getInt("Sy"), tag.getInt("Sz"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Sx", sourcePos.getX());
        tag.putInt("Sy", sourcePos.getY());
        tag.putInt("Sz", sourcePos.getZ());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, serverEntity);
    }

    @Override
    public boolean shouldRiderSit() {
        return true;
    }

    @Override
    protected boolean canRide(Entity entity) {
        return entity instanceof LivingEntity;
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        callback.accept(passenger, getX(), getY(), getZ());
        passenger.setYRot(getYRot());
        passenger.setYHeadRot(getYRot());
        if (passenger instanceof LivingEntity living) {
            living.yBodyRot = getYRot();
        }
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty() && passenger instanceof LivingEntity;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Direction facing = getDirection();
        BlockState state = level().getBlockState(sourcePos);
        if (state.hasProperty(EbDecorBlock.FACING)) {
            facing = state.getValue(EbDecorBlock.FACING);
        }
        // Step off to the side of the chair.
        Direction side = facing.getClockWise();
        BlockPos out = sourcePos.relative(side);
        if (!level().getBlockState(out).isAir()) {
            out = sourcePos.relative(side.getOpposite());
        }
        return new Vec3(out.getX() + 0.5, sourcePos.getY(), out.getZ() + 0.5);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Nullable
    public BlockPos getSourcePos() {
        return sourcePos;
    }
}
