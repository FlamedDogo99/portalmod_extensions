package net.portalmod_extensions.common.tileentities;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.Constants;
import net.portalmod_extensions.common.blocks.EnergyPelletReceiverBlock;
import net.portalmod.common.sorted.button.QuadBlockCorner;
import net.portalmod_extensions.core.init.TileEntityTypeInit;

import javax.annotation.Nullable;

/**
 * Tile entity for the Energy Pellet Receiver.
 *
 * Stored on the main (UP_LEFT) corner block.
 * Tracks:
 *   - whether the receiver is currently holding a pellet
 *   - the BlockPos + dimension of the dispenser whose pellet it is holding
 *
 * When a pellet is caught the receiver notifies the dispenser directly via a
 * BlockPos lookup — no world-wide search is needed.
 *
 * When holding, the block state HOLDING = true causes the block to emit a
 * level-15 redstone signal (handled in EnergyPelletReceiverBlock).
 */
public class EnergyPelletReceiverTileEntity extends TileEntity {

    /** BlockPos of the dispenser whose pellet we are holding.  Null when not holding. */
    @Nullable
    private BlockPos heldDispenserPos = null;

    /**
     * Registry key location string for the dispenser's dimension
     * (e.g. "minecraft:overworld").  Null when not holding.
     */
    @Nullable
    private String heldDispenserDimension = null;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public EnergyPelletReceiverTileEntity() {
        super(TileEntityTypeInit.ENERGY_PELLET_RECEIVER.get());
    }

    // -------------------------------------------------------------------------
    // State queries
    // -------------------------------------------------------------------------

    public boolean isHolding() {
        return heldDispenserPos != null;
    }

    @Nullable
    public BlockPos getHeldDispenserPos() {
        return heldDispenserPos;
    }

    @Nullable
    public String getHeldDispenserDimension() {
        return heldDispenserDimension;
    }

    // -------------------------------------------------------------------------
    // Catch / clear
    // -------------------------------------------------------------------------

    /**
     * Called by EnergyPelletEntity when it collides with this receiver.
     *
     * Records the dispenser's position and dimension, transitions to holding,
     * and notifies the dispenser so it knows where to find us when power is lost.
     *
     * @param dispenserPos       BlockPos of the dispenser that spawned the pellet
     * @param dispenserDimension dimension key string of that dispenser's world
     */
    public void catchPellet(@Nullable BlockPos dispenserPos, @Nullable String dispenserDimension) {
        if (this.level == null || this.level.isClientSide) return;

        this.heldDispenserPos = dispenserPos;
        this.heldDispenserDimension = dispenserDimension;
        setHoldingState(true);
        setChanged();

        // Notify the dispenser that this receiver is now holding its pellet.
        // The dispenser stores our position so it can clear us in O(1) when
        // power is lost — no search needed.
        if (dispenserPos != null && dispenserDimension != null) {
            notifyDispenser(dispenserPos, dispenserDimension, true);
        }
    }

    /**
     * Called by EnergyPelletReceiverBlock.onRemove when this receiver block is
     * broken while holding a pellet.  Notifies the dispenser so it clears its
     * stale receiver pointer.
     */
    public void notifyDispenserOfRemoval() {
        if (heldDispenserPos != null && heldDispenserDimension != null) {
            notifyDispenser(heldDispenserPos, heldDispenserDimension, false);
        }
    }

    /**
     * Called by EnergyPelletDispenserTileEntity when power is lost.
     * Reverts the receiver to not-holding and removes the redstone signal.
     */
    public void clearHeldPellet() {
        if (this.level == null || this.level.isClientSide) return;
        this.heldDispenserPos = null;
        this.heldDispenserDimension = null;
        setHoldingState(false);
        setChanged();
    }

    // -------------------------------------------------------------------------
    // Dispenser notification
    // -------------------------------------------------------------------------

    /**
     * Looks up the dispenser tile entity in the appropriate world and either
     * registers or unregisters this receiver's position on it.
     *
     * Cross-dimension lookup is handled by iterating the server's loaded worlds;
     * if the dispenser's world is not loaded the call is a no-op (the dispenser
     * will discover the receiver's absence when it is next loaded).
     */
    private void notifyDispenser(BlockPos dispenserPos, String dispenserDimension, boolean registering) {
        if (!(this.level instanceof ServerWorld)) return;
        ServerWorld serverWorld = (ServerWorld) this.level;

        // Find the world that owns the dispenser.
        ServerWorld dispenserWorld = findWorld(serverWorld, dispenserDimension);
        if (dispenserWorld == null) return;

        TileEntity te = dispenserWorld.getBlockEntity(dispenserPos);
        if (!(te instanceof EnergyPelletDispenserTileEntity)) return;
        EnergyPelletDispenserTileEntity dispenser = (EnergyPelletDispenserTileEntity) te;

        if (registering) {
            dispenser.registerReceiver(this.getBlockPos(), this.level.dimension().location().toString());
        } else {
            dispenser.unregisterReceiver();
        }
    }

    /**
     * Finds the ServerWorld whose dimension key matches the given string.
     * Returns null if not currently loaded.
     */
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
    // Block-state sync
    // -------------------------------------------------------------------------

    private void setHoldingState(boolean holding) {
        if (this.level == null) return;
        World world = this.level;

        BlockState mainState = world.getBlockState(this.getBlockPos());
        if (!(mainState.getBlock() instanceof EnergyPelletReceiverBlock)) return;

        EnergyPelletReceiverBlock block = (EnergyPelletReceiverBlock) mainState.getBlock();
        block.setBlockStateValue(EnergyPelletReceiverBlock.HOLDING, holding, mainState, world, this.getBlockPos());
        block.updateAllNeighbors(world, this.getBlockPos(), mainState);
    }

    // -------------------------------------------------------------------------
    // NBT persistence
    // -------------------------------------------------------------------------

    @Override
    public CompoundNBT save(CompoundNBT compound) {
        super.save(compound);
        if (heldDispenserPos != null) {
            compound.putInt("HeldDispenserX", heldDispenserPos.getX());
            compound.putInt("HeldDispenserY", heldDispenserPos.getY());
            compound.putInt("HeldDispenserZ", heldDispenserPos.getZ());
        }
        if (heldDispenserDimension != null) {
            compound.putString("HeldDispenserDim", heldDispenserDimension);
        }
        return compound;
    }

    @Override
    public void load(BlockState state, CompoundNBT compound) {
        super.load(state, compound);
        if (compound.contains("HeldDispenserX", Constants.NBT.TAG_INT)) {
            this.heldDispenserPos = new BlockPos(
                    compound.getInt("HeldDispenserX"),
                    compound.getInt("HeldDispenserY"),
                    compound.getInt("HeldDispenserZ"));
        } else {
            this.heldDispenserPos = null;
        }
        if (compound.contains("HeldDispenserDim", Constants.NBT.TAG_STRING)) {
            this.heldDispenserDimension = compound.getString("HeldDispenserDim");
        } else {
            this.heldDispenserDimension = null;
        }
    }
}
