package com.aetherspellbooks.gametest;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherteam.aether.entity.AetherEntityTypes;
import com.aetherteam.aether.entity.monster.dungeon.boss.Slider;
import com.mojang.authlib.GameProfile;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.entity.spells.spectral_hammer.SpectralHammer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;

import java.util.UUID;

/** ASB_RENDER_CHECK=slider: capture the complete hammer swing from front and side. Not shipped. */
public final class SliderRenderCheck {
    private static ServerPlayer caster;

    private SliderRenderCheck() {}

    static void tick(Minecraft mc, MinecraftServer server, Vec3 origin, int timer) {
        int view = timer / 100;
        int frame = timer % 100;
        if (view >= 2) {
            AetherSpellbooks.LOGGER.info("Slider render check finished");
            mc.stop();
            return;
        }
        mc.options.hideGui = true;
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.options.fov().set(55);
        mc.player.getAbilities().flying = true;
        if (mc.screen != null) {
            mc.setScreen(null);
        }
        if (frame == 10) {
            UUID observer = mc.player.getUUID();
            server.execute(() -> {
                var level = server.overworld();
                for (Entity entity : level.getEntitiesOfClass(Entity.class, new AABB(origin, origin).inflate(30),
                        e -> e instanceof Slider || e instanceof SpectralHammer)) {
                    entity.discard();
                }
                if (caster != null) {
                    level.removePlayerImmediately(caster, Entity.RemovalReason.DISCARDED);
                }
                Slider slider = AetherEntityTypes.SLIDER.get().create(level);
                slider.setPos(origin);
                slider.setNoAi(true);
                slider.setNoGravity(true);
                slider.setAwake(true);
                slider.setBossFight(true);
                level.addFreshEntity(slider);
                caster = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "hammer-visual"));
                caster.moveTo(origin.x, origin.y, origin.z + 7, 180, 0);
                caster.setYHeadRot(180);
                caster.setNoGravity(true);
                caster.setInvisible(true);
                level.addNewPlayer(caster);
                ServerPlayer player = server.getPlayerList().getPlayer(observer);
                Vec3 camera = origin.add(view == 0 ? new Vec3(6, 2, 6) : new Vec3(8, 1.7, 0));
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
                player.teleportTo(camera.x, camera.y, camera.z);
                player.setYRot(view == 0 ? 135 : 90);
                player.setXRot(17);
                player.connection.teleport(camera.x, camera.y, camera.z, player.getYRot(), player.getXRot());
            });
        }
        if (frame == 40) {
            server.execute(() -> SpellRegistry.SPECTRAL_HAMMER_SPELL.get().onCast(caster.level(), 1, caster,
                    CastSource.COMMAND, MagicData.getPlayerMagicData(caster)));
        }
        if (frame >= 41 && frame <= 72) {
            String name = String.format(java.util.Locale.ROOT, "asb_slider_%s_%02d.png", view == 0 ? "front" : "side", frame - 41);
            Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> {});
        }
    }
}
