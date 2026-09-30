package com.aetherspellbooks.event;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.item.weapon.AetherSpellbladeItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Spellblade abilities that act on the damage dealt (the same hook the Aether's Flaming and Holy swords use). */
@EventBusSubscriber(modid = AetherSpellbooks.MODID)
public class ASWeaponEvents {
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        // melee only: the attacker itself must be the direct source
        if (!(event.getSource().getDirectEntity() instanceof LivingEntity attacker)) {
            return;
        }
        ItemStack held = attacker.getMainHandItem();
        if (!(held.getItem() instanceof AetherSpellbladeItem blade) || !AetherSpellbladeItem.isFullStrength(attacker)) {
            return;
        }
        LivingEntity target = event.getEntity();
        switch (blade.getAbility()) {
            case FLAMING -> target.igniteForSeconds(AetherSpellbladeItem.burnSeconds(attacker));
            case HOLY -> event.setNewDamage(event.getNewDamage() + AetherSpellbladeItem.holyBonus(held, target));
            default -> {
            }
        }
    }
}
