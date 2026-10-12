package com.aetherspellbooks.compat;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.Enchantments;

/** Resolve the thrown tool's attack value without equipping it or changing the caster's attributes. */
public final class ThrownToolDamage {
    private ThrownToolDamage() {}

    public static ItemStack castingItem(LivingEntity caster) {
        return SpellSelectionManager.OFFHAND.equals(MagicData.getPlayerMagicData(caster).getCastingEquipmentSlot())
                ? caster.getOffhandItem() : caster.getMainHandItem();
    }

    public static float attackDamage(LivingEntity caster, ItemStack tool) {
        AttributeInstance attack = new AttributeInstance(Attributes.ATTACK_DAMAGE, ignored -> {});
        AttributeInstance equippedAttack = caster.getAttribute(Attributes.ATTACK_DAMAGE);
        if (equippedAttack != null) {
            attack.replaceFrom(equippedAttack);
        }
        // Use a copy so Strength, accessories and other non-weapon bonuses remain intact.
        removeHandModifiers(attack, caster.getMainHandItem(), EquipmentSlot.MAINHAND);
        if (tool == caster.getOffhandItem()) {
            removeHandModifiers(attack, tool, EquipmentSlot.OFFHAND);
        }
        // Equipment attributes may still describe the previous item on the tick it was switched.
        attack.removeModifier(Item.BASE_ATTACK_DAMAGE_ID);
        tool.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ATTACK_DAMAGE)) {
                attack.removeModifier(modifier.id());
                attack.addTransientModifier(modifier);
            }
        });
        float damage = (float) attack.getValue();
        damage += Utils.processEnchantment(caster.level(), Enchantments.SHARPNESS,
                EnchantmentEffectComponents.DAMAGE, tool.get(DataComponents.ENCHANTMENTS));
        return Math.max(0, damage);
    }

    private static void removeHandModifiers(AttributeInstance attack, ItemStack stack, EquipmentSlot slot) {
        stack.forEachModifier(slot, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ATTACK_DAMAGE)) {
                attack.removeModifier(modifier.id());
            }
        });
    }
}
