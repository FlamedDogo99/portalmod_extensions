package net.portalmod_extensions.core.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.SpriteRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.portalmod_extensions.PortalModExtensions;
import net.portalmod_extensions.core.init.EntityInit;

@EventBusSubscriber(modid = PortalModExtensions.MODID, bus = Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void clientSetup(final FMLClientSetupEvent event) {
        // Register a sprite renderer for the energy pellet.
        // SpriteRenderer is the vanilla renderer used for 2D-sprite projectiles
        // (snowballs, fireballs, etc.).  It renders a flat sprite billboard using
        // the entity type's registered item texture.  Until the pellet has its own
        // dedicated model, this borrows the vanilla item-renderer sprite pipeline
        // so the entity is visible in-world and the "No renderer" crash is gone.
        RenderingRegistry.registerEntityRenderingHandler(
                EntityInit.ENERGY_PELLET.get(),
                manager -> new SpriteRenderer<>(manager, Minecraft.getInstance().getItemRenderer())
        );
    }
}
