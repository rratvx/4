package com.tres.carverbridge;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Mod(CarverBridge.MODID)
public class CarverBridge {
    public static final String MODID = "tres_carver_bridge";
    public static final Logger LOGGER = LogUtils.getLogger();

    // Los mismos carvers que WF's Cave Overhaul agrega por biome modifier.
    private static final String[] WF_CARVERS = {
            "caveoverhaul:caves_noise_distribution",
            "caveoverhaul:canyons",
            "caveoverhaul:canyons_low_y"
    };

    private static boolean loggedOnce = false;
    private static boolean calledOnce = false;

    public CarverBridge() {
        LOGGER.info("[tres_carver_bridge] cargado v1.3");
        MinecraftForge.EVENT_BUS.addListener(CarverBridge::onServerStarted);
    }

    /** Diagnostico: que generador usa el Overworld y que carvers tienen sus biomas. */
    private static void onServerStarted(ServerStartedEvent event) {
        try {
            ServerLevel level = event.getServer().overworld();
            ChunkGenerator gen = level.getChunkSource().getGenerator();
            LOGGER.info("[tres_carver_bridge] DIAG generador del Overworld: {}", gen.getClass().getName());
            for (Class<?> c = gen.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                boolean has = false;
                for (Method m : c.getDeclaredMethods()) {
                    if (m.getName().contains("carverbridge")) has = true;
                }
                LOGGER.info("[tres_carver_bridge] DIAG clase {} contiene el mixin del puente: {}", c.getName(), has);
            }
            int n = 0;
            for (Holder<Biome> b : gen.getBiomeSource().possibleBiomes()) {
                if (n++ >= 6) break;
                List<String> ids = new ArrayList<>();
                for (Holder<ConfiguredWorldCarver<?>> h : b.value().getGenerationSettings().getCarvers(GenerationStep.Carving.AIR)) {
                    ids.add(h.unwrapKey().map(k -> k.location().toString()).orElse("?"));
                }
                LOGGER.info("[tres_carver_bridge] DIAG bioma {} carvers AIR: {}", b.unwrapKey().map(k -> k.location().toString()).orElse("?"), ids);
            }
        } catch (Throwable t) {
            LOGGER.error("[tres_carver_bridge] DIAG error", t);
        }
    }

    private static boolean headLogged = false;

    public static void headCalled(GenerationStep.Carving step) {
        if (!headLogged) {
            headLogged = true;
            LOGGER.info("[tres_carver_bridge] MIXIN OK: applyCarvers de Moderner Beta fue llamado (paso {})", step);
        }
    }

    /** Llamado por el mixin: devuelve los carvers del bioma + los de WF si faltan. */
    public static Iterable<Holder<ConfiguredWorldCarver<?>>> merge(Iterable<Holder<ConfiguredWorldCarver<?>>> original,
                                                                    GenerationStep.Carving step) {
        try {
            if (!calledOnce) {
                calledOnce = true;
                LOGGER.info("[tres_carver_bridge] EL ENGANCHE FUNCIONA: Moderner Beta pidio carvers (paso {})", step);
            }
            if (step != GenerationStep.Carving.AIR) return original;
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return original;
            Registry<ConfiguredWorldCarver<?>> reg = server.registryAccess().registryOrThrow(Registries.CONFIGURED_CARVER);

            List<Holder<ConfiguredWorldCarver<?>>> out = new ArrayList<>();
            Set<ResourceLocation> present = new HashSet<>();
            for (Holder<ConfiguredWorldCarver<?>> h : original) {
                out.add(h);
                h.unwrapKey().ifPresent(k -> present.add(k.location()));
            }
            int added = 0;
            for (String id : WF_CARVERS) {
                ResourceLocation rl = new ResourceLocation(id);
                if (present.contains(rl)) continue;
                Optional<Holder.Reference<ConfiguredWorldCarver<?>>> opt =
                        reg.getHolder(ResourceKey.create(Registries.CONFIGURED_CARVER, rl));
                if (opt.isPresent()) {
                    out.add(opt.get());
                    added++;
                }
            }
            if (!loggedOnce) {
                loggedOnce = true;
                LOGGER.info("[tres_carver_bridge] Moderner Beta carvers del bioma: {} | agregados de WF: {}", present, added);
            }
            return out;
        } catch (Throwable t) {
            if (!loggedOnce) {
                loggedOnce = true;
                LOGGER.error("[tres_carver_bridge] error", t);
            }
            return original;
        }
    }
}
