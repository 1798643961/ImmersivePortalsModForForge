package com.qouteall.immersive_portals.mixin.client.sync;

import com.mojang.authlib.GameProfile;
import com.qouteall.immersive_portals.CGlobal;
import com.qouteall.immersive_portals.ClientWorldLoader;
import com.qouteall.immersive_portals.Helper;
import com.qouteall.immersive_portals.ModMain;
import com.qouteall.immersive_portals.chunk_loading.DimensionalChunkPos;
import com.qouteall.immersive_portals.dimension_sync.DimensionTypeSync;
import com.qouteall.immersive_portals.ducks.IEBuiltChunk;
import com.qouteall.immersive_portals.ducks.IEClientPlayNetworkHandler;
import com.qouteall.immersive_portals.ducks.IEPlayerPositionLookS2CPacket;
import com.qouteall.immersive_portals.ducks.IEWorldRenderer;
import com.qouteall.immersive_portals.network.NetworkAdapt;
import com.qouteall.immersive_portals.render.MyBuiltChunkStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.play.ClientPlayNetHandler;
import net.minecraft.client.network.play.NetworkPlayerInfo;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SJoinGamePacket;
import net.minecraft.network.play.server.SPlayerPositionLookPacket;
import net.minecraft.network.play.server.SSetPassengersPacket;
import net.minecraft.network.play.server.SUnloadChunkPacket;
import net.minecraft.profiler.IProfiler;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.util.registry.DynamicRegistries;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;

@Mixin(ClientPlayNetHandler.class)
public abstract class MixinClientPlayNetworkHandler implements IEClientPlayNetworkHandler {
    @Shadow
    private ClientWorld world;
    
    @Shadow
    private boolean doneLoadingTerrain;
    
    @Shadow
    private Minecraft client;
    
    @Mutable
    @Shadow
    @Final
    private Map<UUID, NetworkPlayerInfo> playerInfoMap;
    
    @Shadow
    public abstract void handleSetPassengers(SSetPassengersPacket entityPassengersSetS2CPacket_1);
    
    @Shadow
    private DynamicRegistries field_239163_t_;
    
    @Override
    public void setWorld(ClientWorld world) {
        this.world = world;
    }
    
    @Override
    public Map getPlayerListEntries() {
        return playerInfoMap;
    }
    
    @Override
    public void setPlayerListEntries(Map value) {
        playerInfoMap = value;
    }
    
    @Inject(
        method = "<init>",
        at = @At("RETURN")
    )
    private void onInit(
        Minecraft minecraftClient_1,
        Screen screen_1,
        NetworkManager clientConnection_1,
        GameProfile gameProfile_1,
        CallbackInfo ci
    ) {
        isReProcessingPassengerPacket = false;
    }
    
    @Inject(method = "Lnet/minecraft/client/network/play/ClientPlayNetHandler;handleJoinGame(Lnet/minecraft/network/play/server/SJoinGamePacket;)V", at = @At("RETURN"))
    private void onOnGameJoin(SJoinGamePacket packet, CallbackInfo ci) {
        DimensionTypeSync.onGameJoinPacketReceived(packet.func_240817_g_());
    }
    
    @Inject(
        method = "Lnet/minecraft/client/network/play/ClientPlayNetHandler;handlePlayerPosLook(Lnet/minecraft/network/play/server/SPlayerPositionLookPacket;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/PacketThreadUtil;checkThreadAndEnqueue(Lnet/minecraft/network/IPacket;Lnet/minecraft/network/INetHandler;Lnet/minecraft/util/concurrent/ThreadTaskExecutor;)V",
            shift = At.Shift.AFTER
        ),
        cancellable = true
    )
    private void onProcessingPositionPacket(
        SPlayerPositionLookPacket packet,
        CallbackInfo ci
    ) {
        if (!NetworkAdapt.doesServerHasIP()) {
            return;
        }
        
        if (!doneLoadingTerrain) {
            // the first position packet removes the loading gui
            return;
        }
        
        RegistryKey<World> playerDimension = ((IEPlayerPositionLookS2CPacket) packet).getPlayerDimension();
        
        ClientWorld world = client.world;
        
        if (world != null) {
            if (world.func_234923_W_() != playerDimension) {
                if (!Minecraft.getInstance().player.removed) {
                    Helper.log(String.format(
                        "denied position packet %s %s %s %s",
                        ((IEPlayerPositionLookS2CPacket) packet).getPlayerDimension(),
                        packet.getX(), packet.getY(), packet.getZ()
                    ));
                    ci.cancel();
                }
            }
        }
        
        CGlobal.clientTeleportationManager.disableTeleportFor(20);
        
    }
    
    private boolean isReProcessingPassengerPacket;
    
    @Inject(
        method = "Lnet/minecraft/client/network/play/ClientPlayNetHandler;handleSetPassengers(Lnet/minecraft/network/play/server/SSetPassengersPacket;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/PacketThreadUtil;checkThreadAndEnqueue(Lnet/minecraft/network/IPacket;Lnet/minecraft/network/INetHandler;Lnet/minecraft/util/concurrent/ThreadTaskExecutor;)V",
            shift = At.Shift.AFTER
        ),
        cancellable = true
    )
    private void onOnEntityPassengersSet(
        SSetPassengersPacket entityPassengersSetS2CPacket_1,
        CallbackInfo ci
    ) {
        Entity entity_1 = this.world.getEntityByID(entityPassengersSetS2CPacket_1.getEntityId());
        if (entity_1 == null) {
            if (!isReProcessingPassengerPacket) {
                Helper.log("Re-processed riding packet");
                ModMain.clientTaskList.addTask(() -> {
                    isReProcessingPassengerPacket = true;
                    handleSetPassengers(entityPassengersSetS2CPacket_1);
                    isReProcessingPassengerPacket = false;
                    return true;
                });
                ci.cancel();
            }
        }
    }
    
    //fix lag spike
    //this lag spike is more severe with many portals pointing to different area
    @Inject(
        method = "Lnet/minecraft/client/network/play/ClientPlayNetHandler;processChunkUnload(Lnet/minecraft/network/play/server/SUnloadChunkPacket;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientChunkProvider;getLightManager()Lnet/minecraft/world/lighting/WorldLightManager;"
        ),
        cancellable = true
    )
    private void onOnUnload(SUnloadChunkPacket packet, CallbackInfo ci) {
        if (CGlobal.smoothChunkUnload) {
            DimensionalChunkPos pos = new DimensionalChunkPos(
                world.func_234923_W_(), packet.getX(), packet.getZ()
            );
            
            WorldRenderer worldRenderer =
                ClientWorldLoader.getWorldRenderer(world.func_234923_W_());
            ViewFrustum storage = ((IEWorldRenderer) worldRenderer).getBuiltChunkStorage();
            if (storage instanceof MyBuiltChunkStorage) {
                for (int y = 0; y < 16; ++y) {
                    ChunkRenderDispatcher.ChunkRender builtChunk = ((MyBuiltChunkStorage) storage).provideBuiltChunk(
                        new BlockPos(
                            packet.getX() * 16,
                            y * 16,
                            packet.getZ() * 16
                        )
                    );
                    ((IEBuiltChunk) builtChunk).fullyReset();
                }
                
            }
            
            int[] counter = new int[1];
            counter[0] = (int) (Math.random() * 200);
            ModMain.clientTaskList.addTask(() -> {
                ClientWorld world1 = ClientWorldLoader.getWorld(pos.dimension);
                
                if (world1.getChunkProvider().chunkExists(pos.x, pos.z)) {
                    return true;
                }
                
                if (counter[0] > 0) {
                    counter[0]--;
                    return false;
                }
                
                WorldRenderer wr = ClientWorldLoader.getWorldRenderer(pos.dimension);
                
                IProfiler profiler = Minecraft.getInstance().getProfiler();
                profiler.startSection("delayed_unload");
                
                for (int y = 0; y < 16; ++y) {
                    wr.markSurroundingsForRerender(pos.x, y, pos.z);
                    world1.getLightManager().updateSectionStatus(
                        SectionPos.of(pos.x, y, pos.z), true
                    );
                }
                
                world1.getLightManager().enableLightSources(pos.getChunkPos(), false);
                
                profiler.endSection();
                
                return true;
            });
            ci.cancel();
        }
    }
    
    @Override
    public void portal_setRegistryManager(DynamicRegistries arg) {
        field_239163_t_ = arg;
    }
}
