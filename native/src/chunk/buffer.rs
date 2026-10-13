// Vertex buffer management
// Handles GPU buffer allocation, uploads, and pooling

use parking_lot::Mutex;
use std::collections::VecDeque;

pub struct VertexBuffer {
    pub vao: u32,
    pub vbo: u32,
    pub ebo: u32,
    pub vertex_count: usize,
    pub index_count: usize,
}

impl VertexBuffer {
    pub fn new() -> Self {
        Self {
            vao: 0,
            vbo: 0,
            ebo: 0,
            vertex_count: 0,
            index_count: 0,
        }
    }
}

pub struct BufferPool {
    available: Mutex<VecDeque<VertexBuffer>>,
    capacity: usize,
}

impl BufferPool {
    pub fn new(capacity: usize) -> Self {
        Self {
            available: Mutex::new(VecDeque::with_capacity(capacity)),
            capacity,
        }
    }
    
    pub fn acquire(&self) -> Option<VertexBuffer> {
        self.available.lock().pop_front()
    }
    
    pub fn release(&self, buffer: VertexBuffer) {
        let mut pool = self.available.lock();
        if pool.len() < self.capacity {
            pool.push_back(buffer);
        }
    }
}
