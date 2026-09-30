package com.aetherspellbooks.item.weapon;

import com.aetherspellbooks.client.ClientViewHelper;
import com.aetherspellbooks.registry.ASParticles;
import com.aetherteam.aether.AetherTags;
import com.aetherteam.aether.entity.miscellaneous.CloudMinion;
import com.aetherteam.aether.item.EquipmentUtil;
import io.redspace.ironsspellbooks.item.weapons.StaffItem;
import io.redspace.ironsspellbooks.item.weapons.StaffTier;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * An Iron's Spells staff (right-click casts the selected spell; school power while held) with an Aether twist.
 */
public class AetherStaffItem extends StaffItem {
    public enum Trait {
        NONE,
        /** Like the Aether's Cloud Staff: sneak + use summons two cloud minions, swinging makes them fire. */
        CLOUD_MINIONS,
        /** Like gravitite weapons: a fully charged hit launches a grounded target into the air. */
        LAUNCH,
        /** Golden sparkles drift from the scepter while it is held. */
        SPARKLES,
        /** Embers rise from the miniature sun while it is held. */
        EMBERS
    }

    /** Ticks between cloud minion volleys (the Aether's default cloud staff cooldown). */
    public static final int VOLLEY_COOLDOWN = 40;
    private final Trait trait;

    public AetherStaffItem(StaffTier tier, Trait trait, Properties properties) {
        super(properties.attributes(staffAttributes(tier)));
        this.trait = trait;
    }

    private static net.minecraft.world.item.component.ItemAttributeModifiers staffAttributes(StaffTier tier) {
        var builder = net.minecraft.world.item.component.ItemAttributeModifiers.builder();
        var slot = net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND;
        builder.add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(BASE_ATTACK_DAMAGE_ID, tier.getAttackDamageBonus(), net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE), slot);
        builder.add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED,
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(BASE_ATTACK_SPEED_ID, tier.getSpeed(), net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE), slot);
        for (var attribute : tier.getAdditionalAttributes()) builder.add(attribute.attribute(), attribute.createModifier("mainhand"), slot);
        return builder.build();
    }

    public Trait getTrait() {
        return trait;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (trait == Trait.CLOUD_MINIONS && player.isShiftKeyDown()) {
            summonOrDismissMinions(player, hand);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return super.use(level, player, hand);
    }

    /** Two cloud minions at the wielder's sides, or dismisses them if they are already out. */
    public static void summonOrDismissMinions(Player player, InteractionHand hand) {
        java.util.Optional.of(player.getData(com.aetherteam.aether.attachment.AetherDataAttachments.AETHER_PLAYER)).ifPresent(aetherPlayer -> {
            Level level = player.level();
            player.swing(hand);
            if (aetherPlayer.getCloudMinions().isEmpty()) {
                if (!level.isClientSide) {
                    CloudMinion right = new CloudMinion(level, player, HumanoidArm.RIGHT);
                    CloudMinion left = new CloudMinion(level, player, HumanoidArm.LEFT);
                    level.addFreshEntity(right);
                    level.addFreshEntity(left);
                    aetherPlayer.setCloudMinions(player, right, left);
                    if (level instanceof ServerLevel server) {
                        server.sendParticles(ASParticles.CLOUD_PUFF.get(), player.getX(), player.getY() + 1, player.getZ(), 16, 0.8, 0.4, 0.8, 0.02);
                    }
                }
            } else {
                aetherPlayer.getCloudMinions().forEach(minion -> minion.setLifeSpan(0));
            }
        });
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
        if (trait == Trait.CLOUD_MINIONS && entity instanceof Player player && !player.getCooldowns().isOnCooldown(this)) {
            java.util.Optional.of(player.getData(com.aetherteam.aether.attachment.AetherDataAttachments.AETHER_PLAYER)).ifPresent(aetherPlayer -> {
                if (aetherPlayer.isHitting() && !aetherPlayer.getCloudMinions().isEmpty()) {
                    aetherPlayer.getCloudMinions().forEach(minion -> minion.setShouldShoot(true));
                    if (!player.getAbilities().instabuild) {
                        player.getCooldowns().addCooldown(this, VOLLEY_COOLDOWN);
                    }
                }
            });
        }
        return super.onEntitySwing(stack, entity);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (trait == Trait.LAUNCH && !attacker.level().isClientSide && EquipmentUtil.isFullStrength(attacker)
                && !target.getType().is(AetherTags.Entities.UNLAUNCHABLE) && (target.onGround() || target.isInFluidType())) {
            target.push(0, 1, 0);
            if (target instanceof ServerPlayer player) {
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
            }
            if (attacker.level() instanceof ServerLevel server) {
                server.sendParticles(ASParticles.GRAVITY_MOTE.get(), target.getX(), target.getY() + 0.2, target.getZ(), 12, 0.4, 0.1, 0.4, 0.05);
            }
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (!level.isClientSide || !(entity instanceof LivingEntity holder) || holder.getMainHandItem() != stack || level.random.nextInt(3) != 0
                || ClientViewHelper.isFirstPersonSelf(holder)) {
            return;
        }
        ParticleOptions particle = switch (trait) {
            case SPARKLES -> ASParticles.SKY_SPARKLE.get();
            case EMBERS -> ParticleHelper.EMBERS;
            case CLOUD_MINIONS -> level.random.nextInt(4) == 0 ? ASParticles.CLOUD_PUFF.get() : null;
            case LAUNCH -> level.random.nextInt(2) == 0 ? ASParticles.GRAVITY_MOTE.get() : null;
            default -> null;
        };
        if (particle == null) {
            return;
        }
        // roughly where the staff's head is: beside and above the main hand
        Vec3 look = Vec3.directionFromRotation(0, holder.yBodyRot);
        Vec3 side = new Vec3(-look.z, 0, look.x).scale(holder.getMainArm() == HumanoidArm.RIGHT ? -0.45 : 0.45);
        Vec3 head = holder.position().add(side).add(look.scale(0.25)).add(0, holder.getBbHeight() * 0.85, 0);
        level.addParticle(particle, head.x + (level.random.nextDouble() - 0.5) * 0.3, head.y + (level.random.nextDouble() - 0.5) * 0.3,
                head.z + (level.random.nextDouble() - 0.5) * 0.3, 0, trait == Trait.EMBERS ? 0.03 : 0.01, 0);
        if (trait == Trait.EMBERS && level.random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.SMALL_FLAME, head.x, head.y, head.z, 0, 0.01, 0);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        if (trait == Trait.CLOUD_MINIONS || trait == Trait.LAUNCH) {
            tooltip.add(Component.translatable("tooltip.aether_spellbooks.staff." + trait.name().toLowerCase()).withStyle(ChatFormatting.GOLD));
        }
    }
}
