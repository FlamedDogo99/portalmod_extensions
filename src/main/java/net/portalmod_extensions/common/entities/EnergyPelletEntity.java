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
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkHooks;
import net.portalmod_extensions.PortalModExtensions;
import net.portalmod_extensions.common.tileentities.EnergyPelletReceiverTileEntity;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Energy Pellet entity.
 *
 * Extends FireballEntity so it travels in a straight line via xPower/yPower/zPower.
 * On block collision it performs a 100% elastic (perfect mirror) reflection along
 * the axis of the face that was hit.
 * On entity collision it kills non-portalmod entities; portalmod entities have a
 * blank handler left for future implementation.
 *
 * Has an age that counts DOWN from INITIAL_AGE each tick. The entity is removed
 * when the age reaches zero (mirrors FallingBlockEntity behaviour where the NBT
 * tag "Time" counts up and the entity dies after a threshold, but here we count
 * down for simplicity).
 */
public class EnergyPelletEntity extends FireballEntity {

    /** Maximum lifetime in ticks (10 seconds at 20 TPS). */
    public static final int INITIAL_AGE = 200;

    /** Namespace prefix used to identify portalmod entities. */
    private static final String PORTALMOD_NAMESPACE = "portalmod";

    // -------------------------------------------------------------------------
    // Tracked data
    // -------------------------------------------------------------------------

    private static final DataParameter<Integer> DATA_AGE =
            EntityDataManager.defineId(EnergyPelletEntity.class, DataSerializers.INT);

    // -------------------------------------------------------------------------
    // Association with spawning dispenser
    // -------------------------------------------------------------------------

    /** UUID of the EnergyPelletDispenserTileEntity that spawned this pellet.
     *  May be null if not yet set or loaded from NBT. */
    @Nullable
    private UUID dispenserUUID;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Required by EntityType factory (world-only constructor). */
    public EnergyPelletEntity(EntityType<? extends EnergyPelletEntity> type, World world) {
        super(type, world);
    }

    /**
     * Convenience constructor used by EnergyPelletDispenserTileEntity when
     * spawning a new pellet.
     *
     * @param world         the server world
     * @param x             spawn X (centre of 2×2 dispenser face)
     * @param y             spawn Y
     * @param z             spawn Z
     * @param velX          initial velocity X component
     * @param velY          initial velocity Y component
     * @param velZ          initial velocity Z component
     * @param dispenserUUID UUID of the tile entity that spawned this pellet
     */
    public EnergyPelletEntity(World world,
                              double x, double y, double z,
                              double velX, double velY, double velZ,
                              @Nullable UUID dispenserUUID) {
        // FireballEntity(World, x, y, z, accelX, accelY, accelZ) sets position and
        // xPower/yPower/zPower directly — this is the correct constructor to use.
        super(world, x, y, z, velX, velY, velZ);
        this.dispenserUUID = dispenserUUID;
        // Initialise age (defineSynchedData has already run via super chain)
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

        if (!this.level.isClientSide) {
            int age = this.entityData.get(DATA_AGE) - 1;
            this.entityData.set(DATA_AGE, age);
            if (age <= 0) {
                this.remove();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Collision — block
    // -------------------------------------------------------------------------

    /**
     * Called by FireballEntity when a block face is hit.
     *
     * We perform a 100% elastic reflection: the velocity component along the
     * axis of the hit face is negated (mirrored), while the other two components
     * are kept unchanged.
     */
    @Override
    protected void onHitBlock(BlockRayTraceResult result) {
        // Do NOT call super — super ignites the block, which we do not want.

        if (this.level.isClientSide) return;

        // Reflect the power vector along the axis of the face that was hit.
        switch (result.getDirection().getAxis()) {
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

        // Also correct the current delta movement to match, so the entity does not
        // stutter on the frame of the bounce.
        Vector3d vel = this.getDeltaMovement();
        switch (result.getDirection().getAxis()) {
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
        if (te instanceof EnergyPelletReceiverTileEntity) {
            EnergyPelletReceiverTileEntity receiver = (EnergyPelletReceiverTileEntity) te;
            if (!receiver.isHolding()) {
                receiver.catchPellet(this.dispenserUUID);
                this.remove();
            }
            // If the receiver is already holding, the pellet just bounces off normally
            // (the reflection above already applied).
        }
    }

    // -------------------------------------------------------------------------
    // Collision — entity
    // -------------------------------------------------------------------------

    @Override
    protected void onHitEntity(EntityRayTraceResult result) {
        if (this.level.isClientSide) return;

        Entity target = result.getEntity();
        String registryNamespace = target.getType().getRegistryName() != null
                ? target.getType().getRegistryName().getNamespace()
                : "";

        if (registryNamespace.equals(PORTALMOD_NAMESPACE)
                || registryNamespace.equals(PortalModExtensions.MODID)) {
            // Blank handler — behaviour for portalmod entities to be implemented later.
            handlePortalModEntityCollision(target);
        } else {
            // Kill non-portalmod entities.
            if (target instanceof LivingEntity) {
                // Use a large damage value to guarantee death regardless of armour.
                target.hurt(net.minecraft.util.DamageSource.MAGIC, Float.MAX_VALUE);
            } else {
                target.remove();
            }
            this.remove();
        }
    }

    /**
     * Blank handler for collisions with portalmod-namespace entities.
     * To be implemented in a future update.
     */
    @SuppressWarnings("unused")
    private void handlePortalModEntityCollision(Entity target) {
        // TODO: implement portalmod entity collision behaviour
    }

    // -------------------------------------------------------------------------
    // FireballEntity overrides — prevent fire / explosion
    // -------------------------------------------------------------------------

    @Override
    protected void onHit(RayTraceResult result) {
        if (result.getType() == RayTraceResult.Type.BLOCK) {
            onHitBlock((BlockRayTraceResult) result);
        } else if (result.getType() == RayTraceResult.Type.ENTITY) {
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
        if (this.dispenserUUID != null) {
            compound.putUUID("DispenserUUID", this.dispenserUUID);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundNBT compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("Age")) {
            this.entityData.set(DATA_AGE, compound.getInt("Age"));
        }
        if (compound.hasUUID("DispenserUUID")) {
            this.dispenserUUID = compound.getUUID("DispenserUUID");
        }
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    @Nullable
    public UUID getDispenserUUID() {
        return dispenserUUID;
    }

    public void setDispenserUUID(@Nullable UUID dispenserUUID) {
        this.dispenserUUID = dispenserUUID;
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
