package com.aetherspellbooks.gametest;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.dungeon.GuardianKind;
import com.aetherspellbooks.dungeon.GuardianLair;
import com.aetherspellbooks.dungeon.GuardianLairs;
import com.aetherspellbooks.dungeon.RoomSnapshot;
import com.aetherspellbooks.entity.GuardianSoul;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/**
 * ASB_RENDER_CHECK=dungeon: the three vaults idle, awake and from above, the three guardian souls at night, a revival
 * ritual under way, and the new icons in the inventory. Not shipped.
 */
public final class DungeonRenderCheck {
    private static final int VIEW_TICKS = 140;
    private static final String[] TIERS = {"carved", "angelic", "hellfire"};

    /** camera offset from the origin, yaw, pitch, screenshot name */
    private record View(String name, Vec3 camera, float yaw, float pitch) {
    }

    private static final List<View> VIEWS = List.of(
            new View("asb_dungeon_vaults_idle", new Vec3(0, 1.2, 6.5), 180, 10),
            new View("asb_dungeon_vaults_active", new Vec3(0, 0.6, 3.2), 180, 14),
            new View("asb_dungeon_vaults_top", new Vec3(0, 4.5, 2.2), 180, 62),
            new View("asb_dungeon_souls_night", new Vec3(0, 1.6, 2.5), 180, 6),
            new View("asb_dungeon_souls_day", new Vec3(0, 1.6, 2.5), 180, 6),
            new View("asb_dungeon_ritual", new Vec3(0, 2.2, 3.5), 180, -8),
            new View("asb_dungeon_inventory", new Vec3(0, 1, 8), 180, 10));

    private DungeonRenderCheck() {
    }

    static void tick(Minecraft mc, MinecraftServer server, Vec3 origin, int timer) {
        int index = timer / VIEW_TICKS;
        int frame = timer % VIEW_TICKS;
        if (index >= VIEWS.size()) {
            AetherSpellbooks.LOGGER.info("dungeon render check finished");
            mc.stop();
            return;
        }
        View view = VIEWS.get(index);
        boolean inventory = view.name.endsWith("inventory");
        mc.options.hideGui = !inventory;
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (!inventory && mc.screen != null) {
            mc.setScreen(null);
        }
        Vec3 souls = origin.add(0, 0, -5);
        if (frame == 5) {
            server.execute(() -> setUp(server, mc.player.getUUID(), origin, souls, view, index));
        }
        if (view.name.endsWith("ritual") && frame == 30) {
            server.execute(() -> server.overworld().getEntitiesOfClass(GuardianSoul.class, new AABB(souls, souls).inflate(4))
                    .stream().filter(soul -> soul.kind() == GuardianKind.SLIDER).findFirst()
                    .ifPresent(soul -> soul.beginRitual(server.getPlayerList().getPlayer(mc.player.getUUID()))));
        }
        if (inventory && frame == 60 && !(mc.screen instanceof InventoryScreen)) {
            mc.setScreen(new InventoryScreen(mc.player));
        }
        if (frame == (view.name.endsWith("ritual") ? 100 : 110)) {
            Screenshot.grab(mc.gameDirectory, view.name + ".png", mc.getMainRenderTarget(),
                    message -> AetherSpellbooks.LOGGER.info("render check: {}", message.getString()));
        }
    }

    private static void setUp(MinecraftServer server, java.util.UUID observer, Vec3 origin, Vec3 souls, View view, int index) {
        ServerLevel level = server.overworld();
        ServerPlayer player = server.getPlayerList().getPlayer(observer);
        int bx = (int) Math.floor(origin.x), by = (int) Math.floor(origin.y), bz = (int) Math.floor(origin.z);
        if (index == 0) {
            // the render-check world is reused: clear what earlier runs left, then a carved-stone floor and back wall
            AABB area = new AABB(origin, origin).inflate(24);
            level.getEntitiesOfClass(Entity.class, area, e -> !(e instanceof Player)).forEach(Entity::discard);
            GuardianLairs.get(level).forgetWithin(area);
            run(server, "fill " + (bx - 8) + " " + by + " " + (bz - 9) + " " + (bx + 8) + " " + (by + 6) + " " + (bz + 9) + " minecraft:air",
                    "fill " + (bx - 8) + " " + (by - 1) + " " + (bz - 9) + " " + (bx + 8) + " " + (by - 1) + " " + (bz + 9) + " aether:carved_stone",
                    "fill " + (bx - 8) + " " + by + " " + (bz - 9) + " " + (bx + 8) + " " + (by + 5) + " " + (bz - 9) + " aether:carved_stone",
                    "gamerule doDaylightCycle false", "gamerule doMobSpawning false", "weather clear", "clear @p");
            for (int i = 0; i < TIERS.length; i++) {
                run(server, "setblock " + (bx - 2 + 2 * i) + " " + by + " " + bz + " aether_spellbooks:" + TIERS[i] + "_vault[facing=south]");
            }
            for (GuardianKind kind : GuardianKind.values()) {
                Vec3 spot = souls.add((kind.ordinal() - 1) * 2.2, 0.4, 0);
                AABB room = new AABB(spot, spot).inflate(3);
                GuardianLair lair = new GuardianLair(kind, spot, room, RoomSnapshot.capture(level, room, kind.conversions()), null);
                GuardianLairs.get(level).put(lair);
                GuardianLairs.get(level).markDefeated(lair, null, null);
                GuardianSoul soul = new GuardianSoul(level, kind, lair);
                soul.moveTo(lair.soulHome());
                level.addFreshEntity(soul);
            }
        }
        run(server, view.name.endsWith("night") || view.name.endsWith("ritual") ? "time set midnight" : "time set noon");
        level.getEntitiesOfClass(ItemEntity.class, new AABB(origin, origin).inflate(24)).forEach(Entity::discard);
        level.getEntitiesOfClass(Mob.class, new AABB(origin, origin).inflate(24)).forEach(Entity::discard);
        if (view.name.endsWith("inventory")) {
            run(server, "gamemode survival @p", "clear @p",
                    "give @p aether_spellbooks:guardian_shard 12", "give @p aether_spellbooks:sentry_core",
                    "give @p aether_spellbooks:valkyrie_oath", "give @p aether_spellbooks:solar_ember",
                    "give @p aether_spellbooks:carved_vault", "give @p aether_spellbooks:angelic_vault", "give @p aether_spellbooks:hellfire_vault",
                    "give @p aether:bronze_dungeon_key", "give @p aether:silver_dungeon_key", "give @p aether:gold_dungeon_key",
                    "give @p aether:victory_medal", "give @p aether:golden_amber", "give @p aether:treasure_chest",
                    "give @p irons_spellbooks:dead_king_phylactery", "give @p irons_spellbooks:bone_key", "give @p irons_spellbooks:bone_vault");
        } else {
            run(server, "gamemode creative @p");
        }
        Vec3 camera = origin.add(view.camera);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.teleportTo(camera.x, camera.y, camera.z);
        player.connection.teleport(camera.x, camera.y, camera.z, view.yaw, view.pitch);
        AetherSpellbooks.LOGGER.info(String.format(Locale.ROOT, "render check: %s at %.1f %.1f %.1f", view.name, camera.x, camera.y, camera.z));
    }

    private static void run(MinecraftServer server, String... commands) {
        for (String command : commands) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
        }
    }
}
