package net.portalmod_extensions.common.renderer;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.model.RenderMaterial;
import net.minecraft.client.renderer.texture.AtlasTexture;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.vector.Matrix3f;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3f;
import net.portalmod.common.sorted.button.QuadBlockCorner;
import net.portalmod_extensions.common.blocks.EnergyPelletDispenserBlock;
import net.portalmod_extensions.common.tileentities.EnergyPelletDispenserTileEntity;

import javax.annotation.Nonnull;

public class EnergyPelletDispenserTER extends TileEntityRenderer<EnergyPelletDispenserTileEntity> {
    public static final ResourceLocation TEXTURE_TOP = new ResourceLocation("portalmod_extensions", "block/energy_pellet_dispenser_top");
    public static RenderMaterial MATERIAL_LID;

    public EnergyPelletDispenserTER(TileEntityRendererDispatcher dispatcher) {
        super(dispatcher);
        MATERIAL_LID = new RenderMaterial(AtlasTexture.LOCATION_BLOCKS, TEXTURE_TOP);
    }

    public Tuple<Direction, Direction> placementDirectionsFromFacing(Direction.Axis axis) {
        if(axis == Direction.Axis.X) {
            return new Tuple<>(Direction.NORTH, Direction.UP);
        }
        if(axis == Direction.Axis.Z) {
            return new Tuple<>(Direction.EAST, Direction.UP);
        }
        return new Tuple<>(Direction.EAST, Direction.NORTH);
    }

    @Override
    public void render(@Nonnull EnergyPelletDispenserTileEntity tileEntity, float partialTicks, @Nonnull MatrixStack matrixStack, @Nonnull IRenderTypeBuffer buffer, int combinedLight, int combinedOverlay) {

        float armOffset = getArmOffset(tileEntity, partialTicks);

        BlockState state = tileEntity.getBlockState();
        if(!(state.getBlock() instanceof EnergyPelletDispenserBlock)) {
            return;
        }

        Direction facing = state.getValue(EnergyPelletDispenserBlock.FACING);

        for(QuadBlockCorner corner : QuadBlockCorner.values()) {
            matrixStack.pushPose();

            Tuple<Direction, Direction> dirs = placementDirectionsFromFacing(facing.getAxis());
            Direction a = dirs.getA();
            Direction b = dirs.getB();
            int dx = corner.getX() - QuadBlockCorner.UP_LEFT.getX();
            int dy = corner.getY() - QuadBlockCorner.UP_LEFT.getY();
            if(facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
                dx *= -1;
            }
            if(dx != 0) {
                matrixStack.translate(a.getStepX() * dx, a.getStepY() * dx, a.getStepZ() * dx);
            }
            if(dy != 0) {
                matrixStack.translate((dy < 0 ? b.getOpposite() : b).getStepX(), (dy < 0 ? b.getOpposite() : b).getStepY(), (dy < 0 ? b.getOpposite() : b).getStepZ());
            }

            applyBlockOrientation(matrixStack, facing, corner);
            renderLid(matrixStack, buffer, combinedLight, combinedOverlay, armOffset);
            matrixStack.popPose();
        }
    }

    private void renderLid(MatrixStack matrixStack, IRenderTypeBuffer buffer, int combinedLight, int combinedOverlay, float armOffset) {

        float px = 8f / 16f - 0.5f;
        float py = 1f / 16f - 0.5f;
        float pz = 8f / 16f - 0.5f;

        matrixStack.pushPose();

        matrixStack.translate(px, py, pz);

        Vector3f axis = new Vector3f(1, 0, -1);
        axis.normalize();
        matrixStack.mulPose(axis.rotationDegrees(-90f * armOffset));

        matrixStack.translate(-px, -py, -pz);

        IVertexBuilder vb = MATERIAL_LID.buffer(buffer, RenderType::entityCutoutNoCull);
        renderLidFaces(matrixStack, vb, combinedLight, combinedOverlay);

        matrixStack.popPose();
    }

    private void renderLidFaces(MatrixStack matrixStack, IVertexBuilder vb, int light, int overlay) {
        float x0 = 7f / 16f - 0.5f;
        float x1 = 1.0f - 0.5f;
        float y0 = 2f / 16f - 0.5f;
        float y1 = 6f / 16f - 0.5f;
        float z0 = 7f / 16f - 0.5f;
        float z1 = 1.0f - 0.5f;

        Matrix4f m = matrixStack.last().pose();
        Matrix3f n = matrixStack.last().normal();

        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = MATERIAL_LID.sprite();

        // north face
        vb.vertex(m, x0, y1, z0).color(1f, 1f, 1f, 1f).uv(sprite.getU(0f), sprite.getV(9f)).overlayCoords(overlay).uv2(light).normal(n, 0, 0, -1).endVertex();
        vb.vertex(m, x0, y0, z0).color(1f, 1f, 1f, 1f).uv(sprite.getU(0f), sprite.getV(13f)).overlayCoords(overlay).uv2(light).normal(n, 0, 0, -1).endVertex();
        vb.vertex(m, x1, y0, z0).color(1f, 1f, 1f, 1f).uv(sprite.getU(9f), sprite.getV(13f)).overlayCoords(overlay).uv2(light).normal(n, 0, 0, -1).endVertex();
        vb.vertex(m, x1, y1, z0).color(1f, 1f, 1f, 1f).uv(sprite.getU(9f), sprite.getV(9f)).overlayCoords(overlay).uv2(light).normal(n, 0, 0, -1).endVertex();

        // west face
        vb.vertex(m, x0, y1, z1).color(1f, 1f, 1f, 1f).uv(sprite.getU(9f), sprite.getV(0f)).overlayCoords(overlay).uv2(light).normal(n, -1, 0, 0).endVertex();
        vb.vertex(m, x0, y0, z1).color(1f, 1f, 1f, 1f).uv(sprite.getU(9f), sprite.getV(9f)).overlayCoords(overlay).uv2(light).normal(n, -1, 0, 0).endVertex();
        vb.vertex(m, x0, y0, z0).color(1f, 1f, 1f, 1f).uv(sprite.getU(13f), sprite.getV(9f)).overlayCoords(overlay).uv2(light).normal(n, -1, 0, 0).endVertex();
        vb.vertex(m, x0, y1, z0).color(1f, 1f, 1f, 1f).uv(sprite.getU(13f), sprite.getV(0f)).overlayCoords(overlay).uv2(light).normal(n, -1, 0, 0).endVertex();

        // up face
        vb.vertex(m, x0, y1, z0).color(1f, 1f, 1f, 1f).uv(sprite.getU(0f), sprite.getV(0f)).overlayCoords(overlay).uv2(light).normal(n, 0, 1, 0).endVertex();
        vb.vertex(m, x0, y1, z1).color(1f, 1f, 1f, 1f).uv(sprite.getU(0f), sprite.getV(9f)).overlayCoords(overlay).uv2(light).normal(n, 0, 1, 0).endVertex();
        vb.vertex(m, x1, y1, z1).color(1f, 1f, 1f, 1f).uv(sprite.getU(9f), sprite.getV(9f)).overlayCoords(overlay).uv2(light).normal(n, 0, 1, 0).endVertex();
        vb.vertex(m, x1, y1, z0).color(1f, 1f, 1f, 1f).uv(sprite.getU(9f), sprite.getV(0f)).overlayCoords(overlay).uv2(light).normal(n, 0, 1, 0).endVertex();
    }

    public static float getArmOffset(EnergyPelletDispenserTileEntity te, float partialTicks) {
        float interpolated = te.prevAnimationProgress + (te.animationProgress - te.prevAnimationProgress) * partialTicks;
        float half = EnergyPelletDispenserTileEntity.ANIMATION_LENGTH / 2f;
        if(interpolated <= 0f) {
            return 0f;
        }
        if(interpolated >= half) {
            return (EnergyPelletDispenserTileEntity.ANIMATION_LENGTH - interpolated) / half;
        } else {
            return interpolated / half;
        }
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
    public boolean shouldRenderOffScreen(@Nonnull EnergyPelletDispenserTileEntity tileEntity) {
        return true;
    }
}