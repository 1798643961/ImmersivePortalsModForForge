package com.qouteall.immersive_portals.mixin.entity_sync;

import com.qouteall.immersive_portals.SGlobal;
import com.qouteall.immersive_portals.ducks.IEEntityTracker;
import com.qouteall.immersive_portals.ducks.IEThreadedAnvilChunkStorage;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.server.ChunkManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkManager.class)
public abstract class MixinThreadedAnvilChunkStorage_E implements IEThreadedAnvilChunkStorage {
    
    @Shadow
    @Final
    private Int2ObjectMap entities;
    
    @Shadow
    abstract void setPlayerTracking(ServerPlayerEntity p_219234_1_, boolean p_219234_2_);
    
    @Inject(
        method = "untrack",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onUnloadEntity(Entity entity, CallbackInfo ci) {
        //when the player leave this dimension, do not stop tracking entities
        if (entity instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) entity;
            if (SGlobal.serverTeleportationManager.isTeleporting(player)) {
                entities.remove(entity.getEntityId());
                setPlayerTracking(player, false);
                ci.cancel();
            }
        }
    }
    
    @Override
    public void onPlayerRespawn(ServerPlayerEntity oldPlayer) {
        entities.values().forEach(obj -> {
            ((IEEntityTracker) obj).onPlayerRespawn(oldPlayer);
        });
    }
}
