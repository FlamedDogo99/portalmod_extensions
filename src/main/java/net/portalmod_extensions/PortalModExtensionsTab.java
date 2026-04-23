package net.portalmod_extensions;

import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Creative-mode tab for PortalMod Extensions.
 * Icon: minecraft:dirt (placeholder until proper assets exist).
 */
public class PortalModExtensionsTab extends ItemGroup {

    public static final ItemGroup INSTANCE = new PortalModExtensionsTab();

    private PortalModExtensionsTab() {
        super(PortalModExtensions.MODID);
    }

    @Override
    public ItemStack makeIcon() {
        return new ItemStack(Items.DIRT);
    } // TODO: find an artist
}
