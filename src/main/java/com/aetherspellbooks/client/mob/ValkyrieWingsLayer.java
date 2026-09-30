package com.aetherspellbooks.client.mob;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.entity.mob.ValkyrieSorceress;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMob;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.renderer.GeoRenderer;

/**
 * Feathered wings on the Valkyrie Sorceress' back: folded and gently breathing on the ground, beating while she glides,
 * flung wide while she casts. Each wing is two panels (arm and hand) so it bends as it flaps.
 */
public class ValkyrieWingsLayer extends BoneAnchoredLayer {
    public static final ResourceLocation TEXTURE = AetherSpellbooks.id("textures/entity/valkyrie_wings.png");
    /** Root of the wings on the upper back, in torso-bone space (blocks). */
    public static final float ROOT_X = 0.06f;
    public static final float ROOT_Y = 1.36f;
    public static final float ROOT_Z = 0.17f;
    /** Each panel takes half of the texture: the inner panel the left half, the outer panel the right half. */
    public static final float PANEL_WIDTH = 0.62f;
    public static final float WING_TOP = 0.42f;
    public static final float WING_BOTTOM = -0.98f;

    public ValkyrieWingsLayer(GeoRenderer<AbstractSpellCastingMob> renderer) {
        super(renderer, "torso");
    }

    @Override
    protected void renderOnBone(PoseStack poseStack, AbstractSpellCastingMob animatable, MultiBufferSource bufferSource, float partialTick, int packedLight, int packedOverlay) {
        if (!(animatable instanceof ValkyrieSorceress sorceress)) {
            return;
        }
        float spread = sorceress.getWingSpread(partialTick);
        float phase = sorceress.getFlapPhase(partialTick);
        float beat = Mth.sin(phase);
        // swept back when folded, opening out to the sides as they spread
        float sweep = Mth.lerp(spread, 1.25f, 0.25f) + beat * Mth.lerp(spread, 0.04f, 0.28f);
        float lift = Mth.lerp(spread, 0.10f, 0.30f) + beat * Mth.lerp(spread, 0.05f, 0.40f);
        // the outer panel lags behind the beat and tucks in when folded
        float fold = Mth.lerp(spread, 0.35f, 0.08f) + Mth.sin(phase - 0.9f) * Mth.lerp(spread, 0.03f, 0.22f);

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        for (int side = -1; side <= 1; side += 2) {
            poseStack.pushPose();
            poseStack.translate(side * ROOT_X, ROOT_Y, ROOT_Z);
            poseStack.mulPose(Axis.YP.rotation(-side * sweep));
            poseStack.mulPose(Axis.ZP.rotation(side * lift));
            panel(consumer, poseStack.last(), side, 0f, 0.5f, packedLight, packedOverlay);
            poseStack.translate(side * PANEL_WIDTH, 0, 0);
            poseStack.mulPose(Axis.YP.rotation(-side * fold));
            poseStack.mulPose(Axis.ZP.rotation(-side * fold * 0.35f));
            panel(consumer, poseStack.last(), side, 0.5f, 1f, packedLight, packedOverlay);
            poseStack.popPose();
        }
    }

    /** One flat panel extending outward along +X or -X; the texture mirrors with it. */
    private static void panel(VertexConsumer consumer, PoseStack.Pose pose, int side, float u0, float u1, int light, int overlay) {
        float x1 = side * PANEL_WIDTH;
        vertex(consumer, pose, 0, WING_BOTTOM, u0, 1, light, overlay);
        vertex(consumer, pose, x1, WING_BOTTOM, u1, 1, light, overlay);
        vertex(consumer, pose, x1, WING_TOP, u1, 0, light, overlay);
        vertex(consumer, pose, 0, WING_TOP, u0, 0, light, overlay);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float u, float v, int light, int overlay) {
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();
        consumer.addVertex(matrix, x, y, 0)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                // an upward normal lights both faces of the two-sided panels evenly
                .setNormal(pose, 0, 1, 0);
    }
}
