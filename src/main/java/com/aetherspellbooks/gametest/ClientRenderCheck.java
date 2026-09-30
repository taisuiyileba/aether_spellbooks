package com.aetherspellbooks.gametest;

import com.aetherspellbooks.AetherSpellbooks;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import com.aetherteam.aether.data.resources.registries.AetherDimensions;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Development-only visual check (excluded from the jar). With the environment variable ASB_RENDER_CHECK set to
 * "mobs" (or "1"), "armor", "weapons", "items", "spells" or "shrines", the client creates a flat world, sets up that scene, saves screenshots
 * (run/screenshots/asb_*.png) and quits.
 */
@EventBusSubscriber(modid = AetherSpellbooks.MODID, value = Dist.CLIENT)
public class ClientRenderCheck {
    private static final String SCENE = System.getenv("ASB_RENDER_CHECK");
    private static final boolean ENABLED = SCENE != null && !SCENE.isEmpty();
    private static final String WORLD = "asb_render_check_" + SCENE;
    private static final String[] PIECES = {"helmet", "chestplate", "leggings", "boots"};
    private static final String[] SLOTS = {"armor.head", "armor.chest", "armor.legs", "armor.feet"};
    private static int stage;
    private static int timer;
    private static Vec3 origin;
    private static List<Shot> shots;

    private enum Mode {
        /** world only, no GUI or hand */
        WORLD,
        /** first person with the held item visible */
        HAND,
        /** the player's inventory screen */
        INVENTORY
    }

    /** A spell the player casts {@code count} times (8 ticks apart) once the camera is in place; the screenshot is taken
     * {@code delay} ticks after the last cast. */
    private record SpellCast(String spell, int level, int delay, int count) {
        int shotTick() {
            return CAST_AT + (count - 1) * 8 + delay;
        }
    }

    /** camera offset from the scene origin, yaw, pitch, camera type, commands to run before the shot, what to show */
    private record Shot(String name, Vec3 offset, float yaw, float pitch, CameraType camera, List<String> commands, Mode mode, SpellCast cast) {
        Shot(String name, Vec3 offset, float yaw, float pitch, CameraType camera, List<String> commands, Mode mode) {
            this(name, offset, yaw, pitch, camera, commands, mode, null);
        }

        Shot(String name, Vec3 offset, float yaw, float pitch) {
            this(name, offset, yaw, pitch, CameraType.FIRST_PERSON, List.of(), Mode.WORLD);
        }

        Shot(String name, Vec3 offset, float yaw, float pitch, CameraType camera, List<String> commands) {
            this(name, offset, yaw, pitch, camera, commands, Mode.WORLD);
        }
    }

    private static final int CAST_AT = 20;

    /** The shrines: placed in the flat test world, then the nearest naturally generated one in the Aether. */
    private record ShrineShot(String name, String structure, boolean aether, int side) {
    }

    private static final List<ShrineShot> SHRINE_SHOTS = List.of(
            new ShrineShot("asb_shrine_flat_valkyrie_sanctum", "valkyrie_sanctum", false, 0),
            new ShrineShot("asb_shrine_flat_solar_altar", "solar_altar", false, 1),
            new ShrineShot("asb_shrine_aether_valkyrie_sanctum", "valkyrie_sanctum", true, 0),
            new ShrineShot("asb_shrine_aether_valkyrie_sanctum_near", "valkyrie_sanctum", true, 0),
            new ShrineShot("asb_shrine_aether_solar_altar", "solar_altar", true, 0),
            new ShrineShot("asb_shrine_aether_solar_altar_near", "solar_altar", true, 0));
    private static int[] shrineChunk;
    private static final String[] SPELL_ENTITIES = {"radiant_javelin", "storm_cloud", "storm_bolt", "spectral_aerwhale", "icestone_meteor"};

    private static final String[] STAFFS = {"zanite_staff", "aercloud_staff", "gravitite_staff", "valkyrie_scepter", "solar_scepter"};
    private static final String[] BLADES = {"sunfire_spellblade", "stormcaller_spellblade", "hallowed_spellblade", "sanguine_spellblade"};

    private static final String[] ICONS = {
            "aether_spellbooks:slider_codex", "aether_spellbooks:valkyrie_grimoire", "aether_spellbooks:solar_codex",
            "irons_spellbooks:gold_spell_book", "irons_spellbooks:diamond_spell_book", "irons_spellbooks:blaze_spell_book",
            "aether_spellbooks:zanite_focus_pendant", "aether:zanite_pendant", "aether:golden_pendant",
            "aether_spellbooks:valkyrie_mage_helmet", "aether_spellbooks:valkyrie_mage_chestplate", "aether_spellbooks:valkyrie_mage_leggings",
            "aether_spellbooks:valkyrie_mage_boots", "aether:valkyrie_helmet", "aether:valkyrie_chestplate", "aether:valkyrie_leggings",
            "aether:valkyrie_boots", "minecraft:iron_boots",
            "aether_spellbooks:phoenix_mage_helmet", "aether_spellbooks:phoenix_mage_chestplate", "aether_spellbooks:phoenix_mage_leggings",
            "aether_spellbooks:phoenix_mage_boots", "aether:phoenix_helmet", "aether:phoenix_chestplate", "aether:phoenix_leggings",
            "aether:phoenix_boots", "minecraft:diamond_boots",
            "aether_spellbooks:gravitite_casting_gloves", "aether:gravitite_gloves", "aether:zanite_gloves",
            "aether_spellbooks:aether_arcane_upgrade_smithing_template", "minecraft:netherite_upgrade_smithing_template",
            "minecraft:ward_armor_trim_smithing_template"};

    private static List<String> setupCommands() {
        String y = fmt(origin.y), z = fmt(origin.z);
        List<String> commands = new ArrayList<>(List.of("gamerule doDaylightCycle false", "gamerule doMobSpawning false", "time set noon", "weather clear"));
        if ("weapons".equals(SCENE)) {
            // a wall of item frames (staffs above, spellblades below) and armour stands holding them
            double wz = origin.z - 2;
            int bx = (int) Math.floor(origin.x), by = (int) Math.floor(origin.y), bz = (int) Math.floor(wz);
            commands.add("fill " + (bx - 5) + " " + by + " " + bz + " " + (bx + 5) + " " + (by + 3) + " " + bz + " minecraft:smooth_quartz");
            for (int i = 0; i < STAFFS.length; i++) {
                commands.add("summon item_frame " + fmt(origin.x - 2 + i) + " " + fmt(origin.y + 2) + " " + fmt(wz + 1) + " {Facing:3b,Fixed:1b,Invisible:1b,Item:{id:\"aether_spellbooks:" + STAFFS[i] + "\",Count:1b}}");
            }
            for (int i = 0; i < BLADES.length; i++) {
                commands.add("summon item_frame " + fmt(origin.x - 1.5 + i) + " " + fmt(origin.y + 1) + " " + fmt(wz + 1) + " {Facing:3b,Fixed:1b,Invisible:1b,Item:{id:\"aether_spellbooks:" + BLADES[i] + "\",Count:1b}}");
            }
            String[][] stands = {{"solar_scepter", "sunfire_spellblade", "phoenix_mage"}, {"valkyrie_scepter", "hallowed_spellblade", "valkyrie_mage"},
                    {"aercloud_staff", "stormcaller_spellblade", null}};
            for (int i = 0; i < stands.length; i++) {
                String armor = stands[i][2] == null ? "" : ",ArmorItems:" + armorItems(stands[i][2]);
                commands.add("summon armor_stand " + fmt(origin.x - 1.6 + 1.6 * i) + " " + y + " " + fmt(origin.z + 0.5) + " {ShowArms:1b,NoBasePlate:1b,Rotation:[0f,0f],"
                        + "HandItems:[{id:\"aether_spellbooks:" + stands[i][0] + "\",Count:1b},{id:\"aether_spellbooks:" + stands[i][1] + "\",Count:1b}]" + armor + "}");
            }
        } else if ("spells".equals(SCENE)) {
            // a quartz wall behind the targets and a pond beside them (for the meteor's freeze); the husks are placed per shot
            int bx = (int) Math.floor(origin.x), by = (int) Math.floor(origin.y), bz = (int) Math.floor(origin.z);
            // the world is reused between runs: clear what other scenes left behind
            commands.addAll(List.of("kill @e[type=minecraft:armor_stand]", "kill @e[type=minecraft:item_frame]", "kill @e[type=minecraft:item]", "clear @p"));
            commands.add("fill " + (bx - 7) + " " + by + " " + (bz - 4) + " " + (bx + 7) + " " + (by + 4) + " " + (bz - 4) + " minecraft:smooth_quartz");
            commands.add("fill " + (bx + 2) + " " + (by - 1) + " " + (bz - 2) + " " + (bx + 5) + " " + (by - 1) + " " + (bz + 1) + " minecraft:water");
        } else if ("shrines".equals(SCENE)) {
            // the shrines are placed or found per shot
        } else if ("items".equals(SCENE)) {
            // inventory icons only
        } else if ("armor".equals(SCENE)) {
            // an armour stand in each set, facing the camera (south)
            commands.add("summon armor_stand " + fmt(origin.x - 1.2) + " " + y + " " + z + " {ShowArms:1b,NoBasePlate:1b,Rotation:[0f,0f],ArmorItems:" + armorItems("valkyrie_mage") + "}");
            commands.add("summon armor_stand " + fmt(origin.x + 1.2) + " " + y + " " + z + " {ShowArms:1b,NoBasePlate:1b,Rotation:[0f,0f],ArmorItems:" + armorItems("phoenix_mage") + "}");
        } else {
            // a sorceress and an acolyte standing, both facing the camera, and a sorceress hovering with spread wings
            commands.add("summon aether_spellbooks:valkyrie_sorceress " + fmt(origin.x - 1.4) + " " + y + " " + z + " {NoAI:1b,Rotation:[0f,0f]}");
            commands.add("summon aether_spellbooks:solar_acolyte " + fmt(origin.x + 1.4) + " " + y + " " + z + " {NoAI:1b,Rotation:[0f,0f]}");
            commands.add("summon aether_spellbooks:valkyrie_sorceress " + fmt(origin.x) + " " + fmt(origin.y + 1.6) + " " + fmt(origin.z - 1.5) + " {NoAI:1b,NoGravity:1b,Rotation:[0f,0f]}");
        }
        return commands;
    }

    private static List<Shot> sceneShots() {
        if ("weapons".equals(SCENE)) {
            List<String> give = new ArrayList<>(List.of("clear @p"));
            for (String item : STAFFS) {
                give.add("give @p aether_spellbooks:" + item);
            }
            for (String item : BLADES) {
                give.add("give @p aether_spellbooks:" + item);
            }
            return List.of(
                    new Shot("asb_weapons_wall", new Vec3(0, 0.1, 3.6), 180, 12),
                    new Shot("asb_weapons_stands", new Vec3(0, -0.4, 3.4), 180, 4),
                    new Shot("asb_weapons_thirdperson", new Vec3(0, 0, 5), 180, 5, CameraType.THIRD_PERSON_FRONT,
                            List.of("item replace entity @p weapon.mainhand with aether_spellbooks:solar_scepter",
                                    "item replace entity @p weapon.offhand with aether_spellbooks:hallowed_spellblade")),
                    new Shot("asb_weapons_hand", new Vec3(0, 0, 5), 180, 10, CameraType.FIRST_PERSON, List.of("item replace entity @p weapon.mainhand with aether_spellbooks:solar_scepter"), Mode.HAND),
                    new Shot("asb_weapons_inventory", new Vec3(0, 0, 5), 180, 10, CameraType.FIRST_PERSON, give, Mode.INVENTORY));
        }
        if ("spells".equals(SCENE)) {
            Vec3 front = new Vec3(0, 0, 7);
            Vec3 back = new Vec3(0, 1, 12);
            return List.of(
                    spellShot("asb_spell_valkyrie_lunge", back, 4, "valkyrie_lunge", 6, 3),
                    // level 1 chains two dashes: the second ends in the Valkyrie's Verdict shockwave
                    spellShot("asb_spell_valkyrie_lunge_verdict", front, 4, "valkyrie_lunge", 1, 7, 2, CameraType.THIRD_PERSON_BACK),
                    spellShot("asb_spell_radiant_javelin_flight", front, 4, "radiant_javelin", 5, 3),
                    spellShot("asb_spell_radiant_javelin_planted", front, 22, "radiant_javelin", 5, 6),
                    spellShot("asb_spell_radiant_javelin_erupt", front, 22, "radiant_javelin", 5, 13),
                    spellShot("asb_spell_thunderhead", back, 8, "thunderhead", 4, 18),
                    spellShot("asb_spell_thunderhead_late", back, 8, "thunderhead", 4, 40),
                    spellShot("asb_spell_aerwhale_song", back, -14, "aerwhale_song", 3, 46),
                    spellShot("asb_spell_icestone_meteor_fall", back, 10, "icestone_meteor", 3, 7),
                    spellShot("asb_spell_icestone_meteor_impact", back, 10, "icestone_meteor", 3, 15));

        }
        if ("items".equals(SCENE)) {
            // our icons in the hotbar/first rows, next to Iron's Spells, Aether and vanilla ones for comparison
            // survival so the inventory screen shows every row, not the creative tabs
            List<String> give = new ArrayList<>(List.of("gamemode survival @p", "clear @p"));
            for (String item : ICONS) {
                give.add("give @p " + item);
            }
            return List.of(new Shot("asb_items_inventory", new Vec3(0, 0, 5), 180, 10, CameraType.FIRST_PERSON, give, Mode.INVENTORY));
        }
        if ("armor".equals(SCENE)) {
            return List.of(
                    new Shot("asb_armor_front", new Vec3(0, 0.3, 3.4), 180, 6),
                    new Shot("asb_armor_back", new Vec3(0, 0.5, -3.4), 0, 8),
                    new Shot("asb_armor_side", new Vec3(3.6, 0.6, 0), 90, 8),
                    new Shot("asb_player_valkyrie_back", new Vec3(0, 0, 6), 180, 10, CameraType.THIRD_PERSON_BACK, equip("valkyrie_mage")),
                    new Shot("asb_player_phoenix_front", new Vec3(0, 0, 6), 180, 5, CameraType.THIRD_PERSON_FRONT, equip("phoenix_mage")),
                    new Shot("asb_player_phoenix_back", new Vec3(0, 0, 6), 180, 10, CameraType.THIRD_PERSON_BACK, List.of()));
        }
        return List.of(
                new Shot("asb_front", new Vec3(0, 0.6, 4.2), 180, 8),
                new Shot("asb_back", new Vec3(0, 0.9, -4.2), 0, 10),
                new Shot("asb_side", new Vec3(4.5, 1.2, 0.3), 90, 12));
    }

    /** Fresh husks (no AI, so they stay in line), leftovers of the previous shot removed, then a spell cast. */
    private static Shot spellShot(String name, Vec3 offset, float pitch, String spell, int level, int delay) {
        return spellShot(name, offset, pitch, spell, level, delay, 1);
    }

    private static Shot spellShot(String name, Vec3 offset, float pitch, String spell, int level, int delay, int count) {
        return spellShot(name, offset, pitch, spell, level, delay, count, CameraType.FIRST_PERSON);
    }

    private static Shot spellShot(String name, Vec3 offset, float pitch, String spell, int level, int delay, int count, CameraType camera) {
        List<String> commands = new ArrayList<>(List.of("kill @e[type=minecraft:husk]", "kill @e[type=minecraft:item]", "effect clear @p", "gamemode survival @p", "effect give @p minecraft:resistance 999 4 true"));
        for (String type : SPELL_ENTITIES) {
            commands.add("kill @e[type=aether_spellbooks:" + type + "]");
        }
        for (int i = -2; i <= 2; i++) {
            commands.add("summon minecraft:husk " + fmt(origin.x + i * 1.6) + " " + fmt(origin.y) + " " + fmt(origin.z) + " {NoAI:1b,PersistenceRequired:1b,Rotation:[0f,0f]}");
        }
        return new Shot(name, offset, 180, pitch, camera, commands, Mode.WORLD, new SpellCast(AetherSpellbooks.MODID + ":" + spell, level, delay, count));
    }

    private static void castOnServer(MinecraftServer server, UUID playerId, SpellCast cast, boolean first) {
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            AbstractSpell spell = SpellRegistry.getSpell(cast.spell());
            if (player != null && spell != null) {
                if (first) {
                    // start every shot with a fresh recast chain
                    MagicData.getPlayerMagicData(player).getPlayerRecasts().removeRecast(cast.spell());
                }
                spell.onCast(player.level(), cast.level(), player, CastSource.COMMAND, MagicData.getPlayerMagicData(player));
            }
        });
    }

    private static String armorItems(String set) {
        return "[{id:\"aether_spellbooks:" + set + "_boots\",Count:1b},{id:\"aether_spellbooks:" + set + "_leggings\",Count:1b},"
                + "{id:\"aether_spellbooks:" + set + "_chestplate\",Count:1b},{id:\"aether_spellbooks:" + set + "_helmet\",Count:1b}]";
    }

    private static List<String> equip(String set) {
        List<String> commands = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            commands.add("item replace entity @p " + SLOTS[i] + " with aether_spellbooks:" + set + "_" + PIECES[i]);
        }
        return commands;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ENABLED) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        // keep running while the window is in the background, and un-minimise it (a minimised window renders 1x1 shots)
        mc.options.pauseOnLostFocus = false;
        if (mc.getWindow().getWidth() < 64 || mc.getWindow().getHeight() < 64) {
            org.lwjgl.glfw.GLFW.glfwRestoreWindow(mc.getWindow().getWindow());
        }
        timer++;
        if (stage == 0) {
            if (mc.getOverlay() == null && mc.screen != null && mc.level == null && timer > 60) {
                stage = 1;
                LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.NORMAL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
                // the shrines scene needs real structure generation in the Aether
                mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(1L, "shrines".equals(SCENE), false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), null);
            }
            return;
        }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) {
            timer = 0;
            return;
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (stage == 1) {
            if (timer <= 100) {
                return;
            }
            stage = 2;
            timer = 0;
            mc.options.hideGui = true;
            origin = mc.player.position().add(0, 0, -6);
            shots = sceneShots();
            run(server, setupCommands().toArray(String[]::new));
            return;
        }
        int shot = stage - 2;
        if ("shrines".equals(SCENE)) {
            if (shot < SHRINE_SHOTS.size()) {
                shrineShot(mc, server, SHRINE_SHOTS.get(shot));
                return;
            }
            if (timer == 10) {
                AetherSpellbooks.LOGGER.info("render check finished");
                mc.stop();
            }
            return;
        }
        if (shot < shots.size()) {
            Shot s = shots.get(shot);
            if (s.mode != Mode.INVENTORY && mc.screen != null) {
                mc.setScreen(null);
            }
            mc.options.hideGui = s.mode == Mode.WORLD;
            if (s.mode == Mode.INVENTORY && timer == 30 && !(mc.screen instanceof InventoryScreen)) {
                mc.setScreen(new InventoryScreen(mc.player));
            }
            if (timer == 1) {
                Vec3 camera = origin.add(s.offset);
                mc.options.setCameraType(s.camera);
                List<String> commands = new ArrayList<>(s.commands);
                commands.add("tp @p " + fmt(camera.x) + " " + fmt(camera.y) + " " + fmt(camera.z) + " " + s.yaw + " " + s.pitch);
                run(server, commands.toArray(String[]::new));
            } else if (s.cast != null && timer >= CAST_AT && (timer - CAST_AT) % 8 == 0 && (timer - CAST_AT) / 8 < s.cast.count()) {
                castOnServer(server, mc.player.getUUID(), s.cast, timer == CAST_AT);
            }
            if (timer == (s.cast == null ? 60 : s.cast.shotTick())) {
                Screenshot.grab(mc.gameDirectory, s.name + ".png", mc.getMainRenderTarget(), message -> AetherSpellbooks.LOGGER.info("render check: {}", message.getString()));
                stage++;
                timer = 0;
            }
            return;
        }
        if (timer == 10) {
            AetherSpellbooks.LOGGER.info("render check finished");
            mc.stop();
        }
    }

    /**
     * 1: place the shrine (flat world) or find the nearest one in the Aether and fly over it so its chunks load;
     * 80: frame the camera on the structure's actual bounding box; 150: screenshot.
     */
    private static void shrineShot(Minecraft mc, MinecraftServer server, ShrineShot s) {
        mc.options.hideGui = true;
        String id = AetherSpellbooks.MODID + ":" + s.structure();
        boolean near = s.name().endsWith("_near");
        if (timer == 1) {
            server.execute(() -> {
                ServerLevel level = s.aether() ? server.getLevel(AetherDimensions.AETHER_LEVEL) : server.overworld();
                var holder = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                        .getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, net.minecraft.resources.ResourceLocation.tryParse(id)));
                String dim = s.aether() ? "aether:the_aether" : "minecraft:overworld";
                if (s.aether()) {
                    var found = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(holder), net.minecraft.core.BlockPos.ZERO, 60, false);
                    if (found == null) {
                        AetherSpellbooks.LOGGER.warn("render check: no {} found in the Aether", id);
                        shrineChunk = null;
                        return;
                    }
                    shrineChunk = new int[]{found.getFirst().getX() >> 4, found.getFirst().getZ() >> 4};
                    AetherSpellbooks.LOGGER.info("render check: {} generated at chunk {} {}", id, shrineChunk[0], shrineChunk[1]);
                } else {
                    int x = (int) origin.x + 48 * s.side(), z = (int) origin.z - 32;
                    run(server, "place structure " + id + " " + x + " " + (int) origin.y + " " + z);
                    // /place does not record a structure start: frame the chunk the shrine was built in
                    int cx = (x >> 4) * 16 + 8, cz = (z >> 4) * 16 + 8;
                    run(server, "gamemode spectator @p", "tp @p " + cx + " " + ((int) origin.y + 10) + " " + (cz + 26) + " 180 20");
                    shrineChunk = null;
                    return;
                }
                run(server, "gamemode spectator @p", "execute in " + dim + " run tp @p " + (shrineChunk[0] * 16 + 8) + " 160 " + (shrineChunk[1] * 16 + 40) + " 180 40");
            });
        } else if (timer == 80) {
            server.execute(() -> {
                if (shrineChunk == null) {
                    return;
                }
                ServerLevel level = s.aether() ? server.getLevel(AetherDimensions.AETHER_LEVEL) : server.overworld();
                Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(net.minecraft.resources.ResourceLocation.tryParse(id));
                BoundingBox box = null;
                for (int dx = -1; dx <= 1 && box == null; dx++) {
                    for (int dz = -1; dz <= 1 && box == null; dz++) {
                        StructureStart start = level.getChunk(shrineChunk[0] + dx, shrineChunk[1] + dz).getStartForStructure(structure);
                        if (start != null && start.isValid()) {
                            // the start's box is widened by 12 for the terrain beard: frame the pieces themselves
                            box = start.getPieces().get(0).getBoundingBox();
                        }
                    }
                }
                if (box == null) {
                    AetherSpellbooks.LOGGER.warn("render check: {} start not found near chunk {} {}", id, shrineChunk[0], shrineChunk[1]);
                    return;
                }
                AetherSpellbooks.LOGGER.info("render check: {} bounding box {}", id, box);
                var c = box.getCenter();
                String dim = s.aether() ? "aether:the_aether" : "minecraft:overworld";
                double back = near ? 9 : 18;
                double up = near ? 7 : 16;
                run(server, "execute in " + dim + " run tp @p " + c.getX() + " " + (box.minY() + up) + " " + (box.maxZ() + back) + " 180 " + (near ? 18 : 30));
            });
        } else if (timer == 150) {
            Screenshot.grab(mc.gameDirectory, s.name() + ".png", mc.getMainRenderTarget(), message -> AetherSpellbooks.LOGGER.info("render check: {}", message.getString()));
            stage++;
            timer = 0;
        }
    }

    private static void run(MinecraftServer server, String... commands) {
        server.execute(() -> {
            for (String command : commands) {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
            }
        });
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }
}
