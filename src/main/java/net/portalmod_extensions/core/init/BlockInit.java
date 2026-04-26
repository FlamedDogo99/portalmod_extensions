package net.portalmod_extensions.core.init;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.portalmod_extensions.PortalModExtensions;
import net.portalmod_extensions.common.blocks.EnergyPelletDispenserBlock;
import net.portalmod_extensions.common.blocks.EnergyPelletReceiverBlock;

public class BlockInit {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, PortalModExtensions.MODID);

    public static final RegistryObject<EnergyPelletDispenserBlock> ENERGY_PELLET_DISPENSER = BLOCKS.register("energy_pellet_dispenser", () -> new EnergyPelletDispenserBlock(Block.Properties.of(Material.METAL).strength(3.0f).noOcclusion()));

    public static final RegistryObject<EnergyPelletReceiverBlock> ENERGY_PELLET_RECEIVER = BLOCKS.register("energy_pellet_receiver", () -> new EnergyPelletReceiverBlock(Block.Properties.of(Material.METAL).strength(3.0f).noOcclusion()));
}
