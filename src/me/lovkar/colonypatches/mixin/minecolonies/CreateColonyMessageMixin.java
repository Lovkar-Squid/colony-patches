package me.lovkar.colonypatches.mixin.minecolonies;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import me.lovkar.colonypatches.Settings;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The founding window was confirmed: MineColonies founds the colony only if the player owns none.
 * With several colonies allowed, that check passes and the new colony is founded with the player
 * as its owner, next to the ones they have.
 */
@Mixin(targets = "com.minecolonies.core.network.messages.server.CreateColonyMessage", remap = false)
public abstract class CreateColonyMessageMixin {
    @Redirect(method = "onExecute(Lnet/neoforged/neoforge/network/handling/IPayloadContext;Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/minecolonies/api/colony/IColonyManager;getIColonyByOwner(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;)Lcom/minecolonies/api/colony/IColony;"),
            remap = false)
    private IColony colony_patches$ownedColony(final IColonyManager manager, final Level level, final Player player) {
        return Settings.severalColonies() ? null : manager.getIColonyByOwner(level, player);
    }
}
