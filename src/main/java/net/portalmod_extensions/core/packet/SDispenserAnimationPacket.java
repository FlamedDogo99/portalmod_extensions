package net.portalmod_extensions.core.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;
import net.portalmod_extensions.common.tileentities.EnergyPelletDispenserTileEntity;

import java.util.function.Supplier;

/**
 * Sent server to client whenever the dispenser shoots a pellet.
 * The client uses this to start the shoot animation on the matching tile entity.
 */
public class SDispenserAnimationPacket {

    private final BlockPos pos;

    public SDispenserAnimationPacket(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(SDispenserAnimationPacket packet, PacketBuffer buf) {
        buf.writeBlockPos(packet.pos);
    }

    public static SDispenserAnimationPacket decode(PacketBuffer buf) {
        return new SDispenserAnimationPacket(buf.readBlockPos());
    }

    public static boolean handle(SDispenserAnimationPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(packet)));
        ctx.get().setPacketHandled(true);
        return true;
    }

    private static void handleClient(SDispenserAnimationPacket packet) {
        if(Minecraft.getInstance().level == null) {
            return;
        }
        TileEntity tileEntity = Minecraft.getInstance().level.getBlockEntity(packet.pos);
        if(tileEntity instanceof EnergyPelletDispenserTileEntity) {
            ((EnergyPelletDispenserTileEntity) tileEntity).startShootAnimation();
        }
    }
}
