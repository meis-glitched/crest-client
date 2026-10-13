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

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum Direction {
    North,  // -Z
    South,  // +Z
    West,   // -X
    East,   // +X
    Down,   // -Y
    Up,     // +Y
}

impl Direction {
    fn normal(&self) -> [f32; 3] {
        match self {
            Direction::North => [0.0, 0.0, -1.0],
            Direction::South => [0.0, 0.0, 1.0],
            Direction::West => [-1.0, 0.0, 0.0],
            Direction::East => [1.0, 0.0, 0.0],
            Direction::Down => [0.0, -1.0, 0.0],
            Direction::Up => [0.0, 1.0, 0.0],
        }
    }
    
    fn offset(&self) -> (i32, i32, i32) {
        match self {
            Direction::North => (0, 0, -1),
            Direction::South => (0, 0, 1),
            Direction::West => (-1, 0, 0),
            Direction::East => (1, 0, 0),
            Direction::Down => (0, -1, 0),
            Direction::Up => (0, 1, 0),
        }
    }
}

pub struct ChunkMesher {
    mesh: ChunkMesh,
    // Greedy meshing work buffers (reused to avoid allocations)
    mask: Vec<Option<u16>>,
}

impl ChunkMesher {
    pub fn new() -> Self {
        Self {
            mesh: ChunkMesh::new(),
            mask: vec![None; 16 * 16],
        }
    }
    
    pub fn mesh_chunk(&mut self, chunk: &Chunk) -> ChunkMesh {
        self.mesh.clear();
        
        // Greedy meshing: process each axis-aligned direction
        for direction in [
            Direction::North, Direction::South,
            Direction::West, Direction::East,
            Direction::Down, Direction::Up,
        ] {
            self.greedy_mesh_direction(chunk, direction);
        }
        
        ChunkMesh {
            vertices: self.mesh.vertices.clone(),
            indices: self.mesh.indices.clone(),
        }
    }
    
    /// Greedy meshing algorithm for one direction
    /// Merges adjacent quads of the same block type into larger quads
    fn greedy_mesh_direction(&mut self, chunk: &Chunk, direction: Direction) {
        let (axis_u, axis_v, axis_d) = match direction {
            Direction::North | Direction::South => (0, 1, 2), // X, Y, Z
            Direction::West | Direction::East => (2, 1, 0),   // Z, Y, X
            Direction::Down | Direction::Up => (0, 2, 1),     // X, Z, Y
        };
        
        let (dx, dy, dz) = direction.offset();
        
        // Sweep through all slices along the direction axis
        for d in 0..16 {
            // Build mask for this slice
            self.mask.fill(None);
            
            for v in 0..16 {
                for u in 0..16 {
                    let pos = self.decode_uvd(u, v, d, axis_u, axis_v, axis_d);
                    let block = chunk.get_block(pos.0, pos.1, pos.2);
                    
                    if block == 0 {
                        continue; // Air
                    }
                    
                    // Face culling: check if neighbor blocks this face
                    let neighbor_pos = (
                        (pos.0 as i32 + dx) as usize,
                        (pos.1 as i32 + dy) as usize,
                        (pos.2 as i32 + dz) as usize,
                    );
                    
                    let neighbor_blocks = self.is_neighbor_blocking(chunk, neighbor_pos);
                    
                    if !neighbor_blocks {
                        self.mask[v * 16 + u] = Some(block);
                    }
                }
            }
            
            // Greedy mesh the mask: find rectangular regions
            self.generate_quads_from_mask(d, direction, axis_u, axis_v, axis_d);
        }
    }
    
    /// Generate quads from the mask using greedy meshing
    fn generate_quads_from_mask(&mut self, d: usize, direction: Direction, axis_u: usize, axis_v: usize, axis_d: usize) {
        let mut visited = vec![false; 16 * 16];
        
        for v in 0..16 {
            for u in 0..16 {
                let idx = v * 16 + u;
                
                if visited[idx] || self.mask[idx].is_none() {
                    continue;
                }
                
                let block = self.mask[idx].unwrap();
                
                // Find the width (along u axis)
                let mut width = 1;
                while u + width < 16 
                    && !visited[v * 16 + u + width]
                    && self.mask[v * 16 + u + width] == Some(block) {
                    width += 1;
                }
                
                // Find the height (along v axis)
                let mut height = 1;
                'height_search: while v + height < 16 {
                    for du in 0..width {
                        let test_idx = (v + height) * 16 + u + du;
                        if visited[test_idx] || self.mask[test_idx] != Some(block) {
                            break 'height_search;
                        }
                    }
                    height += 1;
                }
                
                // Mark as visited
                for dv in 0..height {
                    for du in 0..width {
                        visited[(v + dv) * 16 + u + du] = true;
                    }
                }
                
                // Create the quad
                self.add_quad(u, v, d, width, height, block, direction, axis_u, axis_v, axis_d);
            }
        }
    }
    
    /// Add a greedy-meshed quad to the mesh
    fn add_quad(&mut self, u: usize, v: usize, d: usize, width: usize, height: usize, 
                _block: u16, direction: Direction, axis_u: usize, axis_v: usize, axis_d: usize) {
        let base_idx = self.mesh.vertices.len() as u32;
        
        let normal = direction.normal();
        
        // Calculate quad corners in world space
        let p0 = self.decode_uvd(u, v, d, axis_u, axis_v, axis_d);
        let p1 = self.decode_uvd(u + width, v, d, axis_u, axis_v, axis_d);
        let p2 = self.decode_uvd(u + width, v + height, d, axis_u, axis_v, axis_d);
        let p3 = self.decode_uvd(u, v + height, d, axis_u, axis_v, axis_d);
        
        // Adjust for face direction
        let (v0, v1, v2, v3) = match direction {
            Direction::North => (
                [p0.0 as f32, p0.1 as f32, p0.2 as f32],
                [p1.0 as f32, p1.1 as f32, p1.2 as f32],
                [p2.0 as f32, p2.1 as f32, p2.2 as f32],
                [p3.0 as f32, p3.1 as f32, p3.2 as f32],
            ),
            Direction::South => (
                [p1.0 as f32, p1.1 as f32, (p1.2 + 1) as f32],
                [p0.0 as f32, p0.1 as f32, (p0.2 + 1) as f32],
                [p3.0 as f32, p3.1 as f32, (p3.2 + 1) as f32],
                [p2.0 as f32, p2.1 as f32, (p2.2 + 1) as f32],
            ),
            Direction::West => (
                [p0.0 as f32, p0.1 as f32, p0.2 as f32],
                [p0.0 as f32, p0.1 as f32, p1.2 as f32],
                [p0.0 as f32, p2.1 as f32, p2.2 as f32],
                [p0.0 as f32, p3.1 as f32, p3.2 as f32],
            ),
            Direction::East => (
                [(p1.0 + 1) as f32, p1.1 as f32, p1.2 as f32],
                [(p0.0 + 1) as f32, p0.1 as f32, p0.2 as f32],
                [(p3.0 + 1) as f32, p3.1 as f32, p3.2 as f32],
                [(p2.0 + 1) as f32, p2.1 as f32, p2.2 as f32],
            ),
            Direction::Down => (
                [p0.0 as f32, p0.1 as f32, p0.2 as f32],
                [p1.0 as f32, p1.1 as f32, p1.2 as f32],
                [p2.0 as f32, p2.1 as f32, p2.2 as f32],
                [p3.0 as f32, p3.1 as f32, p3.2 as f32],
            ),
            Direction::Up => (
                [p0.0 as f32, (p0.1 + 1) as f32, p0.2 as f32],
                [p3.0 as f32, (p3.1 + 1) as f32, p3.2 as f32],
                [p2.0 as f32, (p2.1 + 1) as f32, p2.2 as f32],
                [p1.0 as f32, (p1.1 + 1) as f32, p1.2 as f32],
            ),
        };
        
        // UV coordinates (width and height in blocks)
        let uv_w = width as f32;
        let uv_h = height as f32;
        
        // Default lighting and color
        let color = [255, 255, 255, 255];
        let light = [15, 15]; // Full brightness for now
        
        // Add vertices
        self.mesh.vertices.push(Vertex::new(v0, [0.0, 0.0], color, light, normal));
        self.mesh.vertices.push(Vertex::new(v1, [uv_w, 0.0], color, light, normal));
        self.mesh.vertices.push(Vertex::new(v2, [uv_w, uv_h], color, light, normal));
        self.mesh.vertices.push(Vertex::new(v3, [0.0, uv_h], color, light, normal));
        
        // Add indices (two triangles)
        self.mesh.indices.extend_from_slice(&[
            base_idx, base_idx + 1, base_idx + 2,
            base_idx + 2, base_idx + 3, base_idx,
        ]);
    }
    
    /// Convert U,V,D coordinates to X,Y,Z based on axis mapping
    fn decode_uvd(&self, u: usize, v: usize, d: usize, axis_u: usize, axis_v: usize, axis_d: usize) -> (usize, usize, usize) {
        let mut pos = [0, 0, 0];
        pos[axis_u] = u;
        pos[axis_v] = v;
        pos[axis_d] = d;
        (pos[0], pos[1], pos[2])
    }
    
    /// Check if a neighbor position blocks the face (face culling)
    fn is_neighbor_blocking(&self, chunk: &Chunk, pos: (usize, usize, usize)) -> bool {
        // Out of bounds = air (doesn't block)
        if pos.0 >= 16 || pos.1 >= 16 || pos.2 >= 16 {
            return false;
        }
        
        let neighbor = chunk.get_block(pos.0, pos.1, pos.2);
        
        // Air doesn't block
        if neighbor == 0 {
            return false;
        }
        
        // TODO: Handle transparent blocks (glass, water, etc.)
        // For now, any non-air block blocks the face
        true
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::chunk::ChunkPos;
    
    #[test]
    fn test_empty_chunk() {
        let mut mesher = ChunkMesher::new();
        let chunk = Chunk::new(ChunkPos::new(0, 0, 0));
        let mesh = mesher.mesh_chunk(&chunk);
        
        assert_eq!(mesh.vertices.len(), 0);
        assert_eq!(mesh.indices.len(), 0);
    }
    
    #[test]
    fn test_single_block() {
        let mut mesher = ChunkMesher::new();
        let mut chunk = Chunk::new(ChunkPos::new(0, 0, 0));
        chunk.set_block(0, 0, 0, 1); // Stone at origin
        
        let mesh = mesher.mesh_chunk(&chunk);
        
        // Single block should have 6 faces (no neighbors to cull)
        // Each face is a quad (4 vertices, 6 indices)
        assert_eq!(mesh.vertices.len(), 24); // 6 faces * 4 vertices
        assert_eq!(mesh.indices.len(), 36);  // 6 faces * 6 indices
    }
    
    #[test]
    fn test_face_culling() {
        let mut mesher = ChunkMesher::new();
        let mut chunk = Chunk::new(ChunkPos::new(0, 0, 0));
        
        // 2x1x1 blocks (shared face should be culled)
        chunk.set_block(0, 0, 0, 1);
        chunk.set_block(1, 0, 0, 1);
        
        let mesh = mesher.mesh_chunk(&chunk);
        
        // With greedy meshing, adjacent blocks merge into larger quads
        // Top face: 1 merged 2x1 quad = 4 vertices
        // Bottom face: 1 merged 2x1 quad = 4 vertices
        // North face: 1 merged 2x1 quad = 4 vertices
        // South face: 1 merged 2x1 quad = 4 vertices
        // West face: 1 quad = 4 vertices
        // East face: 1 quad = 4 vertices
        // Total: 6 faces * 4 vertices = 24 vertices
        assert_eq!(mesh.vertices.len(), 24);
        assert_eq!(mesh.indices.len(), 36); // 6 faces * 6 indices
    }
    
    #[test]
    fn test_greedy_meshing() {
        let mut mesher = ChunkMesher::new();
        let mut chunk = Chunk::new(ChunkPos::new(0, 0, 0));
        
        // 4x4 flat plane of stone
        for x in 0..4 {
            for z in 0..4 {
                chunk.set_block(x, 0, z, 1);
            }
        }
        
        let mesh = mesher.mesh_chunk(&chunk);
        
        // Without greedy meshing: 16 blocks * 6 faces * 4 vertices = 384 vertices (minus culled)
        // With greedy meshing: Much less (top/bottom should be single 4x4 quads)
        // Sides have some culling too
        
        // Top face: 1 merged quad (4 vertices)
        // Bottom face: 1 merged quad (4 vertices)  
        // Sides: 4 edges * 4 blocks high * 4 vertices = 64 vertices
        // Total should be much less than naive approach
        
        println!("Greedy mesh vertices: {}", mesh.vertices.len());
        assert!(mesh.vertices.len() < 200); // Should be significantly less than naive
    }
}
