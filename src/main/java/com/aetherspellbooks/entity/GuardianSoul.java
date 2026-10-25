package com.aetherspellbooks.entity;

import com.aetherspellbooks.dungeon.GuardianKind;
import com.aetherspellbooks.dungeon.GuardianLair;
import com.aetherspellbooks.dungeon.GuardianLairs;
import com.aetherspellbooks.dungeon.Guardians;
import com.aetherspellbooks.dungeon.RoomRestorer;
import com.aetherspellbooks.dungeon.RoomSnapshot;
import com.aetherspellbooks.item.GuardianRelicItem;
import com.aetherspellbooks.registry.ASEntities;
import com.aetherspellbooks.registry.ASParticles;
import com.aetherspellbooks.util.ASFx;
import com.aetherteam.aether.client.AetherSoundEvents;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * What a dungeon boss leaves behind: its soul drifts back to where the boss first stood and waits there. Offering the
 * boss's relic starts a ritual that rebuilds the sealed room from the outside in and then calls the boss back. The soul
 * is only a marker - the lair itself is saved with the level, so a lost soul is simply recreated by the relic.
 */
public class GuardianSoul extends Entity {
    private static final EntityDataAccessor<Byte> DATA_KIND = SynchedEntityData.defineId(GuardianSoul.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Vector3f> DATA_HOME = SynchedEntityData.defineId(GuardianSoul.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> DATA_RITUAL = SynchedEntityData.defineId(GuardianSoul.class, EntityDataSerializers.INT);

    public static final int RITUAL_LENGTH = 90;
    private static final int RESTORE_START = 12;
    private static final int RESTORE_END = 70;
    private static final int DOORWAY_TICK = 74;
    private static final double DRIFT_SPEED = 0.1;

    private BlockPos lairKey = BlockPos.ZERO;
    @Nullable
    private List<RoomSnapshot.Entry> plan;
    private int restored;

    public GuardianSoul(EntityType<? extends GuardianSoul> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public GuardianSoul(ServerLevel level, GuardianKind kind, GuardianLair lair) {
        this(ASEntities.GUARDIAN_SOUL.get(), level);
        this.entityData.set(DATA_KIND, (byte) kind.ordinal());
        this.entityData.set(DATA_HOME, lair.soulHome().toVector3f());
        this.lairKey = lair.key();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        builder.define(DATA_KIND, (byte) 0);
        builder.define(DATA_HOME, new Vector3f());
        builder.define(DATA_RITUAL, -1);
    }

    public GuardianKind kind() {
        GuardianKind[] kinds = GuardianKind.values();
        return kinds[Mth.clamp(entityData.get(DATA_KIND), 0, kinds.length - 1)];
    }

    public Vec3 home() {
        return new Vec3(entityData.get(DATA_HOME));
    }

    public BlockPos lairKey() {
        return lairKey;
    }

    /** Ticks into the revival ritual, or -1 while the soul just waits. */
    public int ritualTicks() {
        return entityData.get(DATA_RITUAL);
    }

    public boolean atHome() {
        return home().distanceToSqr(position()) < 0.01;
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 toHome = home().subtract(position());
        if (toHome.lengthSqr() > 1.0E-4) {
            setPos(position().add(toHome.length() <= DRIFT_SPEED ? toHome : toHome.normalize().scale(DRIFT_SPEED)));
        }
        if (level().isClientSide) {
            clientEffects();
            return;
        }
        ServerLevel server = (ServerLevel) level();
        Optional<GuardianLair> lair = GuardianLairs.get(server).get(lairKey);
        if (ritualTicks() >= 0 && lair.isPresent()) {
            tickRitual(server, lair.get());
        } else if (tickCount % 40 == 0 && (lair.isEmpty() || !lair.get().dormant())) {
            // the boss came back some other way, or the lair was forgotten: nothing left to wait for
            discard();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Ritual

    /** @return false if a ritual is already running */
    public boolean beginRitual(Player caller) {
        if (ritualTicks() >= 0) {
            return false;
        }
        entityData.set(DATA_RITUAL, 0);
        plan = null;
        restored = 0;
        playSound(SoundEvents.BEACON_ACTIVATE, 2f, 0.7f);
        playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 1.5f, 0.8f);
        ASFx.blastwave(level(), kind().color(), position().add(0, getBbHeight() * 0.5, 0), 3f);
        return true;
    }

    private void tickRitual(ServerLevel server, GuardianLair lair) {
        int tick = ritualTicks() + 1;
        entityData.set(DATA_RITUAL, tick);
        GuardianKind kind = kind();
        Vec3 center = position().add(0, getBbHeight() * 0.5, 0);
        if (plan == null) {
            plan = RoomRestorer.wallsOutsideIn(lair);
            restored = 0;
        }
        if (tick >= RESTORE_START && tick <= RESTORE_END) {
            int target = plan.size() * (tick - RESTORE_START + 1) / (RESTORE_END - RESTORE_START + 1);
            for (; restored < target; restored++) {
                RoomSnapshot.Entry entry = plan.get(restored);
                if (RoomRestorer.restore(server, kind, entry) && restored % 7 == 0) {
                    Vec3 at = Vec3.atCenterOf(entry.pos());
                    server.sendParticles(dust(kind), at.x, at.y, at.z, 2, 0.35, 0.35, 0.35, 0);
                }
            }
            if (tick % 6 == 0) {
                playSound(SoundEvents.STONE_PLACE, 1.2f, 0.6f + tick / (float) RESTORE_END * 0.6f);
            }
        }
        if (tick == DOORWAY_TICK) {
            if (Guardians.treasureChamberOccupied(server, lair)) {
                // never shut anyone in: the treasure doorway stays open until the boss falls again
                return;
            }
            for (RoomSnapshot.Entry entry : RoomRestorer.treasureDoorways(lair)) {
                if (RoomRestorer.restore(server, kind, entry)) {
                    Vec3 at = Vec3.atCenterOf(entry.pos());
                    server.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 4, 0.3, 0.3, 0.3, 0.02);
                }
            }
            playSound(SoundEvents.IRON_DOOR_CLOSE, 1.5f, 0.6f);
        }
        if (tick % 3 == 0) {
            ASFx.helix(server, dust(kind), lair.origin(), 1.6 - tick / (double) RITUAL_LENGTH, 2.6, 10, 1.5);
        }
        if (tick >= RITUAL_LENGTH) {
            finishRitual(server, lair, center);
        }
    }

    private void finishRitual(ServerLevel server, GuardianLair lair, Vec3 center) {
        GuardianKind kind = kind();
        Mob boss = Guardians.reviveBoss(server, lair);
        server.sendParticles(ParticleTypes.FLASH, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        ASFx.burst(server, accent(kind), center, 60, 0.8, 0.12);
        ASFx.blastwave(server, kind.color(), center, 6f);
        ASFx.shake(server, center, 16f, 20);
        server.playSound(null, center.x, center.y, center.z, awakenSound(kind), SoundSource.HOSTILE, 2.5f, 0.9f);
        server.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2f, 0.6f);
        if (boss != null) {
            Component message = Component.translatable("message.aether_spellbooks.guardian.returned", boss.getDisplayName());
            server.getEntitiesOfClass(Player.class, lair.room().inflate(4)).forEach(player -> player.displayClientMessage(message, false));
        }
        discard();
    }

    private static SoundEvent awakenSound(GuardianKind kind) {
        return switch (kind) {
            case SLIDER -> AetherSoundEvents.ENTITY_SLIDER_AWAKEN.get();
            case VALKYRIE_QUEEN -> AetherSoundEvents.ENTITY_VALKYRIE_QUEEN_INTERACT.get();
            case SUN_SPIRIT -> AetherSoundEvents.ENTITY_SUN_SPIRIT_ACTIVATE.get();
        };
    }

    public static ParticleOptions dust(GuardianKind kind) {
        return new DustParticleOptions(kind.color(), 1.4f);
    }

    /** The particle that tells the three souls apart. */
    public static ParticleOptions accent(GuardianKind kind) {
        return switch (kind) {
            case SLIDER -> ASParticles.SKY_SPARKLE.get();
            case VALKYRIE_QUEEN -> ASParticles.FEATHER.get();
            case SUN_SPIRIT -> ParticleHelper.EMBERS;
        };
    }

    private void clientEffects() {
        GuardianKind kind = kind();
        Vec3 center = position().add(0, getBbHeight() * 0.5, 0);
        int ritual = ritualTicks();
        if (ritual < 0) {
            if (random.nextInt(3) == 0) {
                Vec3 offset = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).scale(0.22);
                level().addParticle(random.nextInt(4) == 0 ? accent(kind) : dust(kind), center.x + offset.x, center.y + offset.y,
                        center.z + offset.z, 0, 0.02, 0);
            }
            return;
        }
        // the ritual draws motes in from the whole room
        for (int i = 0; i < 3; i++) {
            double radius = 2.5 + random.nextDouble() * 3.5;
            double angle = random.nextDouble() * Mth.TWO_PI;
            Vec3 from = center.add(Math.cos(angle) * radius, (random.nextDouble() - 0.3) * 2.5, Math.sin(angle) * radius);
            Vec3 velocity = center.subtract(from).scale(0.06);
            level().addParticle(i == 0 ? accent(kind) : dust(kind), from.x, from.y, from.z, velocity.x, velocity.y, velocity.z);
        }
        if (ritual > RITUAL_LENGTH - 20) {
            level().addParticle(ParticleTypes.END_ROD, center.x, center.y, center.z,
                    random.nextGaussian() * 0.08, random.nextDouble() * 0.12, random.nextGaussian() * 0.08);
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Interaction

    @Override
    public @NotNull InteractionResult interact(@NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof GuardianRelicItem relic) {
            if (level() instanceof ServerLevel server) {
                GuardianRelicItem.offer(server, player, stack, relic.kind(), this);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (hand == InteractionHand.MAIN_HAND && !level().isClientSide && ritualTicks() < 0) {
            player.displayClientMessage(Component.translatable("message.aether_spellbooks.guardian.soul_hint",
                    kind().bossName(), kind().relic().getDescription()), true);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean skipAttackInteraction(@NotNull Entity attacker) {
        return true;
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected @NotNull Component getTypeName() {
        return Component.translatable("entity.aether_spellbooks.guardian_soul." + kind().id());
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        entityData.set(DATA_KIND, (byte) GuardianKind.byId(tag.getString("Kind")).ordinal());
        entityData.set(DATA_HOME, new Vector3f(tag.getFloat("HomeX"), tag.getFloat("HomeY"), tag.getFloat("HomeZ")));
        entityData.set(DATA_RITUAL, tag.contains("Ritual") ? tag.getInt("Ritual") : -1);
        lairKey = NbtUtils.readBlockPos(tag, "Lair").orElse(BlockPos.ZERO);
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        tag.putString("Kind", kind().id());
        Vector3f home = entityData.get(DATA_HOME);
        tag.putFloat("HomeX", home.x);
        tag.putFloat("HomeY", home.y);
        tag.putFloat("HomeZ", home.z);
        tag.putInt("Ritual", ritualTicks());
        tag.put("Lair", NbtUtils.writeBlockPos(lairKey));
    }
}
