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
import net.portalmod_extensions.common.tileentities.EnergyPelletReceiverTileEntity;

import javax.annotation.Nullable;

/**
 * The Energy Pellet Receiver.
 *
 * A 2×2 quad-block mountable on any face.  When a pellet collides with it,
 * it transitions to a "holding" state and emits a level-15 redstone signal
 * through the 2×2 base blocks it is mounted on.
 *
 * The block itself stores the HOLDING boolean state in its blockstate so that
 * the redstone signal can be read without a tile entity lookup.
 */
public class EnergyPelletReceiverBlock extends QuadBlock {

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
    // Tile entity
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
    // Redstone output
    // -------------------------------------------------------------------------

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    /**
     * When holding, this block provides strong power (level 15) to the block
     * it is mounted on, in the direction away from the face the receiver is on.
     *
     * "Strong" power means it can power blocks even through a solid block,
     * which is the correct behaviour for a receiver acting like a button.
     */
    @Override
    public int getSignal(BlockState state, IBlockReader world, BlockPos pos, Direction direction) {
        if (!state.getValue(HOLDING)) return 0;
        // Emit signal toward the base (inward direction) = facing.getOpposite()
        Direction inward = state.getValue(FACING).getOpposite();
        return direction == inward ? 15 : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, IBlockReader world, BlockPos pos, Direction direction) {
        return getSignal(state, world, pos, direction);
    }

    // -------------------------------------------------------------------------
    // Destruction
    // -------------------------------------------------------------------------

    @Override
    public void onRemove(BlockState state, World world, BlockPos pos,
                          BlockState newState, boolean isMoving) {
        if (!world.isClientSide && !state.is(newState.getBlock())) {
            // Update neighbours so redstone reacts immediately.
            updateAllNeighbors(world, pos, state);
        }
        super.onRemove(state, world, pos, newState, isMoving);
    }
}
