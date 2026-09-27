package com.aetherspellbooks.spells;

import com.aetherspellbooks.util.ASFx;
import com.aetherteam.aether.client.AetherSoundEvents;
import com.aetherteam.aether.entity.projectile.crystal.IceCrystal;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Conjures genuine Aether ice crystals and bats them forward, exactly as a player would by hitting them.
 * Because they are real {@link IceCrystal}s, they trigger the Sun Spirit's native freeze mechanic.
 * The crystals leave a frosty trail and chill whatever they strike.
 */
public class FrostboundCrystalSpell extends AetherSpell {
    /** Damage dealt by an Aether ice crystal (fixed by the Aether). */
    public static final float CRYSTAL_DAMAGE = 7f;
    public static final String TAG_LEVEL = "aether_spellbooks.frostbound_level";
    private static final double LATERAL_SPACING = 0.8;
    /** Same speed the Aether gives a crystal batted by a player. */
    public static final double CRYSTAL_SPEED = 2.5;
    private static final List<IceCrystal> TRAILS = new ArrayList<>();

    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.RARE)
            .setSchoolResource(SchoolRegistry.ICE_RESOURCE)
            .setMaxLevel(5)
            .setCooldownSeconds(15)
            .build();

    public FrostboundCrystalSpell() {
        super("frostbound_crystal");
        this.baseManaCost = 40;
        this.manaCostPerLevel = 10;
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 0;
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
        return Optional.of(AetherSoundEvents.ENTITY_SUN_SPIRIT_SHOOT_ICE.get());
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
                Component.translatable("ui.irons_spellbooks.damage", (int) CRYSTAL_DAMAGE),
                Component.translatable("ui.irons_spellbooks.projectile_count", getCrystalCount(spellLevel)),
                Component.translatable("ui.aether_spellbooks.chill_duration", Utils.timeFromTicks(getChillTicks(spellLevel), 1)),
                Component.translatable("ui.aether_spellbooks.freezes_sun_spirit")
        );
    }

    public static int getCrystalCount(int spellLevel) {
        return 1 + (spellLevel - 1) / 2;
    }

    public static int getChillTicks(int spellLevel) {
        return 40 + 20 * spellLevel;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity caster, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel server) {
            int count = getCrystalCount(spellLevel);
            Vec3 look = caster.getLookAngle();
            Vec3 forward = new Vec3(look.x, 0, look.z);
            forward = forward.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : forward.normalize();
            Vec3 right = new Vec3(-forward.z, 0, forward.x);
            // The same kind of hit a player lands when batting a crystal back at the Sun Spirit.
            DamageSource bat = caster instanceof Player player
                    ? caster.damageSources().playerAttack(player)
                    : caster.damageSources().mobAttack(caster);
            Vec3 aim = look.normalize();
            Vec3 eye = caster.getEyePosition();
            for (int i = 0; i < count; i++) {
                IceCrystal crystal = new IceCrystal(level, caster);
                Vec3 spawn = eye.add(right.scale((i - (count - 1) / 2.0) * LATERAL_SPACING)).add(aim.scale(0.9)).subtract(0, 0.35, 0);
                crystal.setPos(spawn.x, spawn.y, spawn.z);
                crystal.getPersistentData().putInt(TAG_LEVEL, spellLevel);
                level.addFreshEntity(crystal);
                // IceCrystal#hurt makes the caster the owner and marks the crystal as "batted" (it now shatters on walls),
                // but only sends it horizontally; the crystal's movement follows its full velocity, so aim it with the crosshair.
                crystal.hurt(bat, 0);
                crystal.setDeltaMovement(aim.scale(CRYSTAL_SPEED));
                TRAILS.add(crystal);
                server.sendParticles(ParticleHelper.SNOWFLAKE, crystal.getX(), crystal.getY() + 0.3, crystal.getZ(), 12, 0.2, 0.2, 0.2, 0.08);
            }
            Vec3 muzzle = eye.add(aim.scale(0.9)).subtract(0, 0.35, 0);
            ASFx.blastwave(server, ASFx.FROST_CYAN, caster.position(), 1.6f);
            server.sendParticles(ParticleHelper.SNOW_DUST, muzzle.x, muzzle.y, muzzle.z, 10, 0.3, 0.2, 0.3, 0.05);
        }
        super.onCast(level, spellLevel, caster, castSource, playerMagicData);
    }

    /** Frost trail for crystals conjured by this spell. */
    public static void tickTrails() {
        TRAILS.removeIf(crystal -> {
            if (crystal.isRemoved()) {
                if (crystal.level() instanceof ServerLevel server) {
                    server.sendParticles(ParticleHelper.SNOWFLAKE, crystal.getX(), crystal.getY() + 0.3, crystal.getZ(), 16, 0.3, 0.3, 0.3, 0.1);
                    server.sendParticles(ParticleHelper.ICY_FOG, crystal.getX(), crystal.getY() + 0.3, crystal.getZ(), 2, 0.2, 0.2, 0.2, 0.01);
                }
                return true;
            }
            if (crystal.level() instanceof ServerLevel server) {
                server.sendParticles(ParticleHelper.SNOWFLAKE, crystal.getX(), crystal.getY() + 0.3, crystal.getZ(), 2, 0.12, 0.12, 0.12, 0.01);
                if (crystal.tickCount % 3 == 0) {
                    server.sendParticles(ParticleHelper.SNOW_DUST, crystal.getX(), crystal.getY() + 0.3, crystal.getZ(), 1, 0.05, 0.05, 0.05, 0);
                }
            }
            return false;
        });
    }

    public static void clearTrails() {
        TRAILS.clear();
    }

    /** Chills targets struck by crystals conjured by this spell. */
    public static void onCrystalHit(LivingEntity target, DamageSource source) {
        if (source.getDirectEntity() instanceof IceCrystal crystal && crystal.getPersistentData().contains(TAG_LEVEL)) {
            int spellLevel = crystal.getPersistentData().getInt(TAG_LEVEL);
            target.addEffect(new MobEffectInstance(MobEffectRegistry.CHILLED.get(), getChillTicks(spellLevel), 0));
            if (target.level() instanceof ServerLevel server) {
                server.sendParticles(ParticleHelper.SNOWFLAKE, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 20, 0.3, 0.4, 0.3, 0.1);
            }
        }
    }
}
