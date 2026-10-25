package com.aetherspellbooks.client;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.dungeon.GuardianKind;
import com.aetherspellbooks.entity.GuardianSoul;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A boss's soul: a flickering sprite (four frames stacked in one texture) inside a tinted glow. During the revival ritual
 * it swells, spins faster and raises a pillar of light that is gone the moment the boss returns.
 */
public class GuardianSoulRenderer extends EntityRenderer<GuardianSoul> {
    private static final int FRAMES = 4;
    private static final ResourceLocation GLOW = AetherSpellbooks.id("textures/entity/guardian_soul_glow.png");
    private static final ResourceLocation BEAM = AetherSpellbooks.id("textures/entity/guardian_soul_beam.png");
    private static final ResourceLocation[] SPRITES = new ResourceLocation[GuardianKind.values().length];

    static {
        for (GuardianKind kind : GuardianKind.values()) {
            SPRITES[kind.ordinal()] = AetherSpellbooks.id("textures/entity/guardian_soul_" + kind.id() + ".png");
        }
    }

    public GuardianSoulRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(@NotNull GuardianSoul soul, float yaw, float partialTicks, PoseStack poseStack, @NotNull MultiBufferSource buffer, int light) {
        GuardianKind kind = soul.kind();
        Vector3f color = kind.color();
        float age = soul.tickCount + partialTicks;
        int ritual = soul.ritualTicks();
        float progress = ritual < 0 ? 0 : Mth.clamp((ritual + partialTicks) / GuardianSoul.RITUAL_LENGTH, 0, 1);
        float bob = Mth.sin(age * 0.08f) * 0.08f + progress * 0.6f;
        float swell = 1f + progress * 0.7f + 0.05f * Mth.sin(age * 0.4f);

        poseStack.pushPose();
        poseStack.translate(0, soul.getBbHeight() * 0.5 + bob, 0);

        if (progress > 0) {
            renderBeam(poseStack, buffer, color, progress, age);
        }

        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180f));

        poseStack.pushPose();
        float glow = 2f * swell;
        poseStack.scale(glow, glow, glow);
        poseStack.mulPose(Axis.ZP.rotationDegrees(-age * (2f + progress * 10f)));
        quad(buffer.getBuffer(RenderType.entityTranslucentEmissive(GLOW)), poseStack.last().pose(), 0, 1, color, 0.75f);
        poseStack.popPose();

        float size = 1.15f * swell;
        poseStack.scale(size, size, size);
        int frame = (int) (age / 3) % FRAMES;
        quad(buffer.getBuffer(RenderType.entityTranslucentEmissive(SPRITES[kind.ordinal()])), poseStack.last().pose(),
                frame / (float) FRAMES, (frame + 1) / (float) FRAMES, new Vector3f(1, 1, 1), 1f);
        poseStack.popPose();
        super.render(soul, yaw, partialTicks, poseStack, buffer, light);
    }

    /** Two crossed vertical quads, growing upward as the ritual goes on. */
    private static void renderBeam(PoseStack poseStack, MultiBufferSource buffer, Vector3f color, float progress, float age) {
        float height = 2f + progress * 6f;
        float width = 0.35f + progress * 0.5f;
        float fade = progress > 0.9f ? (1f - progress) * 10f : Math.min(1f, progress * 4f);
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(BEAM));
        for (int i = 0; i < 2; i++) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(age * 4f + i * 90f));
            poseStack.translate(0, height * 0.5f - 0.6f, 0);
            poseStack.scale(width, height, 1f);
            quad(consumer, poseStack.last().pose(), 0, 1, color, 0.65f * fade);
            poseStack.popPose();
        }
    }

    private static void quad(VertexConsumer consumer, Matrix4f pose, float v0, float v1, Vector3f tint, float alpha) {
        int r = (int) (tint.x * 255);
        int g = (int) (tint.y * 255);
        int b = (int) (tint.z * 255);
        int a = (int) (Mth.clamp(alpha, 0, 1) * 255);
        vertex(consumer, pose, -0.5f, -0.5f, 0f, v1, r, g, b, a);
        vertex(consumer, pose, 0.5f, -0.5f, 1f, v1, r, g, b, a);
        vertex(consumer, pose, 0.5f, 0.5f, 1f, v0, r, g, b, a);
        vertex(consumer, pose, -0.5f, 0.5f, 0f, v0, r, g, b, a);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, float x, float y, float u, float v, int r, int g, int b, int a) {
        // a raw world-up normal keeps the emissive sprite equally bright from every side (see RenderHelper)
        consumer.vertex(pose, x, y, 0f).color(r, g, b, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(0f, 1f, 0f).endVertex();
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull GuardianSoul soul) {
        return SPRITES[soul.kind().ordinal()];
    }
}
