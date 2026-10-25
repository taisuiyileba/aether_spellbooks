package com.aetherspellbooks.dungeon;

import com.aetherspellbooks.registry.ASBlocks;
import com.aetherteam.aether.item.AetherItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.vault.VaultConfig;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The three Aether dungeons, as their Treasure Chests name them ({@code aether:bronze} ...). Each has a vanilla vault reskin
 * that opens with that dungeon's key.
 */
public enum VaultTier {
    CARVED("bronze", AetherItems.BRONZE_DUNGEON_KEY, ASBlocks.CARVED_VAULT),
    ANGELIC("silver", AetherItems.SILVER_DUNGEON_KEY, ASBlocks.ANGELIC_VAULT),
    HELLFIRE("gold", AetherItems.GOLD_DUNGEON_KEY, ASBlocks.HELLFIRE_VAULT);

    // the same ranges as vanilla and Iron's Spells vaults
    private static final double ACTIVATION_RANGE = 4.0;
    private static final double DEACTIVATION_RANGE = 4.5;

    private final ResourceLocation kind;
    private final ResourceKey<LootTable> rewardTable;
    private final Supplier<? extends Item> key;
    private final Supplier<? extends Block> block;

    VaultTier(String dungeon, Supplier<? extends Item> key, Supplier<? extends Block> block) {
        this.kind = ResourceLocation.fromNamespaceAndPath("aether", dungeon);
        this.rewardTable = ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.fromNamespaceAndPath("aether", "chests/dungeon/" + dungeon + "/" + dungeon + "_dungeon_reward"));
        this.key = key;
        this.block = block;
    }

    public static Optional<VaultTier> byKind(ResourceLocation kind) {
        return Arrays.stream(values()).filter(tier -> tier.kind.equals(kind)).findFirst();
    }

    /** The Treasure Chest kind of this dungeon. */
    public ResourceLocation kind() {
        return kind;
    }

    /** The Aether's own reward table, used when a vault is placed by hand. */
    public ResourceKey<LootTable> rewardTable() {
        return rewardTable;
    }

    public Item key() {
        return key.get();
    }

    public Block block() {
        return block.get();
    }

    public VaultConfig config(ResourceKey<LootTable> lootTable) {
        return new VaultConfig(lootTable, ACTIVATION_RANGE, DEACTIVATION_RANGE, new ItemStack(key()), Optional.empty());
    }
}
