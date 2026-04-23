package net.portalmod_extensions.common.entities;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.network.NetworkHooks;
import net.portalmod_extensions.PortalModExtensions;
import net.portalmod_extensions.common.tileentities.EnergyPelletDispenserTileEntity;
import net.portalmod_extensions.common.tileentities.EnergyPelletReceiverTileEntity;

import javax.annotation.Nullable;

/**
 * Energy Pellet entity.
 * <p>
 * Extends FireballEntity so it travels in a straight line via xPower/yPower/zPower.
 * On block collision it performs a 100% elastic (perfect mirror) reflection along
 * the axis of the face that was hit.
 * On entity collision it kills non-portalmod entities; portalmod entities have a
 * blank handler left for future implementation.
 * <p>
 * Has an age that counts DOWN from INITIAL_AGE each tick. The entity is removed
 * when the age reaches zero.
 * <p>
 * Carries the BlockPos and dimension key of the dispenser that spawned it, so that
 * when a receiver catches the pellet it can notify the dispenser directly — no
 * world-wide search needed.
 */
public class EnergyPelletEntity extends FireballEntity {

    /**
     * Maximum lifetime in ticks (10 seconds at 20 TPS).
     */
    public static final int INITIAL_AGE = 200;

    /**
     * Namespace prefix used to identify portalmod entities.
     */
    private static final String PORTALMOD_NAMESPACE = "portalmod";

    // -------------------------------------------------------------------------
    // Tracked data
    // -------------------------------------------------------------------------

    private static final DataParameter<Integer> DATA_AGE = EntityDataManager.defineId(EnergyPelletEntity.class, DataSerializers.INT);

    // -------------------------------------------------------------------------
    // Association with spawning dispenser (position + dimension, not UUID)
    //
    // A BlockPos + dimension key is used instead of a UUID because:
    //   - TileEntities have no persistent UUID; the previous code derived one
    //     deterministically from the position anyway, making the UUID redundant.
    //   - A direct getBlockEntity(pos) lookup is O(1) and never needs a search.
    //   - The dimension key guards against the (currently impossible but
    //     theoretically possible) case where a dispenser and a receiver exist in
    //     different dimensions.
    //
    // Note: pellets are FireballEntities with no teleportation logic, so they
    // always stay in the dimension they were spawned in.  The dimension field on
    // the pellet will therefore equal the dispenser's dimension in all normal
    // usage, but it is stored explicitly for correctness.
    // -------------------------------------------------------------------------

    @Nullable
    private BlockPos dispenserPos = null;

    /**
     * Registry key location string for the dimension the dispenser is in
     * (e.g. "minecraft:overworld").  Stored as a string for NBT simplicity.
     */
    @Nullable
    private String dispenserDimension = null;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Required by EntityType factory (world-only constructor).
     */
    public EnergyPelletEntity(EntityType<? extends EnergyPelletEntity> type, World world) {
        super(type, world);
    }

    /**
     * Convenience constructor used by EnergyPelletDispenserTileEntity when
     * spawning a new pellet.
     */
    public EnergyPelletEntity(World world, double x, double y, double z, double velX, double velY, double velZ, @Nullable BlockPos dispenserPos, @Nullable ResourceLocation dispenserDimension) {
        super(world, x, y, z, velX, velY, velZ);
        this.dispenserPos = dispenserPos;
        this.dispenserDimension = dispenserDimension != null ? dispenserDimension.toString() : null;
        this.entityData.set(DATA_AGE, INITIAL_AGE);
    }

    // -------------------------------------------------------------------------
    // EntityDataManager
    // -------------------------------------------------------------------------

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_AGE, INITIAL_AGE);
    }

    // -------------------------------------------------------------------------
    // Tick / lifetime
    // -------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();

        if(!this.level.isClientSide) {
            int age = this.entityData.get(DATA_AGE) - 1;
            this.entityData.set(DATA_AGE, age);
            if(age <= 0) {
                this.remove();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Collision — block
    // -------------------------------------------------------------------------

    @Override
    protected void onHitBlock(BlockRayTraceResult result) {
        // Do NOT call super — super ignites the block, which we do not want.

        if(this.level.isClientSide) {
            return;
        }

        // Reflect the power vector along the axis of the face that was hit.
        switch(result.getDirection().getAxis()) {
            case X:
                this.xPower = -this.xPower;
                break;
            case Y:
                this.yPower = -this.yPower;
                break;
            case Z:
                this.zPower = -this.zPower;
                break;
        }

        Vector3d vel = this.getDeltaMovement();
        switch(result.getDirection().getAxis()) {
            case X:
                this.setDeltaMovement(-vel.x, vel.y, vel.z);
                break;
            case Y:
                this.setDeltaMovement(vel.x, -vel.y, vel.z);
                break;
            case Z:
                this.setDeltaMovement(vel.x, vel.y, -vel.z);
                break;
        }

        // Check if this is a receiver block and hand off to it.
        TileEntity te = this.level.getBlockEntity(result.getBlockPos());
        if(te instanceof EnergyPelletReceiverTileEntity) {
            EnergyPelletReceiverTileEntity receiver = (EnergyPelletReceiverTileEntity) te;
            if(!receiver.isHolding()) {
                receiver.catchPellet(dispenserPos, dispenserDimension);
                this.remove();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Collision — entity
    // -------------------------------------------------------------------------

    @Override
    protected void onHitEntity(EntityRayTraceResult result) {
        if(this.level.isClientSide) {
            return;
        }

        Entity target = result.getEntity();
        String registryNamespace = target.getType().getRegistryName() != null ? target.getType().getRegistryName().getNamespace() : "";

        if(registryNamespace.equals(PORTALMOD_NAMESPACE) || registryNamespace.equals(PortalModExtensions.MODID)) {
            handlePortalModEntityCollision(target);
        } else {
            if(target instanceof LivingEntity) {
                target.hurt(net.minecraft.util.DamageSource.MAGIC, Float.MAX_VALUE);
            } else {
                target.remove();
            }
            this.remove();
        }
    }

    @SuppressWarnings("unused")
    private void handlePortalModEntityCollision(Entity target) {
        // TODO: implement portalmod entity collision behaviour
    }

    // -------------------------------------------------------------------------
    // FireballEntity overrides — prevent fire / explosion
    // -------------------------------------------------------------------------

    @Override
    protected void onHit(RayTraceResult result) {
        if(result.getType() == RayTraceResult.Type.BLOCK) {
            onHitBlock((BlockRayTraceResult) result);
        } else if(result.getType() == RayTraceResult.Type.ENTITY) {
            onHitEntity((EntityRayTraceResult) result);
        }
        // Do NOT call super.onHit — it would trigger ignition logic.
    }

    // -------------------------------------------------------------------------
    // NBT serialisation
    // -------------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundNBT compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("Age", this.entityData.get(DATA_AGE));
        if(dispenserPos != null) {
            compound.putInt("DispenserX", dispenserPos.getX());
            compound.putInt("DispenserY", dispenserPos.getY());
            compound.putInt("DispenserZ", dispenserPos.getZ());
        }
        if(dispenserDimension != null) {
            compound.putString("DispenserDim", dispenserDimension);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundNBT compound) {
        super.readAdditionalSaveData(compound);
        if(compound.contains("Age")) {
            this.entityData.set(DATA_AGE, compound.getInt("Age"));
        }
        if(compound.contains("DispenserX")) {
            this.dispenserPos = new BlockPos(compound.getInt("DispenserX"), compound.getInt("DispenserY"), compound.getInt("DispenserZ"));
        }
        if(compound.contains("DispenserDim")) {
            this.dispenserDimension = compound.getString("DispenserDim");
        }
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    @Nullable
    public BlockPos getDispenserPos() {
        return dispenserPos;
    }

    @Nullable
    public String getDispenserDimension() {
        return dispenserDimension;
    }

    public int getAge() {
        return this.entityData.get(DATA_AGE);
    }

    // -------------------------------------------------------------------------
    // Network
    // -------------------------------------------------------------------------

    @Override
    public IPacket<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
