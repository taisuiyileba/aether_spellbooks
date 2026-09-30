package com.aetherspellbooks.item.weapon;


import com.aetherteam.aether.item.EquipmentUtil;
import io.redspace.ironsspellbooks.api.item.weapons.MagicSwordItem;
import io.redspace.ironsspellbooks.api.registry.SpellDataRegistryHolder;
import io.redspace.ironsspellbooks.item.weapons.ExtendedWeaponTier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.List;

/**
 * One of the Aether's dungeon swords reforged into an Iron's Spells magic sword: the same damage, an imbued spell,
 * school power and mana while held, and the original sword's ability (on fully charged hits, like the Aether's).
 */
public class AetherSpellbladeItem extends MagicSwordItem {
    public enum Ability {
        /** Flaming Sword: sets the target ablaze for 30 seconds (+4 per level of Fire Aspect). */
        FLAMING,
        /** Lightning Sword: calls lightning down on the target (it spares the wielder). */
        LIGHTNING,
        /** Holy Sword: 8.25 bonus damage to undead (+2.5 per level of Smite). */
        HOLY,
        /** Vampire Blade: each hit heals the wielder by half a heart. */
        VAMPIRE
    }

    public static final float HOLY_BONUS = 8.25f;
    private final Ability ability;

    public AetherSpellbladeItem(ExtendedWeaponTier tier, Ability ability, Properties properties, SpellDataRegistryHolder... spells) {
        super(tier, properties.attributes(createAttributes(tier)), spells);
        this.ability = ability;
    }

    public Ability getAbility() {
        return ability;
    }

    public static boolean isFullStrength(LivingEntity attacker) {
        return EquipmentUtil.isFullStrength(attacker);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!attacker.level().isClientSide && isFullStrength(attacker)) {
            switch (ability) {
                case LIGHTNING -> {
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(attacker.level());
                    if (bolt != null) {
                        bolt.getData(com.aetherteam.aether.attachment.AetherDataAttachments.LIGHTNING_TRACKER).setOwner(attacker);
                        if (attacker instanceof ServerPlayer player) {
                            bolt.setCause(player);
                        }
                        bolt.setPos(target.getX(), target.getY(), target.getZ());
                        attacker.level().addFreshEntity(bolt);
                    }
                }
                case VAMPIRE -> {
                    if (attacker.getHealth() < attacker.getMaxHealth()) {
                        attacker.heal(1.0f);
                    }
                }
                default -> {
                }
            }
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    /** Flaming: how long the target burns. */
    public static int burnSeconds(LivingEntity attacker) {
        return 30 + 4 * EnchantmentHelper.getEnchantmentLevel(attacker.registryAccess().holderOrThrow(Enchantments.FIRE_ASPECT), attacker);
    }

    /** Holy: bonus damage against this target, or 0. */
    public static float holyBonus(ItemStack stack, LivingEntity target) {
        if (!target.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD) && !target.isInvertedHealAndHarm()) {
            return 0;
        }
        return HOLY_BONUS + 2.5f * stack.getEnchantmentLevel(target.registryAccess().holderOrThrow(Enchantments.SMITE));
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.aether_spellbooks.spellblade." + ability.name().toLowerCase()).withStyle(ChatFormatting.GOLD));
    }
}
