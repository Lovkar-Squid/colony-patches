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
 * A new Town Hall is opened: MineColonies looks for a colony the player already owns and, if there is
 * one, offers to delete or abandon it instead of founding a new one. With several colonies allowed,
 * the player owns none as far as this question goes, and the founding window opens.
 */
@Mixin(targets = "com.minecolonies.core.network.messages.server.GetColonyInfoMessage", remap = false)
public abstract class GetColonyInfoMessageMixin {
    @Redirect(method = "onExecute(Lnet/neoforged/neoforge/network/handling/IPayloadContext;Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/minecolonies/api/colony/IColonyManager;getIColonyByOwner(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;)Lcom/minecolonies/api/colony/IColony;"),
            remap = false)
    private IColony colony_patches$ownedColony(final IColonyManager manager, final Level level, final Player player) {
        return Settings.severalColonies() ? null : manager.getIColonyByOwner(level, player);
    }
}
