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
import net.minecraft.block.BlockState;
import net.minecraft.block.StairsBlock;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.state.properties.Half;
import net.minecraft.state.properties.StairsShape;
import net.minecraft.util.Direction;
import net.portalmod_extensions.common.blocks.EnergyPelletReceiverBlock;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/*
 * This is a crappy hack because I don't want to worry about implementing my own behavior
 * for moving through portals, and fireballs already are handled. We have to stop the default
 * behavior of constant acceleration manually
 *
 * Because of this, deltaMovement is the only correct source for velocity
 *
 * We track BlockPos and dimension of the dispenser that spawned it so that we can
 * update recievers etc in O(1)
 */
public class EnergyPelletEntity extends FireballEntity implements net.portalmod.common.sorted.portal.PortalHandler {

    // TODO: arbitrary, maybe we should have a way to customize this in survival?
    public static final int INITIAL_AGE = 200;

    private static final String PORTALMOD_NAMESPACE = "portalmod";
    private static final DataParameter<Integer> DATA_AGE = EntityDataManager.defineId(EnergyPelletEntity.class, DataSerializers.INT);
    private boolean notifiedDispenser = false;

    @Nullable
    private BlockPos dispenserPos = null;
    @Nullable
    private String dispenserDimension = null;

    public EnergyPelletEntity(EntityType<? extends EnergyPelletEntity> type, World world) {
        super(type, world);
    }

    public EnergyPelletEntity(World world, double x, double y, double z, double velX, double velY, double velZ, @Nullable BlockPos dispenserPos, @Nullable ResourceLocation dispenserDimension) {
        super(net.portalmod_extensions.core.init.EntityInit.ENERGY_PELLET.get(), world);
        // reapplyPosition syncs the bounding box
        this.moveTo(x, y, z, this.yRot, this.xRot);
        this.reapplyPosition();
        // Store velocity in deltaMovement, zero xPower/yPower/zPower remain
        this.setDeltaMovement(velX, velY, velZ);
        this.dispenserPos = dispenserPos;
        this.dispenserDimension = dispenserDimension != null ? dispenserDimension.toString() : null;
        this.entityData.set(DATA_AGE, INITIAL_AGE);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_AGE, INITIAL_AGE);
    }

    @Override
    protected float getInertia() {
        return 1.0F;
    }

    // stop player interaction
    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(@Nonnull net.minecraft.util.DamageSource source, float amount) {
        return false;
    }

    // fix jittery movement
    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps, boolean interpolate) {
        super.lerpTo(x, y, z, yaw, pitch, 1, interpolate);
    }

    // reset age when going through portal
    @Override
    public void onTeleport(net.portalmod.common.sorted.portal.PortalEntity from, net.portalmod.common.sorted.portal.PortalEntity to) {
        if(!this.level.isClientSide) {
            this.entityData.set(DATA_AGE, INITIAL_AGE);
        }
    }

    @Override
    public void onTeleportPacket() {
    }

    @Override
    public void tick() {
        super.tick();
        if(!this.level.isClientSide) {
            int age = this.entityData.get(DATA_AGE) - 1;
            this.entityData.set(DATA_AGE, age);
            if(age <= 0) {
                notifyDispenserPelletGone();
                this.remove();
            }
        }
    }

    @Override
    public void remove() {
        if(!this.level.isClientSide) {
            notifyDispenserPelletGone();
        }
        super.remove();
    }

    @Override
    protected void onHitBlock(@Nonnull BlockRayTraceResult result) {
        // don't call super, as it will literally explode
        if(this.level.isClientSide) {
            return;
        }

        BlockPos hitPos = result.getBlockPos();
        BlockState hitState = this.level.getBlockState(hitPos);

        // attempt stair slope reflection first, fall back to axis reflection
        Vector3d slopeNormal = getStairSlopeNormal(hitState, result);
        if(slopeNormal == null) {
            slopeNormal = getHorizontalStairSlopeNormal(hitState, result);
        }
        if(slopeNormal != null) {
            reflectOffNormal(slopeNormal);
            nudgePastPlane(result.getLocation(), slopeNormal);
        } else {
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
        }

        // check for receiver collision, resolve for up left tile entity
        if(hitState.getBlock() instanceof EnergyPelletReceiverBlock) {
            EnergyPelletReceiverBlock receiverBlock = (EnergyPelletReceiverBlock) hitState.getBlock();
            BlockPos mainPos = receiverBlock.getMainPosition(hitState, hitPos);
            TileEntity tileEntity = this.level.getBlockEntity(mainPos);
            if(tileEntity instanceof EnergyPelletReceiverTileEntity) {
                EnergyPelletReceiverTileEntity receiver = (EnergyPelletReceiverTileEntity) tileEntity;
                if(!receiver.isHolding()) {
                    receiver.catchPellet(dispenserPos, dispenserDimension);
                    this.remove();
                }
            }
        }
    }

    @Nullable
    private Vector3d getStairSlopeNormal(BlockState state, BlockRayTraceResult result) {
        if(!(state.getBlock() instanceof StairsBlock)) {
            return null;
        }
        StairsShape shape = state.getValue(BlockStateProperties.STAIRS_SHAPE);
        // only dealing with straight stairs
        if(shape != StairsShape.STRAIGHT) {
            return null;
        }

        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        Half half = state.getValue(BlockStateProperties.HALF);

        // unnormalized slope normal
        double normalizedX = 0, normalizedY = (half == Half.BOTTOM) ? 1.0 : -1.0, normalizedZ = 0;
        switch(facing) {
            case NORTH:
                normalizedZ = 1.0;
                break;
            case SOUTH:
                normalizedZ = -1.0;
                break;
            case WEST:
                normalizedX = 1.0;
                break;
            case EAST:
                normalizedX = -1.0;
                break;
            default:
                return null;
        }
        Vector3d normal = new Vector3d(normalizedX, normalizedY, normalizedZ).normalize();
        Vector3d vel = this.getDeltaMovement();
        if(vel.dot(normal) >= 0) {
            return null;
        }

        return normal;
    }

    private static final String HORIZONTAL_STAIRS_ID = "sideways_stairs:horizontal_stairs";

    @Nullable
    private Vector3d getHorizontalStairSlopeNormal(BlockState state, BlockRayTraceResult result) {
        net.minecraft.util.ResourceLocation regName = state.getBlock().getRegistryName();
        if(regName == null || !HORIZONTAL_STAIRS_ID.equals(regName.toString())) {
            return null;
        }

        // horizontal stair slopes are entered through a vertical face, not top/bottom
        if(result.getDirection().getAxis() == Direction.Axis.Y) {
            return null;
        }

        String facing;
        try {
            facing = state.getValues().entrySet().stream().filter(e -> e.getKey().getName().equals("facing")).map(e -> e.getValue().toString().toUpperCase()).findFirst().orElse(null);
        } catch(Exception e) {
            return null;
        }
        if(facing == null) {
            return null;
        }

        double normalizedX, normalizedZ;
        switch(facing) {
            case "SW":
                normalizedX = -1;
                normalizedZ = -1;
                break;
            case "NE":
                normalizedX = 1;
                normalizedZ = 1;
                break;
            case "SE":
                normalizedX = 1;
                normalizedZ = -1;
                break;
            case "NW":
                normalizedX = -1;
                normalizedZ = 1;
                break;
            default:
                return null;
        }
        Vector3d normal = new Vector3d(normalizedX, 0, normalizedZ).normalize();

        // only apply slope reflection when approaching from the sloped side
        Vector3d vel = this.getDeltaMovement();
        if(vel.dot(normal) >= 0) {
            return null;
        }

        return normal;
    }

    private void reflectOffNormal(Vector3d normal) {
        Vector3d vel = this.getDeltaMovement();
        double dot = vel.dot(normal);
        this.setDeltaMovement(vel.subtract(normal.scale(2.0 * dot)));
    }

    // prevent re-collision the next tick
    private void nudgePastPlane(Vector3d contact, Vector3d normal) {
        final double EPSILON = 1e-3;
        this.moveTo(contact.x + normal.x * EPSILON, contact.y + normal.y * EPSILON, contact.z + normal.z * EPSILON, this.yRot, this.xRot);
        this.reapplyPosition();
    }

    @Override
    protected void onHitEntity(@Nonnull EntityRayTraceResult result) {
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
        // bounce off of portalmod cubes
        if(target instanceof net.portalmod.common.sorted.cube.Cube) {
            reflectOffRotatedCube(result, (net.portalmod.common.sorted.cube.Cube) target);
            return;
        }
        // TODO: is there any other portalmod entity's we need to handle?
    }

    // per-face + rotation collision
    private void reflectOffRotatedCube(EntityRayTraceResult result, net.portalmod.common.sorted.cube.Cube cube) {
        final double HALF = 0.4; // off of bounding box

        Vector3d cubeCenter = cube.getBoundingBox().getCenter();
        Vector3d worldVel = this.getDeltaMovement();
        Vector3d worldRayDir = worldVel.normalize();
        // cube space
        double angleRad = Math.toRadians(cube.yRot);
        double cosA = Math.cos(angleRad);
        double sinA = Math.sin(angleRad);
        Vector3d rel = this.position().subtract(cubeCenter);
        // rotate by -yRot
        double lox = cosA * rel.x - sinA * rel.z;
        double loy = rel.y;
        double loz = sinA * rel.x + cosA * rel.z;

        double ldx = cosA * worldRayDir.x - sinA * worldRayDir.z;
        double ldy = worldRayDir.y;
        double ldz = sinA * worldRayDir.x + cosA * worldRayDir.z;

        // ind axis of which slab is entered last for hit face
        double tEnter = Double.NEGATIVE_INFINITY;
        int hitAxis = 1; // default to top if something goes wrong
        boolean hitMin = true;

        double[] lo = {lox, loy, loz};
        double[] ld = {ldx, ldy, ldz};

        for(int axis = 0; axis < 3; axis++) {
            if(Math.abs(ld[axis]) < 1e-10) {
                continue;
            }
            double t1 = (-HALF - lo[axis]) / ld[axis];
            double t2 = (HALF - lo[axis]) / ld[axis];
            boolean minFirst = t1 < t2;
            double tNear = minFirst ? t1 : t2;
            if(tNear > tEnter) {
                tEnter = tNear;
                hitAxis = axis;
                hitMin = minFirst;
            }
        }

        // outward normal
        double[] localNormal = {0, 0, 0};
        localNormal[hitAxis] = hitMin ? -1.0 : 1.0;

        // back to world space
        double lnx = localNormal[0];
        double lnz = localNormal[2];
        double wnx = cosA * lnx + sinA * lnz;
        double wny = localNormal[1];
        double wnz = -sinA * lnx + cosA * lnz;

        // reflect
        double dot = worldVel.x * wnx + worldVel.y * wny + worldVel.z * wnz;
        this.setDeltaMovement(worldVel.x - 2 * dot * wnx, worldVel.y - 2 * dot * wny, worldVel.z - 2 * dot * wnz);
    }

    @Override
    protected void onHit(RayTraceResult result) {
        if(result.getType() == RayTraceResult.Type.BLOCK) {
            onHitBlock((BlockRayTraceResult) result);
        } else if(result.getType() == RayTraceResult.Type.ENTITY) {
            onHitEntity((EntityRayTraceResult) result);
        }
        // calling super would cause an explosion
    }

    @Override
    protected boolean shouldBurn() {
        return false;
    }

    /*
     * update associated dispenser when pellet removed for non-dispenser reason
     */
    private void notifyDispenserPelletGone() {
        if(notifiedDispenser) {
            return;
        }
        notifiedDispenser = true;

        if(this.level == null || this.level.isClientSide || dispenserPos == null) {
            return;
        }
        if(!(this.level instanceof net.minecraft.world.server.ServerWorld)) {
            return;
        }

        net.minecraft.world.server.ServerWorld serverLevel = (net.minecraft.world.server.ServerWorld) this.level;

        // resolve dispenser's world
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

    @Override
    public void addAdditionalSaveData(@Nonnull CompoundNBT compound) {
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
    public void readAdditionalSaveData(@Nonnull CompoundNBT compound) {
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

    // just in case
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

    @Override
    public IPacket<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}