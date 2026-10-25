package com.aetherspellbooks.dungeon;

import com.aetherspellbooks.registry.ASItems;
import com.aetherteam.aether.entity.AetherEntityTypes;
import com.aetherteam.aether.entity.monster.dungeon.boss.Slider;
import com.aetherteam.aether.entity.monster.dungeon.boss.SunSpirit;
import com.aetherteam.aether.entity.monster.dungeon.boss.ValkyrieQueen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/** The three Aether dungeon bosses that leave a soul behind, with the relic that calls each of them back. */
public enum GuardianKind {
    SLIDER("slider", AetherEntityTypes.SLIDER, VaultTier.CARVED, ASItems.SENTRY_CORE,
            new Vector3f(0.45f, 0.72f, 1f)),
    VALKYRIE_QUEEN("valkyrie_queen", AetherEntityTypes.VALKYRIE_QUEEN, VaultTier.ANGELIC, ASItems.VALKYRIE_OATH,
            new Vector3f(1f, 0.85f, 0.4f)),
    SUN_SPIRIT("sun_spirit", AetherEntityTypes.SUN_SPIRIT, VaultTier.HELLFIRE, ASItems.SOLAR_EMBER,
            new Vector3f(1f, 0.5f, 0.18f));

    private final String id;
    private final Supplier<? extends EntityType<? extends Mob>> entityType;
    private final VaultTier tier;
    private final Supplier<? extends Item> relic;
    private final Vector3f color;

    GuardianKind(String id, Supplier<? extends EntityType<? extends Mob>> entityType, VaultTier tier,
                 Supplier<? extends Item> relic, Vector3f color) {
        this.id = id;
        this.entityType = entityType;
        this.tier = tier;
        this.relic = relic;
        this.color = color;
    }

    public static Optional<GuardianKind> of(Entity entity) {
        return Arrays.stream(values()).filter(kind -> entity.getType() == kind.entityType()).findFirst();
    }

    public static GuardianKind byId(String id) {
        return Arrays.stream(values()).filter(kind -> kind.id.equals(id)).findFirst().orElse(SLIDER);
    }

    public String id() {
        return id;
    }

    public EntityType<? extends Mob> entityType() {
        return entityType.get();
    }

    public VaultTier tier() {
        return tier;
    }

    public Item relic() {
        return relic.get();
    }

    /** Soul and ritual colour. */
    public Vector3f color() {
        return color;
    }

    public Component bossName() {
        return entityType().getDescription();
    }

    /** What the boss turns each of its locked dungeon blocks into when it dies (the Aether's own table). */
    public Map<Block, Function<BlockState, BlockState>> conversions() {
        return switch (this) {
            case SLIDER -> Slider.DUNGEON_BLOCK_CONVERSIONS;
            case VALKYRIE_QUEEN -> ValkyrieQueen.DUNGEON_BLOCK_CONVERSIONS;
            case SUN_SPIRIT -> SunSpirit.DUNGEON_BLOCK_CONVERSIONS;
        };
    }
}
