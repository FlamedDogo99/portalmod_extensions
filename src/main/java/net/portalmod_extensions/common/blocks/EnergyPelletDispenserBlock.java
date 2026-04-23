package net.portalmod_extensions.common.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.StateContainer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.portalmod_extensions.common.tileentities.EnergyPelletDispenserTileEntity;

import javax.annotation.Nullable;

/**
 * The Energy Pellet Dispenser.
 *
 * A 2×2 quad-block mountable on any face.  When receiving a redstone signal
 * (via any of the 2×2 base blocks) it spawns an EnergyPelletEntity.
 * When the signal is lost it kills the associated pellet and clears any
 * receiver that is holding that pellet.
 *
 * Redstone logic mirrors how a redstone_lamp decides it is powered:
 * the block checks whether ANY of the blocks it is placed on receives a
 * signal coming from the direction of the dispenser face.  Both hard (direct)
 * and soft (indirect) power are considered, matching Minecraft's
 * World#hasNeighborSignal / World#hasSignal semantics.
 *
 * See SuperButtonBlock#neighborChanged in portalmod for the exact equivalent.
 */
public class EnergyPelletDispenserBlock extends QuadBlock {

    public EnergyPelletDispenserBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.UP)
                .setValue(CORNER, QuadBlockCorner.UP_LEFT));
    }

    // -------------------------------------------------------------------------
    // Block-state definition
    // -------------------------------------------------------------------------

    @Override
    protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(FACING, CORNER);
    }

    // -------------------------------------------------------------------------
    // Tile entity
    // -------------------------------------------------------------------------

    @Override
    public boolean hasTileEntity(BlockState state) {
        // Only the UP_LEFT (main) corner owns the tile entity.
        return state.getValue(CORNER) == QuadBlockCorner.UP_LEFT;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        if (state.getValue(CORNER) == QuadBlockCorner.UP_LEFT) {
            return new EnergyPelletDispenserTileEntity();
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Redstone
    // -------------------------------------------------------------------------

    /**
     * Called whenever a neighbouring block changes.  We check whether the
     * 2×2 base is powered and notify the tile entity on the main corner.
     */
    @Override
    public void neighborChanged(BlockState state, World world, BlockPos pos,
                                 Block block, BlockPos neighborPos, boolean isMoving) {
        if (world.isClientSide) return;

        boolean nowPowered = isReceivingPower(state, world, pos);

        // Find the main tile entity and forward the power-change event.
        BlockPos mainPos = getMainPosition(state, pos);
        TileEntity te = world.getBlockEntity(mainPos);
        if (te instanceof EnergyPelletDispenserTileEntity) {
            ((EnergyPelletDispenserTileEntity) te).onRedstoneChanged(nowPowered);
        }
    }

    /**
     * Returns true if any of the four 2×2 base blocks is receiving a redstone
     * signal directed toward the dispenser face.
     *
     * This mirrors how the redstone_lamp (and SuperButtonBlock in portalmod) work:
     *   World#hasSignal(pos, direction) checks for BOTH strong and weak signal
     *   coming from the given direction into the given position.
     */
    public boolean isReceivingPower(BlockState state, World world, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        Direction inward = facing.getOpposite(); // direction toward the base blocks

        return getAllPositions(state, pos).stream().anyMatch(memberPos -> {
            // The base block is one step inward from each member.
            BlockPos basePos = memberPos.relative(inward);
            // hasSignal checks whether basePos is receiving a signal from the
            // direction that points back toward the dispenser (i.e. 'facing').
            return world.hasSignal(basePos, facing);
        });
    }

    // -------------------------------------------------------------------------
    // Destruction — clean up the tile entity on all corners
    // -------------------------------------------------------------------------

    @Override
    public void onRemove(BlockState state, World world, BlockPos pos,
                          BlockState newState, boolean isMoving) {
        if (!world.isClientSide && !state.is(newState.getBlock())) {
            // If the main corner is being removed, kill the pellet first.
            BlockPos mainPos = getMainPosition(state, pos);
            TileEntity te = world.getBlockEntity(mainPos);
            if (te instanceof EnergyPelletDispenserTileEntity) {
                ((EnergyPelletDispenserTileEntity) te).killPelletAndClearReceivers();
            }
        }
        super.onRemove(state, world, pos, newState, isMoving);
    }
}
