package com.aetherspellbooks.dungeon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A dungeon-themed reskin of the vanilla vault: each player can unlock it once per reset with the dungeon key,
 * receiving all rolled loot at once. Like the dungeon's locked stone, it cannot be mined or blown up, so one player cannot take the
 * vault away from the others.
 */
public class DungeonVaultBlock extends VaultBlock {
    public static final MapCodec<DungeonVaultBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.xmap(VaultTier::valueOf, VaultTier::name).fieldOf("tier").forGetter(DungeonVaultBlock::tier),
            propertiesCodec()).apply(instance, DungeonVaultBlock::new));

    private final VaultTier tier;

    public DungeonVaultBlock(VaultTier tier, BlockBehaviour.Properties properties) {
        super(properties);
        this.tier = tier;
    }

    /** The vanilla vault's look, sound and light, with the strength of the dungeon's locked blocks. */
    public static BlockBehaviour.Properties vaultProperties() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.VAULT).strength(-1.0f, 3600000.0f).noLootTable();
    }

    public VaultTier tier() {
        return tier;
    }

    @Override
    public @NotNull MapCodec<VaultBlock> codec() {
        @SuppressWarnings({"unchecked", "rawtypes"})
        MapCodec<VaultBlock> codec = (MapCodec) CODEC;
        return codec;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        VaultBlockEntity vault = new VaultBlockEntity(pos, state);
        // a converted Treasure Chest overwrites this with the chest's own loot table (saved with the block entity)
        vault.setConfig(tier.config(tier.rewardTable()));
        return vault;
    }

    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return false;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull Item.TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable("block.aether_spellbooks.dungeon_vault.desc", tier.key().getDescription())
                .withStyle(ChatFormatting.GRAY));
    }
}
