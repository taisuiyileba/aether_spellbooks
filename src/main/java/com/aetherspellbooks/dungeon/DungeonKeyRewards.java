package com.aetherspellbooks.dungeon;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Keys owed to participants, shared across dimensions and kept until their inventories can accept them. */
public final class DungeonKeyRewards extends SavedData {
    private static final String NAME = "aether_spellbooks_dungeon_keys";
    private final Map<UUID, List<ItemStack>> pending = new HashMap<>();

    public static DungeonKeyRewards get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(DungeonKeyRewards::new, DungeonKeyRewards::load), NAME);
    }

    public void award(UUID player, ItemStack reward) {
        ItemStack rest = reward.copy();
        if (rest.isEmpty()) {
            return;
        }
        List<ItemStack> keys = pending.computeIfAbsent(player, id -> new ArrayList<>());
        for (ItemStack key : keys) {
            if (ItemStack.isSameItemSameComponents(key, rest)) {
                int moved = Math.min(rest.getCount(), key.getMaxStackSize() - key.getCount());
                key.grow(moved);
                rest.shrink(moved);
                if (rest.isEmpty()) {
                    break;
                }
            }
        }
        while (!rest.isEmpty()) {
            keys.add(rest.split(rest.getMaxStackSize()));
        }
        setDirty();
    }

    public boolean hasPending(UUID player) {
        return pending.containsKey(player);
    }

    /** Called immediately for online participants, and periodically after login, respawn or freeing inventory slots. */
    public void deliver(ServerPlayer player) {
        List<ItemStack> keys = pending.get(player.getUUID());
        if (keys == null || !player.isAlive() || player.isSpectator()) {
            return;
        }
        boolean changed = false;
        for (Iterator<ItemStack> iterator = keys.iterator(); iterator.hasNext();) {
            ItemStack key = iterator.next();
            int before = key.getCount();
            player.getInventory().add(key);
            changed |= key.getCount() != before;
            if (key.isEmpty()) {
                iterator.remove();
            }
        }
        if (keys.isEmpty()) {
            pending.remove(player.getUUID());
        }
        if (changed) {
            setDirty();
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != player.inventoryMenu) {
                player.containerMenu.broadcastChanges();
            }
        }
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        ListTag players = new ListTag();
        pending.forEach((id, keys) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", id);
            ListTag items = new ListTag();
            keys.forEach(key -> items.add(key.save(registries)));
            entry.put("Keys", items);
            players.add(entry);
        });
        tag.put("Players", players);
        return tag;
    }

    public static DungeonKeyRewards load(CompoundTag tag, HolderLookup.Provider registries) {
        DungeonKeyRewards rewards = new DungeonKeyRewards();
        for (Tag playerTag : tag.getList("Players", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) playerTag;
            if (!entry.hasUUID("Player")) {
                continue;
            }
            List<ItemStack> keys = new ArrayList<>();
            for (Tag itemTag : entry.getList("Keys", Tag.TAG_COMPOUND)) {
                ItemStack key = ItemStack.parseOptional(registries, (CompoundTag) itemTag);
                if (!key.isEmpty()) {
                    keys.add(key);
                }
            }
            if (!keys.isEmpty()) {
                rewards.pending.put(entry.getUUID("Player"), keys);
            }
        }
        return rewards;
    }
}
