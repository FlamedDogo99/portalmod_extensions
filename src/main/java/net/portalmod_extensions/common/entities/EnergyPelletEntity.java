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
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkHooks;
import net.portalmod_extensions.PortalModExtensions;
import net.portalmod_extensions.common.tileentities.EnergyPelletReceiverTileEntity;

import javax.annotation.Nullable;

/**
 * Energy Pellet entity.
 * <p>
 * Extends FireballEntity (DamagingProjectileEntity) but neutralizes the two
 * behaviors that caused continuous speed increase:
 * <p>
 * 1. xPower/yPower/zPower accumulation — DamagingProjectileEntity.tick() adds
 * these to deltaMovement every tick, continuously accelerating the entity.
 * We use the type-only constructor (which leaves them at zero) and never set
 * them, so the add is always +0 and has no effect.
 * <p>
 * 2. Inertia scaling — tick() multiplies deltaMovement by getInertia() (0.95F
 * by default) every tick, creating drag.  We override getInertia() to return
 * 1.0F so deltaMovement is never damped.
 * <p>
 * With both neutralized, deltaMovement is the sole source of truth for velocity.
 * It is set once at spawn and only ever changed by the elastic reflection in
 * onHitBlock, giving true constant-velocity travel between bounces.
 * <p>
 * Carries the BlockPos and dimension key of the spawning dispenser so that when
 * a receiver catches it, the receiver can notify the dispenser in O(1).
 */
public class EnergyPelletEntity extends FireballEntity {

    /**
     * Maximum lifetime in ticks (10 seconds at 20 TPS).
     */
    public static final int INITIAL_AGE = 200;

    private static final String PORTALMOD_NAMESPACE = "portalmod";

    // -------------------------------------------------------------------------
    // Tracked data
    // -------------------------------------------------------------------------

    private static final DataParameter<Integer> DATA_AGE = EntityDataManager.defineId(EnergyPelletEntity.class, DataSerializers.INT);

    // -------------------------------------------------------------------------
    // Dispenser association
    // -------------------------------------------------------------------------

    @Nullable
    private BlockPos dispenserPos = null;
    @Nullable
    private String dispenserDimension = null;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Required by EntityType factory.
     */
    public EnergyPelletEntity(EntityType<? extends EnergyPelletEntity> type, World world) {
        super(type, world);
        // xPower/yPower/zPower are already zero — leave them that way.
    }

    /**
     * Spawn constructor called by EnergyPelletDispenserTileEntity.
     * <p>
     * Uses the type-only super constructor so that DamagingProjectileEntity's
     * position+velocity constructor cannot normalise and rescale our velocity
     * vector into xPower.  Position and deltaMovement are set manually below.
     */
    public EnergyPelletEntity(World world, double x, double y, double z, double velX, double velY, double velZ, @Nullable BlockPos dispenserPos, @Nullable ResourceLocation dispenserDimension) {
        super(net.portalmod_extensions.core.init.EntityInit.ENERGY_PELLET.get(), world);
        // Place the entity. reapplyPosition() syncs the bounding box.
        this.moveTo(x, y, z, this.yRot, this.xRot);
        this.reapplyPosition();
        // Store velocity purely in deltaMovement. xPower/yPower/zPower remain
        // zero, so DamagingProjectileEntity.tick()'s "+= power" adds nothing.
        this.setDeltaMovement(velX, velY, velZ);
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
    // Inertia — disable drag so deltaMovement is never damped between ticks
    // -------------------------------------------------------------------------

    @Override
    protected float getInertia() {
        return 1.0F;
    }

    // -------------------------------------------------------------------------
    // Tick / lifetime
    // -------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick(); // runs DamagingProjectileEntity.tick(): adds xPower (0),
        // scales by getInertia() (1.0), moves, detects hits

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
        // Do NOT call super — FireballEntity.onHitBlock causes an explosion.

        if(this.level.isClientSide) {
            return;
        }

        // 100% elastic reflection: negate only the component on the hit axis.
        // deltaMovement is the sole velocity store; xPower/yPower/zPower are
        // all zero and do not need updating.
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

        // Check for receiver.
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
        String namespace = target.getType().getRegistryName() != null ? target.getType().getRegistryName().getNamespace() : "";

        if(namespace.equals(PORTALMOD_NAMESPACE) || namespace.equals(PortalModExtensions.MODID)) {
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
    // FireballEntity/DamagingProjectileEntity overrides — prevent fire/explosion
    // -------------------------------------------------------------------------

    @Override
    protected void onHit(RayTraceResult result) {
        if(result.getType() == RayTraceResult.Type.BLOCK) {
            onHitBlock((BlockRayTraceResult) result);
        } else if(result.getType() == RayTraceResult.Type.ENTITY) {
            onHitEntity((EntityRayTraceResult) result);
        }
        // Do NOT call super — it would trigger FireballEntity's ignition logic.
    }

    @Override
    protected boolean shouldBurn() {
        return false;
    }

    // -------------------------------------------------------------------------
    // NBT
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