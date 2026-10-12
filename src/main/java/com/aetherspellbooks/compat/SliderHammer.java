package com.aetherspellbooks.compat;

import com.aetherteam.aether.entity.monster.dungeon.boss.Slider;
import net.minecraft.world.entity.LivingEntity;

/** Server-side strike context attached to Iron's Spells' existing hammer entity. */
public interface SliderHammer {
    void aetherSpellbooks$prepareStrike(LivingEntity caster, Slider target, float damage);

    boolean aetherSpellbooks$isStrikeFor(Slider target, LivingEntity caster);
}
