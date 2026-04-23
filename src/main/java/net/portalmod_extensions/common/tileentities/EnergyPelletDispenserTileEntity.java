package net.portalmod_extensions.common.tileentities;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.Constants;
import net.portalmod_extensions.common.blocks.EnergyPelletDispenserBlock;
import net.portalmod_extensions.common.entities.EnergyPelletEntity;
import net.portalmod_extensions.core.init.TileEntityTypeInit;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Tile entity for the Energy Pellet Dispenser.
 *
 * Stored only on the main (UP_LEFT) corner block.
 * Tracks:
 *   - whether the block is currently powered
 *   - the UUID of the live pellet (for killing it — World.getEntity(UUID) is O(1))
 *   - the BlockPos + dimension of the receiver currently holding the pellet
 *     (registered by the receiver when it catches the pellet; cleared when power
 *     is lost — getBlockEntity(pos) is O(1), no search needed)
 *
 * Redstone logic:
 *   - Rising edge  (unpowered → powered):  spawn a pellet if none exists.
 *   - Falling edge (powered → unpowered):  kill the pellet and clear the receiver.
 */
public class EnergyPelletDispenserTileEntity extends TileEntity {

    /** Whether the dispenser was powered during the last redstone check. */
    private boolean wasPowered = false;

    /**
     * UUID of the currently live pellet.  Used only for killing it via the O(1)
     * World.getEntity(UUID) lookup.  Null when no pellet is in flight.
     */
    @Nullable
    private UUID pelletUUID = null;

    /**
     * BlockPos of the receiver currently holding this dispenser's pellet.
     * Null until a receiver catches the pellet (which calls registerReceiver).
     */
    @Nullable
    private BlockPos receiverPos = null;

    /**
     * Dimension key string of the receiver's world (e.g. "minecraft:overworld").
     * Null when receiverPos is null.
     */
    @Nullable
    private String receiverDimension = null;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public EnergyPelletDispenserTileEntity() {
        super(TileEntityTypeInit.ENERGY_PELLET_DISPENSER.get());
    }

    // -------------------------------------------------------------------------
    // Receiver registration — called by EnergyPelletReceiverTileEntity
    // -------------------------------------------------------------------------

    /**
     * Called when a receiver catches this dispenser's pellet.
     * Records the receiver's location so killPelletAndClearReceivers can reach
     * it in O(1) without any search.
     */
    public void registerReceiver(BlockPos pos, String dimension) {
        this.receiverPos = pos;
        this.receiverDimension = dimension;
        // The pellet has been caught; it will remove itself, so clear our UUID.
        this.pelletUUID = null;
        setChanged();
    }

    /**
     * Called by the pellet when it removes itself for a reason the dispenser did
     * NOT initiate (age expiry or entity-kill collision).  Clears the tracked
     * UUID so the dispenser knows no pellet is in flight, then immediately
     * re-spawns one if the dispenser is still powered.
     */
    public void onPelletExpired() {
        this.pelletUUID = null;
        setChanged();
        if(wasPowered) {
            spawnPelletIfAbsent();
        }
    }

    /**
     * Called when the receiver clears itself by means other than a dispenser
     * power-off (e.g. the receiver block is broken).  Clears the stale receiver
     * pointer and, if still powered, immediately re-spawns a pellet — the same
     * behaviour as onPelletExpired for in-flight pellets.
     */
    public void unregisterReceiver() {
        this.receiverPos = null;
        this.receiverDimension = null;
        // pelletUUID is already null (cleared in registerReceiver when the
        // receiver caught the pellet), so spawnPelletIfAbsent's guard passes.
        setChanged();
        if (wasPowered) {
            spawnPelletIfAbsent();
        }
    }

    // -------------------------------------------------------------------------
    // Redstone edge-detection
    // -------------------------------------------------------------------------

    public void onPowerChanged(boolean nowPowered) {
        net.portalmod_extensions.PortalModExtensions.LOGGER.info("onPowerChanged: " + nowPowered);
        if (nowPowered && !wasPowered) {
            spawnPelletIfAbsent();
        } else if (!nowPowered && wasPowered) {
            killPelletAndClearReceivers();
        }
        wasPowered = nowPowered;
    }

    // -------------------------------------------------------------------------
    // Pellet spawning
    // -------------------------------------------------------------------------

    private void spawnPelletIfAbsent() {
        net.portalmod_extensions.PortalModExtensions.LOGGER.info("spawnPelletIfAbsent: ");
        if (this.level == null || this.level.isClientSide) return;
        if (!(this.level instanceof ServerWorld)) return;

        // Already active — pellet is in flight or caught by a receiver.
        if (pelletUUID != null || receiverPos != null) return;

        ServerWorld serverLevel = (ServerWorld) this.level;

        BlockState state = this.getBlockState();
        if (!(state.getBlock() instanceof EnergyPelletDispenserBlock)) return;

        EnergyPelletDispenserBlock block = (EnergyPelletDispenserBlock) state.getBlock();
        net.minecraft.util.Direction facing = state.getValue(EnergyPelletDispenserBlock.FACING);
        List<BlockPos> allPositions = block.getAllPositions(state, this.getBlockPos());

        double cx = allPositions.stream().mapToDouble(p -> p.getX() + 0.5).average().orElse(this.getBlockPos().getX() + 0.5);
        double cy = allPositions.stream().mapToDouble(p -> p.getY() + 0.5).average().orElse(this.getBlockPos().getY() + 0.5);
        double cz = allPositions.stream().mapToDouble(p -> p.getZ() + 0.5).average().orElse(this.getBlockPos().getZ() + 0.5);

        double ox = facing.getStepX() * 0.5;
        double oy = facing.getStepY() * 0.5;
        double oz = facing.getStepZ() * 0.5;

        double speed = 0.5;
        double vx = facing.getStepX() * speed;
        double vy = facing.getStepY() * speed;
        double vz = facing.getStepZ() * speed;

        ResourceLocation dimLocation = serverLevel.dimension().location();

        EnergyPelletEntity pellet = new EnergyPelletEntity(
            serverLevel,
            cx + ox, cy + oy, cz + oz,
            vx, vy, vz,
            this.getBlockPos(),
            dimLocation);

        serverLevel.addFreshEntity(pellet);
        pelletUUID = pellet.getUUID();
        setChanged();
    }

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    /**
     * Kills the live pellet (O(1) UUID lookup) and clears the registered
     * receiver (O(1) BlockPos lookup).  No world search of any kind.
     *
     * Called on falling redstone edge or block destruction.
     */
    public void killPelletAndClearReceivers() {
        net.portalmod_extensions.PortalModExtensions.LOGGER.info("killPelletAndClearReceivers: ");
        if (this.level == null || this.level.isClientSide) return;
        if (!(this.level instanceof ServerWorld)) return;

        ServerWorld serverLevel = (ServerWorld) this.level;

        // Kill the pellet — World.getEntity(UUID) is a hash-map lookup, O(1).
        if (pelletUUID != null) {
            Entity entity = serverLevel.getEntity(pelletUUID);
            if (entity != null) {
                entity.remove();
            }
            pelletUUID = null;
            setChanged();
        }

        // Clear the receiver — getBlockEntity(pos) is also O(1).
        if (receiverPos != null && receiverDimension != null) {
            ServerWorld receiverWorld = findWorld(serverLevel, receiverDimension);
            if (receiverWorld != null) {
                TileEntity te = receiverWorld.getBlockEntity(receiverPos);
                if (te instanceof EnergyPelletReceiverTileEntity) {
                    ((EnergyPelletReceiverTileEntity) te).clearHeldPellet();
                }
            }
            receiverPos = null;
            receiverDimension = null;
            setChanged();
        }
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    @Nullable
    private static ServerWorld findWorld(ServerWorld anyWorld, String dimensionKey) {
        for (ServerWorld w : anyWorld.getServer().getAllLevels()) {
            if (w.dimension().location().toString().equals(dimensionKey)) {
                return w;
            }
        }
        return null;
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
        if (receiverPos != null) {
            compound.putInt("ReceiverX", receiverPos.getX());
            compound.putInt("ReceiverY", receiverPos.getY());
            compound.putInt("ReceiverZ", receiverPos.getZ());
        }
        if (receiverDimension != null) {
            compound.putString("ReceiverDim", receiverDimension);
        }
        return compound;
    }

    @Override
    public void load(BlockState state, CompoundNBT compound) {
        super.load(state, compound);
        wasPowered = compound.getBoolean("WasPowered");
        if (compound.hasUUID("PelletUUID")) {
            pelletUUID = compound.getUUID("PelletUUID");
        } else {
            pelletUUID = null;
        }
        if (compound.contains("ReceiverX", Constants.NBT.TAG_INT)) {
            receiverPos = new BlockPos(
                compound.getInt("ReceiverX"),
                compound.getInt("ReceiverY"),
                compound.getInt("ReceiverZ"));
        } else {
            receiverPos = null;
        }
        if (compound.contains("ReceiverDim", Constants.NBT.TAG_STRING)) {
            receiverDimension = compound.getString("ReceiverDim");
        } else {
            receiverDimension = null;
        }
    }
}