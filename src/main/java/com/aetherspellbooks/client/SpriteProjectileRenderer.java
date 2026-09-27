package com.aetherspellbooks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Renders an entity as a full-bright, camera-facing sprite that spins and pulses,
 * optionally with an additive glow halo behind it.
 */
public class SpriteProjectileRenderer<T extends Entity> extends EntityRenderer<T> {
    private final ResourceLocation texture;
    @Nullable
    private final ResourceLocation glow;
    private final float scale;
    private final float glowScale;
    private final float spinSpeed;

    public SpriteProjectileRenderer(EntityRendererProvider.Context context, ResourceLocation texture, float scale) {
        this(context, texture, null, scale, 0, 12f);
    }

    public SpriteProjectileRenderer(EntityRendererProvider.Context context, ResourceLocation texture, @Nullable ResourceLocation glow,
                                    float scale, float glowScale, float spinSpeed) {
        super(context);
        this.texture = texture;
        this.glow = glow;
        this.scale = scale;
        this.glowScale = glowScale;
        this.spinSpeed = spinSpeed;
    }

    @Override
    public void render(@NotNull T entity, float yaw, float partialTicks, PoseStack poseStack, @NotNull MultiBufferSource buffer, int light) {
        float age = entity.tickCount + partialTicks;
        float pulse = 1f + 0.08f * Mth.sin(age * 0.6f);
        poseStack.pushPose();
        poseStack.translate(0, entity.getBbHeight() * 0.5, 0);
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180f));

        if (glow != null) {
            poseStack.pushPose();
            poseStack.scale(glowScale * pulse, glowScale * pulse, glowScale * pulse);
            poseStack.mulPose(Axis.ZP.rotationDegrees(-age * spinSpeed * 0.5f));
            RenderHelper.quad(buffer.getBuffer(RenderType.eyes(glow)), poseStack.last(), 255, 255, 255, 200);
            poseStack.popPose();
        }

        poseStack.scale(scale * pulse, scale * pulse, scale * pulse);
        poseStack.mulPose(Axis.ZP.rotationDegrees(age * spinSpeed));
        RenderHelper.quad(buffer.getBuffer(RenderType.entityTranslucentEmissive(texture)), poseStack.last(), 255, 255, 255, 255);
        poseStack.popPose();
        super.render(entity, yaw, partialTicks, poseStack, buffer, light);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull T entity) {
        return texture;
    }
}
