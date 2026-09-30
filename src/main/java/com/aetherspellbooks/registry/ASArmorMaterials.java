package com.aetherspellbooks.registry;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.item.AetherMageArmorItem;
import com.aetherteam.aether.client.AetherSoundEvents;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.core.Holder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.core.registries.Registries;
import io.redspace.ironsspellbooks.item.weapons.AttributeContainer;
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
public enum ASArmorMaterials {
    VALKYRIE_MAGE("valkyrie_mage", "valkyrie_repairing", Rarity.RARE, AetherSoundEvents.ITEM_ARMOR_EQUIP_VALKYRIE, () -> Map.of(
            AttributeRegistry.MAX_MANA, new AttributeModifier(AetherSpellbooks.id("max_mana"), 125, Operation.ADD_VALUE),
            AttributeRegistry.HOLY_SPELL_POWER, new AttributeModifier(AetherSpellbooks.id("holy_power"), 0.10, Operation.ADD_MULTIPLIED_BASE),
            AttributeRegistry.LIGHTNING_SPELL_POWER, new AttributeModifier(AetherSpellbooks.id("lightning_power"), 0.05, Operation.ADD_MULTIPLIED_BASE))),
    PHOENIX_MAGE("phoenix_mage", "phoenix_repairing", Rarity.EPIC, AetherSoundEvents.ITEM_ARMOR_EQUIP_PHOENIX, () -> Map.of(
            AttributeRegistry.MAX_MANA, new AttributeModifier(AetherSpellbooks.id("max_mana"), 125, Operation.ADD_VALUE),
            AttributeRegistry.FIRE_SPELL_POWER, new AttributeModifier(AetherSpellbooks.id("fire_power"), 0.10, Operation.ADD_MULTIPLIED_BASE),
            AttributeRegistry.FIRE_MAGIC_RESIST, new AttributeModifier(AetherSpellbooks.id("fire_resist"), 0.05, Operation.ADD_MULTIPLIED_BASE)));

    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, AetherSpellbooks.MODID);
    private DeferredHolder<ArmorMaterial, ArmorMaterial> holder;
    public Holder<ArmorMaterial> holder() { return holder; }
    public AttributeContainer[] attributeContainers() {
        return getAdditionalAttributes().entrySet().stream().map(e -> new AttributeContainer(e.getKey(), e.getValue().amount(), e.getValue().operation())).toArray(AttributeContainer[]::new);
    }

    private static final int DURABILITY_MULTIPLIER = 33;
    private static final Map<ArmorItem.Type, Integer> BASE_DURABILITY = Map.of(
            ArmorItem.Type.HELMET, 11, ArmorItem.Type.CHESTPLATE, 16, ArmorItem.Type.LEGGINGS, 15, ArmorItem.Type.BOOTS, 13);
    private static final Map<ArmorItem.Type, Integer> DEFENSE = Map.of(
            ArmorItem.Type.HELMET, 3, ArmorItem.Type.CHESTPLATE, 8, ArmorItem.Type.LEGGINGS, 6, ArmorItem.Type.BOOTS, 3);
    public static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    static {
        for (ASArmorMaterials set : values()) {
            set.holder = MATERIALS.register(set.id, () -> new ArmorMaterial(
                    ASArmorMaterials.DEFENSE, 15, set.equipSound, set::getRepairIngredient,
                    java.util.List.of(new ArmorMaterial.Layer(AetherSpellbooks.id(set.id))), 2f, 0f));
        }
    }

    private final String id;
    private final String repairTag;
    private final Rarity rarity;
    private final Holder<SoundEvent> equipSound;
    private final Supplier<Map<Holder<Attribute>, AttributeModifier>> attributes;
    private Map<Holder<Attribute>, AttributeModifier> resolvedAttributes;

    ASArmorMaterials(String id, String repairTag, Rarity rarity, Holder<SoundEvent> equipSound, Supplier<Map<Holder<Attribute>, AttributeModifier>> attributes) {
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

    public Map<Holder<Attribute>, AttributeModifier> getAdditionalAttributes() {
        if (resolvedAttributes == null) {
            resolvedAttributes = attributes.get();
        }
        return resolvedAttributes;
    }

    public int getDurabilityForType(ArmorItem.Type type) {
        return BASE_DURABILITY.get(type) * DURABILITY_MULTIPLIER;
    }

    public int getDefenseForType(ArmorItem.Type type) {
        return DEFENSE.get(type);
    }

    public int getEnchantmentValue() {
        return 15;
    }

    public SoundEvent getEquipSound() {
        return equipSound.value();
    }

    public Ingredient getRepairIngredient() {
        return Ingredient.of(ItemTags.create(ResourceLocation.fromNamespaceAndPath("aether", repairTag)));
    }

    public String getName() {
        return AetherSpellbooks.MODID + ":" + id;
    }

    public float getToughness() {
        return 2.0f;
    }

    public float getKnockbackResistance() {
        return 0;
    }
}
