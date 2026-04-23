package net.portalmod_extensions.common.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.PushReaction;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.DirectionProperty;
import net.minecraft.state.EnumProperty;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.*;

/**
 * A 2×2 multi-block that can be oriented on any of the three axes.
 * Placement logic is taken directly from portalmod's QuadBlock + MultiBlock.
 *
 * The "facing" direction is the outward normal of the face the block is
 * mounted on (i.e. the direction the block shoots / receives towards).
 */
public abstract class QuadBlock extends Block {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final EnumProperty<QuadBlockCorner> CORNER =
            EnumProperty.create("corner", QuadBlockCorner.class);

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    protected QuadBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.UP)
                .setValue(CORNER, QuadBlockCorner.UP_LEFT));
    }

    // -------------------------------------------------------------------------
    // Multi-block geometry helpers (mirrors portalmod QuadBlock)
    // -------------------------------------------------------------------------

    /**
     * Returns the "main" (UP_LEFT corner) position of the multi-block given any
     * member position and its state.
     */
    public BlockPos getMainPosition(BlockState state, BlockPos pos) {
        QuadBlockCorner corner = state.getValue(CORNER);
        Direction facing = state.getValue(FACING);

        Direction horizontal = facing == Direction.UP   ? Direction.WEST
                             : facing == Direction.DOWN ? Direction.EAST
                             : facing.getClockWise();
        Direction vertical   = facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : Direction.UP;

        if (!corner.isLeft()) pos = pos.relative(horizontal);
        if (!corner.isUp())   pos = pos.relative(vertical);
        return pos;
    }

    /** Returns the three secondary positions given the main position and state. */
    public List<BlockPos> getConnectedPositions(BlockState mainState, BlockPos mainPos) {
        Direction facing = mainState.getValue(FACING);
        Direction horizontal = facing == Direction.UP   ? Direction.EAST
                             : facing == Direction.DOWN ? Direction.WEST
                             : facing.getCounterClockWise();
        Direction vertical   = facing.getAxis() == Direction.Axis.Y ? Direction.SOUTH : Direction.DOWN;

        return new ArrayList<>(Arrays.asList(
                mainPos.relative(horizontal),
                mainPos.relative(horizontal).relative(vertical),
                mainPos.relative(vertical)));
    }

    /** Returns all four positions (including the main one) of the multi-block. */
    public List<BlockPos> getAllPositions(BlockState state, BlockPos pos) {
        BlockPos mainPos = getMainPosition(state, pos);
        List<BlockPos> list = getConnectedPositions(state, mainPos);
        list.add(mainPos);
        return list;
    }

    /** Returns a map of extra positions → their block-states. */
    public Map<BlockPos, BlockState> getOtherParts(BlockState state, BlockPos pos) {
        QuadBlockCorner base = state.getValue(CORNER);
        Direction facing = state.getValue(FACING);
        Map<BlockPos, BlockState> map = new HashMap<>();
        for (QuadBlockCorner corner : QuadBlockCorner.values()) {
            if (corner == base) continue;
            map.put(getOtherBlock(pos, base, corner, facing), state.setValue(CORNER, corner));
        }
        return map;
    }

    public BlockPos getOtherBlock(BlockPos pos, QuadBlockCorner base,
                                  QuadBlockCorner corner, Direction facing) {
        Tuple<Direction, Direction> dirs = placementDirectionsFromFacing(facing.getAxis());
        Direction a = dirs.getA();
        Direction b = dirs.getB();
        int dx = corner.getX() - base.getX();
        int dy = corner.getY() - base.getY();
        if (facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE) dx *= -1;
        BlockPos p = new BlockPos(pos);
        if (dx != 0) p = p.relative(dx < 0 ? a.getOpposite() : a);
        if (dy != 0) p = p.relative(dy < 0 ? b.getOpposite() : b);
        return p;
    }

    public Tuple<Direction, Direction> placementDirectionsFromFacing(Direction.Axis axis) {
        if (axis == Direction.Axis.X) return new Tuple<>(Direction.NORTH, Direction.UP);
        if (axis == Direction.Axis.Z) return new Tuple<>(Direction.EAST,  Direction.UP);
        return new Tuple<>(Direction.EAST, Direction.NORTH);
    }

    /** Returns all four block positions for a given clicked-pos + corner + facing. */
    public List<BlockPos> getAllBlocks(BlockPos pos, QuadBlockCorner base, Direction facing) {
        List<BlockPos> list = new ArrayList<>();
        for (QuadBlockCorner c : QuadBlockCorner.values())
            list.add(getOtherBlock(pos, base, c, facing));
        return list;
    }

    // -------------------------------------------------------------------------
    // Utility: set a property on all four member blocks at once
    // -------------------------------------------------------------------------

    public <T extends Comparable<T>> void setBlockStateValue(
            net.minecraft.state.Property<T> property, T value,
            BlockState state, World world, BlockPos pos) {
        for (BlockPos p : getAllPositions(state, pos)) {
            BlockState s = world.getBlockState(p);
            if (s.getBlock().is(this)) {
                world.setBlock(p, s.setValue(property, value), 2);
            }
        }
    }

    /** Notifies all neighbours of every member block (excluding members themselves). */
    public void updateAllNeighbors(World world, BlockPos pos, BlockState state) {
        if (world.isClientSide) return;
        List<BlockPos> positions = getAllPositions(state, pos);
        for (BlockPos p : positions) {
            for (Direction dir : Direction.values()) {
                BlockPos nb = p.relative(dir);
                if (!positions.contains(nb)) world.blockUpdated(nb, this);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Placement
    // -------------------------------------------------------------------------

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        Direction clickedFace = context.getClickedFace();
        Direction.Axis axis = clickedFace.getAxis();
        boolean positive = clickedFace.getAxisDirection() == Direction.AxisDirection.POSITIVE;

        Direction upDir   = axis == Direction.Axis.Y ? Direction.NORTH : Direction.UP;
        Direction leftDir = axis == Direction.Axis.Y
                ? (positive ? Direction.WEST : Direction.EAST)
                : clickedFace.getClockWise();

        boolean prefersUp   = clickedOnPositiveHalf(context, upDir.getOpposite());
        boolean prefersLeft = clickedOnPositiveHalf(context, leftDir.getOpposite());

        boolean[] flipUp   = {false, false, true,  true};
        boolean[] flipLeft = {false, true,  false, true};

        for (int i = 0; i < 4; i++) {
            QuadBlockCorner corner = QuadBlockCorner.getCorner(
                    prefersUp ^ flipUp[i], prefersLeft ^ flipLeft[i]);
            if (isCornerPlaceable(context, corner)) {
                return this.defaultBlockState()
                        .setValue(FACING, clickedFace)
                        .setValue(CORNER, corner);
            }
        }
        return null;
    }

    /**
     * A corner is placeable when all four blocks it would occupy are replaceable
     * AND the four blocks one step in the opposite-facing direction are all solid
     * (the 2×2 base must be collidable).
     */
    private boolean isCornerPlaceable(BlockItemUseContext context, QuadBlockCorner corner) {
        Direction facing = context.getClickedFace();
        List<BlockPos> footprint = getAllBlocks(context.getClickedPos(), corner, facing);

        // All four positions must be replaceable (air / replaceable blocks)
        boolean allReplaceable = footprint.stream().allMatch(p -> canPlaceAt(context, p));
        if (!allReplaceable) return false;

        // The 2×2 base (blocks behind the facing direction) must all be solid
        Direction inward = facing.getOpposite();
        return footprint.stream().allMatch(p -> {
            BlockState base = context.getLevel().getBlockState(p.relative(inward));
            return base.isFaceSturdy(context.getLevel(), p.relative(inward), facing);
        });
    }

    private static boolean canPlaceAt(BlockItemUseContext context, BlockPos pos) {
        return context.getLevel().getBlockState(pos).canBeReplaced(context)
                && pos.getY() < context.getLevel().getMaxBuildHeight()
                && pos.getY() >= 0;
    }

    // -------------------------------------------------------------------------
    // Survival / neighbour update
    // -------------------------------------------------------------------------

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                   IWorld world, BlockPos pos, BlockPos neighborPos) {
        if (getAllPositions(state, pos).contains(neighborPos)) {
            BlockState expected = getOtherParts(state, pos).get(neighborPos);
            if (neighborState.is(this) && expected != null
                    && neighborState.getValue(FACING) == expected.getValue(FACING)
                    && neighborState.getValue(CORNER)  == expected.getValue(CORNER)) {
                return state;
            }
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    // -------------------------------------------------------------------------
    // Place / destroy
    // -------------------------------------------------------------------------

    @Override
    public void setPlacedBy(World world, BlockPos pos, BlockState state,
                             @Nullable net.minecraft.entity.LivingEntity placer,
                             ItemStack stack) {
        getOtherParts(state, pos).forEach(world::setBlockAndUpdate);
    }

    @Override
    public void playerWillDestroy(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClientSide) {
            if (player.isCreative()) {
                preventCreativeDropFromMainPart(world, pos, state, player);
            } else {
                dropResources(state, world, pos, null, player, player.getMainHandItem());
            }
        }
        super.playerWillDestroy(world, pos, state, player);
    }

    @Override
    public void playerDestroy(World world, PlayerEntity player, BlockPos pos,
                               BlockState state, @Nullable TileEntity te, ItemStack stack) {
        super.playerDestroy(world, player, pos, Blocks.AIR.defaultBlockState(), te, stack);
    }

    private void preventCreativeDropFromMainPart(World world, BlockPos pos,
                                                   BlockState state, PlayerEntity player) {
        BlockPos mainPos = getMainPosition(state, pos);
        if (!pos.equals(mainPos)) {
            BlockState mainState = world.getBlockState(mainPos);
            if (mainState.getBlock().is(this)) {
                world.setBlock(mainPos, Blocks.AIR.defaultBlockState(), 35);
                world.levelEvent(player, 2001, mainPos, Block.getId(mainState));
            }
        }
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }

    // -------------------------------------------------------------------------
    // Rotation / mirror
    // -------------------------------------------------------------------------

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        Direction facing = state.getValue(FACING);
        if (facing.getAxis() == Direction.Axis.Y) {
            int times = getRotationAmount(rotation);
            if (facing == Direction.DOWN) times = 4 - times;
            return state.setValue(CORNER, state.getValue(CORNER).rotate(times));
        }
        return state.setValue(FACING, rotation.rotate(facing));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        Direction facing = state.getValue(FACING);
        if (facing.getAxis() == Direction.Axis.Y) {
            if (mirror == Mirror.FRONT_BACK) return state.setValue(CORNER, state.getValue(CORNER).mirrorLeftRight());
            if (mirror == Mirror.LEFT_RIGHT)  return state.setValue(CORNER, state.getValue(CORNER).mirrorUpDown());
            return state;
        }
        if (mirror == Mirror.NONE) return state;
        BlockState flipped = state.setValue(CORNER, state.getValue(CORNER).mirrorLeftRight());
        if (mirror.mirror(facing) == facing) return flipped;
        return flipped.setValue(FACING, facing.getOpposite());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean clickedOnPositiveHalf(BlockItemUseContext ctx, Direction dir) {
        boolean isPos = dir.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        return clickedOnPositiveHalfAxis(ctx, dir.getAxis()) == isPos;
    }

    private static boolean clickedOnPositiveHalfAxis(BlockItemUseContext ctx, Direction.Axis axis) {
        BlockPos pos = ctx.getClickedPos();
        if (axis == Direction.Axis.X) return ctx.getClickLocation().x - pos.getX() > 0.5;
        if (axis == Direction.Axis.Y) return ctx.getClickLocation().y - pos.getY() > 0.5;
        return ctx.getClickLocation().z - pos.getZ() > 0.5;
    }

    private static int getRotationAmount(Rotation rotation) {
        switch (rotation) {
            case CLOCKWISE_90:       return 1;
            case CLOCKWISE_180:      return 2;
            case COUNTERCLOCKWISE_90:return 3;
            default:                 return 0;
        }
    }
}
