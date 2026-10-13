// CrestRender - High-performance Minecraft renderer in Rust
// Reticulum branch - Complete Sodium replacement
// 
// Architecture:
// - JNI bridge for Java ↔ Rust communication
// - Multi-threaded chunk meshing with Rayon
// - OpenGL 3.3+ rendering pipeline
// - Lock-free data structures for performance

pub mod bridge;
pub mod chunk;
pub mod render;
pub mod culling;
pub mod util;

use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean};
use std::sync::Once;

static INIT: Once = Once::new();

#[no_mangle]
pub extern "system" fn Java_com_crest_client_render_CrestRenderNative_nativeInit(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    INIT.call_once(|| {
        let _ = env_logger::try_init();
        log::info!("CrestRender native library initialized");
    });
    
    log::info!("CrestRender v{} - Reticulum renderer", env!("CARGO_PKG_VERSION"));
    
    1 // true
}

#[no_mangle]
pub extern "system" fn Java_com_crest_client_render_CrestRenderNative_nativeGetVersion<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> JString<'local> {
    let version = format!("CrestRender {}", env!("CARGO_PKG_VERSION"));
    env.new_string(version).unwrap()
}

#[no_mangle]
pub extern "system" fn Java_com_crest_client_render_CrestRenderNative_nativeDestroy(
    _env: JNIEnv,
    _class: JClass,
) {
    log::info!("CrestRender shutting down");
}
