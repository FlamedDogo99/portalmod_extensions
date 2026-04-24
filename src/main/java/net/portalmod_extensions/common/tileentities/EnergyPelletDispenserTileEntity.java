package net.portalmod_extensions.common.tileentities;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.Constants;
import net.portalmod_extensions.common.blocks.EnergyPelletDispenserBlock;
import net.portalmod_extensions.common.entities.EnergyPelletEntity;
import net.portalmod_extensions.core.init.SoundInit;
import net.portalmod_extensions.core.init.TileEntityTypeInit;
import net.portalmod_extensions.core.packet.PacketHandler;
import net.portalmod_extensions.core.packet.SDispenserAnimationPacket;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/*
 * tile entity for dispenser
 * stored on up left corner block
 * This tracks if the block is powered, the UUID of the current pellet, BlockPos and dimension of receiver holding pellet
 *
 * For redstone, rising edge: spawns a pellet if needed
 * falling edge is kill pellet and clear receivers if needed
 */
public class EnergyPelletDispenserTileEntity extends TileEntity implements ITickableTileEntity {

    public static final int ANIMATION_LENGTH = 20;
    public int animationProgress = 0;
    public int prevAnimationProgress = 0;

    public void startShootAnimation() {
        this.prevAnimationProgress = this.animationProgress;
        this.animationProgress = ANIMATION_LENGTH;
    }

    @Override
    public void tick() {
        if(this.level == null || !this.level.isClientSide) {
            return;
        }
        prevAnimationProgress = animationProgress;
        if(animationProgress > 0) {
            animationProgress--;
        }
    }

    private boolean wasPowered = false;

    @Nullable
    private UUID pelletUUID = null;

    @Nullable
    private BlockPos receiverPos = null;

    @Nullable
    private String receiverDimension = null;

    public EnergyPelletDispenserTileEntity() {
        super(TileEntityTypeInit.ENERGY_PELLET_DISPENSER.get());
    }

    public void registerReceiver(BlockPos pos, String dimension) {
        this.receiverPos = pos;
        this.receiverDimension = dimension;
        // The pellet has been caught; it will remove itself, so clear our UUID.
        this.pelletUUID = null;
        setChanged();
    }

    public void onPelletExpired() {
        this.pelletUUID = null;
        setChanged();
        if(wasPowered) {
            spawnPelletIfAbsent();
        }
    }

    public void unregisterReceiver() {
        this.receiverPos = null;
        this.receiverDimension = null;
        setChanged();
        if(wasPowered) {
            spawnPelletIfAbsent();
        }
    }

    public void onPowerChanged(boolean nowPowered) {
        net.portalmod_extensions.PortalModExtensions.LOGGER.info("onPowerChanged: " + nowPowered);
        if(nowPowered && !wasPowered) {
            spawnPelletIfAbsent();
        } else if(!nowPowered && wasPowered) {
            killPelletAndClearReceivers();
        }
        wasPowered = nowPowered;
    }

    private void spawnPelletIfAbsent() {
        net.portalmod_extensions.PortalModExtensions.LOGGER.info("spawnPelletIfAbsent: ");
        if(this.level == null || this.level.isClientSide) {
            return;
        }
        if(!(this.level instanceof ServerWorld)) {
            return;
        }

        // pellet in the air or held by receiver
        if(pelletUUID != null || receiverPos != null) {
            return;
        }

        ServerWorld serverLevel = (ServerWorld) this.level;

        BlockState state = this.getBlockState();
        if(!(state.getBlock() instanceof EnergyPelletDispenserBlock)) {
            return;
        }

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

        EnergyPelletEntity pellet = new EnergyPelletEntity(serverLevel, cx + ox, cy + oy, cz + oz, vx, vy, vz, this.getBlockPos(), dimLocation);

        serverLevel.addFreshEntity(pellet);
        pelletUUID = pellet.getUUID();
        setChanged();

        serverLevel.playSound(null, cx + ox, cy + oy, cz + oz, SoundInit.ENERGY_PELLET_DISPENSER_SHOOT.get(), net.minecraft.util.SoundCategory.BLOCKS, 1.0f, 1.0f);

        PacketHandler.sendToTrackingChunk(new SDispenserAnimationPacket(this.getBlockPos()), this.getBlockPos(), serverLevel);
    }

    public void killPelletAndClearReceivers() {
        net.portalmod_extensions.PortalModExtensions.LOGGER.info("killPelletAndClearReceivers: ");
        if(this.level == null || this.level.isClientSide) {
            return;
        }
        if(!(this.level instanceof ServerWorld)) {
            return;
        }

        ServerWorld serverLevel = (ServerWorld) this.level;

        if(pelletUUID != null) {
            Entity entity = serverLevel.getEntity(pelletUUID);
            if(entity != null) {
                entity.remove();
            }
            pelletUUID = null;
            setChanged();
        }

        if(receiverPos != null && receiverDimension != null) {
            ServerWorld receiverWorld = findWorld(serverLevel, receiverDimension);
            if(receiverWorld != null) {
                TileEntity te = receiverWorld.getBlockEntity(receiverPos);
                if(te instanceof EnergyPelletReceiverTileEntity) {
                    ((EnergyPelletReceiverTileEntity) te).clearHeldPellet();
                }
            }
            receiverPos = null;
            receiverDimension = null;
            setChanged();
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

    @Override
    public CompoundNBT save(CompoundNBT compound) {
        super.save(compound);
        compound.putBoolean("WasPowered", wasPowered);
        if(pelletUUID != null) {
            compound.putUUID("PelletUUID", pelletUUID);
        }
        if(receiverPos != null) {
            compound.putInt("ReceiverX", receiverPos.getX());
            compound.putInt("ReceiverY", receiverPos.getY());
            compound.putInt("ReceiverZ", receiverPos.getZ());
        }
        if(receiverDimension != null) {
            compound.putString("ReceiverDim", receiverDimension);
        }
        return compound;
    }

    @Override
    public void load(BlockState state, CompoundNBT compound) {
        super.load(state, compound);
        wasPowered = compound.getBoolean("WasPowered");
        if(compound.hasUUID("PelletUUID")) {
            pelletUUID = compound.getUUID("PelletUUID");
        } else {
            pelletUUID = null;
        }
        if(compound.contains("ReceiverX", Constants.NBT.TAG_INT)) {
            receiverPos = new BlockPos(compound.getInt("ReceiverX"), compound.getInt("ReceiverY"), compound.getInt("ReceiverZ"));
        } else {
            receiverPos = null;
        }
        if(compound.contains("ReceiverDim", Constants.NBT.TAG_STRING)) {
            receiverDimension = compound.getString("ReceiverDim");
        } else {
            receiverDimension = null;
        }
    }
}