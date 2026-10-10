package me.lovkar.colonypatches;

import me.lovkar.colonypatches.patch.ModuleNumbering;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Colony Patches: small compatibility fixes for MineColonies modpacks. Each fix only acts when the
 * broken combination of mods is actually loaded; see the {@code patch} package.
 */
@Mod(ColonyPatches.MOD_ID)
public final class ColonyPatches {
    public static final String MOD_ID = "colony_patches";
    public static final Logger LOG = LoggerFactory.getLogger("Colony Patches");

    public ColonyPatches(final IEventBus modBus, final ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, Settings.SPEC);
        modBus.addListener(RegisterPayloadHandlersEvent.class, SameOnBothSides::register);
        // after every mod has made its building modules, on the main thread, before any world loads
        modBus.addListener(FMLLoadCompleteEvent.class, event -> event.enqueueWork(() -> {
            if (ModList.get().isLoaded("minecolonies")) {
                ModuleNumbering.renumber();
            }
        }));
    }
}
