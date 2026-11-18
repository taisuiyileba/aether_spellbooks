package com.aetherspellbooks.gametest;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.registry.ASSpells;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.UUID;

/** Capture the cast, impact and recovery from a camera independent of the caster. */
final class NatureRenderCheck {
    private static ArmorStand camera;
    private static final int FLOWER_SCENE = 96;

    static void tick(Minecraft mc, MinecraftServer server, Vec3 origin, int tick) {
        Vec3 field = Vec3.atBottomCenterOf(BlockPos.containing(origin)).add(0, 32, 0);
        UUID playerId = mc.player.getUUID();
        if (tick == 1) {
            server.execute(() -> prepare(server, field));
            camera = new ArmorStand(EntityType.ARMOR_STAND, mc.level);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }
        boolean flower = tick >= FLOWER_SCENE;
        int frame = flower ? tick - FLOWER_SCENE : tick;
        if (flower && frame == 0) {
            server.execute(() -> {
                server.overworld().getEntities(EntityType.HUSK, e -> e.getTags().contains("asb_nature_target"))
                        .forEach(net.minecraft.world.entity.Entity::discard);
                target(server, field.add(2.9, 0, -0.7));
            });
        }
        Vec3 focus = flower ? field.add(1.0, 1.5, 2.55) : field.add(0, 0.78, 0.25);
        double orbit = flower ? Mth.clamp((frame - 68) / 56.0, 0, 1) : Mth.clamp((frame - 24) / 40.0, 0, 1);
        orbit = Mth.smoothstep(orbit);
        double angle = Math.toRadians(flower ? -80 + orbit * 127 : 16 + orbit * 21);
        double radius = flower ? 6.3 : 9.2;
        Vec3 eye = focus.add(Math.sin(angle) * radius, flower ? 1.15 : 4.65, Math.cos(angle) * radius);
        Vec3 look = focus.subtract(eye);
        float yaw = (float) (-Mth.atan2(look.x, look.z) * Mth.RAD_TO_DEG);
        float pitch = (float) (-Mth.atan2(look.y, look.horizontalDistance()) * Mth.RAD_TO_DEG);
        camera.moveTo(eye.x, eye.y - camera.getEyeHeight(), eye.z, yaw, pitch);
        camera.setYHeadRot(yaw);
        camera.yHeadRotO = yaw;
        camera.setOldPosAndRot();
        if (mc.getCameraEntity() != camera) mc.setCameraEntity(camera);
        int castAt = flower ? 14 : 26;
        if (frame == castAt) {
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    MagicData.getPlayerMagicData(player).setMana(500);
                    AbstractSpell spell = flower ? ASSpells.AECHOR_FLOWER.get() : ASSpells.THORN_SPIKES.get();
                    if (!spell.attemptInitiateCast(player.getMainHandItem(), flower ? 1 : 4,
                            player.level(), player, CastSource.COMMAND, false, "mainhand")) {
                        throw new IllegalStateException("Unable to cast " + spell.getSpellId());
                    }
                }
            });
        }
        if (frame >= castAt - 4 && (flower ? frame <= 206 : frame <= 78)) {
            String filename = String.format(Locale.ROOT, "asb_nature_%s_%03d.png", flower ? "flower" : "thorns", frame);
            Screenshot.grab(mc.gameDirectory, filename, mc.getMainRenderTarget(), ignored -> {});
        }
        if (flower && frame == 220) {
            mc.setCameraEntity(mc.player);
            AetherSpellbooks.LOGGER.info("Nature cast recordings complete");
            mc.stop();
        }
    }

    private static void prepare(MinecraftServer server, Vec3 field) {
        String at = "execute positioned " + coords(field) + " run ";
        command(server, "gamerule doDaylightCycle false");
        command(server, "gamerule doMobSpawning false");
        command(server, "time set 1800");
        command(server, "weather clear");
        command(server, at + "fill ~-12 ~-3 ~-10 ~11 ~-2 ~10 aether:holystone");
        command(server, at + "fill ~-12 ~-1 ~-10 ~11 ~-1 ~10 aether:aether_grass_block");
        command(server, at + "fill ~-9 ~ ~-8 ~7 ~ ~-6 aether:mossy_holystone");
        command(server, at + "fill ~-8 ~1 ~-8 ~6 ~1 ~-7 aether:holystone");
        command(server, at + "fill ~-10 ~ ~-10 ~-5 ~1 ~-6 aether:aether_grass_block");
        command(server, at + "fill ~4 ~ ~-10 ~8 ~1 ~-6 aether:aether_grass_block");
        command(server, at + "place feature aether:skyroot_tree ~-7 ~2 ~-8");
        command(server, at + "place feature aether:golden_oak_tree ~6 ~2 ~-8");
        command(server, at + "setblock ~-5 ~ ~-3 aether:purple_flower");
        command(server, at + "setblock ~-4 ~ ~-4 aether:white_flower");
        command(server, at + "setblock ~5 ~ ~-3 aether:berry_bush");
        command(server, "kill @e[tag=asb_nature_target]");
        for (int i = -1; i <= 1; i++) target(server, field.add(i * 2.25, 0, -3 + Math.abs(i) * 0.7));
        command(server, "gamemode creative @p");
        command(server, "item replace entity @p weapon.mainhand with aether_spellbooks:zanite_staff");
        command(server, "item replace entity @p armor.chest with aether_spellbooks:valkyrie_mage_chestplate");
        command(server, "item replace entity @p armor.legs with aether_spellbooks:valkyrie_mage_leggings");
        command(server, "item replace entity @p armor.feet with aether_spellbooks:valkyrie_mage_boots");
        command(server, "tp @p " + coords(field.add(0, 0, 4.8)) + " 180 6");
    }

    private static void target(MinecraftServer server, Vec3 pos) {
        command(server, "summon minecraft:husk " + coords(pos)
                + " {Tags:[\"asb_nature_target\"],NoAI:1b,PersistenceRequired:1b,Health:60f,attributes:[{id:\"minecraft:generic.max_health\",base:60d}],Rotation:[0f,0f]}");
    }

    private static String coords(Vec3 p) {
        return String.format(Locale.ROOT, "%.2f %.2f %.2f", p.x, p.y, p.z);
    }

    private static void command(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }
}
