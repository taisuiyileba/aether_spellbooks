package com.aetherspellbooks.client.accessory;

import com.aetherspellbooks.registry.ASItems;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.function.Predicate;
import java.util.function.Supplier;

/** Chooses one appearance per body position, with visible Aether slots taking priority. */
public enum WearableSlot {
    PENDANT(ASItems.ZANITE_FOCUS_PENDANT, "pendant", "necklace"),
    GLOVES(ASItems.GRAVITITE_CASTING_GLOVES, "gloves", "hands"),
    MANTLE(ASItems.VALKYRIE_MANTLE, "cape", "back");

    private final Supplier<? extends Item> item;
    private final String aetherSlot;
    private final String curioSlot;

    WearableSlot(Supplier<? extends Item> item, String aetherSlot, String curioSlot) {
        this.item = item;
        this.aetherSlot = aetherSlot;
        this.curioSlot = curioSlot;
    }

    public boolean isVisible(AbstractClientPlayer player) {
        Item target = item.get();
        return CuriosApi.getCuriosInventory(player).map(inventory -> {
            var slots = inventory.getCurios();
            ItemStack aether = findCurio(slots.get("aether_" + aetherSlot), stack -> true);
            if (!aether.isEmpty()) {
                return aether.is(target);
            }
            return !findCurio(slots.get(curioSlot), stack -> stack.is(target)).isEmpty()
                    || !findCurio(slots.get("curio"), stack -> stack.is(target)).isEmpty();
        }).orElse(false);
    }

    private static ItemStack findCurio(ICurioStacksHandler slot, Predicate<ItemStack> matches) {
        if (slot != null) {
            var renders = slot.getRenders();
            for (int i = 0; i < slot.getStacks().getSlots(); i++) {
                ItemStack stack = slot.getCosmeticStacks().getStackInSlot(i);
                if (stack.isEmpty() && i < renders.size() && renders.get(i)) {
                    stack = slot.getStacks().getStackInSlot(i);
                }
                if (!stack.isEmpty() && matches.test(stack)) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }
}
