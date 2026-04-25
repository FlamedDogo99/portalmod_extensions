package net.portalmod_extensions;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.portalmod_extensions.core.init.BlockInit;
import net.portalmod_extensions.core.init.EntityInit;
import net.portalmod_extensions.core.init.ItemInit;
import net.portalmod_extensions.core.init.SoundInit;
import net.portalmod_extensions.core.init.TileEntityTypeInit;
import net.portalmod_extensions.core.packet.PacketHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(PortalModExtensions.MODID)
public class PortalModExtensions {

    public static final Logger LOGGER = LogManager.getLogger();
    public static final String MODID = "portalmod_extensions";

    public PortalModExtensions() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();

        EntityInit.ENTITIES.register(bus);
        BlockInit.BLOCKS.register(bus);
        ItemInit.ITEMS.register(bus);
        TileEntityTypeInit.TILE_ENTITY_TYPES.register(bus);
        SoundInit.SOUNDS.register(bus);

        bus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        PacketHandler.init();
    }
}
