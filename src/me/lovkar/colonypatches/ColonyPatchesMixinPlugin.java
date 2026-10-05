package me.lovkar.colonypatches;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mixin plugin: applies each fix's mixins only when the mods it patches are installed, so a game without them
 * loads no mixin for them and logs no warning about a missing target class.
 */
public final class ColonyPatchesMixinPlugin implements IMixinConfigPlugin {
    /** Mixin (relative to this package) -> the mod ids it needs. */
    private static final Map<String, List<String>> NEEDS = Map.of(
            "compat.CornCropMixin", List.of("minecolonies_compatibility", "culturaldelights"));

    @Override
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
        final String mixin = mixinClassName.substring(mixinClassName.indexOf(".mixin.") + ".mixin.".length());
        final List<String> mods = NEEDS.get(mixin);
        return mods == null || mods.stream().allMatch(ColonyPatchesMixinPlugin::installed);
    }

    private static boolean installed(final String modId) {
        final LoadingModList list = LoadingModList.get();
        return list != null && list.getModFileById(modId) != null;
    }

    @Override
    public void onLoad(final String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(final Set<String> myTargets, final Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(final String targetClassName, final ClassNode targetClass, final String mixinClassName, final IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(final String targetClassName, final ClassNode targetClass, final String mixinClassName, final IMixinInfo mixinInfo) {
    }
}
