package net.portalmod_extensions.common.renderer;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.block.BlockState;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.util.Direction;
import net.minecraft.util.math.vector.Vector3f;
import net.portalmod.common.sorted.button.QuadBlockCorner;
import net.portalmod_extensions.common.blocks.EnergyPelletReceiverBlock;
import net.portalmod_extensions.common.tileentities.EnergyPelletReceiverTileEntity;

import javax.annotation.Nonnull;

public class EnergyPelletReceiverTER extends TileEntityRenderer<EnergyPelletReceiverTileEntity> {

    public EnergyPelletReceiverTER(TileEntityRendererDispatcher dispatcher) {
        super(dispatcher);
    }

    @Override
    public void render(@Nonnull EnergyPelletReceiverTileEntity tileEntity, float partialTicks, @Nonnull MatrixStack matrixStack, @Nonnull IRenderTypeBuffer buffer, int combinedLight, int combinedOverlay) {
        float armClose = getArmClose(tileEntity, partialTicks);

        BlockState state = tileEntity.getBlockState();
        if(!(state.getBlock() instanceof EnergyPelletReceiverBlock)) {
            return;
        }

        Direction facing = state.getValue(EnergyPelletReceiverBlock.FACING);
        QuadBlockCorner corner = state.getValue(EnergyPelletReceiverBlock.CORNER);

        matrixStack.pushPose();

        applyBlockOrientation(matrixStack, facing, corner);

        matrixStack.popPose();
    }

    public static float getArmClose(EnergyPelletReceiverTileEntity tileEntity, float partialTicks) {
        if(tileEntity.animationProgress <= 0 && tileEntity.prevAnimationProgress <= 0) {
            return tileEntity.isHolding() ? 1f : 0f;
        }

        float interpolated = tileEntity.prevAnimationProgress + (tileEntity.animationProgress - tileEntity.prevAnimationProgress) * partialTicks;
        float progress = 1f - (interpolated / EnergyPelletReceiverTileEntity.ANIMATION_LENGTH);
        return Math.max(0f, Math.min(1f, progress));
    }

    private static void applyBlockOrientation(MatrixStack matrixStack, Direction facing, QuadBlockCorner corner) {
        matrixStack.translate(0.5, 0.5, 0.5);

        Direction.Axis axis = facing.getAxis();

        if(facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
            if(axis == Direction.Axis.Y) {
                matrixStack.mulPose(Vector3f.ZP.rotationDegrees(180));
            } else {
                matrixStack.mulPose(Vector3f.YP.rotationDegrees(180));
            }
        }

        if(axis == Direction.Axis.X) {
            matrixStack.mulPose(Vector3f.ZN.rotationDegrees(90));
        }
        if(axis == Direction.Axis.Z) {
            matrixStack.mulPose(Vector3f.XP.rotationDegrees(90));
        }

        matrixStack.mulPose(Vector3f.YP.rotationDegrees(corner.getRot() + (axis == Direction.Axis.X ? 0 : -90)));
    }

    @Override
    public boolean shouldRenderOffScreen(EnergyPelletReceiverTileEntity te) {
        return true;
    }
}
