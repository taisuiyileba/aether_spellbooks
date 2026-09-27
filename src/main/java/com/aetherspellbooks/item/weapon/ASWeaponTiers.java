package com.aetherspellbooks.item.weapon;

import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.item.weapons.AttributeContainer;
import io.redspace.ironsspellbooks.item.weapons.ExtendedWeaponTier;
import io.redspace.ironsspellbooks.item.weapons.StaffTier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Stats of the Aether spellblades and staffs, in Iron's Spells' weapon tier format.
 * Spellblades keep the damage and speed of the Aether swords they are reforged from (7 damage, 1.6 speed).
 */
public final class ASWeaponTiers {
    private ASWeaponTiers() {
    }

    private static Ingredient repair(String aetherTag) {
        return Ingredient.of(ItemTags.create(ResourceLocation.fromNamespaceAndPath("aether", aetherTag)));
    }

    // --- Spellblades: durability, damage, speed, enchantability, repair, attributes
    public static final ExtendedWeaponTier SUNFIRE = new ExtendedWeaponTier(1000, 6f, -2.4f, 14, () -> repair("flaming_repairing"),
            new AttributeContainer(AttributeRegistry.FIRE_SPELL_POWER, 0.10, Operation.MULTIPLY_BASE),
            new AttributeContainer(AttributeRegistry.MAX_MANA, 50, Operation.ADDITION));
    public static final ExtendedWeaponTier STORMCALLER = new ExtendedWeaponTier(1000, 6f, -2.4f, 14, () -> repair("lightning_repairing"),
            new AttributeContainer(AttributeRegistry.LIGHTNING_SPELL_POWER, 0.10, Operation.MULTIPLY_BASE),
            new AttributeContainer(AttributeRegistry.MAX_MANA, 50, Operation.ADDITION));
    public static final ExtendedWeaponTier HALLOWED = new ExtendedWeaponTier(1000, 6f, -2.4f, 14, () -> repair("holy_repairing"),
            new AttributeContainer(AttributeRegistry.HOLY_SPELL_POWER, 0.10, Operation.MULTIPLY_BASE),
            new AttributeContainer(AttributeRegistry.MAX_MANA, 50, Operation.ADDITION));
    public static final ExtendedWeaponTier SANGUINE = new ExtendedWeaponTier(2031, 6f, -2.4f, 14, () -> repair("vampire_repairing"),
            new AttributeContainer(AttributeRegistry.BLOOD_SPELL_POWER, 0.10, Operation.MULTIPLY_BASE),
            new AttributeContainer(AttributeRegistry.MAX_MANA, 50, Operation.ADDITION));

    // --- Staffs: melee damage, attack speed, attributes while held in the main hand
    public static final StaffTier ZANITE_STAFF = new StaffTier(3f, -3f,
            new AttributeContainer(AttributeRegistry.SPELL_POWER, 0.10, Operation.MULTIPLY_BASE),
            new AttributeContainer(AttributeRegistry.MANA_REGEN, 0.15, Operation.MULTIPLY_BASE));
    public static final StaffTier AERCLOUD_STAFF = new StaffTier(3f, -3f,
            new AttributeContainer(AttributeRegistry.EVOCATION_SPELL_POWER, 0.15, Operation.MULTIPLY_BASE),
            new AttributeContainer(AttributeRegistry.CAST_TIME_REDUCTION, 0.10, Operation.MULTIPLY_BASE),
            new AttributeContainer(AttributeRegistry.SPELL_POWER, 0.05, Operation.MULTIPLY_BASE));
    public static final StaffTier GRAVITITE_STAFF = new StaffTier(5f, -3f,
            new AttributeContainer(AttributeRegistry.ENDER_SPELL_POWER, 0.15, Operation.MULTIPLY_BASE),
            new AttributeContainer(AttributeRegistry.COOLDOWN_REDUCTION, 0.10, Operation.MULTIPLY_BASE),
            new AttributeContainer(AttributeRegistry.SPELL_POWER, 0.05, Operation.MULTIPLY_BASE));
    public static final StaffTier VALKYRIE_SCEPTER = new StaffTier(5f, -3f,
            new AttributeContainer(AttributeRegistry.HOLY_SPELL_POWER, 0.15, Operation.MULTIPLY_BASE),
            new AttributeContainer(AttributeRegistry.LIGHTNING_SPELL_POWER, 0.10, Operation.MULTIPLY_BASE),
            new AttributeContainer(AttributeRegistry.SPELL_POWER, 0.05, Operation.MULTIPLY_BASE));
    public static final StaffTier SOLAR_SCEPTER = new StaffTier(6f, -3f,
            new AttributeContainer(AttributeRegistry.FIRE_SPELL_POWER, 0.15, Operation.MULTIPLY_BASE),
            new AttributeContainer(AttributeRegistry.CAST_TIME_REDUCTION, 0.10, Operation.MULTIPLY_BASE),
            new AttributeContainer(AttributeRegistry.SPELL_POWER, 0.10, Operation.MULTIPLY_BASE));
}
