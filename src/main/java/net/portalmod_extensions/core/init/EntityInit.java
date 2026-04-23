package net.portalmod_extensions.core.init;

import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.portalmod_extensions.PortalModExtensions;
import net.portalmod_extensions.common.entities.EnergyPelletEntity;

public class EntityInit {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITIES, PortalModExtensions.MODID);

    public static final RegistryObject<EntityType<EnergyPelletEntity>> ENERGY_PELLET =
            ENTITIES.register("energy_pellet",
                    () -> EntityType.Builder.<EnergyPelletEntity>of(EnergyPelletEntity::new, EntityClassification.MISC)
                            .sized(0.4f, 0.4f)
                            .build(new ResourceLocation(PortalModExtensions.MODID, "energy_pellet").toString()));
}
