package com.aetherspellbooks.client;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.entity.SolarOrb;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.cache.texture.AnimatableTexture;

/**
 * A miniature sun: two counter-rotating additive ray layers and a wide additive halo around a boiling,
 * alpha-blended core, all full-bright.
 */
public class SolarOrbRenderer extends EntityRenderer<SolarOrb> {
    private static final ResourceLocation CORE = AetherSpellbooks.id("textures/entity/solar_core.png");
    private static final ResourceLocation RAYS = AetherSpellbooks.id("textures/entity/solar_rays.png");
    private static final ResourceLocation GLOW = AetherSpellbooks.id("textures/entity/thunder_glow.png");

    public SolarOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(@NotNull SolarOrb entity, float yaw, float partialTicks, PoseStack poseStack, @NotNull MultiBufferSource buffer, int light) {
        float age = entity.tickCount + partialTicks;
        // grow in over the first half second
        float grow = Mth.clamp(age / 10f, 0f, 1f);
        float pulse = 1f + 0.07f * Mth.sin(age * 0.5f);

        poseStack.pushPose();
        poseStack.translate(0, entity.getBbHeight() * 0.5, 0);
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180f));
        poseStack.scale(grow, grow, grow);

        layer(poseStack, buffer, RenderType.eyes(GLOW), 4.2f * pulse, 0, 255, 170, 80, 170);
        layer(poseStack, buffer, RenderType.eyes(RAYS), 3.4f, age * 1.5f, 255, 200, 90, 220);
        layer(poseStack, buffer, RenderType.eyes(RAYS), 2.6f * pulse, -age * 2.4f + 20, 255, 230, 140, 200);
        // the core is alpha blended, not additive, so the sun keeps a solid, dark-rimmed heart against the bright sky;
        // its frames (solar_core.png.mcmeta) make it boil
        AnimatableTexture.setAndUpdate(CORE);
        layer(poseStack, buffer, RenderType.entityTranslucentEmissive(CORE), 1.5f * pulse, age * 3f, 255, 255, 255, 255);

        poseStack.popPose();
        super.render(entity, yaw, partialTicks, poseStack, buffer, light);
    }

    private static void layer(PoseStack poseStack, MultiBufferSource buffer, RenderType type, float size, float spin, int r, int g, int b, int a) {
        poseStack.pushPose();
        poseStack.scale(size, size, size);
        poseStack.mulPose(Axis.ZP.rotationDegrees(spin));
        RenderHelper.quad(buffer.getBuffer(type), poseStack.last(), r, g, b, a);
        poseStack.popPose();
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull SolarOrb entity) {
        return CORE;
    }
}
