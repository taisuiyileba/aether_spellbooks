package com.aetherspellbooks.compat;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.item.MultiSlotCurioItem;
import com.aetherspellbooks.registry.ASItems;
import io.wispforest.accessories.api.AccessoriesAPI;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.Accessory;
import io.wispforest.accessories.api.attributes.AccessoryAttributeBuilder;
import io.wispforest.accessories.api.slot.SlotReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Bridges the addon's Curios items to The Aether's bundled Accessories API. */
public final class AccessoriesCompat {
    private AccessoriesCompat() {}

    public static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> ASItems.ITEMS.getEntries().forEach(entry -> {
            if (entry.get() instanceof MultiSlotCurioItem item) {
                AccessoriesAPI.registerAccessory(item, new Accessory() {
                    @Override
                    public void getDynamicModifiers(ItemStack stack, SlotReference reference, AccessoryAttributeBuilder builder) {
                        String slot = switch (reference.slotName()) {
                            case "aether:ring_slot" -> "aether_ring";
                            case "aether:pendant_slot" -> "aether_pendant";
                            case "aether:gloves_slot" -> "aether_gloves";
                            case "aether:cape_slot", "cape" -> "aether_cape";
                            case "hand" -> "hands";
                            default -> reference.slotName();
                        };
                        var context = new SlotContext(slot, reference.entity(), reference.slot(), false, true);
                        item.getAttributeModifiers(context, AetherSpellbooks.id("accessory"), stack)
                                .forEach(builder::addStackable);
                    }
                });
            }
        }));
    }

    public static void forEachEquipped(LivingEntity entity, Predicate<ItemStack> filter, Consumer<ItemStack> action) {
        Set<ItemStack> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        CuriosApi.getCuriosInventory(entity).ifPresent(inventory -> inventory.findCurios(filter)
                .forEach(slot -> { if (seen.add(slot.stack())) action.accept(slot.stack()); }));
        AccessoriesCapability.getOptionally(entity).ifPresent(inventory -> inventory.getEquipped(filter)
                .forEach(slot -> { if (seen.add(slot.stack())) action.accept(slot.stack()); }));
    }

    public static boolean isEquipped(LivingEntity entity, Item item) {
        return CuriosApi.getCuriosInventory(entity).map(inventory -> inventory.isEquipped(item)).orElse(false)
                || AccessoriesCapability.getOptionally(entity).map(inventory -> inventory.isEquipped(item)).orElse(false);
    }
}
