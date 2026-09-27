package com.aetherspellbooks.client.mob;

import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMob;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMobModel;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders a spellcaster with Iron's Spells' shared humanoid casting model and its own skin.
 */
public class AetherCasterRenderer extends AbstractSpellCastingMobRenderer {
    public AetherCasterRenderer(EntityRendererProvider.Context context, ResourceLocation texture) {
        super(context, new SkinModel(texture));
    }

    private static class SkinModel extends AbstractSpellCastingMobModel {
        private final ResourceLocation texture;

        SkinModel(ResourceLocation texture) {
            this.texture = texture;
        }

        @Override
        public ResourceLocation getTextureResource(AbstractSpellCastingMob object) {
            return texture;
        }
    }
}
