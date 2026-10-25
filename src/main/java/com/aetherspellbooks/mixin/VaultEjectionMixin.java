package com.aetherspellbooks.mixin;

import com.aetherspellbooks.dungeon.DungeonVaults;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultConfig;
import net.minecraft.world.level.block.entity.vault.VaultServerData;
import net.minecraft.world.level.block.entity.vault.VaultSharedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Dungeon vaults burst out all merged rewards the moment they open, while keeping the per-player reward
 * history. Original Iron's Spells vaults use burst ejection only when their compatibility option is enabled.
 */
@Mixin(value = VaultBlockEntity.Server.class)
public abstract class VaultEjectionMixin {
    @Inject(method = "unlock", at = @At("HEAD"), cancellable = true)
    private static void aetherSpellbooks$ejectAllAtOnce(ServerLevel level, BlockState state, BlockPos pos, VaultConfig config,
                                                        VaultServerData serverData, VaultSharedData sharedData, List<ItemStack> items,
                                                        CallbackInfo ci) {
        if (DungeonVaults.shouldEjectAll(state)) {
            DungeonVaults.ejectAll(level, pos, state, serverData, sharedData, items);
            ci.cancel();
        }
    }

    @Inject(method = "resolveItemsToEject", at = @At("RETURN"), cancellable = true)
    private static void aetherSpellbooks$mergeDungeonReward(ServerLevel level, VaultConfig config, BlockPos pos, Player player,
                                                            CallbackInfoReturnable<List<ItemStack>> cir) {
        if (DungeonVaults.shouldEjectAll(level.getBlockState(pos))) {
            cir.setReturnValue(DungeonVaults.mergeForEjection(cir.getReturnValue()));
        }
    }
}
