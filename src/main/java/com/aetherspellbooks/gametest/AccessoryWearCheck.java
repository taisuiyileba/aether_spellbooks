package com.aetherspellbooks.gametest;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.client.accessory.WearableSlot;
import com.aetherspellbooks.registry.ASItems;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

/** Photographs the actual player layer and first-person hand after slot contents sync from the server. */
final class AccessoryWearCheck {
    private static final int VIEW_TICKS = 24;
    private static final String[] KITS = {"aether", "curios", "generic", "armored"};
    private static final String[] VIEWS = {"front", "back", "right_hand", "left_hand"};
    private static final CameraType[] CAMERAS = {
            CameraType.THIRD_PERSON_FRONT, CameraType.THIRD_PERSON_BACK, CameraType.FIRST_PERSON, CameraType.FIRST_PERSON
    };
    private static final String[] AETHER_SLOTS = {"ring", "pendant", "gloves", "cape"};
    private static final String[] CURIO_SLOTS = {"ring", "necklace", "hands", "back"};

    private AccessoryWearCheck() {}

    static void tick(Minecraft mc, MinecraftServer server, int timer) {
        int shot = (timer - 1) / VIEW_TICKS;
        int kit = shot / VIEWS.length;
        if (kit == KITS.length) {
            AetherSpellbooks.LOGGER.info("[ASB-WEAR] All {} views captured", KITS.length * VIEWS.length);
            mc.stop();
            return;
        }
        int view = shot % VIEWS.length;
        int tick = (timer - 1) % VIEW_TICKS;
        if (tick == 0) {
            if (shot == 0) {
                AetherSpellbooks.LOGGER.info("[ASB-WEAR] Skin model {}", mc.player.getModelName());
            }
            if (view == 0) {
                var uuid = mc.player.getUUID();
                server.execute(() -> equip(server.getPlayerList().getPlayer(uuid), kit));
            }
            mc.options.fov().set(42);
            mc.options.setCameraType(CAMERAS[view]);
            mc.options.hideGui = CAMERAS[view] != CameraType.FIRST_PERSON;
            if (CAMERAS[view] == CameraType.FIRST_PERSON) {
                mc.options.mainHand().set(view == 2 ? HumanoidArm.RIGHT : HumanoidArm.LEFT);
                mc.options.broadcastOptions();
            }
        }
        if (tick == VIEW_TICKS - 2) {
            if (CAMERAS[view] == CameraType.FIRST_PERSON
                    && mc.player.getMainArm() != (view == 2 ? HumanoidArm.RIGHT : HumanoidArm.LEFT)) {
                throw new IllegalStateException("Player hand preference did not sync");
            }
            for (WearableSlot slot : WearableSlot.values()) {
                if (!slot.isVisible(mc.player)) {
                    throw new IllegalStateException(KITS[kit] + " did not sync a visible " + slot);
                }
            }
            String name = "asb_wear_" + KITS[kit] + "_" + VIEWS[view];
            Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(),
                    message -> AetherSpellbooks.LOGGER.info("[ASB-WEAR] {}", message.getString()));
        }
    }

    private static void equip(ServerPlayer player, int kit) {
        player.getInventory().clearContent();
        player.setItemSlot(EquipmentSlot.CHEST, kit == 3
                ? new ItemStack(ASItems.VALKYRIE_MAGE_CHESTPLATE.get()) : ItemStack.EMPTY);
        Item[] items = {ASItems.AMBROSIUM_RING.get(), ASItems.ZANITE_FOCUS_PENDANT.get(),
                ASItems.GRAVITITE_CASTING_GLOVES.get(), ASItems.VALKYRIE_MANTLE.get()};
        CuriosApi.getCuriosInventory(player).ifPresent(inventory -> {
            var generic = inventory.getStacksHandler("curio").orElseThrow();
            for (int i = 0; i < items.length; i++) {
                var aether = inventory.getStacksHandler("aether_" + AETHER_SLOTS[i]).orElseThrow();
                aether.getStacks().setStackInSlot(0, kit == 0 || kit == 3 ? new ItemStack(items[i]) : ItemStack.EMPTY);
                var slot = inventory.getStacksHandler(CURIO_SLOTS[i]).orElseThrow();
                slot.getStacks().setStackInSlot(0, kit == 1 || kit == 3 ? new ItemStack(items[i]) : ItemStack.EMPTY);
                generic.getStacks().setStackInSlot(i, kit == 2 ? new ItemStack(items[i]) : ItemStack.EMPTY);
            }
        });
        player.inventoryMenu.broadcastChanges();
    }
}
