package com.aetherspellbooks.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import io.redspace.ironsspellbooks.item.curios.CurioBaseItem;
import io.redspace.ironsspellbooks.item.weapons.AttributeContainer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A curio that grants its attributes in any of several slots, so it works both in the Aether's own
 * accessory slots ({@code aether_ring}, {@code aether_pendant}) and in the default Curios slots
 * used by Iron's Spells ({@code ring}, {@code necklace}), whichever layout a modpack uses.
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
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(SlotContext slotContext, UUID uuid, ItemStack stack) {
        if (!appliesTo(slotContext)) {
            return ImmutableMultimap.of();
        }
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        for (AttributeContainer container : attributes) {
            builder.put(container.attribute().get(), new AttributeModifier(uuid, "aether_spellbooks_curio", container.value(), container.operation()));
        }
        return builder.build();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
