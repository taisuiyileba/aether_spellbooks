package com.aetherspellbooks.gametest;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.registry.ASItems;
import com.aetherteam.aether.network.packet.serverbound.OpenAccessoriesPacket;
import io.wispforest.accessories.api.AccessoriesCapability;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.List;

/**
 * ASB_RENDER_CHECK=accessories: equips the four accessories (and a spellbook) the way a player does - right click from
 * the hotbar, then dragging them into The Aether's accessory menu - and logs where each one ended up, with a screenshot
 * of the menu after each method. Not shipped.
 */
public final class AccessoryRenderCheck {
    private AccessoryRenderCheck() {}

    private static List<Item> items() {
        return List.of(ASItems.AMBROSIUM_RING.get(), ASItems.ZANITE_FOCUS_PENDANT.get(), ASItems.GRAVITITE_CASTING_GLOVES.get(),
                ASItems.VALKYRIE_MANTLE.get(), ASItems.VALKYRIE_GRIMOIRE.get());
    }

    static void tick(Minecraft mc, MinecraftServer server, int timer) {
        var items = items();
        if (timer == 10 || timer == 200) {
            reset(mc, server, items);
        }
        if (timer == 20) {
            for (Item item : items) {
                var lines = new ItemStack(item).getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player,
                        net.minecraft.world.item.TooltipFlag.Default.NORMAL);
                AetherSpellbooks.LOGGER.info("[ASB-ACC] tooltip {}: {}", key(item),
                        lines.stream().map(c -> c.getString()).toList());
            }
        }
        // 1) right click each item from the hotbar
        for (int i = 0; i < items.size(); i++) {
            if (timer == 30 + i * 20) {
                mc.player.getInventory().selected = i;
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            }
            if (timer == 40 + i * 20) {
                report(server, mc, "right-click " + key(items.get(i)));
            }
        }
        if (timer == 140) {
            PacketDistributor.sendToServer(new OpenAccessoriesPacket(ItemStack.EMPTY));
        }
        if (timer == 170) {
            Screenshot.grab(mc.gameDirectory, "asb_accessories_rightclick.png", mc.getMainRenderTarget(), m -> {});
            mc.setScreen(null);
        }
        // 2) drag each item from the inventory onto the accessory slot that should take it
        if (timer == 220) {
            PacketDistributor.sendToServer(new OpenAccessoriesPacket(ItemStack.EMPTY));
        }
        for (int i = 0; i < items.size(); i++) {
            if (timer == 250 + i * 12) {
                drag(mc, items.get(i));
            }
            if (timer == 256 + i * 12) {
                report(server, mc, "drag " + key(items.get(i)));
            }
        }
        if (timer == 330) {
            Screenshot.grab(mc.gameDirectory, "asb_accessories_drag.png", mc.getMainRenderTarget(), m -> {});
        }
        if (timer == 340) {
            AetherSpellbooks.LOGGER.info("[ASB-ACC] finished");
            mc.stop();
        }
    }

    private static String key(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).getPath();
    }

    private static void reset(Minecraft mc, MinecraftServer server, List<Item> items) {
        mc.options.hideGui = false;
        mc.setScreen(null);
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            p.getInventory().clearContent();
            AccessoriesCapability.get(p).getContainers().values().forEach(c -> {
                for (int s = 0; s < c.getSize(); s++) c.getAccessories().setItem(s, ItemStack.EMPTY);
            });
            CuriosApi.getCuriosInventory(p).ifPresent(cur -> cur.getCurios().values().forEach(h -> {
                for (int s = 0; s < h.getSlots(); s++) h.getStacks().setStackInSlot(s, ItemStack.EMPTY);
            }));
            for (int i = 0; i < items.size(); i++) p.getInventory().setItem(i, new ItemStack(items.get(i)));
            p.inventoryMenu.broadcastChanges();
        });
    }

    /** Client side: pick the item up from the player's inventory and click it onto every accessory slot that accepts it. */
    private static void drag(Minecraft mc, Item item) {
        var menu = mc.player.containerMenu;
        Slot from = null;
        StringBuilder accepts = new StringBuilder();
        for (Slot s : menu.slots) {
            if (s.container instanceof Inventory && s.getItem().is(item)) from = s;
        }
        if (from == null) {
            AetherSpellbooks.LOGGER.info("[ASB-ACC] drag {}: not in the inventory (menu {})", key(item), menu.getClass().getSimpleName());
            return;
        }
        ItemStack stack = from.getItem();
        Slot target = null;
        for (Slot s : menu.slots) {
            if (s.container instanceof Inventory) continue;
            if (s.mayPlace(stack)) {
                accepts.append(s.index).append(':').append(s.getClass().getSimpleName()).append(' ');
                // only real accessory slots: the crafting grid accepts anything
                if (target == null && s.getItem().isEmpty() && s.getClass().getSimpleName().contains("Accessor")) target = s;
            }
        }
        AetherSpellbooks.LOGGER.info("[ASB-ACC] drag {}: menu {} - slots that accept it client side: {}", key(item),
                menu.getClass().getSimpleName(), accepts);
        if (target != null) {
            mc.gameMode.handleInventoryMouseClick(menu.containerId, from.index, 0, ClickType.PICKUP, mc.player);
            mc.gameMode.handleInventoryMouseClick(menu.containerId, target.index, 0, ClickType.PICKUP, mc.player);
        }
    }

    private static void report(MinecraftServer server, Minecraft mc, String step) {
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            StringBuilder where = new StringBuilder();
            AccessoriesCapability.get(p).getContainers().forEach((name, c) -> {
                for (int s = 0; s < c.getSize(); s++) {
                    var st = c.getAccessories().getItem(s);
                    if (!st.isEmpty()) where.append("accessories[").append(name).append("]=").append(key(st.getItem())).append(' ');
                }
            });
            CuriosApi.getCuriosInventory(p).ifPresent(cur -> cur.getCurios().forEach((id, h) -> {
                for (int s = 0; s < h.getSlots(); s++) {
                    var st = h.getStacks().getStackInSlot(s);
                    if (!st.isEmpty()) where.append("curios[").append(id).append("]=").append(key(st.getItem())).append(' ');
                }
            }));
            AetherSpellbooks.LOGGER.info("[ASB-ACC] after {}: equipped {}", step, where);
        });
    }
}
