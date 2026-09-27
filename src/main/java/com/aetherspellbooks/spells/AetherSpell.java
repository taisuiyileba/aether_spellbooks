package com.aetherspellbooks.spells;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.config.ASConfig;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.resources.ResourceLocation;

/**
 * Shared base for all spells in this mod.
 * Spells are Aether-exclusive by default: generic Iron's Spells loot skips them,
 * while this mod's own loot tables pick them with {@code "force": true}.
 */
public abstract class AetherSpell extends AbstractSpell {
    private final ResourceLocation spellId;

    protected AetherSpell(String id) {
        this.spellId = AetherSpellbooks.id(id);
    }

    @Override
    public ResourceLocation getSpellResource() {
        return spellId;
    }

    @Override
    public boolean allowLooting() {
        return !ASConfig.aetherExclusive();
    }
}
