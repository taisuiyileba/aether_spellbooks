package com.aetherspellbooks.item;

import com.aetherspellbooks.config.ASConfig;
import com.aetherteam.aether.item.AetherItems;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
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
 * Mirrors the Aether's zanite tools, which grow stronger as they wear down:
 * spell power rises from the minimum to the maximum bonus as durability is used.
 * It never breaks; once fully worn it is depleted and grants nothing until repaired with zanite.
 */
public class ZaniteFocusPendantItem extends MultiSlotCurioItem {
    public static final Set<String> SLOTS = Set.of("aether_pendant", "necklace");

    public ZaniteFocusPendantItem(Properties properties) {
        super(properties, SLOTS);
    }

    public static boolean isDepleted(ItemStack stack) {
        return stack.getDamageValue() >= stack.getMaxDamage() - 1;
    }

    public static double getSpellPowerBonus(ItemStack stack) {
        if (isDepleted(stack)) {
            return 0;
        }
        double wear = stack.getMaxDamage() <= 0 ? 0 : (double) stack.getDamageValue() / (stack.getMaxDamage() - 1);
        return Mth.lerp(wear, ASConfig.PENDANT_MIN_SPELL_POWER.get(), ASConfig.PENDANT_MAX_SPELL_POWER.get());
    }

    /**
     * Called whenever the wearer casts a spell.
     */
    public static void wear(ItemStack stack) {
        if (!isDepleted(stack)) {
            stack.setDamageValue(stack.getDamageValue() + 1);
        }
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(SlotContext slotContext, UUID uuid, ItemStack stack) {
        if (!appliesTo(slotContext) || isDepleted(stack)) {
            return ImmutableMultimap.of();
        }
        return ImmutableMultimap.of(AttributeRegistry.SPELL_POWER.get(),
                new AttributeModifier(uuid, "aether_spellbooks_zanite_pendant", getSpellPowerBonus(stack), AttributeModifier.Operation.MULTIPLY_BASE));
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return repair.is(AetherItems.ZANITE_GEMSTONE.get());
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        if (isDepleted(stack)) {
            tooltip.add(Component.translatable("item.aether_spellbooks.zanite_focus_pendant.depleted").withStyle(ChatFormatting.RED));
        } else {
            tooltip.add(Component.translatable("item.aether_spellbooks.zanite_focus_pendant.current",
                    String.format("%.0f", getSpellPowerBonus(stack) * 100)).withStyle(ChatFormatting.BLUE));
        }
    }
}
