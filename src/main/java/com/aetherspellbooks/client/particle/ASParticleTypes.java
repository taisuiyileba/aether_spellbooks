package com.aetherspellbooks.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * Client implementations of the mod's particles.
 */
public final class ASParticleTypes {
    private ASParticleTypes() {
    }

    private static final int FULL_BRIGHT = 0xF000F0;

    /** Base class: translucent, fades out over its last ticks. */
    abstract static class FadingParticle extends TextureSheetParticle {
        protected final SpriteSet sprites;
        protected final float startAlpha;

        FadingParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites, float alpha) {
            super(level, x, y, z, dx, dy, dz);
            this.sprites = sprites;
            this.xd = dx;
            this.yd = dy;
            this.zd = dz;
            this.startAlpha = alpha;
            this.alpha = alpha;
        }

        @Override
        public void tick() {
            super.tick();
            float remaining = (lifetime - age) / (float) lifetime;
            this.alpha = startAlpha * Mth.clamp(remaining * 3f, 0f, 1f);
        }

        @Override
        public @NotNull ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    // ------------------------------------------------------------------ feather
    public static class Feather extends FadingParticle {
        private final float spin;
        private final float swayPhase;

        Feather(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
            super(level, x, y, z, dx, dy, dz, sprites, 1f);
            pickSprite(sprites);
            this.lifetime = 40 + random.nextInt(30);
            this.gravity = 0.012f;
            this.friction = 0.94f;
            this.quadSize = 0.09f + random.nextFloat() * 0.06f;
            this.spin = (random.nextFloat() - 0.5f) * 0.2f;
            this.swayPhase = random.nextFloat() * Mth.TWO_PI;
            this.roll = random.nextFloat() * Mth.TWO_PI;
            this.oRoll = roll;
        }

        @Override
        public void tick() {
            super.tick();
            oRoll = roll;
            roll += spin;
            // gentle side-to-side drift while falling
            float sway = Mth.sin(age * 0.25f + swayPhase) * 0.006f;
            xd += sway;
            zd += sway * 0.6f;
        }
    }

    // ------------------------------------------------------------------ sparkle
    public static class Sparkle extends FadingParticle {
        Sparkle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
            super(level, x, y, z, dx, dy, dz, sprites, 1f);
            this.lifetime = 10 + random.nextInt(10);
            this.gravity = 0f;
            this.friction = 0.88f;
            this.quadSize = 0.08f + random.nextFloat() * 0.1f;
            this.hasPhysics = false;
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (!removed) {
                setSpriteFromAge(sprites);
            }
        }

        @Override
        protected int getLightColor(float partialTick) {
            return FULL_BRIGHT;
        }
    }

    // ------------------------------------------------------------------ cloud puff
    public static class CloudPuff extends FadingParticle {
        private final float growth;

        CloudPuff(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
            super(level, x, y, z, dx, dy, dz, sprites, 0.85f);
            this.lifetime = 20 + random.nextInt(20);
            this.gravity = -0.002f;
            this.friction = 0.9f;
            this.quadSize = 0.25f + random.nextFloat() * 0.25f;
            this.growth = 1.02f + random.nextFloat() * 0.02f;
            this.roll = random.nextFloat() * Mth.TWO_PI;
            this.oRoll = roll;
            float tint = 0.92f + random.nextFloat() * 0.08f;
            setColor(tint, tint, 1f);
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            quadSize *= growth;
            if (!removed) {
                setSpriteFromAge(sprites);
            }
        }
    }

    // ------------------------------------------------------------------ gravity mote
    public static class GravityMote extends FadingParticle {
        GravityMote(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
            super(level, x, y, z, dx, dy, dz, sprites, 1f);
            this.lifetime = 25 + random.nextInt(20);
            this.gravity = -0.03f; // floats upward, like gravitite
            this.friction = 0.92f;
            this.quadSize = 0.06f + random.nextFloat() * 0.06f;
            this.hasPhysics = false;
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (!removed) {
                // loop the 4 frames
                setSprite(sprites.get(age % 8, 8));
            }
        }

        @Override
        protected int getLightColor(float partialTick) {
            return FULL_BRIGHT;
        }
    }

    // ------------------------------------------------------------------ providers
    public record FeatherProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(@NotNull SimpleParticleType type, @NotNull ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new Feather(level, x, y, z, dx, dy, dz, sprites);
        }
    }

    public record SparkleProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(@NotNull SimpleParticleType type, @NotNull ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new Sparkle(level, x, y, z, dx, dy, dz, sprites);
        }
    }

    public record CloudPuffProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(@NotNull SimpleParticleType type, @NotNull ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new CloudPuff(level, x, y, z, dx, dy, dz, sprites);
        }
    }

    public record GravityMoteProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(@NotNull SimpleParticleType type, @NotNull ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new GravityMote(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
