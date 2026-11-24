package com.frrnq.serverscore.airdrop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.NonNullList;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

public final class AirDropData extends WorldSavedData {
    private static final String KEY = "frrnqserverscore_airdrop_templates";
    static final int SLOTS = 27;

    static final class Template {
        final String name;
        final UUID id;
        private final NonNullList<ItemStack> samples = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

        Template(String name, UUID id) {
            this.name = name;
            this.id = id;
        }

        int filled() {
            int count = 0;
            for (ItemStack stack : samples) {
                if (!stack.isEmpty()) { count++; }
            }
            return count;
        }

        ItemStack sample(int slot) { return samples.get(slot); }

        ItemStack copySlot(int slot) { return samples.get(slot).copy(); }
    }

    private final Map<String, Template> templates = new LinkedHashMap<>();

    public AirDropData(String name) { super(name); }

    static AirDropData get(MinecraftServer server) {
        MapStorage storage = server.getWorld(0).getMapStorage();
        AirDropData data = (AirDropData) storage.getOrLoadData(AirDropData.class, KEY);
        if (data == null) {
            data = new AirDropData(KEY);
            storage.setData(KEY, data);
        }
        return data;
    }

    static boolean validName(String name) {
        return name.length() <= 48 && name.matches("[\\p{L}\\p{N}_-]{1,48}");
    }

    Template find(String name) { return templates.get(name.toLowerCase(Locale.ROOT)); }

    List<String> names() {
        List<String> names = new ArrayList<>();
        for (Template template : templates.values()) { names.add(template.name); }
        return names;
    }

    void create(String name) {
        if (!validName(name) || find(name) != null) { throw new IllegalArgumentException(name); }
        templates.put(name.toLowerCase(Locale.ROOT), new Template(name, UUID.randomUUID()));
        markDirty();
    }

    void delete(Template template) {
        templates.remove(template.name.toLowerCase(Locale.ROOT));
        markDirty();
    }

    void setSample(Template template, int slot, ItemStack cursor) {
        template.samples.set(slot, cursor.copy());
        markDirty();
    }

    @Override public void readFromNBT(NBTTagCompound tag) {
        templates.clear();
        NBTTagList list = tag.getTagList("Templates", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            String name = entry.getString("Name");
            if (!validName(name) || find(name) != null || !entry.hasUniqueId("Id")) { continue; }
            Template template = new Template(name, entry.getUniqueId("Id"));
            ItemStackHelper.loadAllItems(entry, template.samples);
            templates.put(name.toLowerCase(Locale.ROOT), template);
        }
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        NBTTagList list = new NBTTagList();
        for (Template template : templates.values()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("Name", template.name);
            entry.setUniqueId("Id", template.id);
            ItemStackHelper.saveAllItems(entry, template.samples);
            list.appendTag(entry);
        }
        tag.setTag("Templates", list);
        return tag;
    }
}
