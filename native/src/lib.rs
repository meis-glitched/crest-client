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
use jni::objects::{JClass, JString, JByteArray};
use jni::sys::{jboolean, jlong, jint};
use std::sync::Once;
use parking_lot::Mutex;
use std::sync::Arc;

use crate::chunk::{Chunk, ChunkPos, mesher::ChunkMesher};
use crate::render::buffer::BufferManager;
use crate::render::shader::{Shader, create_chunk_shader};

static INIT: Once = Once::new();

struct RenderState {
    buffer_manager: BufferManager,
    chunk_shader: Option<Shader>,
}

impl RenderState {
    fn new() -> Self {
        Self {
            buffer_manager: BufferManager::new(),
            chunk_shader: None,
        }
    }
}

lazy_static::lazy_static! {
    static ref RENDER_STATE: Arc<Mutex<RenderState>> = Arc::new(Mutex::new(RenderState::new()));
}

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

#[no_mangle]
pub extern "system" fn Java_com_crest_client_render_CrestRenderNative_nativeInitRenderer(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    gl::load_with(|s| {
        let c_str = std::ffi::CString::new(s).unwrap();
        unsafe {
            let lib = libc::dlopen(std::ptr::null(), libc::RTLD_LAZY | libc::RTLD_LOCAL);
            let func_ptr = libc::dlsym(lib, c_str.as_ptr());
            if !func_ptr.is_null() {
                return func_ptr as *const _;
            }
            
            let gl_lib = libc::dlopen(b"libGL.so.1\0".as_ptr() as *const _, libc::RTLD_LAZY);
            if !gl_lib.is_null() {
                let func = libc::dlsym(gl_lib, c_str.as_ptr());
                if !func.is_null() {
                    return func as *const _;
                }
            }
            
            std::ptr::null()
        }
    });
    
    let mut state = RENDER_STATE.lock();
    
    match create_chunk_shader() {
        Ok(shader) => {
            state.chunk_shader = Some(shader);
            log::info!("Chunk shader compiled successfully");
            1
        }
        Err(e) => {
            log::error!("Failed to compile chunk shader: {}", e);
            0
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_crest_client_render_CrestRenderNative_nativeMeshChunk(
    env: JNIEnv,
    _class: JClass,
    chunk_x: jint,
    chunk_y: jint,
    chunk_z: jint,
    block_data: JByteArray,
) -> jlong {
    let block_data_vec = match env.convert_byte_array(&block_data) {
        Ok(data) => data,
        Err(e) => {
            log::error!("Failed to convert block data: {}", e);
            return -1;
        }
    };
    
    if block_data_vec.len() != 16 * 16 * 16 * 2 {
        log::error!("Invalid block data size: expected 8192, got {}", block_data_vec.len());
        return -1;
    }
    
    let pos = ChunkPos::new(chunk_x, chunk_y, chunk_z);
    let mut chunk = Chunk::new(pos);
    
    for i in 0..4096 {
        let block_id = u16::from_le_bytes([block_data_vec[i * 2], block_data_vec[i * 2 + 1]]);
        let x = i % 16;
        let y = i / 256;
        let z = (i / 16) % 16;
        chunk.set_block(x, y, z, block_id);
    }
    
    let mut mesher = ChunkMesher::new();
    let mesh = mesher.mesh_chunk(&chunk);
    
    let chunk_pos = ((chunk_x as i64) << 42) | ((chunk_y as i64) << 21) | (chunk_z as i64);
    
    let mut state = RENDER_STATE.lock();
    state.buffer_manager.upload_chunk(chunk_pos, &mesh);
    
    log::debug!("Meshed chunk ({}, {}, {}): {} vertices, {} indices", 
        chunk_x, chunk_y, chunk_z, mesh.vertices.len(), mesh.indices.len());
    
    chunk_pos
}

#[no_mangle]
pub extern "system" fn Java_com_crest_client_render_CrestRenderNative_nativeDrawChunk(
    _env: JNIEnv,
    _class: JClass,
    chunk_handle: jlong,
    _projection: JByteArray,
    _view: JByteArray,
    _model: JByteArray,
) {
    let state = RENDER_STATE.lock();
    
    if let Some(shader) = &state.chunk_shader {
        shader.use_program();
        
        state.buffer_manager.draw_chunk(chunk_handle);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_crest_client_render_CrestRenderNative_nativeUnloadChunk(
    _env: JNIEnv,
    _class: JClass,
    chunk_handle: jlong,
) {
    let mut state = RENDER_STATE.lock();
    state.buffer_manager.remove_chunk(chunk_handle);
}
