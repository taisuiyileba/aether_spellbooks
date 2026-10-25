package com.aetherspellbooks.dungeon;

import io.redspace.ironsspellbooks.api.backwards_compat.blocks.vault.VaultBlock;
import io.redspace.ironsspellbooks.api.backwards_compat.blocks.vault.data.VaultBlockEntity;
import io.redspace.ironsspellbooks.api.backwards_compat.blocks.vault.data.VaultState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A dungeon-themed vault based on Iron's Spells' 1.20.1 vault backport: each player can unlock it once per reset with the
 * matching dungeon key, and it bursts out all rolled loot at once. Like the dungeon's locked stone, it cannot be mined
 * or blown up.
 */
public class DungeonVaultBlock extends VaultBlock {
    private final VaultTier tier;

    public DungeonVaultBlock(VaultTier tier) {
        this.tier = tier;
    }

    public VaultTier tier() {
        return tier;
    }

    @Override
    public @NotNull InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                                          @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty() || state.getValue(STATE) != VaultState.ACTIVE) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel serverLevel && serverLevel.getBlockEntity(pos) instanceof VaultBlockEntity vault) {
            VaultBlockEntity.Server.tryInsertKey(serverLevel, pos, state, vault.getConfig(), vault.getServerData(), vault.getSharedData(), player, stack);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
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
    public float getDestroyProgress(@NotNull BlockState state, @NotNull Player player, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return 0f;
    }

    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return 3600000f;
    }

    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return false;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable BlockGetter level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable("block.aether_spellbooks.dungeon_vault.desc", tier.key().getDescription())
                .withStyle(ChatFormatting.GRAY));
    }
}
