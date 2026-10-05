package me.lovkar.colonypatches.patch;

import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import me.lovkar.colonypatches.ColonyPatches;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * The same building-module numbers on the server and on every client.
 *
 * <p>MineColonies numbers its building-module producers with a counter, in the order they are
 * created, and sends module data between server and client by that number (the building window
 * sync and the messages from the windows: settings, hiring, item lists). The order follows the order
 * the mods are loaded in, and NeoForge fixes that order only where mods depend on each other; for the
 * rest it follows a HashMap whose order changes with the set of installed mods. A dedicated server
 * has no client-only mods, so its order - and its numbers - can differ from the players'. Then a
 * building window reads another module's data: windows stay empty, settings reach the wrong module,
 * and the town hall's statistics page crashes on a worker module that never got its job.</p>
 *
 * <p>Once every mod has loaded, the producers are numbered again by their names, which are the same
 * on both sides. The numbers are never saved (a building stores its modules by name), and nothing
 * keeps producers in a hash table keyed by them, so renumbering at that point is safe. Both sides
 * have to do it, which is why Colony Patches registers a required network payload.</p>
 */
public final class ModuleNumbering {
    private ModuleNumbering() {
    }

    @SuppressWarnings("unchecked")
    public static void renumber() {
        try {
            final Field allField = BuildingEntry.ModuleProducer.class.getDeclaredField("ALL_MODULES");
            final Field idField = BuildingEntry.ModuleProducer.class.getDeclaredField("id");
            final Field counterField = BuildingEntry.ModuleProducer.class.getDeclaredField("runtimeIdGenerator");
            allField.setAccessible(true);
            idField.setAccessible(true);
            counterField.setAccessible(true);
            final Map<String, BuildingEntry.ModuleProducer<?, ?>> all = (Map<String, BuildingEntry.ModuleProducer<?, ?>>) allField.get(null);

            final List<String> keys = new ArrayList<>(all.keySet());
            keys.sort(null);
            int number = 0;
            int changed = 0;
            for (final String key : keys) {
                final BuildingEntry.ModuleProducer<?, ?> producer = all.get(key);
                number++;
                if (producer.getRuntimeID() != number) {
                    changed++;
                }
                idField.setInt(producer, number);
            }
            // producers made later continue after the last one, as they would have
            counterField.setInt(null, number);

            final String names = String.join(";", keys);
            final String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(names.getBytes(StandardCharsets.UTF_8))).substring(0, 12);
            ColonyPatches.LOG.info("Numbered {} MineColonies building modules by name ({} changed their number); "
                    + "module set {} - the same on the server and its players", number, changed, hash);
        } catch (final ReflectiveOperationException | GeneralSecurityException | RuntimeException e) {
            ColonyPatches.LOG.error("Could not renumber the MineColonies building modules", e);
        }
    }
}
