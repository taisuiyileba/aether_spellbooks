package com.aetherspellbooks.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import io.redspace.ironsspellbooks.item.curios.CurioBaseItem;
import io.redspace.ironsspellbooks.item.weapons.AttributeContainer;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import top.theillusivec4.curios.api.SlotContext;

import java.util.List;
import java.util.Set;

/**
 * An accessory that grants its attributes only in the named slots. On 1.21.1 The Aether's accessory slots belong to
 * Accessories ({@code aether:ring_slot} and friends, mapped to {@code aether_ring} etc. by AccessoriesCompat), and the
 * items are tagged for those slots only: they are not Curios curios, so nothing can put them in a slot that The
 * Aether's accessory menu does not show.
 */
public class MultiSlotCurioItem extends CurioBaseItem {
    private final Set<String> slots;
    private final AttributeContainer[] attributes;

    public MultiSlotCurioItem(Properties properties, Set<String> slots, AttributeContainer... attributes) {
        super(properties);
        this.slots = slots;
        this.attributes = attributes;
    }

    protected boolean appliesTo(SlotContext slotContext) {
        return slots.contains(slotContext.identifier());
    }

    @Override
    public Multimap<net.minecraft.core.Holder<Attribute>, AttributeModifier> getAttributeModifiers(SlotContext slotContext, ResourceLocation id, ItemStack stack) {
        if (!appliesTo(slotContext)) {
            return ImmutableMultimap.of();
        }
        ImmutableMultimap.Builder<net.minecraft.core.Holder<Attribute>, AttributeModifier> builder = ImmutableMultimap.builder();
        for (AttributeContainer container : attributes) {
            builder.put(container.attribute(), new AttributeModifier(id, container.value(), container.operation()));
        }
        return builder.build();
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
