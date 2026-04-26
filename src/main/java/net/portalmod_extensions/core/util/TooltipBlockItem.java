package net.portalmod_extensions.core.util;

import net.minecraft.block.Block;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Util;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

// A simple BlockItem subclass that adds a tooltip
public class TooltipBlockItem extends BlockItem {
    private final String tooltipKey;

    public TooltipBlockItem(Block block, Properties props, String tooltipKey) {
        super(block, props);
        this.tooltipKey = tooltipKey;
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nullable World world, @Nonnull List<ITextComponent> list, @Nonnull ITooltipFlag flag) {
        if(!Screen.hasControlDown()) {
            list.add(new TranslationTextComponent("tooltip.portalmod_extensions.hold_control", Util.getPlatform() == Util.OS.OSX ? "Command" : "Ctrl").setStyle(Style.EMPTY.withColor(TextFormatting.GRAY).withItalic(false)));
            return;
        }
        int i = 1;
        while(true) {
            String key = "tooltip.portalmod_extensions." + tooltipKey + "_" + i;
            if(!I18n.exists(key)) {
                break;
            }
            list.add(new TranslationTextComponent(key).setStyle(Style.EMPTY.withColor(TextFormatting.GRAY).withItalic(false)));
            i++;
        }
    }
}
