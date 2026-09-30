package com.aetherspellbooks.client.armor;

import com.aetherspellbooks.item.AetherMageArmorItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerModelPart;

/**
 * The cape of a mage chestplate. It swings like the vanilla cape (same cloak physics) but hangs just behind
 * the chestplate's back plate instead of inside it, and is drawn with cutout so the phoenix cape's flames show.
 * Players with their own cape keep theirs.
 */
public class MageCapeLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** How far behind the body the cape hangs, in blocks (the vanilla cape sits at 0.125, inside a chestplate). */
    private static final float DEPTH = 0.2f;

    public MageCapeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!(player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof AetherMageArmorItem armor)
                || player.isInvisible() || !player.isModelPartShown(PlayerModelPart.CAPE) || player.getSkin().capeTexture() != null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, DEPTH);
        double dx = Mth.lerp(partialTicks, player.xCloakO, player.xCloak) - Mth.lerp(partialTicks, player.xo, player.getX());
        double dy = Mth.lerp(partialTicks, player.yCloakO, player.yCloak) - Mth.lerp(partialTicks, player.yo, player.getY());
        double dz = Mth.lerp(partialTicks, player.zCloakO, player.zCloak) - Mth.lerp(partialTicks, player.zo, player.getZ());
        float bodyYaw = Mth.rotLerp(partialTicks, player.yBodyRotO, player.yBodyRot);
        double sin = Mth.sin(bodyYaw * Mth.DEG_TO_RAD);
        double cos = -Mth.cos(bodyYaw * Mth.DEG_TO_RAD);
        float lift = Mth.clamp((float) dy * 10.0F, -6.0F, 32.0F);
        float swing = Mth.clamp((float) (dx * sin + dz * cos) * 100.0F, 0.0F, 150.0F);
        float sway = Mth.clamp((float) (dx * cos - dz * sin) * 100.0F, -20.0F, 20.0F);
        float bob = Mth.lerp(partialTicks, player.oBob, player.bob);
        lift += Mth.sin(Mth.lerp(partialTicks, player.walkDistO, player.walkDist) * 6.0F) * 32.0F * bob;
        if (player.isCrouching()) {
            lift += 25.0F;
            poseStack.translate(0.0F, 0.1F, 0.05F);
        }
        poseStack.mulPose(Axis.XP.rotationDegrees(6.0F + swing / 2.0F + lift));
        poseStack.mulPose(Axis.ZP.rotationDegrees(sway / 2.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - sway / 2.0F));
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(armor.getCapeTexture()));
        getParentModel().renderCloak(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}
