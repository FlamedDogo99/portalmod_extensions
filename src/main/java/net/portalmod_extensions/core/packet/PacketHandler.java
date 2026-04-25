package net.portalmod_extensions.core.packet;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import net.portalmod_extensions.PortalModExtensions;

public class PacketHandler {

    private static int id = 0;
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(PortalModExtensions.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void init() {
        CHANNEL.messageBuilder(SDispenserAnimationPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SDispenserAnimationPacket::encode)
                .decoder(SDispenserAnimationPacket::decode)
                .consumer(SDispenserAnimationPacket::handle)
                .add();

        CHANNEL.messageBuilder(SReceiverAnimationPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SReceiverAnimationPacket::encode)
                .decoder(SReceiverAnimationPacket::decode)
                .consumer(SReceiverAnimationPacket::handle)
                .add();
    }

    /** Send a packet to all players tracking the chunk that contains {@code pos}. */
    public static void sendToTrackingChunk(Object packet, BlockPos pos, ServerWorld world) {
        CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> world.getChunkAt(pos)), packet);
    }
}
