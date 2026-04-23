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
import net.portalmod_extensions.common.tileentities.EnergyPelletDispenserTileEntity;
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
                notifyDispenserPelletGone();
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
            handlePortalModEntityCollision(result, target);
        } else {
            if(target instanceof LivingEntity) {
                target.hurt(net.minecraft.util.DamageSource.MAGIC, Float.MAX_VALUE);
            } else {
                target.remove();
            }
            notifyDispenserPelletGone();
            this.remove();
        }
    }

    private void handlePortalModEntityCollision(EntityRayTraceResult result, Entity target) {
        // Elastic reflection off companion/storage/vintage cubes.
        // All three are registered as net.portalmod.common.sorted.cube.Cube instances.
        if(target instanceof net.portalmod.common.sorted.cube.Cube) {
            reflectOffRotatedCube(result, (net.portalmod.common.sorted.cube.Cube) target);
            return;
        }
        // All other portalmod entities: no-op for now.
    }

    /**
     * Reflects the pellet off a rotating cube using 100% elastic collision,
     * correctly accounting for the cube's Y-axis rotation (yRot).
     * <p>
     * Strategy:
     *   1. Express the incoming ray in the cube's local space by translating
     *      to the cube's centre and un-rotating by -yRot around Y.
     *   2. Slab-test that local ray against the axis-aligned unit cube
     *      (half-extent = 0.4 on all axes) to find which local face was hit.
     *   3. Re-rotate that local normal back to world space by +yRot around Y.
     *   4. Reflect deltaMovement off the world-space normal:
     *         v' = v - 2(v·n)n
     */
    private void reflectOffRotatedCube(EntityRayTraceResult result, net.portalmod.common.sorted.cube.Cube cube) {
        final double HALF = 0.4; // sized(0.8, 0.8) → half-extent 0.4

        Vector3d cubeCenter = cube.getBoundingBox().getCenter();
        Vector3d worldVel   = this.getDeltaMovement();
        Vector3d worldRayDir = worldVel.normalize();

        // Transform ray origin into cube-local space (translate then rotate by -yRot around Y).
        double angleRad = Math.toRadians(cube.yRot);
        double cosA = Math.cos(angleRad);
        double sinA = Math.sin(angleRad);

        Vector3d rel = this.position().subtract(cubeCenter);
        // Rotate by -yRot:  x' = cosA*x - sinA*z,  z' = sinA*x + cosA*z
        double lox =  cosA * rel.x - sinA * rel.z;
        double loy =  rel.y;
        double loz =  sinA * rel.x + cosA * rel.z;

        double ldx =  cosA * worldRayDir.x - sinA * worldRayDir.z;
        double ldy =  worldRayDir.y;
        double ldz =  sinA * worldRayDir.x + cosA * worldRayDir.z;

        // Slab test: find the axis whose slab is entered last — that is the hit face.
        double tEnter  = Double.NEGATIVE_INFINITY;
        int    hitAxis = 1;       // default Y (top) if ray is degenerate
        boolean hitMin = true;

        double[] lo = { lox, loy, loz };
        double[] ld = { ldx, ldy, ldz };

        for(int axis = 0; axis < 3; axis++) {
            if(Math.abs(ld[axis]) < 1e-10) continue;
            double t1 = (-HALF - lo[axis]) / ld[axis];
            double t2 = ( HALF - lo[axis]) / ld[axis];
            boolean minFirst = t1 < t2;
            double tNear = minFirst ? t1 : t2;
            if(tNear > tEnter) {
                tEnter  = tNear;
                hitAxis = axis;
                hitMin  = minFirst;
            }
        }

        // Local outward normal: +1 on the far side of entry, -1 on the near side.
        double[] localNormal = { 0, 0, 0 };
        localNormal[hitAxis] = hitMin ? -1.0 : 1.0;

        // Rotate local normal back to world space by +yRot.
        double lnx = localNormal[0];
        double lnz = localNormal[2];
        double wnx =  cosA * lnx + sinA * lnz;
        double wny =  localNormal[1];
        double wnz = -sinA * lnx + cosA * lnz;

        // Reflect: v' = v - 2(v·n)n
        double dot = worldVel.x * wnx + worldVel.y * wny + worldVel.z * wnz;
        this.setDeltaMovement(
            worldVel.x - 2 * dot * wnx,
            worldVel.y - 2 * dot * wny,
            worldVel.z - 2 * dot * wnz
        );
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
    // Dispenser notification
    // -------------------------------------------------------------------------

    /**
     * Called whenever the pellet removes itself for a reason the dispenser did
     * NOT initiate (age expiry, entity-kill collision).  Tells the dispenser to
     * clear its tracked UUID and, if still powered, immediately re-spawn.
     * <p>
     * NOT called on receiver-catch (the dispenser stays dormant while a receiver
     * holds the pellet) and NOT called when the dispenser itself killed us via
     * killPelletAndClearReceivers (it handles its own cleanup directly).
     */
    private void notifyDispenserPelletGone() {
        if(this.level == null || this.level.isClientSide || dispenserPos == null) return;
        if(!(this.level instanceof net.minecraft.world.server.ServerWorld)) return;

        net.minecraft.world.server.ServerWorld serverLevel = (net.minecraft.world.server.ServerWorld) this.level;

        // Resolve the dispenser's world (normally the same as ours).
        net.minecraft.world.server.ServerWorld dispenserWorld = serverLevel;
        if(dispenserDimension != null && !dispenserDimension.equals(serverLevel.dimension().location().toString())) {
            for(net.minecraft.world.server.ServerWorld w : serverLevel.getServer().getAllLevels()) {
                if(w.dimension().location().toString().equals(dispenserDimension)) {
                    dispenserWorld = w;
                    break;
                }
            }
        }

        TileEntity te = dispenserWorld.getBlockEntity(dispenserPos);
        if(te instanceof EnergyPelletDispenserTileEntity) {
            ((EnergyPelletDispenserTileEntity) te).onPelletExpired();
        }
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