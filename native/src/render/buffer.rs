use crate::chunk::mesher::{ChunkMesh, Vertex};
use gl::types::*;
use std::ptr;

pub struct GpuBuffer {
    pub vao: GLuint,
    pub vbo: GLuint,
    pub ebo: GLuint,
    pub index_count: i32,
}

impl GpuBuffer {
    pub fn new() -> Self {
        let mut vao = 0;
        let mut vbo = 0;
        let mut ebo = 0;
        
        unsafe {
            gl::GenVertexArrays(1, &mut vao);
            gl::GenBuffers(1, &mut vbo);
            gl::GenBuffers(1, &mut ebo);
        }
        
        Self {
            vao,
            vbo,
            ebo,
            index_count: 0,
        }
    }
    
    pub fn upload(&mut self, mesh: &ChunkMesh) {
        unsafe {
            gl::BindVertexArray(self.vao);
            
            gl::BindBuffer(gl::ARRAY_BUFFER, self.vbo);
            gl::BufferData(
                gl::ARRAY_BUFFER,
                (mesh.vertices.len() * std::mem::size_of::<Vertex>()) as GLsizeiptr,
                mesh.vertices.as_ptr() as *const _,
                gl::STATIC_DRAW,
            );
            
            gl::BindBuffer(gl::ELEMENT_ARRAY_BUFFER, self.ebo);
            gl::BufferData(
                gl::ELEMENT_ARRAY_BUFFER,
                (mesh.indices.len() * std::mem::size_of::<u32>()) as GLsizeiptr,
                mesh.indices.as_ptr() as *const _,
                gl::STATIC_DRAW,
            );
            
            let stride = std::mem::size_of::<Vertex>() as GLsizei;
            
            gl::EnableVertexAttribArray(0);
            gl::VertexAttribPointer(0, 3, gl::FLOAT, gl::FALSE, stride, ptr::null());
            
            gl::EnableVertexAttribArray(1);
            gl::VertexAttribPointer(1, 2, gl::FLOAT, gl::FALSE, stride, 
                (3 * std::mem::size_of::<f32>()) as *const _);
            
            gl::EnableVertexAttribArray(2);
            gl::VertexAttribPointer(2, 4, gl::UNSIGNED_BYTE, gl::TRUE, stride,
                (5 * std::mem::size_of::<f32>()) as *const _);
            
            gl::EnableVertexAttribArray(3);
            gl::VertexAttribPointer(3, 2, gl::UNSIGNED_BYTE, gl::FALSE, stride,
                (5 * std::mem::size_of::<f32>() + 4) as *const _);
            
            gl::EnableVertexAttribArray(4);
            gl::VertexAttribPointer(4, 3, gl::FLOAT, gl::FALSE, stride,
                (5 * std::mem::size_of::<f32>() + 6) as *const _);
            
            gl::BindVertexArray(0);
            
            self.index_count = mesh.indices.len() as i32;
        }
    }
    
    pub fn draw(&self) {
        if self.index_count == 0 {
            return;
        }
        
        unsafe {
            gl::BindVertexArray(self.vao);
            gl::DrawElements(gl::TRIANGLES, self.index_count, gl::UNSIGNED_INT, ptr::null());
            gl::BindVertexArray(0);
        }
    }
}

impl Drop for GpuBuffer {
    fn drop(&mut self) {
        unsafe {
            gl::DeleteVertexArrays(1, &self.vao);
            gl::DeleteBuffers(1, &self.vbo);
            gl::DeleteBuffers(1, &self.ebo);
        }
    }
}

pub struct BufferManager {
    buffers: ahash::AHashMap<i64, GpuBuffer>,
}

impl BufferManager {
    pub fn new() -> Self {
        Self {
            buffers: ahash::AHashMap::new(),
        }
    }
    
    pub fn upload_chunk(&mut self, chunk_pos: i64, mesh: &ChunkMesh) -> i64 {
        let buffer = self.buffers.entry(chunk_pos).or_insert_with(GpuBuffer::new);
        buffer.upload(mesh);
        chunk_pos
    }
    
    pub fn draw_chunk(&self, chunk_pos: i64) {
        if let Some(buffer) = self.buffers.get(&chunk_pos) {
            buffer.draw();
        }
    }
    
    pub fn remove_chunk(&mut self, chunk_pos: i64) {
        self.buffers.remove(&chunk_pos);
    }
    
    pub fn clear(&mut self) {
        self.buffers.clear();
    }
}
