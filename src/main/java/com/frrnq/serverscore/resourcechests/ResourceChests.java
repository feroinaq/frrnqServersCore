package com.frrnq.serverscore.resourcechests;

import com.frrnq.serverscore.FrrnqServersCore;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.registry.GameRegistry;

@Mod.EventBusSubscriber(modid = FrrnqServersCore.MODID)
public final class ResourceChests {
    public static final ResourceChestBlock BLOCK = new ResourceChestBlock();
    public static void init() {
        GameRegistry.registerTileEntity(ResourceChestTile.class, new ResourceLocation(FrrnqServersCore.MODID, "resource_chest"));
        NetworkRegistry.INSTANCE.registerGuiHandler(FrrnqServersCore.instance, new ResourceGuiHandler());
    }
    @SubscribeEvent public static void blocks(RegistryEvent.Register<Block> event) { event.getRegistry().register(BLOCK); }
    @SubscribeEvent public static void items(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(new ItemBlock(BLOCK).setRegistryName(BLOCK.getRegistryName()));
    }
}