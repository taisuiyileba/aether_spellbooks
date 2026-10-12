package com.aetherspellbooks.mixin;

import com.aetherspellbooks.compat.SliderSpellCompat;
import com.aetherteam.aether.entity.monster.dungeon.boss.Slider;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(value = Slider.class, remap = false)
public abstract class SliderMixin {
    @Shadow
    private Optional<LivingEntity> sendTooFarMessage(LivingEntity attacker) {
        throw new AssertionError();
    }

    @Inject(method = "canDamageSlider", at = @At("HEAD"), cancellable = true)
    private void aetherSpellbooks$acceptMiningSpells(DamageSource source, CallbackInfoReturnable<Optional<LivingEntity>> cir) {
        Slider slider = (Slider) (Object) this;
        LivingEntity attacker = SliderSpellCompat.compatibleAttacker(slider, source);
        if (attacker == null) {
            return;
        }
        if (slider.level().getDifficulty() == Difficulty.PEACEFUL) {
            cir.setReturnValue(Optional.empty());
        } else if (!SliderSpellCompat.canAttackFrom(slider, attacker)) {
            cir.setReturnValue(sendTooFarMessage(attacker));
        } else {
            cir.setReturnValue(Optional.of(attacker));
        }
    }
}
