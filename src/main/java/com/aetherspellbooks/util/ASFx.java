package com.aetherspellbooks.util;

import io.redspace.ironsspellbooks.api.util.CameraShakeData;
import io.redspace.ironsspellbooks.api.util.CameraShakeManager;
import io.redspace.ironsspellbooks.particle.BlastwaveParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Server-side visual effect helpers. Everything here is broadcast to nearby clients.
 */
public final class ASFx {
    private ASFx() {
    }

    public static final Vector3f CLOUD_WHITE = new Vector3f(0.92f, 0.97f, 1f);
    public static final Vector3f VALKYRIE_GOLD = new Vector3f(1f, 0.82f, 0.3f);
    public static final Vector3f GRAVITITE_PINK = new Vector3f(0.85f, 0.45f, 0.9f);
    public static final Vector3f SOLAR_ORANGE = new Vector3f(1f, 0.55f, 0.15f);
    public static final Vector3f FROST_CYAN = new Vector3f(0.7f, 0.95f, 1f);
    public static final Vector3f THUNDER_YELLOW = new Vector3f(1f, 0.92f, 0.45f);
    public static final Vector3f HOLYSTONE_GREY = new Vector3f(0.82f, 0.82f, 0.78f);

    /** Particles at random points inside a box around {@code center}. */
    public static void burst(Level level, ParticleOptions particle, Vec3 center, int count, double spread, double speed) {
        if (level instanceof ServerLevel server) {
            server.sendParticles(particle, center.x, center.y, center.z, count, spread, spread, spread, speed);
        }
    }

    /** A flat ring of particles, optionally flung outward. */
    public static void ring(Level level, ParticleOptions particle, Vec3 center, double radius, int count, double outwardSpeed) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        for (int i = 0; i < count; i++) {
            double angle = i * Mth.TWO_PI / count;
            double cx = Math.cos(angle);
            double cz = Math.sin(angle);
            // count = 0 makes the offsets a velocity
            server.sendParticles(particle, center.x + cx * radius, center.y, center.z + cz * radius, 0, cx, 0.05, cz, outwardSpeed);
        }
    }

    /** Particles evenly spaced along a line. */
    public static void line(Level level, ParticleOptions particle, Vec3 from, Vec3 to, int count, double jitter) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        for (int i = 0; i <= count; i++) {
            Vec3 p = from.lerp(to, i / (double) count);
            server.sendParticles(particle, p.x, p.y, p.z, 1, jitter, jitter, jitter, 0);
        }
    }

    /** A spiral of particles rising around a vertical axis. */
    public static void helix(Level level, ParticleOptions particle, Vec3 base, double radius, double height, int count, double turns) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        for (int i = 0; i < count; i++) {
            double t = i / (double) count;
            double angle = t * turns * Mth.TWO_PI;
            server.sendParticles(particle, base.x + Math.cos(angle) * radius, base.y + t * height, base.z + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
        }
    }

    /** Iron's Spells' expanding shockwave ring. */
    public static void blastwave(Level level, Vector3f color, Vec3 pos, float radius) {
        if (level instanceof ServerLevel server) {
            server.sendParticles(new BlastwaveParticleOptions(color, radius), pos.x, pos.y + 0.15, pos.z, 1, 0, 0, 0, 0);
        }
    }

    public static void shake(Level level, Vec3 pos, float radius, int durationTicks) {
        if (!level.isClientSide) {
            CameraShakeManager.addCameraShake(new CameraShakeData(level, durationTicks, pos, radius));
        }
    }
}
