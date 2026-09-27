package com.aetherspellbooks.spells;

import com.aetherspellbooks.entity.StonebreakerShard;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import com.aetherspellbooks.util.ASFx;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * Fires a spinning holystone shard. Can damage the Slider, and deals bonus damage to Aether dungeon mobs.
 */
public class StonebreakerShardSpell extends AetherSpell {
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.COMMON)
            .setSchoolResource(SchoolRegistry.NATURE_RESOURCE)
            .setMaxLevel(10)
            .setCooldownSeconds(3)
            .build();

    public StonebreakerShardSpell() {
        super("stonebreaker_shard");
        this.baseManaCost = 15;
        this.manaCostPerLevel = 3;
        this.baseSpellPower = 5;
        this.spellPowerPerLevel = 1;
        this.castTime = 0;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public CastType getCastType() {
        return CastType.INSTANT;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.STONE_HIT);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        var info = new java.util.ArrayList<MutableComponent>(List.of(
                Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(getDamage(spellLevel, caster), 1)),
                Component.translatable("ui.irons_spellbooks.projectile_count", getShardCount(spellLevel)),
                Component.translatable("ui.aether_spellbooks.dungeon_bonus", Utils.stringTruncation((StonebreakerShard.DUNGEON_MOB_MULTIPLIER - 1) * 100, 0))));
        if (getPierce(spellLevel) > 0) {
            info.add(Component.translatable("ui.aether_spellbooks.pierce", getPierce(spellLevel)));
        }
        return info;
    }

    public float getDamage(int spellLevel, LivingEntity caster) {
        return getSpellPower(spellLevel, caster);
    }

    /** Shards per cast: 1 at level 1-4, 2 at 5-8, 3 at 9-10. */
    public static int getShardCount(int spellLevel) {
        return 1 + (spellLevel - 1) / 4;
    }

    /** From level 7 the shards pierce through one extra target. */
    public static int getPierce(int spellLevel) {
        return spellLevel >= 7 ? 1 : 0;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource, MagicData playerMagicData) {
        int count = getShardCount(spellLevel);
        Vec3 look = caster.getLookAngle();
        Vec3 eye = caster.position().add(0, caster.getEyeHeight() - 0.2, 0);
        for (int i = 0; i < count; i++) {
            float yaw = (i - (count - 1) / 2f) * 7f;
            Vec3 direction = look.yRot(yaw * Mth.DEG_TO_RAD);
            StonebreakerShard shard = new StonebreakerShard(level, caster);
            shard.setPos(eye.add(direction.scale(0.4)));
            shard.shoot(direction);
            shard.setDamage(getDamage(spellLevel, caster));
            shard.setPierceLevel(getPierce(spellLevel));
            level.addFreshEntity(shard);
        }
        ASFx.burst(level, new BlockParticleOption(ParticleTypes.BLOCK, StonebreakerShard.holystone()), eye.add(look.scale(0.8)), 8, 0.15, 0.1);
        super.onCast(level, spellLevel, caster, castSource, playerMagicData);
    }
}
