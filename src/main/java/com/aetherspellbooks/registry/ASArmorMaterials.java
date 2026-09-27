package com.aetherspellbooks.registry;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.item.AetherMageArmorItem;
import com.aetherteam.aether.client.AetherSoundEvents;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.item.armor.IronsExtendedArmorMaterial;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Map;
import java.util.function.Supplier;

/**
 * The Aether's Valkyrie and Phoenix armour, reforged for spellcasters: the same protection as the originals
 * (3/8/6/3, toughness 2, repaired with the same materials) plus Iron's Spells mana and school power on every piece.
 */
public enum ASArmorMaterials implements IronsExtendedArmorMaterial {
    VALKYRIE_MAGE("valkyrie_mage", "valkyrie_repairing", Rarity.RARE, AetherSoundEvents.ITEM_ARMOR_EQUIP_VALKYRIE::get, () -> Map.of(
            AttributeRegistry.MAX_MANA.get(), new AttributeModifier("Max Mana", 125, Operation.ADDITION),
            AttributeRegistry.HOLY_SPELL_POWER.get(), new AttributeModifier("Holy Power", 0.10, Operation.MULTIPLY_BASE),
            AttributeRegistry.LIGHTNING_SPELL_POWER.get(), new AttributeModifier("Lightning Power", 0.05, Operation.MULTIPLY_BASE))),
    PHOENIX_MAGE("phoenix_mage", "phoenix_repairing", Rarity.EPIC, AetherSoundEvents.ITEM_ARMOR_EQUIP_PHOENIX::get, () -> Map.of(
            AttributeRegistry.MAX_MANA.get(), new AttributeModifier("Max Mana", 125, Operation.ADDITION),
            AttributeRegistry.FIRE_SPELL_POWER.get(), new AttributeModifier("Fire Power", 0.10, Operation.MULTIPLY_BASE),
            AttributeRegistry.FIRE_MAGIC_RESIST.get(), new AttributeModifier("Fire Resist", 0.05, Operation.MULTIPLY_BASE)));

    private static final int DURABILITY_MULTIPLIER = 33;
    private static final Map<ArmorItem.Type, Integer> BASE_DURABILITY = Map.of(
            ArmorItem.Type.HELMET, 11, ArmorItem.Type.CHESTPLATE, 16, ArmorItem.Type.LEGGINGS, 15, ArmorItem.Type.BOOTS, 13);
    private static final Map<ArmorItem.Type, Integer> DEFENSE = Map.of(
            ArmorItem.Type.HELMET, 3, ArmorItem.Type.CHESTPLATE, 8, ArmorItem.Type.LEGGINGS, 6, ArmorItem.Type.BOOTS, 3);
    public static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final String id;
    private final String repairTag;
    private final Rarity rarity;
    private final Supplier<SoundEvent> equipSound;
    private final Supplier<Map<Attribute, AttributeModifier>> attributes;
    private Map<Attribute, AttributeModifier> resolvedAttributes;

    ASArmorMaterials(String id, String repairTag, Rarity rarity, Supplier<SoundEvent> equipSound, Supplier<Map<Attribute, AttributeModifier>> attributes) {
        this.id = id;
        this.repairTag = repairTag;
        this.rarity = rarity;
        this.equipSound = equipSound;
        this.attributes = attributes;
    }

    /** Model, texture and animation base name, e.g. "valkyrie_mage". */
    public String id() {
        return id;
    }

    public Rarity rarity() {
        return rarity;
    }

    /** True when all four armour slots hold pieces of this set. */
    public boolean isWornBy(LivingEntity entity) {
        return countWornBy(entity) == ARMOR_SLOTS.length;
    }

    public int countWornBy(LivingEntity entity) {
        int count = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (entity.getItemBySlot(slot).getItem() instanceof AetherMageArmorItem armor && armor.getSet() == this) {
                count++;
            }
        }
        return count;
    }

    @Override
    public Map<Attribute, AttributeModifier> getAdditionalAttributes() {
        if (resolvedAttributes == null) {
            resolvedAttributes = attributes.get();
        }
        return resolvedAttributes;
    }

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        return BASE_DURABILITY.get(type) * DURABILITY_MULTIPLIER;
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return DEFENSE.get(type);
    }

    @Override
    public int getEnchantmentValue() {
        return 15;
    }

    @Override
    public SoundEvent getEquipSound() {
        return equipSound.get();
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.of(ItemTags.create(ResourceLocation.fromNamespaceAndPath("aether", repairTag)));
    }

    @Override
    public String getName() {
        return AetherSpellbooks.MODID + ":" + id;
    }

    @Override
    public float getToughness() {
        return 2.0f;
    }

    @Override
    public float getKnockbackResistance() {
        return 0;
    }
}
