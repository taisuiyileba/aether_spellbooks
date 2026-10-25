package com.aetherspellbooks.dungeon;

import com.aetherspellbooks.registry.ASBlocks;
import com.aetherteam.aether.item.AetherItems;
import io.redspace.ironsspellbooks.api.backwards_compat.blocks.vault.data.VaultConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.RegistryObject;

import java.util.Arrays;
import java.util.Optional;

/**
 * The three Aether dungeons, as their Treasure Chests name them ({@code aether:bronze} ...). Each has a vault reskin that
 * opens with that dungeon's key.
 */
public enum VaultTier {
    CARVED("bronze", AetherItems.BRONZE_DUNGEON_KEY, ASBlocks.CARVED_VAULT),
    ANGELIC("silver", AetherItems.SILVER_DUNGEON_KEY, ASBlocks.ANGELIC_VAULT),
    HELLFIRE("gold", AetherItems.GOLD_DUNGEON_KEY, ASBlocks.HELLFIRE_VAULT);

    // the same ranges as vanilla and Iron's Spells vaults
    private static final double ACTIVATION_RANGE = 4.0;
    private static final double DEACTIVATION_RANGE = 4.5;

    private final ResourceLocation kind;
    private final ResourceLocation rewardTable;
    private final RegistryObject<Item> key;
    private final RegistryObject<Block> block;

    VaultTier(String dungeon, RegistryObject<Item> key, RegistryObject<Block> block) {
        this.kind = ResourceLocation.fromNamespaceAndPath("aether", dungeon);
        this.rewardTable = ResourceLocation.fromNamespaceAndPath("aether", "chests/dungeon/" + dungeon + "/" + dungeon + "_dungeon_reward");
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
    public ResourceLocation rewardTable() {
        return rewardTable;
    }

    public Item key() {
        return key.get();
    }

    public Block block() {
        return block.get();
    }

    public VaultConfig config(ResourceLocation lootTable) {
        return new VaultConfig(lootTable, ACTIVATION_RANGE, DEACTIVATION_RANGE, new ItemStack(key()), Optional.empty());
    }
}
