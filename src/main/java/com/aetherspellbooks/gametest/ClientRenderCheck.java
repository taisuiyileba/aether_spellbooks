package com.aetherspellbooks.gametest;

import com.aetherspellbooks.AetherSpellbooks;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Development-only visual check (excluded from the jar). With the environment variable ASB_RENDER_CHECK set to
 * "mobs" (or "1") or "armor", the client creates a flat world, sets up that scene, saves screenshots
 * (run/screenshots/asb_*.png) and quits.
 */
@Mod.EventBusSubscriber(modid = AetherSpellbooks.MODID, value = Dist.CLIENT)
public class ClientRenderCheck {
    private static final String SCENE = System.getenv("ASB_RENDER_CHECK");
    private static final boolean ENABLED = SCENE != null && !SCENE.isEmpty();
    private static final String WORLD = "asb_render_check";
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

    /** camera offset from the scene origin, yaw, pitch, camera type, commands to run before the shot, what to show */
    private record Shot(String name, Vec3 offset, float yaw, float pitch, CameraType camera, List<String> commands, Mode mode) {
        Shot(String name, Vec3 offset, float yaw, float pitch) {
            this(name, offset, yaw, pitch, CameraType.FIRST_PERSON, List.of(), Mode.WORLD);
        }

        Shot(String name, Vec3 offset, float yaw, float pitch, CameraType camera, List<String> commands) {
            this(name, offset, yaw, pitch, camera, commands, Mode.WORLD);
        }
    }

    private static final String[] STAFFS = {"zanite_staff", "aercloud_staff", "gravitite_staff", "valkyrie_scepter", "solar_scepter"};
    private static final String[] BLADES = {"sunfire_spellblade", "stormcaller_spellblade", "hallowed_spellblade", "sanguine_spellblade"};

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
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        // keep running while the window is in the background
        mc.options.pauseOnLostFocus = false;
        timer++;
        if (stage == 0) {
            if (mc.getOverlay() == null && mc.screen != null && mc.level == null && timer > 60) {
                stage = 1;
                LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.NORMAL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(1L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
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
            } else if (timer == 60) {
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
