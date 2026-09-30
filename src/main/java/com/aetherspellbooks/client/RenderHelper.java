package com.aetherspellbooks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Small helpers for drawing camera-facing quads and simple meshes.
 */
public final class RenderHelper {
    private RenderHelper() {
    }

    /** Draws a unit quad (-0.5..0.5) in the current pose, full-bright, with the given tint. */
    public static void quad(VertexConsumer consumer, PoseStack.Pose pose, int r, int g, int b, int a) {
        vertex(consumer, pose, -0.5f, -0.5f, 0f, 1f, r, g, b, a);
        vertex(consumer, pose, 0.5f, -0.5f, 1f, 1f, r, g, b, a);
        vertex(consumer, pose, 0.5f, 0.5f, 1f, 0f, r, g, b, a);
        vertex(consumer, pose, -0.5f, 0.5f, 0f, 0f, r, g, b, a);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float u, float v, int r, int g, int b, int a) {
        Matrix4f poseMatrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();
        consumer.addVertex(poseMatrix, x, y, 0f)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0f, 1f, 0f);
    }

    /**
     * Draws an octahedron (a gem) of the given half-height and half-width into a POSITION_COLOR quad buffer
     * (e.g. {@code RenderType.lightning()}); each triangular face is emitted as a degenerate quad.
     */
    public static void octahedron(VertexConsumer consumer, Matrix4f matrix, float halfWidth, float halfHeight, int r, int g, int b, int a) {
        float[][] ring = {{halfWidth, 0, 0}, {0, 0, halfWidth}, {-halfWidth, 0, 0}, {0, 0, -halfWidth}};
        for (int i = 0; i < 4; i++) {
            float[] p0 = ring[i];
            float[] p1 = ring[(i + 1) % 4];
            // lighter top faces, darker bottom faces for a faceted look
            triangle(consumer, matrix, 0, halfHeight, 0, p0, p1, r, g, b, a);
            triangle(consumer, matrix, 0, -halfHeight, 0, p1, p0, (int) (r * 0.75f), (int) (g * 0.75f), (int) (b * 0.75f), a);
        }
    }

    private static void triangle(VertexConsumer consumer, Matrix4f m, float tx, float ty, float tz, float[] p0, float[] p1, int r, int g, int b, int a) {
        consumer.addVertex(m, tx, ty, tz).setColor(r, g, b, a);
        consumer.addVertex(m, p0[0], p0[1], p0[2]).setColor(r, g, b, a);
        consumer.addVertex(m, p1[0], p1[1], p1[2]).setColor(r, g, b, a);
        consumer.addVertex(m, p1[0], p1[1], p1[2]).setColor(r, g, b, a);
    }
}
