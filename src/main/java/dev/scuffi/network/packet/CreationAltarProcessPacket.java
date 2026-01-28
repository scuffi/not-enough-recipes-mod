package dev.scuffi.network.packet;

import dev.scuffi.NotEnoughRecipes;
import dev.scuffi.block.entity.CreationAltarBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Packet sent from client to server when player clicks "Create Item" button.
 */
public record CreationAltarProcessPacket(BlockPos pos) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<CreationAltarProcessPacket> TYPE = 
        new CustomPacketPayload.Type<>(Identifier.parse(NotEnoughRecipes.MOD_ID + ":altar_process"));
    
    public static final StreamCodec<ByteBuf, CreationAltarProcessPacket> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        CreationAltarProcessPacket::pos,
        CreationAltarProcessPacket::new
    );
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    public static void register() {
        // Register the payload type first
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(TYPE, CODEC);
        
        // Then register the receiver
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (packet, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                var be = player.level().getBlockEntity(packet.pos());
                if (be instanceof CreationAltarBlockEntity altar) {
                    altar.startProcessing();
                }
            });
        });
    }
    
    public static void send(BlockPos pos) {
        ClientPlayNetworking.send(new CreationAltarProcessPacket(pos));
    }
}
