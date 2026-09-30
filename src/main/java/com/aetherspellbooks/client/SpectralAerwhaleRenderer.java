package com.aetherspellbooks.client;

import com.aetherspellbooks.entity.SpectralAerwhale;
import com.aetherteam.aether.client.renderer.entity.AerwhaleRenderer;
import com.aetherteam.aether.entity.passive.Aerwhale;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The Aether's own Aerwhale renderer, drawn translucent, pale and full-bright so the whale looks like a spirit;
 * it fades in and out with {@link SpectralAerwhale#getFade}.
 */
public class SpectralAerwhaleRenderer extends AerwhaleRenderer {
    private static final float OPACITY = 0.72f;

    public SpectralAerwhaleRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0f;
    }

    @Override
    public void render(@NotNull Aerwhale entity, float yaw, float partialTicks, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int light) {
        float alpha = OPACITY * (entity instanceof SpectralAerwhale spirit ? spirit.getFade(partialTicks) : 1f);
        if (alpha <= 0.01f) {
            return;
        }
        MultiBufferSource ghostly = type -> new TintedVertexConsumer(buffer.getBuffer(type), 0.82f, 0.92f, 1f, alpha);
        super.render(entity, yaw, partialTicks, poseStack, ghostly, LightTexture.FULL_BRIGHT);
    }

    @Override
    protected @Nullable RenderType getRenderType(@NotNull Aerwhale entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        // culled, so the far side of the boxy model does not show through and make it look like glass
        return RenderType.entityTranslucentCull(getTextureLocation(entity));
    }

    /** Scales every vertex colour, used to tint and fade a model drawn through an ordinary renderer. */
    public record TintedVertexConsumer(VertexConsumer delegate, float r, float g, float b, float a) implements VertexConsumer {
        @Override
        public @NotNull VertexConsumer vertex(double x, double y, double z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public @NotNull VertexConsumer color(int red, int green, int blue, int alpha) {
            delegate.color((int) (red * r), (int) (green * g), (int) (blue * b), (int) (alpha * a));
            return this;
        }

        @Override
        public @NotNull VertexConsumer uv(float u, float v) {
            delegate.uv(u, v);
            return this;
        }

        @Override
        public @NotNull VertexConsumer overlayCoords(int u, int v) {
            delegate.overlayCoords(u, v);
            return this;
        }

        @Override
        public @NotNull VertexConsumer uv2(int u, int v) {
            delegate.uv2(u, v);
            return this;
        }

        @Override
        public @NotNull VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            delegate.endVertex();
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
            delegate.defaultColor(red, green, blue, alpha);
        }

        @Override
        public void unsetDefaultColor() {
            delegate.unsetDefaultColor();
        }
    }
}
