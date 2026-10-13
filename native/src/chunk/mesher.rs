// Chunk mesher - converts block data to renderable geometry
// Uses greedy meshing for optimal vertex count

use super::Chunk;

#[repr(C)]
#[derive(Debug, Clone, Copy)]
pub struct Vertex {
    pub position: [f32; 3],
    pub uv: [f32; 2],
    pub color: [u8; 4],
    pub light: [u8; 2],
    pub normal: [f32; 3],
}

impl Vertex {
    pub fn new(position: [f32; 3], uv: [f32; 2], color: [u8; 4], light: [u8; 2], normal: [f32; 3]) -> Self {
        Self { position, uv, color, light, normal }
    }
}

pub struct ChunkMesh {
    pub vertices: Vec<Vertex>,
    pub indices: Vec<u32>,
}

impl ChunkMesh {
    pub fn new() -> Self {
        Self {
            vertices: Vec::with_capacity(4096),
            indices: Vec::with_capacity(6144),
        }
    }
    
    pub fn clear(&mut self) {
        self.vertices.clear();
        self.indices.clear();
    }
}

pub struct ChunkMesher {
    mesh: ChunkMesh,
}

impl ChunkMesher {
    pub fn new() -> Self {
        Self {
            mesh: ChunkMesh::new(),
        }
    }
    
    pub fn mesh_chunk(&mut self, chunk: &Chunk) -> ChunkMesh {
        self.mesh.clear();
        
        // TODO: Implement greedy meshing algorithm
        // For now, just a placeholder that generates a simple cube per non-air block
        
        for y in 0..16 {
            for z in 0..16 {
                for x in 0..16 {
                    let block = chunk.get_block(x, y, z);
                    if block == 0 {
                        continue; // Air
                    }
                    
                    // Generate faces for this block
                    self.add_block_faces(x as f32, y as f32, z as f32, block);
                }
            }
        }
        
        ChunkMesh {
            vertices: self.mesh.vertices.clone(),
            indices: self.mesh.indices.clone(),
        }
    }
    
    fn add_block_faces(&mut self, x: f32, y: f32, z: f32, _block: u16) {
        let base_idx = self.mesh.vertices.len() as u32;
        
        // Simple cube - will be replaced with greedy meshing + face culling
        let positions = [
            // Front face
            [x, y, z], [x + 1.0, y, z], [x + 1.0, y + 1.0, z], [x, y + 1.0, z],
            // Back face
            [x, y, z + 1.0], [x, y + 1.0, z + 1.0], [x + 1.0, y + 1.0, z + 1.0], [x + 1.0, y, z + 1.0],
        ];
        
        let color = [255, 255, 255, 255];
        let light = [15, 15];
        let normal = [0.0, 0.0, 1.0];
        
        for pos in positions.iter() {
            self.mesh.vertices.push(Vertex::new(*pos, [0.0, 0.0], color, light, normal));
        }
        
        // Cube indices (6 faces * 2 triangles * 3 vertices)
        let indices = [
            0, 1, 2, 2, 3, 0, // Front
            4, 5, 6, 6, 7, 4, // Back
        ];
        
        for idx in indices.iter() {
            self.mesh.indices.push(base_idx + idx);
        }
    }
}
