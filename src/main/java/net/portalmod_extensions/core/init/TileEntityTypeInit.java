package net.portalmod_extensions.core.init;

import net.minecraft.tileentity.TileEntityType;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.portalmod_extensions.PortalModExtensions;
import net.portalmod_extensions.common.tileentities.EnergyPelletDispenserTileEntity;
import net.portalmod_extensions.common.tileentities.EnergyPelletReceiverTileEntity;

@SuppressWarnings("DataFlowIssue")
public class TileEntityTypeInit {
    public static final DeferredRegister<TileEntityType<?>> TILE_ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.TILE_ENTITIES, PortalModExtensions.MODID);

    public static final RegistryObject<TileEntityType<EnergyPelletDispenserTileEntity>> ENERGY_PELLET_DISPENSER = TILE_ENTITY_TYPES.register("energy_pellet_dispenser", () -> TileEntityType.Builder.of(EnergyPelletDispenserTileEntity::new, BlockInit.ENERGY_PELLET_DISPENSER.get()).build(null));

    public static final RegistryObject<TileEntityType<EnergyPelletReceiverTileEntity>> ENERGY_PELLET_RECEIVER = TILE_ENTITY_TYPES.register("energy_pellet_receiver", () -> TileEntityType.Builder.of(EnergyPelletReceiverTileEntity::new, BlockInit.ENERGY_PELLET_RECEIVER.get()).build(null));
}
