// Chunk meshing and management
// Multi-threaded chunk building with greedy meshing

pub mod mesher;
pub mod buffer;



#[derive(Debug, Clone, Copy)]
pub struct ChunkPos {
    pub x: i32,
    pub y: i32,
    pub z: i32,
}

impl ChunkPos {
    pub fn new(x: i32, y: i32, z: i32) -> Self {
        Self { x, y, z }
    }
    
    pub fn distance_sq(&self, other: &ChunkPos) -> f32 {
        let dx = (self.x - other.x) as f32;
        let dy = (self.y - other.y) as f32;
        let dz = (self.z - other.z) as f32;
        dx * dx + dy * dy + dz * dz
    }
}

#[derive(Debug)]
pub struct Chunk {
    pub pos: ChunkPos,
    pub blocks: Box<[u16; 16 * 16 * 16]>,
    pub dirty: bool,
}

impl Chunk {
    pub fn new(pos: ChunkPos) -> Self {
        Self {
            pos,
            blocks: Box::new([0; 16 * 16 * 16]),
            dirty: true,
        }
    }
    
    pub fn get_block(&self, x: usize, y: usize, z: usize) -> u16 {
        if x >= 16 || y >= 16 || z >= 16 {
            return 0;
        }
        self.blocks[y * 256 + z * 16 + x]
    }
    
    pub fn set_block(&mut self, x: usize, y: usize, z: usize, block: u16) {
        if x >= 16 || y >= 16 || z >= 16 {
            return;
        }
        self.blocks[y * 256 + z * 16 + x] = block;
        self.dirty = true;
    }
}
