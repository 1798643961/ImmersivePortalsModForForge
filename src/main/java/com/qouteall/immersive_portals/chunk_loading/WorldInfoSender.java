package com.qouteall.immersive_portals.chunk_loading;

import com.qouteall.hiding_in_the_bushes.MyNetwork;
import com.qouteall.immersive_portals.McHelper;
import com.qouteall.immersive_portals.ModMain;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.play.server.SChangeGameStatePacket;
import net.minecraft.network.play.server.SUpdateTimePacket;
import net.minecraft.util.RegistryKey;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import java.util.Set;

public class WorldInfoSender {
    public static void init() {
        ModMain.postServerTickSignal.connect(() -> {
            McHelper.getServer().getProfiler().startSection("portal_send_world_info");
            if (McHelper.getServerGameTime() % 100 == 42) {
                for (ServerPlayerEntity player : McHelper.getCopiedPlayerList()) {
                    Set<RegistryKey<World>> visibleDimensions = NewChunkTrackingGraph.getVisibleDimensions(player);
                    
                    if (player.world.func_234923_W_() != World.field_234918_g_) {
                        sendWorldInfo(
                            player,
                            McHelper.getServer().getWorld(World.field_234918_g_)
                        );
                    }
                    
                    McHelper.getServer().getWorlds().forEach(thisWorld -> {
                        if (isNonOverworldSurfaceDimension(thisWorld)) {
                            if (visibleDimensions.contains(thisWorld.func_234923_W_())) {
                                sendWorldInfo(
                                    player,
                                    thisWorld
                                );
                            }
                        }
                    });
                    
                }
            }
            McHelper.getServer().getProfiler().endSection();
        });
    }
    
    //send the daytime and weather info to player when player is in nether
    public static void sendWorldInfo(ServerPlayerEntity player, ServerWorld world) {
        RegistryKey<World> remoteDimension = world.func_234923_W_();
        
        player.connection.sendPacket(
            MyNetwork.createRedirectedMessage(
                remoteDimension,
                new SUpdateTimePacket(
                    world.getGameTime(),
                    world.getDayTime(),
                    world.getGameRules().getBoolean(
                        GameRules.DO_DAYLIGHT_CYCLE
                    )
                )
            )
        );
        
        /**{@link net.minecraft.client.network.ClientPlayNetworkHandler#onGameStateChange(GameStateChangeS2CPacket)}*/
        
        if (world.isRaining()) {
            player.connection.sendPacket(MyNetwork.createRedirectedMessage(
                world.func_234923_W_(),
                new SChangeGameStatePacket(
                    SChangeGameStatePacket.field_241765_b_,
                    0.0F
                )
            ));
        }
        else {
            //if the weather is already not raining when the player logs in then no need to sync
            //if the weather turned to not raining then elsewhere syncs it
        }
        
        player.connection.sendPacket(MyNetwork.createRedirectedMessage(
            world.func_234923_W_(),
            new SChangeGameStatePacket(
                SChangeGameStatePacket.field_241771_h_,
                world.getRainStrength(1.0F)
            )
        ));
        player.connection.sendPacket(MyNetwork.createRedirectedMessage(
            world.func_234923_W_(),
            new SChangeGameStatePacket(
                SChangeGameStatePacket.field_241772_i_,
                world.getThunderStrength(1.0F)
            )
        ));
    }
    
    public static boolean isNonOverworldSurfaceDimension(World world) {
        return world.func_230315_m_().hasSkyLight() && world.func_234923_W_() != World.field_234918_g_;
    }
}
