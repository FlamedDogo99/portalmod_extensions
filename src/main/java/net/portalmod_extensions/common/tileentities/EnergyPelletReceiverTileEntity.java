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

/*
 * tile entity for dispenser
 * stored on up left corner block
 * This tracks if the receiver is holding a pellet, BlockPos and dimension of dispenser associated with held pellet
 *
 * when holding, emits redstone level 15
 */
public class EnergyPelletReceiverTileEntity extends TileEntity {

    @Nullable
    private BlockPos heldDispenserPos = null;

    @Nullable
    private String heldDispenserDimension = null;

    public EnergyPelletReceiverTileEntity() {
        super(TileEntityTypeInit.ENERGY_PELLET_RECEIVER.get());
    }

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

    public void catchPellet(@Nullable BlockPos dispenserPos, @Nullable String dispenserDimension) {
        if(this.level == null || this.level.isClientSide) {
            return;
        }

        this.heldDispenserPos = dispenserPos;
        this.heldDispenserDimension = dispenserDimension;
        setHoldingState(true);
        setChanged();

        // notify dispenser
        if(dispenserPos != null && dispenserDimension != null) {
            notifyDispenser(dispenserPos, dispenserDimension, true);
        }
    }

    // called when broken while holding a pellet
    public void notifyDispenserOfRemoval() {
        if(heldDispenserPos != null && heldDispenserDimension != null) {
            notifyDispenser(heldDispenserPos, heldDispenserDimension, false);
        }
    }

    public void clearHeldPellet() {
        if(this.level == null || this.level.isClientSide) {
            return;
        }
        this.heldDispenserPos = null;
        this.heldDispenserDimension = null;
        setHoldingState(false);
        setChanged();
    }

    private void notifyDispenser(BlockPos dispenserPos, String dispenserDimension, boolean registering) {
        if(!(this.level instanceof ServerWorld)) {
            return;
        }
        ServerWorld serverWorld = (ServerWorld) this.level;

        // Find the world that owns the dispenser.
        ServerWorld dispenserWorld = findWorld(serverWorld, dispenserDimension);
        if(dispenserWorld == null) {
            return;
        }

        TileEntity te = dispenserWorld.getBlockEntity(dispenserPos);
        if(!(te instanceof EnergyPelletDispenserTileEntity)) {
            return;
        }
        EnergyPelletDispenserTileEntity dispenser = (EnergyPelletDispenserTileEntity) te;

        if(registering) {
            dispenser.registerReceiver(this.getBlockPos(), this.level.dimension().location().toString());
        } else {
            dispenser.unregisterReceiver();
        }
    }

    @Nullable
    private static ServerWorld findWorld(ServerWorld anyWorld, String dimensionKey) {
        for(ServerWorld w : anyWorld.getServer().getAllLevels()) {
            if(w.dimension().location().toString().equals(dimensionKey)) {
                return w;
            }
        }
        return null;
    }

    private void setHoldingState(boolean holding) {
        if(this.level == null) {
            return;
        }
        World world = this.level;

        BlockState mainState = world.getBlockState(this.getBlockPos());
        if(!(mainState.getBlock() instanceof EnergyPelletReceiverBlock)) {
            return;
        }

        EnergyPelletReceiverBlock block = (EnergyPelletReceiverBlock) mainState.getBlock();
        block.setBlockStateValue(EnergyPelletReceiverBlock.HOLDING, holding, mainState, world, this.getBlockPos());
        block.updateAllNeighbors(world, this.getBlockPos(), mainState);
    }

    @Override
    public CompoundNBT save(CompoundNBT compound) {
        super.save(compound);
        if(heldDispenserPos != null) {
            compound.putInt("HeldDispenserX", heldDispenserPos.getX());
            compound.putInt("HeldDispenserY", heldDispenserPos.getY());
            compound.putInt("HeldDispenserZ", heldDispenserPos.getZ());
        }
        if(heldDispenserDimension != null) {
            compound.putString("HeldDispenserDim", heldDispenserDimension);
        }
        return compound;
    }

    @Override
    public void load(BlockState state, CompoundNBT compound) {
        super.load(state, compound);
        if(compound.contains("HeldDispenserX", Constants.NBT.TAG_INT)) {
            this.heldDispenserPos = new BlockPos(compound.getInt("HeldDispenserX"), compound.getInt("HeldDispenserY"), compound.getInt("HeldDispenserZ"));
        } else {
            this.heldDispenserPos = null;
        }
        if(compound.contains("HeldDispenserDim", Constants.NBT.TAG_STRING)) {
            this.heldDispenserDimension = compound.getString("HeldDispenserDim");
        } else {
            this.heldDispenserDimension = null;
        }
    }
}
