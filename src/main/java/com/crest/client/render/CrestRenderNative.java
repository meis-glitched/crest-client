package com.crest.client.render;

/**
 * JNI bridge to the CrestRender native library (Rust).
 * Reticulum - High-performance Minecraft renderer replacing Sodium.
 */
public final class CrestRenderNative {
    
    private CrestRenderNative() {}
    
    /**
     * Initialize the native renderer.
     * Must be called before any other native methods.
     * 
     * @return true if initialization succeeded
     */
    public static native boolean nativeInit();
    
    /**
     * Get the renderer version string.
     * 
     * @return version string (e.g. "CrestRender 0.1.0")
     */
    public static native String nativeGetVersion();
    
    /**
     * Cleanup and destroy the native renderer.
     * Should be called on client shutdown.
     */
    public static native void nativeDestroy();
    
    /**
     * Initialize the OpenGL renderer (shaders, etc).
     * Must be called after OpenGL context is created.
     * 
     * @return true if initialization succeeded
     */
    public static native boolean nativeInitRenderer();
    
    /**
     * Mesh a chunk and upload to GPU in one call.
     * 
     * @param chunkX chunk X coordinate
     * @param chunkY chunk Y coordinate
     * @param chunkZ chunk Z coordinate
     * @param blockData block state IDs (16x16x16 * 2 bytes = 8192 bytes)
     * @return chunk handle (for drawing/unloading)
     */
    public static native long nativeMeshChunk(int chunkX, int chunkY, int chunkZ, byte[] blockData);
    
    /**
     * Draw a chunk.
     * 
     * @param chunkHandle handle from nativeMeshChunk
     * @param projection projection matrix (64 bytes, 16 floats)
     * @param view view matrix (64 bytes, 16 floats)
     * @param model model matrix (64 bytes, 16 floats)
     */
    public static native void nativeDrawChunk(long chunkHandle, byte[] projection, byte[] view, byte[] model);
    
    /**
     * Unload a chunk from GPU.
     * 
     * @param chunkHandle handle from nativeMeshChunk
     */
    public static native void nativeUnloadChunk(long chunkHandle);
}
