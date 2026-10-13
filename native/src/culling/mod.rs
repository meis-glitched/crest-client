// Frustum and occlusion culling

pub mod frustum;

use glam::{Vec3, Mat4};

pub struct Frustum {
    pub planes: [Vec3; 6],
}

impl Frustum {
    pub fn from_matrix(_mvp: Mat4) -> Self {
        // TODO: Extract frustum planes from view-projection matrix
        Self {
            planes: [Vec3::ZERO; 6],
        }
    }
    
    pub fn contains_aabb(&self, _min: Vec3, _max: Vec3) -> bool {
        // TODO: AABB vs frustum test
        true
    }
}
