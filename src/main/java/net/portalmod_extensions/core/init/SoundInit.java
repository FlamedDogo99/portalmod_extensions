package net.portalmod_extensions.core.init;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.portalmod_extensions.PortalModExtensions;

public class SoundInit {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, PortalModExtensions.MODID);

    private SoundInit() {}

    public static final RegistryObject<SoundEvent> ENERGY_PELLET_DISPENSER_SHOOT =
            register("block.energy_pellet_dispenser.shoot");

    private static RegistryObject<SoundEvent> register(String id) {
        return SOUNDS.register(id, () -> new SoundEvent(new ResourceLocation(PortalModExtensions.MODID, id)));
    }
}
