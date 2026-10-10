package me.lovkar.colonypatches;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The server's settings, in {@code config/colony_patches-server.toml}. Everything here is off by
 * default: Colony Patches changes nothing a server owner did not ask for.
 */
public final class Settings {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue SEVERAL_COLONIES;

    static {
        final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("colonies");
        SEVERAL_COLONIES = builder
                .comment("Let a player own several colonies at once: placing a new Town Hall founds another colony",
                        "instead of offering to delete or abandon the one they have. MineColonies' own allowinfinitecolonies",
                        "setting does not do this (it only adds the abandon button). Where MineColonies asks for 'the'",
                        "colony of a player - /minecolonies home, commands without a colony id, a flag placed outside",
                        "any colony - it takes one of them.")
                .define("severalColoniesPerPlayer", false);
        builder.pop();
        SPEC = builder.build();
    }

    private Settings() {
    }

    /** Several colonies per player allowed? False until the server's config has loaded. */
    public static boolean severalColonies() {
        try {
            return SPEC.isLoaded() && SEVERAL_COLONIES.get();
        } catch (final IllegalStateException notLoaded) {
            return false;
        }
    }
}
