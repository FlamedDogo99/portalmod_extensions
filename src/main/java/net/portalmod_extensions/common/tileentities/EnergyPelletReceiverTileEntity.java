package net.portalmod_extensions.common.tileentities;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.portalmod_extensions.common.blocks.EnergyPelletReceiverBlock;
import net.portalmod.common.sorted.button.QuadBlockCorner;
import net.portalmod_extensions.core.init.TileEntityTypeInit;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Tile entity for the Energy Pellet Receiver.
 *
 * Stored on the main (UP_LEFT) corner block.
 * Tracks:
 *   - whether the receiver is currently holding a pellet
 *   - the UUID of the dispenser whose pellet it is holding
 *
 * When holding, the block state HOLDING = true causes the block to emit a
 * level-15 redstone signal (handled in EnergyPelletReceiverBlock).
 */
public class EnergyPelletReceiverTileEntity extends TileEntity {

    /** UUID of the dispenser that owns the currently held pellet.  Null when not holding. */
    @Nullable
    private UUID heldDispenserUUID = null;

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
        return heldDispenserUUID != null;
    }

    @Nullable
    public UUID getHeldDispenserUUID() {
        return heldDispenserUUID;
    }

    // -------------------------------------------------------------------------
    // Catch / clear
    // -------------------------------------------------------------------------

    /**
     * Called by EnergyPelletEntity when it collides with this receiver and the
     * receiver is not already holding a pellet.
     *
     * Sets the holding state, records which dispenser owns the pellet, and
     * propagates the block-state change so redstone updates propagate.
     *
     * @param dispenserUUID UUID of the dispenser that spawned the colliding pellet
     */
    public void catchPellet(@Nullable UUID dispenserUUID) {
        if (this.level == null || this.level.isClientSide) return;
        this.heldDispenserUUID = dispenserUUID;
        setHoldingState(true);
        setChanged();
    }

    /**
     * Called by EnergyPelletDispenserTileEntity when power is lost.
     * Reverts the receiver to not-holding and removes the redstone signal.
     */
    public void clearHeldPellet() {
        if (this.level == null || this.level.isClientSide) return;
        this.heldDispenserUUID = null;
        setHoldingState(false);
        setChanged();
    }

    // -------------------------------------------------------------------------
    // Block-state sync
    // -------------------------------------------------------------------------

    private void setHoldingState(boolean holding) {
        if (this.level == null) return;
        World world = this.level;

        // Apply the HOLDING property to all four member blocks.
        BlockState mainState = world.getBlockState(this.getBlockPos());
        if (!(mainState.getBlock() instanceof EnergyPelletReceiverBlock)) return;

        EnergyPelletReceiverBlock block = (EnergyPelletReceiverBlock) mainState.getBlock();
        block.setBlockStateValue(EnergyPelletReceiverBlock.HOLDING, holding, mainState, world, this.getBlockPos());

        // Notify neighbours so redstone reacts immediately.
        block.updateAllNeighbors(world, this.getBlockPos(), mainState);
    }

    // -------------------------------------------------------------------------
    // NBT persistence
    // -------------------------------------------------------------------------

    @Override
    public CompoundNBT save(CompoundNBT compound) {
        super.save(compound);
        if (heldDispenserUUID != null) {
            compound.putUUID("HeldDispenserUUID", heldDispenserUUID);
        }
        return compound;
    }

    @Override
    public void load(BlockState state, CompoundNBT compound) {
        super.load(state, compound);
        if (compound.hasUUID("HeldDispenserUUID")) {
            heldDispenserUUID = compound.getUUID("HeldDispenserUUID");
        } else {
            heldDispenserUUID = null;
        }
    }
}
