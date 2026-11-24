package com.frrnq.serverscore.client.resource;

import com.frrnq.serverscore.FrrnqServersCore;
import com.frrnq.serverscore.resourcechests.ResourceChests;
import com.frrnq.serverscore.resourcechests.ResourceChestTile;
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
public final class ResourceChestModels {
    @SubscribeEvent public static void models(ModelRegistryEvent event) {
        ResourceChestRenderer renderer = new ResourceChestRenderer();
        ClientRegistry.bindTileEntitySpecialRenderer(ResourceChestTile.class, renderer);
        Item item = Item.getItemFromBlock(ResourceChests.BLOCK);
        // Vanilla's builtin/entity model supplies all GUI/hand transformations.
        ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation("minecraft:chest", "inventory"));
        item.setTileEntityItemStackRenderer(new TileEntityItemStackRenderer() {
            @Override public void renderByItem(ItemStack stack, float partialTicks) {
                renderer.render(null, 0, 0, 0, partialTicks, -1, 1);
            }
        });
        // TESR renders the block; a vanilla baked model supplies wood particles only.
        // No dependency on custom JSONs being present in an IDE launch's resource output.
        ModelLoader.setCustomStateMapper(ResourceChests.BLOCK, new StateMapperBase() {
            @Override protected ModelResourceLocation getModelResourceLocation(IBlockState state) {
                return new ModelResourceLocation("minecraft:planks", "variant=oak");
            }
        });
    }
}