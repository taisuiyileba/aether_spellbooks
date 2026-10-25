package com.aetherspellbooks.mixin;

import net.minecraft.world.level.block.entity.vault.VaultServerData;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Package-private VaultServerData accessors for revival resets and instant burst ejection. */
@Mixin(value = VaultServerData.class)
public interface VaultRewardsAccessor {
    @Accessor("rewardedPlayers")
    Set<UUID> aetherSpellbooks$getRewardedPlayers();

    @Accessor("itemsToEject")
    List<ItemStack> aetherSpellbooks$getItemsToEject();

    @Invoker("setItemsToEject")
    void aetherSpellbooks$setItemsToEject(List<ItemStack> items);

    @Invoker("pauseStateUpdatingUntil")
    void aetherSpellbooks$pauseStateUpdatingUntil(long gameTime);
}
