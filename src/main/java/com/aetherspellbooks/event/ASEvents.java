package com.aetherspellbooks.event;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.item.ZaniteFocusPendantItem;
import com.aetherspellbooks.registry.ASItems;
import com.aetherspellbooks.spells.FrostboundCrystalSpell;
import com.aetherspellbooks.util.CloudSentinels;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = AetherSpellbooks.MODID)
public class ASEvents {
    public static final float MANTLE_FALL_DAMAGE_MULTIPLIER = 0.5f;

    /**
     * Every cast wears down an equipped Zanite Focus Pendant (raising its bonus),
     * and makes active Cloud Sentinels loose a volley.
     */
    @SubscribeEvent
    public static void onSpellCast(SpellOnCastEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        com.aetherspellbooks.compat.AccessoriesCompat.forEachEquipped(player,
                stack -> stack.getItem() instanceof ZaniteFocusPendantItem, ZaniteFocusPendantItem::wear);
        CloudSentinels.volley(player);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!event.getEntity().level().isClientSide) {
            CloudSentinels.tick(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        FrostboundCrystalSpell.tickTrails();
    }

    /** Frostbound crystals chill what they hit. */
    @SubscribeEvent
    public static void onLivingHurt(LivingIncomingDamageEvent event) {
        FrostboundCrystalSpell.onCrystalHit(event.getEntity(), event.getSource());
    }

    /** The Valkyrie Mantle softens falls, like a Valkyrie gliding down. */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        LivingEntity entity = event.getEntity();
        if (event.getDistance() > 3 && com.aetherspellbooks.compat.AccessoriesCompat.isEquipped(entity, ASItems.VALKYRIE_MANTLE.get())) {
            event.setDamageMultiplier(event.getDamageMultiplier() * MANTLE_FALL_DAMAGE_MULTIPLIER);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CloudSentinels.clear();
        FrostboundCrystalSpell.clearTrails();
    }
}
