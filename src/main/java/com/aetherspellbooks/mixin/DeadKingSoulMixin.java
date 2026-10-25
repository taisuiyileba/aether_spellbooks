package com.aetherspellbooks.mixin;

import com.aetherspellbooks.config.ASConfig;
import com.aetherspellbooks.dungeon.DungeonVaults;
import io.redspace.ironsspellbooks.entity.mobs.dead_king_boss.DeadKingSoulEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * When the Dead King is revived via his phylactery, refreshes nearby Catacombs vaults so players can
 * unlock them again, provided the config switch is enabled.
 */
@Mixin(value = DeadKingSoulEntity.class)
public abstract class DeadKingSoulMixin {
    @Inject(method = "interactAt",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private void aetherSpellbooks$onDeadKingRevived(Player player, Vec3 hitVec, InteractionHand hand,
                                                    CallbackInfoReturnable<InteractionResult> cir) {
        if (ASConfig.modifyIronsVaults() && player.level() instanceof ServerLevel serverLevel) {
            DeadKingSoulEntity soul = (DeadKingSoulEntity) (Object) this;
            DungeonVaults.refreshVaultsAround(serverLevel, soul.blockPosition(), 64);
        }
    }
}
