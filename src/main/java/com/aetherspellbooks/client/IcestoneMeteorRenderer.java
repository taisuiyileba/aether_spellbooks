package com.aetherspellbooks.client;

import com.aetherspellbooks.entity.IcestoneMeteor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Renders the meteor as a big tumbling boulder of the Aether's icestone (its block model, loaded at runtime),
 * lit as if it glows with cold.
 */
public class IcestoneMeteorRenderer extends EntityRenderer<IcestoneMeteor> {
    public IcestoneMeteorRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.8f;
    }

    @Override
    public void render(@NotNull IcestoneMeteor entity, float yaw, float partialTicks, PoseStack poseStack, @NotNull MultiBufferSource buffer, int light) {
        float age = entity.tickCount + partialTicks;
        float grow = Math.min(1f, age / 6f);
        poseStack.pushPose();
        poseStack.translate(0, entity.getBbHeight() * 0.5, 0);
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 9f));
        poseStack.mulPose(Axis.XP.rotationDegrees(age * 14f));
        poseStack.mulPose(Axis.ZP.rotationDegrees(age * 5f));
        float size = 2.1f * grow;
        poseStack.scale(size, size, size);
        poseStack.translate(-0.5, -0.5, -0.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(IcestoneMeteor.icestone(), poseStack, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(entity, yaw, partialTicks, poseStack, buffer, light);
    }

    @SuppressWarnings("deprecation")
    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull IcestoneMeteor entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
