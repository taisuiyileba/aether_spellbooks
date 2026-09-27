package com.aetherspellbooks.event;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.item.ZaniteFocusPendantItem;
import com.aetherspellbooks.registry.ASItems;
import com.aetherspellbooks.spells.FrostboundCrystalSpell;
import com.aetherspellbooks.util.CloudSentinels;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

@Mod.EventBusSubscriber(modid = AetherSpellbooks.MODID)
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
        CuriosApi.getCuriosInventory(player).ifPresent(inventory ->
                inventory.findCurios(stack -> stack.getItem() instanceof ZaniteFocusPendantItem)
                        .forEach(slot -> ZaniteFocusPendantItem.wear(slot.stack())));
        CloudSentinels.volley(player);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide) {
            CloudSentinels.tick(event.player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            FrostboundCrystalSpell.tickTrails();
        }
    }

    /** Frostbound crystals chill what they hit. */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        FrostboundCrystalSpell.onCrystalHit(event.getEntity(), event.getSource());
    }

    /** The Valkyrie Mantle softens falls, like a Valkyrie gliding down. */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        LivingEntity entity = event.getEntity();
        if (event.getDistance() > 3 && CuriosApi.getCuriosInventory(entity)
                .map(inventory -> inventory.isEquipped(ASItems.VALKYRIE_MANTLE.get())).orElse(false)) {
            event.setDamageMultiplier(event.getDamageMultiplier() * MANTLE_FALL_DAMAGE_MULTIPLIER);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CloudSentinels.clear();
        FrostboundCrystalSpell.clearTrails();
    }
}
