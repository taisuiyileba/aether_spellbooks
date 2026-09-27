package com.aetherspellbooks.event;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.item.weapon.AetherSpellbladeItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Spellblade abilities that act on the damage dealt (the same hook the Aether's Flaming and Holy swords use). */
@Mod.EventBusSubscriber(modid = AetherSpellbooks.MODID)
public class ASWeaponEvents {
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
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
            case FLAMING -> target.setSecondsOnFire(AetherSpellbladeItem.burnSeconds(attacker));
            case HOLY -> event.setAmount(event.getAmount() + AetherSpellbladeItem.holyBonus(held, target));
            default -> {
            }
        }
    }
}
