package net.portalmod_extensions.common.tileentities;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.portalmod_extensions.common.blocks.EnergyPelletDispenserBlock;
import net.portalmod_extensions.common.blocks.QuadBlockCorner;
import net.portalmod_extensions.common.entities.EnergyPelletEntity;
import net.portalmod_extensions.core.init.TileEntityTypeInit;

import java.util.List;
import java.util.UUID;

/**
 * Tile entity for the Energy Pellet Dispenser.
 *
 * Stored only on the main (UP_LEFT) corner block.
 * Tracks:
 *   - whether the block is currently powered
 *   - the UUID of the EnergyPelletEntity it has spawned (null if none)
 *
 * Redstone logic:
 *   - Rising edge  (unpowered → powered):  spawn a pellet if none exists.
 *   - Falling edge (powered → unpowered):  kill the pellet and clear any receiver.
 */
public class EnergyPelletDispenserTileEntity extends TileEntity {

    /** UUID of the currently live pellet spawned by this dispenser.  Null when none. */
    private UUID pelletUUID = null;

    /** Whether the dispenser was powered during the last redstone check. */
    private boolean wasPowered = false;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public EnergyPelletDispenserTileEntity() {
        super(TileEntityTypeInit.ENERGY_PELLET_DISPENSER.get());
    }

    // -------------------------------------------------------------------------
    // Unique identifier (tile entities share a UUID with their BlockPos in NBT;
    // we give the dispenser its own UUID so pellets and receivers can reference it).
    // -------------------------------------------------------------------------

    /** Stable UUID derived from this tile entity's block position. */
    public UUID getDispenserUUID() {
        // A stable, deterministic UUID based on the position string.
        BlockPos pos = this.getBlockPos();
        String key = "dispenser_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
        return UUID.nameUUIDFromBytes(key.getBytes());
    }

    // -------------------------------------------------------------------------
    // Redstone edge-detection
    // -------------------------------------------------------------------------

    /**
     * Called from EnergyPelletDispenserBlock#neighborChanged with the current
     * power state.  Detects rising and falling edges.
     */
    public void onRedstoneChanged(boolean nowPowered) {
        if (nowPowered && !wasPowered) {
            // Rising edge: spawn pellet if none exists.
            spawnPelletIfAbsent();
        } else if (!nowPowered && wasPowered) {
            // Falling edge: kill pellet and clear receivers.
            killPelletAndClearReceivers();
        }
        wasPowered = nowPowered;
    }

    // -------------------------------------------------------------------------
    // Pellet spawning
    // -------------------------------------------------------------------------

    private void spawnPelletIfAbsent() {
        if (this.level == null || this.level.isClientSide) return;
        if (!(this.level instanceof ServerWorld)) return;

        ServerWorld serverLevel = (ServerWorld) this.level;

        // If a pellet with our UUID is already alive, do nothing.
        if (pelletUUID != null && serverLevel.getEntity(pelletUUID) instanceof EnergyPelletEntity) {
            return;
        }

        BlockState state = this.getBlockState();
        if (!(state.getBlock() instanceof EnergyPelletDispenserBlock)) return;

        Direction facing = state.getValue(EnergyPelletDispenserBlock.FACING);

        // Spawn the pellet centred above (in the facing direction) the 2×2 footprint.
        // The dispenser's main corner is UP_LEFT.  We need the centre of the 2×2 face.
        EnergyPelletDispenserBlock block = (EnergyPelletDispenserBlock) state.getBlock();
        List<BlockPos> allPositions = block.getAllPositions(state, this.getBlockPos());

        // Average position of the four blocks gives us the centre of the 2×2.
        double cx = allPositions.stream().mapToDouble(p -> p.getX() + 0.5).average().orElse(this.getBlockPos().getX() + 0.5);
        double cy = allPositions.stream().mapToDouble(p -> p.getY() + 0.5).average().orElse(this.getBlockPos().getY() + 0.5);
        double cz = allPositions.stream().mapToDouble(p -> p.getZ() + 0.5).average().orElse(this.getBlockPos().getZ() + 0.5);

        // Offset by 0.5 in the facing direction so the pellet spawns just outside the face.
        double ox = facing.getStepX() * 0.5;
        double oy = facing.getStepY() * 0.5;
        double oz = facing.getStepZ() * 0.5;

        // Velocity: 1 block/tick in the facing direction (a reasonable pellet speed).
        double speed = 0.5;
        double vx = facing.getStepX() * speed;
        double vy = facing.getStepY() * speed;
        double vz = facing.getStepZ() * speed;

        EnergyPelletEntity pellet = new EnergyPelletEntity(
                serverLevel,
                cx + ox, cy + oy, cz + oz,
                vx, vy, vz,
                getDispenserUUID());

        serverLevel.addFreshEntity(pellet);
        pelletUUID = pellet.getUUID();
        setChanged();
    }

    // -------------------------------------------------------------------------
    // Pellet / receiver cleanup
    // -------------------------------------------------------------------------

    /**
     * Kills the associated pellet (if still alive) and clears any receiver
     * that is holding a pellet associated with this dispenser.
     * Called on falling redstone edge or block destruction.
     */
    public void killPelletAndClearReceivers() {
        if (this.level == null || this.level.isClientSide) return;
        if (!(this.level instanceof ServerWorld)) return;

        ServerWorld serverLevel = (ServerWorld) this.level;
        UUID myUUID = getDispenserUUID();

        // Kill the pellet.
        if (pelletUUID != null) {
            net.minecraft.entity.Entity entity = serverLevel.getEntity(pelletUUID);
            if (entity instanceof EnergyPelletEntity) {
                entity.remove();
            }
            pelletUUID = null;
            setChanged();
        }

        // Clear any receiver that is holding a pellet from this dispenser.
        // We search in a reasonable radius around the dispenser.
        // A full level scan would be too expensive; 256 blocks is generous for puzzle rooms.
        BlockPos center = this.getBlockPos();
        int searchRadius = 256;

        for (BlockPos p : BlockPos.betweenClosed(
                center.offset(-searchRadius, -searchRadius, -searchRadius),
                center.offset( searchRadius,  searchRadius,  searchRadius))) {

            TileEntity te = serverLevel.getBlockEntity(p);
            if (te instanceof EnergyPelletReceiverTileEntity) {
                EnergyPelletReceiverTileEntity receiver = (EnergyPelletReceiverTileEntity) te;
                if (myUUID.equals(receiver.getHeldDispenserUUID())) {
                    receiver.clearHeldPellet();
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Accessor
    // -------------------------------------------------------------------------

    public UUID getPelletUUID() {
        return pelletUUID;
    }

    // -------------------------------------------------------------------------
    // NBT persistence
    // -------------------------------------------------------------------------

    @Override
    public CompoundNBT save(CompoundNBT compound) {
        super.save(compound);
        compound.putBoolean("WasPowered", wasPowered);
        if (pelletUUID != null) {
            compound.putUUID("PelletUUID", pelletUUID);
        }
        return compound;
    }

    @Override
    public void load(BlockState state, CompoundNBT compound) {
        super.load(state, compound);
        wasPowered = compound.getBoolean("WasPowered");
        if (compound.hasUUID("PelletUUID")) {
            pelletUUID = compound.getUUID("PelletUUID");
        }
    }
}
