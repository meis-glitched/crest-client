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
     * Create a new chunk mesh from block data.
     * 
     * @param chunkX chunk X coordinate
     * @param chunkY chunk Y coordinate
     * @param chunkZ chunk Z coordinate
     * @param blocks block state IDs (16x16x16 = 4096 elements)
     * @return mesh handle (native pointer)
     */
    public static native long nativeMeshChunk(int chunkX, int chunkY, int chunkZ, short[] blocks);
    
    /**
     * Upload a mesh to the GPU.
     * 
     * @param meshHandle mesh handle from nativeMeshChunk
     * @return GPU buffer handle (VAO ID)
     */
    public static native int nativeUploadMesh(long meshHandle);
    
    /**
     * Free a mesh.
     * 
     * @param meshHandle mesh handle to free
     */
    public static native void nativeFreeMesh(long meshHandle);
    
    /**
     * Render a chunk.
     * 
     * @param vaoId VAO ID from nativeUploadMesh
     * @param viewMatrix view matrix (16 floats)
     * @param projMatrix projection matrix (16 floats)
     */
    public static native void nativeRenderChunk(int vaoId, float[] viewMatrix, float[] projMatrix);
}
