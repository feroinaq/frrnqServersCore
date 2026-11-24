package com.frrnq.serverscore.client.airdrop;

import com.frrnq.serverscore.FrrnqServersCore;
import com.frrnq.serverscore.airdrop.AirDrops;
import com.frrnq.serverscore.airdrop.AirDropTile;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = FrrnqServersCore.MODID, value = Side.CLIENT)
public final class AirDropModels {
    @SubscribeEvent public static void models(ModelRegistryEvent event) {
        AirDropRenderer renderer = new AirDropRenderer();
        ClientRegistry.bindTileEntitySpecialRenderer(AirDropTile.class, renderer);
        Item item = Item.getItemFromBlock(AirDrops.BLOCK);
        // Vanilla's builtin/entity model supplies all GUI/hand transformations.
        ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation("minecraft:chest", "inventory"));
        item.setTileEntityItemStackRenderer(new TileEntityItemStackRenderer() {
            private final ItemStack chest = new ItemStack(net.minecraft.init.Blocks.CHEST);
            @Override public void renderByItem(ItemStack stack, float partialTicks) {
                TileEntityItemStackRenderer.instance.renderByItem(chest, partialTicks);
            }
        });
        // TESR renders the block; a vanilla baked model supplies wood particles only.
        // No dependency on custom JSONs being present in an IDE launch's resource output.
        ModelLoader.setCustomStateMapper(AirDrops.BLOCK, new StateMapperBase() {
            @Override protected ModelResourceLocation getModelResourceLocation(IBlockState state) {
                return new ModelResourceLocation("minecraft:planks", "variant=oak");
            }
        });
    }
}
