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

public class EnergyPelletDispenserBlock extends QuadBlock implements AntlineActivated {

    public EnergyPelletDispenserBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.UP).setValue(CORNER, QuadBlockCorner.UP_LEFT));
    }

    @Override
    protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(FACING, CORNER);
    }

    @Override
    public boolean hasTileEntity(BlockState state) {
        return state.getValue(CORNER) == QuadBlockCorner.UP_LEFT;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        if(state.getValue(CORNER) == QuadBlockCorner.UP_LEFT) {
            return new EnergyPelletDispenserTileEntity();
        }
        return null;
    }

    /*
     * The surface this block is mounted on is opposite to FACING.
     * If we face UP we are sitting on the DOWN side, same as SuperButtonBlock
     */
    @Override
    public Direction getHorsedOn(BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    @Override
    public boolean antlineConnectsInDirection(Direction direction, BlockState state) {
        return direction.getAxis() != state.getValue(FACING).getAxis();
    }

    // power state handled by tile entity
    @Override
    public void onAntlineActivation(boolean active, BlockState state, World world, BlockPos pos) {
        if(world.isClientSide) {
            return;
        }

        BlockPos mainPos = getMainPosition(state, pos);
        TileEntity te = world.getBlockEntity(mainPos);
        if(te instanceof EnergyPelletDispenserTileEntity) {
            ((EnergyPelletDispenserTileEntity) te).onPowerChanged(active);
        }
    }

    @Override
    public void neighborChanged(BlockState state, World world, BlockPos pos, Block block, BlockPos neighborPos, boolean isMoving) {
        if(world.isClientSide) {
            return;
        }
        if(isDirectlyPoweredByRedstone(state, world, pos)) {
            onAntlineActivation(true, state, world, pos);
        } else {
            this.updateAntlineActivation(state, world, pos);
        }
    }

    private boolean isDirectlyPoweredByRedstone(BlockState state, World world, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        Direction inward = facing.getOpposite(); // direction pointing into the base

        // Check each block in the multiblock footprint (including this one).
        for(BlockPos bp : getAllPositions(state, pos)) {
            if(!(world.getBlockState(bp).getBlock() instanceof EnergyPelletDispenserBlock)) {
                continue;
            }
            // "base block" is one step in the inward direction from each corner.
            if(world.hasSignal(bp.relative(inward), facing)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onPlace(BlockState state, World world, BlockPos pos, BlockState oldState, boolean isMoving) {
        // update antlines.
        world.updateNeighborsAt(pos, this);
        this.updateAntlineActivation(state, world, pos);
    }

    // kill pellet and clean up when broken, handled by upper left corner
    @Override
    public void onRemove(BlockState state, World world, BlockPos pos, BlockState newState, boolean isMoving) {
        if(!world.isClientSide && !state.is(newState.getBlock())) {
            // Only the main corner owns the tile entity — skip the other three.
            if(state.getValue(CORNER) == net.portalmod.common.sorted.button.QuadBlockCorner.UP_LEFT) {
                TileEntity te = world.getBlockEntity(pos);
                if(te instanceof EnergyPelletDispenserTileEntity) {
                    ((EnergyPelletDispenserTileEntity) te).killPelletAndClearReceivers();
                }
            }
        }
        super.onRemove(state, world, pos, newState, isMoving);
    }
}
