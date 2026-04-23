package net.portalmod_extensions.common.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.portalmod.common.blocks.QuadBlock;
import net.portalmod.common.sorted.antline.AntlineActivator;
import net.portalmod.common.sorted.button.QuadBlockCorner;
import net.portalmod_extensions.common.tileentities.EnergyPelletReceiverTileEntity;

import javax.annotation.Nullable;

/**
 * The Energy Pellet Receiver.
 *
 * A 2×2 quad-block mountable on any face.  When a pellet collides with it,
 * it transitions to a "holding" state and:
 *   1. Emits a level-15 redstone signal through the 2×2 base blocks (classic
 *      redstone, so vanilla contraptions keep working), and
 *   2. Activates any connected antlines via {@link AntlineActivator}, exactly
 *      as the super button powers antlines when pressed.
 *
 * The "horsed-on" face is opposite to FACING.  Antlines connect from any
 * direction whose axis differs from the FACING axis — same as SuperButtonBlock.
 */
public class EnergyPelletReceiverBlock extends QuadBlock implements AntlineActivator {

    /** True when this receiver is holding an energy pellet. */
    public static final BooleanProperty HOLDING = BooleanProperty.create("holding");

    public EnergyPelletReceiverBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING,  Direction.UP)
                .setValue(CORNER,  QuadBlockCorner.UP_LEFT)
                .setValue(HOLDING, false));
    }

    // -------------------------------------------------------------------------
    // Block-state definition
    // -------------------------------------------------------------------------

    @Override
    protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(FACING, CORNER, HOLDING);
    }

    // -------------------------------------------------------------------------
    // Tile entity — only the main (UP_LEFT) corner owns a TE
    // -------------------------------------------------------------------------

    @Override
    public boolean hasTileEntity(BlockState state) {
        return state.getValue(CORNER) == QuadBlockCorner.UP_LEFT;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        if (state.getValue(CORNER) == QuadBlockCorner.UP_LEFT) {
            return new EnergyPelletReceiverTileEntity();
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // AntlineActivator — drives connected antlines
    // -------------------------------------------------------------------------

    /**
     * The antline system queries this every time a neighbour changes.
     * Returns true when the receiver is holding a pellet, causing connected
     * antlines to light up exactly as a super button does when pressed.
     */
    @Override
    public boolean isAntlineActive(BlockState state) {
        return state.getValue(HOLDING);
    }

    // -------------------------------------------------------------------------
    // AntlineConnector (required by AntlineActivator)
    // -------------------------------------------------------------------------

    /** The surface this block is mounted on — opposite to FACING. */
    @Override
    public Direction getHorsedOn(BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    /** Antlines connect from directions not on the FACING axis. */
    @Override
    public boolean antlineConnectsInDirection(Direction direction, BlockState state) {
        return direction.getAxis() != state.getValue(FACING).getAxis();
    }

    // -------------------------------------------------------------------------
    // Redstone output (vanilla — keeps compatibility with non-antline builds)
    // -------------------------------------------------------------------------

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    /**
     * Provides strong power (level 15) into the block the receiver is mounted
     * on when holding a pellet — matching how a super button powers its base.
     */
    @Override
    public int getSignal(BlockState state, IBlockReader world, BlockPos pos, Direction direction) {
        if (!state.getValue(HOLDING)) return 0;
        Direction inward = state.getValue(FACING).getOpposite();
        return direction == inward ? 15 : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, IBlockReader world, BlockPos pos, Direction direction) {
        return getSignal(state, world, pos, direction);
    }

    // -------------------------------------------------------------------------
    // Placement hook — announce ourselves to adjacent antlines
    // -------------------------------------------------------------------------

    @Override
    public void onPlace(BlockState state, World world, BlockPos pos,
                        BlockState oldState, boolean isMoving) {
        // Tell surrounding antlines that a new connectable block has appeared.
        world.updateNeighborsAt(pos, this);
    }

    // -------------------------------------------------------------------------
    // Destruction
    // -------------------------------------------------------------------------

    @Override
    public void onRemove(BlockState state, World world, BlockPos pos,
                         BlockState newState, boolean isMoving) {
        if (!world.isClientSide && !state.is(newState.getBlock())) {
            // Propagate neighbour updates so antlines and redstone react.
            updateAllNeighbors(world, pos, state);
        }
        super.onRemove(state, world, pos, newState, isMoving);
    }
}
