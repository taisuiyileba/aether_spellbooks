package com.aetherspellbooks.client;

import com.aetherspellbooks.AetherSpellbooks;
import com.aetherspellbooks.client.armor.MageCapeLayer;
import com.aetherspellbooks.client.mob.AetherCasterRenderer;
import com.aetherspellbooks.client.mob.SolarHaloLayer;
import com.aetherspellbooks.client.mob.ValkyrieWingsLayer;
import com.aetherspellbooks.client.particle.ASParticleTypes;
import com.aetherspellbooks.registry.ASEntities;
import com.aetherspellbooks.registry.ASItems;
import com.aetherspellbooks.registry.ASParticles;
import com.aetherteam.aether.client.renderer.entity.FireMinionRenderer;
import com.aetherteam.aether.client.renderer.entity.MoaRenderer;
import io.redspace.ironsspellbooks.render.SpellBookCurioRenderer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

@Mod.EventBusSubscriber(modid = AetherSpellbooks.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ASClient {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ASEntities.STONEBREAKER_SHARD.get(), StonebreakerShardRenderer::new);
        event.registerEntityRenderer(ASEntities.THUNDER_CRYSTAL.get(), ThunderCrystalRenderer::new);
        event.registerEntityRenderer(ASEntities.ZEPHYR_ORB.get(), context -> new SpriteProjectileRenderer<>(context,
                AetherSpellbooks.id("textures/entity/zephyr_orb.png"), null, 1.0f, 0, 18f));
        event.registerEntityRenderer(ASEntities.SOLAR_BOLT.get(), context -> new SpriteProjectileRenderer<>(context,
                AetherSpellbooks.id("textures/entity/solar_bolt.png"), AetherSpellbooks.id("textures/entity/thunder_glow.png"), 0.6f, 1.4f, 20f));
        event.registerEntityRenderer(ASEntities.SOLAR_ORB.get(), SolarOrbRenderer::new);
        // the whirlwind is drawn entirely with particles
        event.registerEntityRenderer(ASEntities.AETHER_WHIRLWIND.get(), NoopRenderer::new);
        // Summons reuse the Aether's own renderers (loaded from the Aether at runtime, not redistributed)
        event.registerEntityRenderer(ASEntities.SUMMONED_FIRE_MINION.get(), FireMinionRenderer::new);
        event.registerEntityRenderer(ASEntities.SUMMONED_MOA.get(), MoaRenderer::new);
        event.registerEntityRenderer(ASEntities.RADIANT_JAVELIN.get(), RadiantJavelinRenderer::new);
        // the thundercloud is all particles; its bolts are drawn by their own renderer
        event.registerEntityRenderer(ASEntities.STORM_CLOUD.get(), NoopRenderer::new);
        event.registerEntityRenderer(ASEntities.STORM_BOLT.get(), StormBoltRenderer::new);
        event.registerEntityRenderer(ASEntities.SPECTRAL_AERWHALE.get(), SpectralAerwhaleRenderer::new);
        event.registerEntityRenderer(ASEntities.ICESTONE_METEOR.get(), IcestoneMeteorRenderer::new);
        // Spellcasters use Iron's Spells' humanoid casting model with their own skins and layers
        event.registerEntityRenderer(ASEntities.VALKYRIE_SORCERESS.get(), context -> {
            AetherCasterRenderer renderer = new AetherCasterRenderer(context, AetherSpellbooks.id("textures/entity/valkyrie_sorceress.png"));
            renderer.addRenderLayer(new ValkyrieWingsLayer(renderer));
            return renderer;
        });
        event.registerEntityRenderer(ASEntities.SOLAR_ACOLYTE.get(), context -> {
            AetherCasterRenderer renderer = new AetherCasterRenderer(context, AetherSpellbooks.id("textures/entity/solar_acolyte.png"));
            // burning eyes and sun embroidery, from solar_acolyte_glowmask.png
            renderer.addRenderLayer(new AutoGlowingGeoLayer<>(renderer));
            renderer.addRenderLayer(new SolarHaloLayer(renderer));
            return renderer;
        });
    }

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new MageCapeLayer(renderer));
            }
        }
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ASParticles.FEATHER.get(), ASParticleTypes.FeatherProvider::new);
        event.registerSpriteSet(ASParticles.SKY_SPARKLE.get(), ASParticleTypes.SparkleProvider::new);
        event.registerSpriteSet(ASParticles.CLOUD_PUFF.get(), ASParticleTypes.CloudPuffProvider::new);
        event.registerSpriteSet(ASParticles.GRAVITY_MOTE.get(), ASParticleTypes.GravityMoteProvider::new);
        event.registerSpriteSet(ASParticles.STORM_PUFF.get(), ASParticleTypes.StormPuffProvider::new);
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ASItems.SPELLBOOKS.forEach(book -> CuriosRendererRegistry.register(book.get(), SpellBookCurioRenderer::new)));
    }
}
