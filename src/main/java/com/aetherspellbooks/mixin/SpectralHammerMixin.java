package com.aetherspellbooks.mixin;

import com.aetherspellbooks.compat.SliderHammer;
import com.aetherspellbooks.compat.SliderSpellCompat;
import com.aetherspellbooks.config.ASConfig;
import com.aetherteam.aether.entity.monster.dungeon.boss.Slider;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.entity.spells.spectral_hammer.SpectralHammer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(value = SpectralHammer.class, remap = false)
public abstract class SpectralHammerMixin implements SliderHammer {
    @Shadow private int ticksAlive;
    @Unique private UUID aetherSpellbooks$caster;
    @Unique private UUID aetherSpellbooks$target;
    @Unique private Vec3 aetherSpellbooks$impact;
    @Unique private float aetherSpellbooks$damage;
    @Unique private boolean aetherSpellbooks$struck;

    @Override
    public void aetherSpellbooks$prepareStrike(LivingEntity caster, Slider target, float damage) {
        aetherSpellbooks$caster = caster.getUUID();
        aetherSpellbooks$target = target.getUUID();
        aetherSpellbooks$impact = SliderSpellCompat.hammerImpact(target);
        aetherSpellbooks$damage = damage;
    }

    @Override
    public boolean aetherSpellbooks$isStrikeFor(Slider target, LivingEntity caster) {
        return target.getUUID().equals(aetherSpellbooks$target) && caster.getUUID().equals(aetherSpellbooks$caster);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void aetherSpellbooks$impactSlider(CallbackInfo ci) {
        SpectralHammer hammer = (SpectralHammer) (Object) this;
        if (!(hammer.level() instanceof ServerLevel level) || ticksAlive < 13
                || aetherSpellbooks$target == null || aetherSpellbooks$struck) {
            return;
        }
        aetherSpellbooks$struck = true;
        if (!ASConfig.spectralHammerDamageSlider()
                || !(level.getEntity(aetherSpellbooks$caster) instanceof LivingEntity caster)
                || !caster.isAlive() || caster.isSpectator()
                || !(level.getEntity(aetherSpellbooks$target) instanceof Slider target) || !target.isAlive()
                || !target.getBoundingBox().inflate(0.5).contains(aetherSpellbooks$impact)
                || !SliderSpellCompat.canAttackFrom(target, caster)
                || !SliderSpellCompat.unobstructed(caster, aetherSpellbooks$impact)) {
            return;
        }
        DamageSources.applyDamage(target, aetherSpellbooks$damage,
                SpellRegistry.SPECTRAL_HAMMER_SPELL.get().getDamageSource(hammer, caster));
    }
}
