package com.qouteall.immersive_portals.render;

import com.qouteall.immersive_portals.CGlobal;
import com.qouteall.immersive_portals.CHelper;
import com.qouteall.immersive_portals.ducks.IEMinecraftClient;
import com.qouteall.immersive_portals.my_util.LimitedLogger;
import com.qouteall.immersive_portals.render.context_management.RenderStates;
import com.qouteall.immersive_portals.render.context_management.WorldRenderInfo;
import org.apache.commons.lang3.Validate;

import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import java.util.HashMap;

@OnlyIn(Dist.CLIENT)
public class GuiPortalRendering {
    private static final LimitedLogger limitedLogger = new LimitedLogger(10);
    
    @Nullable
    private static Framebuffer renderingFrameBuffer = null;
    
    @Nullable
    public static Framebuffer getRenderingFrameBuffer() {
        return renderingFrameBuffer;
    }
    
    public static boolean isRendering() {
        return getRenderingFrameBuffer() != null;
    }
    
    public static void renderWorldIntoFrameBuffer(
        WorldRenderInfo worldRenderInfo,
        Framebuffer framebuffer
    ) {
        RenderStates.projectionMatrix = null;
        
        CHelper.checkGlError();
        
        Validate.isTrue(renderingFrameBuffer == null);
        renderingFrameBuffer = framebuffer;
        
        MyRenderHelper.restoreViewPort();
        
        Framebuffer mcFb = MyGameRenderer.client.getFramebuffer();
        
        Validate.isTrue(mcFb != framebuffer);
        
        ((IEMinecraftClient) MyGameRenderer.client).setFrameBuffer(framebuffer);
        
        framebuffer.bindFramebuffer(true);
        
        CGlobal.renderer.prepareRendering();
        
        CGlobal.renderer.invokeWorldRendering(worldRenderInfo);
        
        CGlobal.renderer.finishRendering();
        
        ((IEMinecraftClient) MyGameRenderer.client).setFrameBuffer(mcFb);
        
        mcFb.bindFramebuffer(true);
        
        renderingFrameBuffer = null;
        
        MyRenderHelper.restoreViewPort();
        
        CHelper.checkGlError();
        
        RenderStates.projectionMatrix = null;
    }
    
    private static final HashMap<Framebuffer, WorldRenderInfo> renderingTasks = new HashMap<>();
    
    public static void submitNextFrameRendering(
        WorldRenderInfo worldRenderInfo,
        Framebuffer renderTarget
    ) {
        Validate.isTrue(!renderingTasks.containsKey(renderTarget));
        
        renderingTasks.put(renderTarget, worldRenderInfo);
        
        if (Minecraft.func_238218_y_()) {
            limitedLogger.err("GUI Portal Rendering is Currently Problematic with Fabulous Graphics!" +
                " It's recommended to turn to fancy.");
        }
    }
    
    // Not API
    public static void onGameRenderEnd() {
        renderingTasks.forEach((frameBuffer, worldRendering) -> {
            renderWorldIntoFrameBuffer(
                worldRendering, frameBuffer
            );
        });
        renderingTasks.clear();
    }
}
