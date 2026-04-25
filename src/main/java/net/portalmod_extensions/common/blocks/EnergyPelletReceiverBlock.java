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

public class EnergyPelletReceiverBlock extends QuadBlock implements AntlineActivator {
    // when holding pellet
    public static final BooleanProperty HOLDING = BooleanProperty.create("holding");

    public EnergyPelletReceiverBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.UP).setValue(CORNER, QuadBlockCorner.UP_LEFT).setValue(HOLDING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(FACING, CORNER, HOLDING);
    }

    @Override
    public boolean hasTileEntity(BlockState state) {
        return state.getValue(CORNER) == QuadBlockCorner.UP_LEFT;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        if(state.getValue(CORNER) == QuadBlockCorner.UP_LEFT) {
            return new EnergyPelletReceiverTileEntity();
        }
        return null;
    }

    @Override
    public boolean isAntlineActive(BlockState state) {
        return state.getValue(HOLDING);
    }

    @Override
    public Direction getHorsedOn(BlockState state) {
        return state.getValue(FACING).getOpposite();
    }


    @Override
    public boolean antlineConnectsInDirection(Direction direction, BlockState state) {
        return direction.getAxis() != state.getValue(FACING).getAxis();
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, IBlockReader world, BlockPos pos, Direction direction) {
        if(!state.getValue(HOLDING)) {
            return 0;
        }
        return direction == state.getValue(FACING) ? 15 : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, IBlockReader world, BlockPos pos, Direction direction) {
        return getSignal(state, world, pos, direction);
    }

    @Override
    public void onPlace(BlockState state, World world, BlockPos pos, BlockState oldState, boolean isMoving) {
        // updated surrounding antlines
        world.updateNeighborsAt(pos, this);
    }

    @Override
    public void onRemove(BlockState state, World world, BlockPos pos, BlockState newState, boolean isMoving) {
        if(!world.isClientSide && !state.is(newState.getBlock())) {
            // main corner handles tile entity
            if(state.getValue(CORNER) == net.portalmod.common.sorted.button.QuadBlockCorner.UP_LEFT) {
                net.minecraft.tileentity.TileEntity te = world.getBlockEntity(pos);
                if(te instanceof net.portalmod_extensions.common.tileentities.EnergyPelletReceiverTileEntity) {
                    // update tracked receivers for dispenser
                    ((net.portalmod_extensions.common.tileentities.EnergyPelletReceiverTileEntity) te).notifyDispenserOfRemoval();
                }
            }
            if(state.getValue(HOLDING)) {
                for(BlockPos p : getAllPositions(state, pos)) {
                    world.updateNeighborsAt(p, this);
                }
            } else {
                updateAllNeighbors(world, pos, state);
            }
        }
        super.onRemove(state, world, pos, newState, isMoving);
    }
}