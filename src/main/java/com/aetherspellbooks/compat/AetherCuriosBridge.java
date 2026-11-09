package com.aetherspellbooks.compat;

import com.aetherspellbooks.AetherSpellbooks;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.slot.SlotEntryReference;
import io.wispforest.accessories.api.slot.SlotReference;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.common.capability.ItemizedCurioCapability;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

public final class AetherCuriosBridge {
    private static final ResourceLocation BRIDGE_ID = AetherSpellbooks.id("aether_curios");
    public static final ResourceLocation BRIDGE_PACK_MARKER = BRIDGE_ID.withPrefix("tags/item/").withSuffix(".json");
    private static final TagKey<Item> BRIDGED_ITEMS = TagKey.create(Registries.ITEM, BRIDGE_ID);
    private static final List<Slot> SLOTS = List.of(
            new Slot("ring", "ring", "rings"),
            new Slot("pendant", "necklace", "pendants"),
            new Slot("gloves", "hands", "gloves"),
            new Slot("cape", "back", "capes"),
            new Slot("accessory", "charm", "miscellaneous"),
            new Slot("shield", "aether_shield", "shields")
    );
    private static final AetherCurio CURIO = new AetherCurio();

    private AetherCuriosBridge() {}

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Curios' own providers come first. Resolve tag membership at use time, including after a data reload.
        event.registerItem(CuriosCapability.ITEM,
                (stack, unused) -> isBridged(stack) ? new ItemizedCurioCapability(CURIO, stack) : null,
                BuiltInRegistries.ITEM.stream().toArray(Item[]::new));
    }

    public static boolean isEnabled(LivingEntity entity) {
        return entity instanceof Player && hasBridgeTags();
    }

    public static boolean hasBridgeTags() {
        // The server synchronizes item tags, so the server's setting also drives client-side behavior.
        return BuiltInRegistries.ITEM.getTag(BRIDGED_ITEMS).isPresent();
    }

    public static boolean isBridged(ItemStack stack) {
        return stack.is(BRIDGED_ITEMS);
    }

    public static void removeNativeSlots(Map<String, ?> slots) {
        SLOTS.forEach(slot -> slots.remove(slot.accessories()));
    }

    public static void recoverGearFromRemainingSlots(ServerPlayer player) {
        if (isEnabled(player)) {
            AccessoriesCapability.getOptionally(player).ifPresent(inventory -> inventory.getContainers().values().forEach(slot -> {
                returnBridgedItems(player, slot.getAccessories());
                returnBridgedItems(player, slot.getCosmeticAccessories());
            }));
        }
    }

    private static void returnBridgedItems(ServerPlayer player, Container container) {
        for (int index = 0; index < container.getContainerSize(); index++) {
            var stack = container.getItem(index);
            if (isBridged(stack)) {
                container.setItem(index, ItemStack.EMPTY);
                ItemHandlerHelper.giveItemToPlayer(player, stack);
            }
        }
    }

    public static SlotReference reference(SlotContext context, ItemStack stack) {
        if (!(context.entity() instanceof Player) || !isBridged(stack)) {
            return null;
        }
        for (Slot slot : SLOTS) {
            if (slot.curios().equals(context.identifier()) && stack.is(slot.items())) {
                return CuriosSlotReference.create(context, slot.accessories());
            }
        }
        return null;
    }

    public static List<SlotEntryReference> findEquipped(LivingEntity entity, Predicate<ItemStack> matches) {
        if (!isEnabled(entity)) {
            return List.of();
        }
        return CuriosApi.getCuriosInventory(entity).map(inventory -> inventory
                .findCurios(stack -> isBridged(stack) && matches.test(stack)).stream()
                .map(slot -> {
                    var reference = reference(slot.slotContext(), slot.stack());
                    return reference == null ? null : new SlotEntryReference(reference, slot.stack());
                })
                .filter(Objects::nonNull)
                .toList()).orElseGet(List::of);
    }

    public static boolean equipFromDispenser(LivingEntity entity, ItemStack stack) {
        var inventory = CuriosApi.getCuriosInventory(entity).orElse(null);
        if (inventory == null) {
            return false;
        }
        var offered = stack.copyWithCount(1);
        var curio = CuriosApi.getCurio(offered);
        for (Slot slot : SLOTS) {
            var handler = inventory.getStacksHandler(slot.curios()).orElse(null);
            if (!offered.is(slot.items()) || handler == null) {
                continue;
            }
            var stacks = handler.getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                var context = new SlotContext(slot.curios(), entity, i, false, true);
                if (inventory.isSlotActive(slot.curios(), i) && stacks.getStackInSlot(i).isEmpty()
                        && curio.map(value -> value.canEquip(context)).orElse(true)
                        && stacks.insertItem(i, offered, false).isEmpty()) {
                    stack.shrink(1);
                    curio.ifPresent(value -> value.onEquipFromUse(context));
                    return true;
                }
            }
        }
        return false;
    }

    private record Slot(String accessories, String curios, TagKey<Item> items) {
        private Slot(String aetherSlot, String curios, String tagSuffix) {
            this("aether:" + aetherSlot + "_slot", curios,
                    TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("aether", "accessories_" + tagSuffix)));
        }
    }
}
