package com.aetherspellbooks.registry;

import com.aetherspellbooks.AetherSpellbooks;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ASParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, AetherSpellbooks.MODID);

    /** A drifting white feather (Moa, Valkyries). */
    public static final RegistryObject<SimpleParticleType> FEATHER = PARTICLES.register("feather", () -> new SimpleParticleType(false));
    /** A golden four-point twinkle (holy, sun, Valkyries). */
    public static final RegistryObject<SimpleParticleType> SKY_SPARKLE = PARTICLES.register("sky_sparkle", () -> new SimpleParticleType(false));
    /** A soft, expanding aercloud puff. */
    public static final RegistryObject<SimpleParticleType> CLOUD_PUFF = PARTICLES.register("cloud_puff", () -> new SimpleParticleType(false));
    /** A rising pink-violet gravitite mote. */
    public static final RegistryObject<SimpleParticleType> GRAVITY_MOTE = PARTICLES.register("gravity_mote", () -> new SimpleParticleType(false));

    public static void register(IEventBus bus) {
        PARTICLES.register(bus);
    }
}
