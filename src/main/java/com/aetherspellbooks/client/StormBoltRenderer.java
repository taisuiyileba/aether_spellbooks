package com.aetherspellbooks.client;

import com.aetherspellbooks.entity.StormBolt;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

/**
 * A jagged, forking lightning bolt from the cloud down to the ground, in three glowing layers (like vanilla lightning).
 * The path re-jags every other tick so the bolt flickers, and fades over its short life.
 */
public class StormBoltRenderer extends EntityRenderer<StormBolt> {
    private static final float[][] LAYERS = {
            // half width, r, g, b, alpha
            {0.24f, 0.45f, 0.55f, 1.0f, 0.22f},
            {0.11f, 0.75f, 0.82f, 1.0f, 0.45f},
            {0.045f, 1.0f, 1.0f, 1.0f, 0.95f},
    };

    public StormBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(@NotNull StormBolt entity, float yaw, float partialTicks, PoseStack poseStack, @NotNull MultiBufferSource buffer, int light) {
        float height = entity.getHeight();
        float fade = Mth.clamp(1f - (entity.tickCount + partialTicks) / StormBolt.LIFETIME, 0f, 1f);
        RandomSource random = RandomSource.create(entity.getId() * 7919L + entity.tickCount / 2);
        int segments = Math.max(4, (int) (height / 0.7f));
        float[][] main = new float[segments + 1][3];
        float x = (random.nextFloat() - 0.5f) * 0.8f;
        float z = (random.nextFloat() - 0.5f) * 0.8f;
        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            // wander on the way down, land exactly on the target
            float settle = 1f - t * t;
            main[i][0] = x * settle;
            main[i][1] = height * (1f - t);
            main[i][2] = z * settle;
            x += (random.nextFloat() - 0.5f) * 0.9f;
            z += (random.nextFloat() - 0.5f) * 0.9f;
        }
        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
        Matrix4f matrix = poseStack.last().pose();
        for (float[] layer : LAYERS) {
            float a = layer[4] * fade;
            for (int i = 0; i < segments; i++) {
                prism(consumer, matrix, main[i], main[i + 1], layer[0] * (1f - 0.3f * i / segments), layer[1], layer[2], layer[3], a);
            }
        }
        // two thin forks branching off the upper half
        for (int f = 0; f < 2; f++) {
            int from = 1 + random.nextInt(Math.max(1, segments / 2));
            float[] p = main[from].clone();
            float dx = (random.nextFloat() - 0.5f) * 1.4f;
            float dz = (random.nextFloat() - 0.5f) * 1.4f;
            for (int s = 0; s < 3; s++) {
                float[] q = {p[0] + dx + (random.nextFloat() - 0.5f) * 0.4f, p[1] - 0.6f - random.nextFloat() * 0.4f, p[2] + dz + (random.nextFloat() - 0.5f) * 0.4f};
                for (float[] layer : LAYERS) {
                    prism(consumer, matrix, p, q, layer[0] * 0.55f * (1f - s / 3f), layer[1], layer[2], layer[3], layer[4] * fade * 0.8f);
                }
                p = q;
            }
        }
        super.render(entity, yaw, partialTicks, poseStack, buffer, light);
    }

    /** A four-sided glowing strip between two points (drawn from both sides, so no face is culled). */
    private static void prism(VertexConsumer c, Matrix4f m, float[] p0, float[] p1, float w, float r, float g, float b, float a) {
        float[][] offsets = {{w, 0}, {0, w}, {-w, 0}, {0, -w}};
        for (int k = 0; k < 4; k++) {
            float[] o0 = offsets[k];
            float[] o1 = offsets[(k + 1) % 4];
            quad(c, m, p0[0] + o0[0], p0[1], p0[2] + o0[1], p0[0] + o1[0], p0[1], p0[2] + o1[1],
                    p1[0] + o1[0], p1[1], p1[2] + o1[1], p1[0] + o0[0], p1[1], p1[2] + o0[1], r, g, b, a);
        }
    }

    private static void quad(VertexConsumer c, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3, float r, float g, float b, float a) {
        c.vertex(m, x0, y0, z0).color(r, g, b, a).endVertex();
        c.vertex(m, x1, y1, z1).color(r, g, b, a).endVertex();
        c.vertex(m, x2, y2, z2).color(r, g, b, a).endVertex();
        c.vertex(m, x3, y3, z3).color(r, g, b, a).endVertex();
        c.vertex(m, x3, y3, z3).color(r, g, b, a).endVertex();
        c.vertex(m, x2, y2, z2).color(r, g, b, a).endVertex();
        c.vertex(m, x1, y1, z1).color(r, g, b, a).endVertex();
        c.vertex(m, x0, y0, z0).color(r, g, b, a).endVertex();
    }

    @SuppressWarnings("deprecation")
    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull StormBolt entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
