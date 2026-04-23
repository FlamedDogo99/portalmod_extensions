package net.portalmod_extensions.common.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.StateContainer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.portalmod.common.blocks.QuadBlock;
import net.portalmod.common.sorted.antline.AntlineActivated;
import net.portalmod.common.sorted.button.QuadBlockCorner;
import net.portalmod_extensions.common.tileentities.EnergyPelletDispenserTileEntity;

import javax.annotation.Nullable;

/**
 * The Energy Pellet Dispenser.
 *
 * A 2×2 quad-block mountable on any face.  When activated by an antline signal
 * it spawns an EnergyPelletEntity.  When deactivated it kills the pellet and
 * clears any receiver that is holding that pellet.
 *
 * Implements {@link AntlineActivated} so that connected antlines (and
 * AntlineActivator blocks such as the super button) can drive it directly,
 * matching how portalmod's own test-element receivers work.
 *
 * The "horsed-on" face is the face the block is mounted on — the opposite of
 * FACING.  Antlines connect from any direction not on the FACING axis,
 * mirroring the convention used by SuperButtonBlock.
 */
public class EnergyPelletDispenserBlock extends QuadBlock implements AntlineActivated {

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
            return new EnergyPelletDispenserTileEntity();
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // AntlineConnector (required by AntlineActivated)
    // -------------------------------------------------------------------------

    /**
     * The surface this block is mounted on is opposite to FACING.
     * If we face UP we are sitting on the DOWN side — identical to how
     * SuperButtonBlock computes its horsed-on direction.
     */
    @Override
    public Direction getHorsedOn(BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    /**
     * Antlines connect from any direction whose axis differs from the FACING
     * axis — same rule as SuperButtonBlock.
     */
    @Override
    public boolean antlineConnectsInDirection(Direction direction, BlockState state) {
        return direction.getAxis() != state.getValue(FACING).getAxis();
    }

    // -------------------------------------------------------------------------
    // AntlineActivated — called by the antline system on activation changes
    // -------------------------------------------------------------------------

    /**
     * Delegates the power state to the main tile entity which handles
     * pellet spawning (rising edge) and killing (falling edge).
     */
    @Override
    public void onAntlineActivation(boolean active, BlockState state, World world, BlockPos pos) {
        if (world.isClientSide) return;

        BlockPos mainPos = getMainPosition(state, pos);
        TileEntity te = world.getBlockEntity(mainPos);
        if (te instanceof EnergyPelletDispenserTileEntity) {
            ((EnergyPelletDispenserTileEntity) te).onPowerChanged(active);
        }
    }

    // -------------------------------------------------------------------------
    // Neighbor / placement hooks — trigger antline re-evaluation
    // -------------------------------------------------------------------------

    @Override
    public void neighborChanged(BlockState state, World world, BlockPos pos,
                                Block block, BlockPos neighborPos, boolean isMoving) {
        if (world.isClientSide) return;
        this.updateAntlineActivation(state, world, pos);
    }

    @Override
    public void onPlace(BlockState state, World world, BlockPos pos,
                        BlockState oldState, boolean isMoving) {
        // Announce ourselves to surrounding antlines, then check their state.
        world.updateNeighborsAt(pos, this);
        this.updateAntlineActivation(state, world, pos);
    }

    // -------------------------------------------------------------------------
    // Destruction — kill the pellet when the block is broken
    // -------------------------------------------------------------------------

    @Override
    public void onRemove(BlockState state, World world, BlockPos pos,
                         BlockState newState, boolean isMoving) {
        if (!world.isClientSide && !state.is(newState.getBlock())) {
            BlockPos mainPos = getMainPosition(state, pos);
            TileEntity te = world.getBlockEntity(mainPos);
            if (te instanceof EnergyPelletDispenserTileEntity) {
                ((EnergyPelletDispenserTileEntity) te).killPelletAndClearReceivers();
            }
        }
        super.onRemove(state, world, pos, newState, isMoving);
    }
}
