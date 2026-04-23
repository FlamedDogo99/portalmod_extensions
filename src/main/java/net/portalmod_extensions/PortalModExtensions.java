package net.portalmod_extensions;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.portalmod_extensions.core.init.BlockInit;
import net.portalmod_extensions.core.init.EntityInit;
import net.portalmod_extensions.core.init.TileEntityTypeInit;
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
        TileEntityTypeInit.TILE_ENTITY_TYPES.register(bus);

        MinecraftForge.EVENT_BUS.register(this);
    }
}
