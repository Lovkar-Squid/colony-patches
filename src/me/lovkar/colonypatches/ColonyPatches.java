package me.lovkar.colonypatches;

import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Colony Patches: small compatibility fixes for MineColonies modpacks. Every fix is a mixin that only
 * acts when the broken combination of mods is actually loaded; see the {@code patch} package.
 */
@Mod(ColonyPatches.MOD_ID)
public final class ColonyPatches {
    public static final String MOD_ID = "colony_patches";
    public static final Logger LOG = LoggerFactory.getLogger("Colony Patches");
}
