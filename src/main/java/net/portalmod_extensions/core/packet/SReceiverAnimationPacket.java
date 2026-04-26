package net.portalmod_extensions.core.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;
import net.portalmod_extensions.common.tileentities.EnergyPelletReceiverTileEntity;

import java.util.function.Supplier;

/**
 * Sent server to client whenever the receiver catches a pellet.
 * The client uses this to start the catch animation on the matching tile entity.
 */
public class SReceiverAnimationPacket {

    private final BlockPos pos;

    public SReceiverAnimationPacket(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(SReceiverAnimationPacket packet, PacketBuffer buf) {
        buf.writeBlockPos(packet.pos);
    }

    public static SReceiverAnimationPacket decode(PacketBuffer buf) {
        return new SReceiverAnimationPacket(buf.readBlockPos());
    }

    public static boolean handle(SReceiverAnimationPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient(packet)));
        ctx.get().setPacketHandled(true);
        return true;
    }

    private static void handleClient(SReceiverAnimationPacket packet) {
        if(Minecraft.getInstance().level == null) {
            return;
        }
        TileEntity tileEntity = Minecraft.getInstance().level.getBlockEntity(packet.pos);
        if(tileEntity instanceof EnergyPelletReceiverTileEntity) {
            ((EnergyPelletReceiverTileEntity) tileEntity).startCatchAnimation();
        }
    }
}
