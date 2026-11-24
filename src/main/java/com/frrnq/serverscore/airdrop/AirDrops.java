package com.frrnq.serverscore.airdrop;

import java.util.ArrayList;
import java.util.List;
import com.frrnq.serverscore.FrrnqServersCore;
import com.frrnq.serverscore.network.CoreNetwork;
import com.frrnq.serverscore.network.ResourceNoticeMessage;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

@Mod.EventBusSubscriber(modid = FrrnqServersCore.MODID)
public final class AirDrops {
    public static final AirDropBlock BLOCK = new AirDropBlock();
    private static final List<BlockEvent.PlaceEvent> placements = new ArrayList<>();

    public static void init() {
        GameRegistry.registerTileEntity(AirDropTile.class, new ResourceLocation(FrrnqServersCore.MODID, "airdrop_chest"));
    }

    @SubscribeEvent public static void blocks(RegistryEvent.Register<Block> event) { event.getRegistry().register(BLOCK); }
    @SubscribeEvent public static void items(RegistryEvent.Register<Item> event) { event.getRegistry().register(new AirDropItem(BLOCK)); }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void placed(BlockEvent.PlaceEvent event) {
        if (!event.getWorld().isRemote && event.getPlacedBlock().getBlock() == BLOCK
                && event.getPlacedBlock().getValue(AirDropBlock.ACTIVE)
                && event.getPlayer().getHeldItem(event.getHand()).getItem() instanceof AirDropItem) {
            placements.add(event);
        }
    }

    @SubscribeEvent public static void afterPlacements(TickEvent.ServerTickEvent tick) {
        if (tick.phase != TickEvent.Phase.END || placements.isEmpty()) { return; }
        // All placement subscribers and Forge's snapshot commit/rollback have now finished.
        for (BlockEvent.PlaceEvent placement : placements) {
            if (!placement.isCanceled()) { CoreNetwork.CHANNEL.sendToAll(ResourceNoticeMessage.airdrop()); }
        }
        placements.clear();
    }

    public static void stopped() { placements.clear(); }
}
