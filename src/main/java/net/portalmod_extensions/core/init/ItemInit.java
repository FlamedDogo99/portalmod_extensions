package net.portalmod_extensions.core.init;

import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.portalmod_extensions.PortalModExtensions;
import net.portalmod_extensions.PortalModExtensionsTab;

public class ItemInit {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, PortalModExtensions.MODID);

    public static final RegistryObject<Item> ENERGY_PELLET_DISPENSER =
            ITEMS.register("energy_pellet_dispenser",
                    () -> new BlockItem(BlockInit.ENERGY_PELLET_DISPENSER.get(), properties()));

    public static final RegistryObject<Item> ENERGY_PELLET_RECEIVER =
            ITEMS.register("energy_pellet_receiver",
                    () -> new BlockItem(BlockInit.ENERGY_PELLET_RECEIVER.get(), properties()));

    // -------------------------------------------------------------------------

    private static Item.Properties properties() {
        return new Item.Properties().tab(PortalModExtensionsTab.INSTANCE);
    }
}
