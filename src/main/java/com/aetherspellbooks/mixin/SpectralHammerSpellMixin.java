package com.aetherspellbooks.mixin;

import com.aetherspellbooks.compat.SliderSpellCompat;
import com.aetherspellbooks.config.ASConfig;
import com.aetherteam.aether.entity.monster.dungeon.boss.Slider;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.spells.evocation.SpectralHammerSpell;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = SpectralHammerSpell.class, remap = false)
public abstract class SpectralHammerSpellMixin extends AbstractSpell {
    @Inject(method = "checkPreCastConditions", at = @At("HEAD"), cancellable = true)
    private void aetherSpellbooks$allowSliderTarget(Level level, int spellLevel, LivingEntity caster,
                                                   MagicData data, CallbackInfoReturnable<Boolean> cir) {
        if (SliderSpellCompat.findHammerTarget(caster) != null) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "onCast", at = @At("HEAD"), cancellable = true)
    private void aetherSpellbooks$strikeSlider(Level level, int spellLevel, LivingEntity caster,
                                              CastSource source, MagicData data, CallbackInfo ci) {
        Slider target = SliderSpellCompat.findHammerTarget(caster);
        if (target != null) {
            if (!level.isClientSide) {
                SliderSpellCompat.spawnHammer(caster, target, spellLevel);
            }
            super.onCast(level, spellLevel, caster, source, data);
            ci.cancel();
        }
    }

    @Inject(method = "getUniqueInfo", at = @At("RETURN"), cancellable = true)
    private void aetherSpellbooks$describeSliderDamage(int spellLevel, LivingEntity caster,
                                                      CallbackInfoReturnable<List<MutableComponent>> cir) {
        if (ASConfig.spectralHammerDamageSlider()) {
            List<MutableComponent> info = new ArrayList<>(cir.getReturnValue());
            info.add(Component.translatable("ui.aether_spellbooks.slider_damage",
                    Utils.stringTruncation(SliderSpellCompat.hammerDamage(spellLevel, caster), 1)));
            cir.setReturnValue(info);
        }
    }
}
