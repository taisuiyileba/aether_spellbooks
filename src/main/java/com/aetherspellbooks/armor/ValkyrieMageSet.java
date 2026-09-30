package com.aetherspellbooks.armor;

import com.aetherspellbooks.registry.ASArmorMaterials;
import com.aetherspellbooks.registry.ASParticles;
import com.aetherteam.aether.mixin.mixins.common.accessor.ServerGamePacketListenerImplAccessor;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;


/**
 * Full Valkyrie Mage set.
 * <ul>
 *     <li>Inherited from the Aether's Valkyrie armour: holding jump in mid-air lifts the wearer for a short flight
 *     (the same timer and lift curve as the original, sharing the Aether's player data), and no fall damage.</li>
 *     <li>Valkyrie's Grace: 15% faster casting while airborne.</li>
 * </ul>
 */
public final class ValkyrieMageSet {
    public static final net.minecraft.resources.ResourceLocation GRACE_ID = com.aetherspellbooks.AetherSpellbooks.id("valkyries_grace");
    public static final double GRACE_CAST_TIME_REDUCTION = 0.15;

    private ValkyrieMageSet() {
    }

    public static boolean isWornBy(LivingEntity entity) {
        return ASArmorMaterials.VALKYRIE_MAGE.isWornBy(entity);
    }

    /** Called every tick for every player, on both sides (the client moves its own player). */
    public static void tick(Player player, boolean fullSet) {
        boolean grounded = player.onGround() || player.isInFluidType();
        updateGrace(player, fullSet && !grounded);
        if (!fullSet || player.getAbilities().flying) {
            return;
        }
        java.util.Optional.of(player.getData(com.aetherteam.aether.attachment.AetherDataAttachments.AETHER_PLAYER)).ifPresent(aetherPlayer -> {
            // Mirrors the Aether's ValkyrieArmor#handleFlight
            if (aetherPlayer.isJumping() && !grounded) {
                if (aetherPlayer.getFlightModifier() >= aetherPlayer.getFlightModifierMax()) {
                    aetherPlayer.setFlightModifier(aetherPlayer.getFlightModifierMax());
                }
                if (aetherPlayer.getFlightTimer() > 2) {
                    if (aetherPlayer.getFlightTimer() < aetherPlayer.getFlightTimerMax()) {
                        aetherPlayer.setFlightModifier(aetherPlayer.getFlightModifier() + 0.25f);
                        aetherPlayer.setFlightTimer(aetherPlayer.getFlightTimer() + 1);
                    }
                } else {
                    aetherPlayer.setFlightTimer(aetherPlayer.getFlightTimer() + 1);
                }
            } else if (!aetherPlayer.isJumping()) {
                aetherPlayer.setFlightModifier(1.0f);
            }
            if (grounded) {
                aetherPlayer.setFlightTimer(0);
                aetherPlayer.setFlightModifier(1.0f);
            }
            boolean flying = aetherPlayer.isJumping() && !grounded && aetherPlayer.getFlightTimer() > 2
                    && aetherPlayer.getFlightTimer() < aetherPlayer.getFlightTimerMax() && aetherPlayer.getFlightModifier() > 1.0f;
            if (flying) {
                Vec3 motion = player.getDeltaMovement();
                player.setDeltaMovement(motion.x, 0.025f * aetherPlayer.getFlightModifier(), motion.z);
                if (player.level() instanceof ServerLevel server && player.tickCount % 2 == 0) {
                    Vec3 back = Vec3.directionFromRotation(0, player.yBodyRot).scale(-0.4);
                    server.sendParticles(ASParticles.FEATHER.get(), player.getX() + back.x, player.getY() + 1.1, player.getZ() + back.z, 1, 0.35, 0.2, 0.35, 0.01);
                    server.sendParticles(ASParticles.SKY_SPARKLE.get(), player.getX() + back.x, player.getY() + 0.9, player.getZ() + back.z, 1, 0.3, 0.3, 0.3, 0.01);
                }
            }
            if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
                // the server would otherwise kick the player for flying
                ((ServerGamePacketListenerImplAccessor) serverPlayer.connection).aether$setAboveGroundTickCount(0);
            }
        });
    }

    private static void updateGrace(Player player, boolean active) {
        if (player.level().isClientSide) {
            return;
        }
        AttributeInstance castTime = player.getAttribute(AttributeRegistry.CAST_TIME_REDUCTION);
        if (castTime == null) {
            return;
        }
        boolean present = castTime.getModifier(GRACE_ID) != null;
        if (active && !present) {
            castTime.addTransientModifier(new AttributeModifier(GRACE_ID, GRACE_CAST_TIME_REDUCTION, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        } else if (!active && present) {
            castTime.removeModifier(GRACE_ID);
        }
    }
}
