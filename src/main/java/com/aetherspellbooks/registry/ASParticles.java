package com.aetherspellbooks.registry;

import com.aetherspellbooks.AetherSpellbooks;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ASParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, AetherSpellbooks.MODID);

    /** A drifting white feather (Moa, Valkyries). */
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> FEATHER = PARTICLES.register("feather", () -> new SimpleParticleType(false));
    /** A golden four-point twinkle (holy, sun, Valkyries). */
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> SKY_SPARKLE = PARTICLES.register("sky_sparkle", () -> new SimpleParticleType(false));
    /** A soft, expanding aercloud puff. */
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> CLOUD_PUFF = PARTICLES.register("cloud_puff", () -> new SimpleParticleType(false));
    /** A heavy slate-grey thundercloud puff (Thunderhead). */
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> STORM_PUFF = PARTICLES.register("storm_puff", () -> new SimpleParticleType(false));
    /** A rising pink-violet gravitite mote. */
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> GRAVITY_MOTE = PARTICLES.register("gravity_mote", () -> new SimpleParticleType(false));

    public static void register(IEventBus bus) {
        PARTICLES.register(bus);
    }
}
