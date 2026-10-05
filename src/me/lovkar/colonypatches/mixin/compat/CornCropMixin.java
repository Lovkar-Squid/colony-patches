package me.lovkar.colonypatches.mixin.compat;

import me.lovkar.colonypatches.patch.CulturalDelightsCorn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import steve_gall.minecolonies_compatibility.api.common.plant.CustomizedCrop;
import steve_gall.minecolonies_compatibility.api.common.plant.PlantBlockContext;

/**
 * MineColonies Compatibility's Cultural Delights corn handler: no special harvest position for the
 * one-block corn, whose position function needs the removed CornUpperBlock (see CulturalDelightsCorn).
 * Named by string, so the mixin is simply skipped when the addon is not installed.
 */
@Mixin(targets = "steve_gall.minecolonies_compatibility.module.common.culturaldelights.CornCrop", remap = false)
public abstract class CornCropMixin {
    @Inject(method = "getSpecialHarvestPosition", at = @At("HEAD"), cancellable = true, remap = false)
    private void colony_patches$oneBlockCorn(final PlantBlockContext context,
            final CallbackInfoReturnable<CustomizedCrop.SpecialHarvestPositionFunction> cir) {
        if (CulturalDelightsCorn.brokenHandler(((Object) this).getClass())) {
            cir.setReturnValue(null);
        }
    }
}
