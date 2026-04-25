package net.portalmod_extensions.core.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraft.client.renderer.entity.SpriteRenderer;
import net.minecraft.client.renderer.texture.AtlasTexture;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.portalmod_extensions.PortalModExtensions;
import net.portalmod_extensions.common.renderer.EnergyPelletDispenserTER;
import net.portalmod_extensions.common.renderer.EnergyPelletReceiverTER;
import net.portalmod_extensions.core.init.BlockInit;
import net.portalmod_extensions.core.init.EntityInit;
import net.portalmod_extensions.core.init.TileEntityTypeInit;

@EventBusSubscriber(modid = PortalModExtensions.MODID, bus = Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void onTextureStitch(TextureStitchEvent.Pre event) {
        if (event.getMap().location() == AtlasTexture.LOCATION_BLOCKS) {
            event.addSprite(EnergyPelletDispenserTER.TEXTURE_TOP);
        }
    }

    @SubscribeEvent
    public static void clientSetup(final FMLClientSetupEvent event) {
        RenderingRegistry.registerEntityRenderingHandler(EntityInit.ENERGY_PELLET.get(), manager -> new SpriteRenderer<>(manager, Minecraft.getInstance().getItemRenderer()));

        ClientRegistry.bindTileEntityRenderer(TileEntityTypeInit.ENERGY_PELLET_DISPENSER.get(), EnergyPelletDispenserTER::new);

        ClientRegistry.bindTileEntityRenderer(TileEntityTypeInit.ENERGY_PELLET_RECEIVER.get(), EnergyPelletReceiverTER::new);
        RenderTypeLookup.setRenderLayer(BlockInit.ENERGY_PELLET_DISPENSER.get(), RenderType.cutout());
        RenderTypeLookup.setRenderLayer(BlockInit.ENERGY_PELLET_RECEIVER.get(), RenderType.cutout());
    }
}