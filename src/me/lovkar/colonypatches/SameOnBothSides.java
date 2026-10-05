package me.lovkar.colonypatches;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * A payload that is never sent. Registered as required, it makes NeoForge refuse a connection when
 * only one side has Colony Patches: the module renumbering only helps when the server and the player
 * both do it, and a half-installed pack would otherwise break colony windows without a word.
 */
public record SameOnBothSides() implements CustomPacketPayload {
    public static final Type<SameOnBothSides> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ColonyPatches.MOD_ID, "module_numbers"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SameOnBothSides> CODEC = StreamCodec.unit(new SameOnBothSides());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void register(final RegisterPayloadHandlersEvent event) {
        // the version goes up whenever the numbering itself changes
        event.registrar("1").playToClient(TYPE, CODEC, (payload, context) -> { });
    }
}
