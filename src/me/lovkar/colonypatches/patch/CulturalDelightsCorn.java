package me.lovkar.colonypatches.patch;

import me.lovkar.colonypatches.ColonyPatches;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Farmers and Cultural Delights corn.
 *
 * MineColonies Compatibility 3.57 harvests Cultural Delights corn as the two-block plant it was up to
 * Cultural Delights 0.17: a CornBlock with a CornUpperBlock on top. Cultural Delights 0.18 made corn
 * one CornBlock with a height property and removed CornUpperBlock, so the addon's corn handler throws
 * NoClassDefFoundError the moment a farmer looks at a corn plant in his field, and the server crashes.
 *
 * The new corn is an ordinary crop for MineColonies: the bottom block at full age is the harvest and
 * drops the cobs. So when the addon's corn handler still needs CornUpperBlock and Cultural Delights no
 * longer has it, the handler stops asking for a special harvest position and MineColonies' own crop
 * handling takes over (mixin.compat.CornCropMixin). Anything else - the old two-block corn, or a corn
 * handler that no longer names CornUpperBlock - is left as it is.
 */
public final class CulturalDelightsCorn {
    private static final String UPPER = "com.baisylia.culturaldelights.block.custom.CornUpperBlock";
    private static Boolean broken;

    private CulturalDelightsCorn() {
    }

    /** True when the given corn handler needs CornUpperBlock and the class is missing: checked once. */
    public static boolean brokenHandler(final Class<?> cornCrop) {
        if (broken == null) {
            boolean upper;
            try {
                Class.forName(UPPER, false, cornCrop.getClassLoader());
                upper = true;
            } catch (final ClassNotFoundException | LinkageError e) {
                upper = false;
            }
            final Boolean names = names(cornCrop, UPPER.replace('.', '/'));
            // an unreadable class file: a crash is worse than a skipped special harvest, so act on the missing class
            broken = !upper && !Boolean.FALSE.equals(names);
            if (broken) {
                ColonyPatches.LOG.info("MineColonies Compatibility's corn handler needs CornUpperBlock, which this Cultural "
                        + "Delights no longer has: farmers harvest its one-block corn as an ordinary crop");
            } else {
                ColonyPatches.LOG.info(upper ? "Cultural Delights still has CornUpperBlock: the corn handler is left as it is"
                        : "MineColonies Compatibility's corn handler does not need CornUpperBlock: left as it is");
            }
        }
        return broken;
    }

    /** Whether the class file of c names the internal class name; null when it cannot be read. */
    private static Boolean names(final Class<?> c, final String internalName) {
        final String resource = c.getName().replace('.', '/') + ".class";
        final ClassLoader loader = c.getClassLoader();
        try (InputStream in = loader == null ? null : loader.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1).contains(internalName);
        } catch (final IOException e) {
            return null;
        }
    }
}
