package net.portalmod_extensions;

import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.portalmod_extensions.core.init.ItemInit;

public class PortalModExtensionsTab extends ItemGroup {

    public static final ItemGroup INSTANCE = new PortalModExtensionsTab();

    private PortalModExtensionsTab() {
        super(PortalModExtensions.MODID);
    }

    @Override
    public ItemStack makeIcon() {
        return new ItemStack(ItemInit.ENERGY_PELLET_DISPENSER.get());
    }
}
