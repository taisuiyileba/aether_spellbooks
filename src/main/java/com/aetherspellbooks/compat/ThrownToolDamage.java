package com.aetherspellbooks.compat;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.UUID;

/** Resolve the thrown tool's attack value without equipping it or changing the caster's attributes. */
public final class ThrownToolDamage {
    // Item.BASE_ATTACK_DAMAGE_UUID is protected in 1.20.1. This is the vanilla
    // weapon modifier's stable ID (public as a ResourceLocation in 1.21.1).
    private static final UUID BASE_ATTACK_DAMAGE = UUID.fromString("fa233e1c-4180-4865-b01b-bcce9785aca3");
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
        // Copy all non-weapon bonuses, including Strength and accessories.
        removeHandModifiers(attack, caster.getMainHandItem(), EquipmentSlot.MAINHAND);
        if (tool == caster.getOffhandItem()) {
            removeHandModifiers(attack, tool, EquipmentSlot.OFFHAND);
        }
        // The old hand item's equipment modifier can still be present on a swap tick.
        attack.removeModifier(BASE_ATTACK_DAMAGE);
        tool.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE).forEach(modifier -> {
            attack.removeModifier(modifier.getId());
            attack.addTransientModifier(modifier);
        });
        float damage = (float) attack.getValue();
        int sharpness = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SHARPNESS, tool);
        if (sharpness > 0) {
            damage += 0.5f * sharpness + 0.5f;
        }
        return Math.max(0, damage);
    }

    private static void removeHandModifiers(AttributeInstance attack, ItemStack stack, EquipmentSlot slot) {
        stack.getAttributeModifiers(slot).get(Attributes.ATTACK_DAMAGE)
                .forEach(modifier -> attack.removeModifier(modifier.getId()));
    }
}
