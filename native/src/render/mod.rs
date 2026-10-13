pub mod pipeline;
pub mod gl_context;
pub mod buffer;
pub mod shader;

use glam::Mat4;

pub struct RenderContext {
    pub view_matrix: Mat4,
    pub projection_matrix: Mat4,
    pub camera_pos: [f32; 3],
}

impl RenderContext {
    pub fn new() -> Self {
        Self {
            view_matrix: Mat4::IDENTITY,
            projection_matrix: Mat4::IDENTITY,
            camera_pos: [0.0, 0.0, 0.0],
        }
    }
}
