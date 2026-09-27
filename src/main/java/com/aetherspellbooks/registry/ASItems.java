package com.aetherspellbooks.registry;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.item.AetherMageArmorItem;
import com.aetherspellbooks.item.MultiSlotCurioItem;
import com.aetherspellbooks.item.ZaniteFocusPendantItem;
import com.aetherspellbooks.item.weapon.ASWeaponTiers;
import com.aetherspellbooks.item.weapon.AetherSpellbladeItem;
import com.aetherspellbooks.item.weapon.AetherStaffItem;
import io.redspace.ironsspellbooks.api.registry.SpellDataRegistryHolder;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.item.SpellBook;
import io.redspace.ironsspellbooks.item.weapons.AttributeContainer;
import io.redspace.ironsspellbooks.util.ItemPropertiesHelper;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.Set;

public class ASItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AetherSpellbooks.MODID);

    // --- Boss spellbooks (bronze / silver / gold dungeon) ---
    public static final RegistryObject<Item> SLIDER_CODEX = ITEMS.register("slider_codex",
            () -> new SpellBook(8, bookProperties(Rarity.UNCOMMON)).withSpellbookAttributes(
                    new AttributeContainer(AttributeRegistry.MAX_MANA, 100, Operation.ADDITION),
                    new AttributeContainer(AttributeRegistry.NATURE_SPELL_POWER, 0.10, Operation.MULTIPLY_BASE),
                    new AttributeContainer(() -> Attributes.KNOCKBACK_RESISTANCE, 0.2, Operation.ADDITION)));
    public static final RegistryObject<Item> VALKYRIE_GRIMOIRE = ITEMS.register("valkyrie_grimoire",
            () -> new SpellBook(10, bookProperties(Rarity.RARE)).withSpellbookAttributes(
                    new AttributeContainer(AttributeRegistry.MAX_MANA, 150, Operation.ADDITION),
                    new AttributeContainer(AttributeRegistry.HOLY_SPELL_POWER, 0.10, Operation.MULTIPLY_BASE),
                    new AttributeContainer(AttributeRegistry.LIGHTNING_SPELL_POWER, 0.10, Operation.MULTIPLY_BASE)));
    public static final RegistryObject<Item> SOLAR_CODEX = ITEMS.register("solar_codex",
            () -> new SpellBook(12, bookProperties(Rarity.EPIC)).withSpellbookAttributes(
                    new AttributeContainer(AttributeRegistry.MAX_MANA, 200, Operation.ADDITION),
                    new AttributeContainer(AttributeRegistry.FIRE_SPELL_POWER, 0.15, Operation.MULTIPLY_BASE),
                    new AttributeContainer(AttributeRegistry.ICE_SPELL_POWER, 0.10, Operation.MULTIPLY_BASE),
                    new AttributeContainer(AttributeRegistry.COOLDOWN_REDUCTION, 0.05, Operation.MULTIPLY_BASE)));

    // --- Mage accessories (work in both Aether and default Curios slots) ---
    public static final RegistryObject<Item> AMBROSIUM_RING = ITEMS.register("ambrosium_ring",
            () -> new MultiSlotCurioItem(ItemPropertiesHelper.equipment(1).rarity(Rarity.UNCOMMON), Set.of("aether_ring", "ring"),
                    new AttributeContainer(AttributeRegistry.MANA_REGEN, 0.15, Operation.MULTIPLY_BASE)));
    public static final RegistryObject<Item> ZANITE_FOCUS_PENDANT = ITEMS.register("zanite_focus_pendant",
            () -> new ZaniteFocusPendantItem(new Item.Properties().durability(300).rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> GRAVITITE_CASTING_GLOVES = ITEMS.register("gravitite_casting_gloves",
            () -> new MultiSlotCurioItem(ItemPropertiesHelper.equipment(1).rarity(Rarity.RARE), Set.of("aether_gloves", "hands"),
                    new AttributeContainer(AttributeRegistry.CAST_TIME_REDUCTION, 0.10, Operation.MULTIPLY_BASE),
                    new AttributeContainer(AttributeRegistry.ENDER_SPELL_POWER, 0.08, Operation.MULTIPLY_BASE)));
    public static final RegistryObject<Item> VALKYRIE_MANTLE = ITEMS.register("valkyrie_mantle",
            () -> new MultiSlotCurioItem(ItemPropertiesHelper.equipment(1).rarity(Rarity.RARE), Set.of("aether_cape", "back"),
                    new AttributeContainer(AttributeRegistry.HOLY_SPELL_POWER, 0.10, Operation.MULTIPLY_BASE),
                    new AttributeContainer(AttributeRegistry.MAX_MANA, 50, Operation.ADDITION)));

    // --- Mage armour: the Aether's Valkyrie and Phoenix sets reforged on a smithing table ---
    public static final RegistryObject<Item> VALKYRIE_MAGE_HELMET = mageArmor("valkyrie_mage_helmet", ASArmorMaterials.VALKYRIE_MAGE, ArmorItem.Type.HELMET);
    public static final RegistryObject<Item> VALKYRIE_MAGE_CHESTPLATE = mageArmor("valkyrie_mage_chestplate", ASArmorMaterials.VALKYRIE_MAGE, ArmorItem.Type.CHESTPLATE);
    public static final RegistryObject<Item> VALKYRIE_MAGE_LEGGINGS = mageArmor("valkyrie_mage_leggings", ASArmorMaterials.VALKYRIE_MAGE, ArmorItem.Type.LEGGINGS);
    public static final RegistryObject<Item> VALKYRIE_MAGE_BOOTS = mageArmor("valkyrie_mage_boots", ASArmorMaterials.VALKYRIE_MAGE, ArmorItem.Type.BOOTS);
    public static final RegistryObject<Item> PHOENIX_MAGE_HELMET = mageArmor("phoenix_mage_helmet", ASArmorMaterials.PHOENIX_MAGE, ArmorItem.Type.HELMET);
    public static final RegistryObject<Item> PHOENIX_MAGE_CHESTPLATE = mageArmor("phoenix_mage_chestplate", ASArmorMaterials.PHOENIX_MAGE, ArmorItem.Type.CHESTPLATE);
    public static final RegistryObject<Item> PHOENIX_MAGE_LEGGINGS = mageArmor("phoenix_mage_leggings", ASArmorMaterials.PHOENIX_MAGE, ArmorItem.Type.LEGGINGS);
    public static final RegistryObject<Item> PHOENIX_MAGE_BOOTS = mageArmor("phoenix_mage_boots", ASArmorMaterials.PHOENIX_MAGE, ArmorItem.Type.BOOTS);

    // --- Spellblades: the Aether's dungeon swords reforged on a smithing table, each with an imbued spell ---
    public static final RegistryObject<Item> SUNFIRE_SPELLBLADE = ITEMS.register("sunfire_spellblade", () -> new AetherSpellbladeItem(
            ASWeaponTiers.SUNFIRE, AetherSpellbladeItem.Ability.FLAMING, weaponProperties(Rarity.EPIC).fireResistant(),
            new SpellDataRegistryHolder(ASSpells.SOLAR_FLARE, 2)));
    public static final RegistryObject<Item> STORMCALLER_SPELLBLADE = ITEMS.register("stormcaller_spellblade", () -> new AetherSpellbladeItem(
            ASWeaponTiers.STORMCALLER, AetherSpellbladeItem.Ability.LIGHTNING, weaponProperties(Rarity.EPIC),
            new SpellDataRegistryHolder(ASSpells.THUNDER_CRYSTAL, 4)));
    public static final RegistryObject<Item> HALLOWED_SPELLBLADE = ITEMS.register("hallowed_spellblade", () -> new AetherSpellbladeItem(
            ASWeaponTiers.HALLOWED, AetherSpellbladeItem.Ability.HOLY, weaponProperties(Rarity.EPIC),
            new SpellDataRegistryHolder(ASSpells.VALKYRIE_LUNGE, 4)));
    public static final RegistryObject<Item> SANGUINE_SPELLBLADE = ITEMS.register("sanguine_spellblade", () -> new AetherSpellbladeItem(
            ASWeaponTiers.SANGUINE, AetherSpellbladeItem.Ability.VAMPIRE, weaponProperties(Rarity.EPIC),
            new SpellDataRegistryHolder(SpellRegistry.BLOOD_SLASH_SPELL, 4)));

    // --- Staffs: right-click casts the selected spell, school power while held ---
    public static final RegistryObject<Item> ZANITE_STAFF = ITEMS.register("zanite_staff",
            () -> new AetherStaffItem(ASWeaponTiers.ZANITE_STAFF, AetherStaffItem.Trait.NONE, weaponProperties(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> AERCLOUD_STAFF = ITEMS.register("aercloud_staff",
            () -> new AetherStaffItem(ASWeaponTiers.AERCLOUD_STAFF, AetherStaffItem.Trait.CLOUD_MINIONS, weaponProperties(Rarity.RARE)));
    public static final RegistryObject<Item> GRAVITITE_STAFF = ITEMS.register("gravitite_staff",
            () -> new AetherStaffItem(ASWeaponTiers.GRAVITITE_STAFF, AetherStaffItem.Trait.LAUNCH, weaponProperties(Rarity.RARE)));
    public static final RegistryObject<Item> VALKYRIE_SCEPTER = ITEMS.register("valkyrie_scepter",
            () -> new AetherStaffItem(ASWeaponTiers.VALKYRIE_SCEPTER, AetherStaffItem.Trait.SPARKLES, weaponProperties(Rarity.EPIC)));
    public static final RegistryObject<Item> SOLAR_SCEPTER = ITEMS.register("solar_scepter",
            () -> new AetherStaffItem(ASWeaponTiers.SOLAR_SCEPTER, AetherStaffItem.Trait.EMBERS, weaponProperties(Rarity.EPIC).fireResistant()));

    /** Smithing template: Aether armour, dungeon swords or the Cloud Staff + a rune -> the spellcaster version (enchantments are kept). */
    public static final RegistryObject<Item> ARCANE_UPGRADE_TEMPLATE = ITEMS.register("aether_arcane_upgrade_smithing_template", () -> new SmithingTemplateItem(
            Component.translatable("item.aether_spellbooks.smithing_template.arcane_upgrade.applies_to").withStyle(ChatFormatting.BLUE),
            Component.translatable("item.aether_spellbooks.smithing_template.arcane_upgrade.ingredients").withStyle(ChatFormatting.BLUE),
            Component.translatable("upgrade.aether_spellbooks.arcane_upgrade").withStyle(ChatFormatting.GRAY),
            Component.translatable("item.aether_spellbooks.smithing_template.arcane_upgrade.base_slot_description"),
            Component.translatable("item.aether_spellbooks.smithing_template.arcane_upgrade.additions_slot_description"),
            List.of(ResourceLocation.withDefaultNamespace("item/empty_armor_slot_helmet"), ResourceLocation.withDefaultNamespace("item/empty_slot_sword"),
                    ResourceLocation.withDefaultNamespace("item/empty_armor_slot_chestplate"), ResourceLocation.withDefaultNamespace("item/empty_armor_slot_leggings"),
                    ResourceLocation.withDefaultNamespace("item/empty_armor_slot_boots")),
            List.of(AetherSpellbooks.id("item/empty_slot_rune"))));

    // --- Spawn eggs ---
    public static final RegistryObject<Item> VALKYRIE_SORCERESS_SPAWN_EGG = ITEMS.register("valkyrie_sorceress_spawn_egg",
            () -> new ForgeSpawnEggItem(ASEntities.VALKYRIE_SORCERESS, 0xECEFF6, 0xE3B64A, new Item.Properties()));
    public static final RegistryObject<Item> SOLAR_ACOLYTE_SPAWN_EGG = ITEMS.register("solar_acolyte_spawn_egg",
            () -> new ForgeSpawnEggItem(ASEntities.SOLAR_ACOLYTE, 0x7A2418, 0xFFB43C, new Item.Properties()));

    public static final List<RegistryObject<Item>> SPELLBOOKS = List.of(SLIDER_CODEX, VALKYRIE_GRIMOIRE, SOLAR_CODEX);

    private static RegistryObject<Item> mageArmor(String name, ASArmorMaterials set, ArmorItem.Type type) {
        return ITEMS.register(name, () -> {
            Item.Properties properties = ItemPropertiesHelper.equipment(1).rarity(set.rarity());
            return new AetherMageArmorItem(set, type, set == ASArmorMaterials.PHOENIX_MAGE ? properties.fireResistant() : properties);
        });
    }

    private static Item.Properties weaponProperties(Rarity rarity) {
        return ItemPropertiesHelper.equipment(1).rarity(rarity);
    }

    private static Item.Properties bookProperties(Rarity rarity) {
        return ItemPropertiesHelper.equipment().stacksTo(1).fireResistant().rarity(rarity);
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
