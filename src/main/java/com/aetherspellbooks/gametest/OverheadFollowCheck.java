package com.aetherspellbooks.gametest;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.entity.OverheadSpellEntity;
import com.aetherspellbooks.entity.SolarOrb;
import com.aetherspellbooks.registry.ASSpells;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.UUID;

/** Exercise the local predicted player and a separately network-interpolated caster. Development only. */
@EventBusSubscriber(modid = AetherSpellbooks.MODID, value = Dist.CLIENT)
public final class OverheadFollowCheck {
    private static final boolean ENABLED = "overhead".equals(System.getenv("ASB_RENDER_CHECK"));
    private static Villager remote;
    private static Vec3 origin;
    private static final int[] samples = new int[4];
    private static double maxError;

    static void tick(Minecraft mc, MinecraftServer server, int tick) {
        UUID id = mc.player.getUUID();
        if (tick == 1) {
            origin = mc.player.position();
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(id);
                remote = EntityType.VILLAGER.create(player.serverLevel());
                remote.setNoAi(true);
                remote.setPersistenceRequired();
                remote.moveTo(origin.x + 3, origin.y, origin.z, 0, 0);
                player.serverLevel().addFreshEntity(remote);
                cast(ASSpells.AECHOR_FLOWER.get(), player);
                cast(ASSpells.SOLAR_FLARE.get(), remote);
            });
        }
        if (tick == 76) {
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(id);
                var level = player.serverLevel();
                level.getEntitiesOfClass(OverheadSpellEntity.class, player.getBoundingBox().inflate(60),
                        e -> e.getOwner() == player || e.getOwner() == remote).forEach(Entity::discard);
                player.teleportTo(level, origin.x + 9, origin.y + 1, origin.z, 179, 0);
                cast(ASSpells.SOLAR_FLARE.get(), player);
                cast(ASSpells.AECHOR_FLOWER.get(), remote);
            });
        }
        mc.options.keyUp.setDown(tick >= 12 && tick < 138);
        mc.options.keySprint.setDown(tick >= 12 && tick < 138);
        mc.options.keyRight.setDown(tick % 36 < 13 && tick < 138);
        mc.options.keyJump.setDown(tick == 28 || tick == 100);
        if (tick >= 42 && tick < 52 || tick >= 106 && tick < 116) {
            mc.player.setYRot(mc.player.getYRot() + 22);
        }
        if (tick == 62 || tick == 124) mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (tick == 72 || tick == 134) mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        if (tick > 2 && tick < 145) server.execute(() -> {
            remote.setPos(origin.x + 3 + 4 * Math.sin(tick * 0.09), origin.y, origin.z + 3 * Math.cos(tick * 0.09));
            remote.setYRot(Mth.wrapDegrees(tick * 9f));
        });
        if (tick == 39 || tick == 50 || tick == 99 || tick == 114) {
            Screenshot.grab(mc.gameDirectory, "asb_overhead_" + tick + ".png", mc.getMainRenderTarget(), ignored -> {});
        }
        if (tick == 145) {
            for (int count : samples) if (count < 5) throw new IllegalStateException("Missing moving attachment samples");
            // The game uses a sine lookup table; the independent reference uses double precision.
            if (maxError > 0.0001) throw new IllegalStateException("Overhead render drift: " + maxError);
            AetherSpellbooks.LOGGER.info("Overhead follow passed: {} moving frame samples; maximum attachment error {} blocks",
                    java.util.Arrays.toString(samples), maxError);
            mc.stop();
        }
    }

    @SubscribeEvent
    public static void checkFrame(RenderLevelStageEvent event) {
        if (!ENABLED || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || origin == null) return;
        Minecraft mc = Minecraft.getInstance();
        float t = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof OverheadSpellEntity spell) || spell.getOwner() == null) continue;
            LivingEntity caster = spell.getOwner();
            if (Math.abs(caster.getX() - caster.xOld) + Math.abs(caster.getZ() - caster.zOld) < 0.015) continue;
            Vec3 rendered = interpolate(spell, t).add(mc.getEntityRenderDispatcher().getRenderer(spell).getRenderOffset(spell, t));
            boolean sun = spell instanceof SolarOrb;
            double time = mc.level.getGameTime() - 1 + (double) t;
            double yaw = Math.toRadians(Mth.rotLerp(t, caster.yRotO, caster.getYRot()));
            Vec3 expected = interpolate(caster, t).add(sun ? 0.6 * Math.sin(yaw) : 0,
                    caster.getBbHeight() + (sun ? 1.4 + 0.15 * Math.sin(time * 0.12) : 0.83 + 0.07 * Math.sin(time * 0.15)),
                    sun ? -0.6 * Math.cos(yaw) : 0);
            maxError = Math.max(maxError, rendered.distanceTo(expected));
            if (!spell.getBoundingBoxForCulling().contains(rendered)) throw new IllegalStateException("Attachment outside culling bounds");
            samples[(caster == mc.player ? 0 : 2) + (sun ? 1 : 0)]++;
        }
    }

    private static Vec3 interpolate(Entity e, float t) {
        return new Vec3(e.xOld, e.yOld, e.zOld).lerp(e.position(), t);
    }

    private static void cast(AbstractSpell spell, LivingEntity caster) {
        spell.onCast(caster.level(), 3, caster, CastSource.COMMAND, MagicData.getPlayerMagicData(caster));
    }
}
