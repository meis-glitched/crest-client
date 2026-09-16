package dev.bongocat.client.live2d;

// Minimal JNI bridge over liblive2d_jni.so's exported framework C++ API.
// The binding's built-in OpenGL renderer is incompatible with desktop core profile,
// so we extract geometry/data via this bridge and render with our own GL code.
public final class NativeCat {

    private NativeCat() {}

    public static native long nativeCreate(byte[] moc);
    public static native void  nativeDispose(long h);

    public static native int   nativeGetDrawableCount(long h);
    public static native float nativeGetCanvasWidth(long h);
    public static native float nativeGetCanvasHeight(long h);
    public static native int   nativeGetParamCount(long h);
    public static native String[] nativeGetParamIds(long h);
    public static native float[]  nativeGetParamMinMaxDefault(long h); // 3 floats per param

    public static native void  nativeSetParam(long h, int index, float value);
    public static native float nativeGetParam(long h, int index);
    public static native void  nativeUpdate(long h, float delta);

    // Geometry contract: EVERY drawable's data appended cumulatively.
    // meta layout per drawable: [vertexCount indexCount opacity isVisible blend texIndex renderOrder]
    public static native int nativeRead(long h, int[] metaOut, float[] posOut, float[] uvOut, char[] idxOut);
}