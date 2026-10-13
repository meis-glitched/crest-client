// OpenGL context management
// Thread-local GL state tracking

use std::cell::RefCell;

thread_local! {
    static GL_STATE: RefCell<GlState> = RefCell::new(GlState::new());
}

pub struct GlState {
    pub bound_vao: u32,
    pub bound_vbo: u32,
    pub bound_program: u32,
}

impl GlState {
    pub fn new() -> Self {
        Self {
            bound_vao: 0,
            bound_vbo: 0,
            bound_program: 0,
        }
    }
}

pub fn bind_vao(vao: u32) {
    GL_STATE.with(|state| {
        let mut s = state.borrow_mut();
        if s.bound_vao != vao {
            unsafe {
                gl::BindVertexArray(vao);
            }
            s.bound_vao = vao;
        }
    });
}
