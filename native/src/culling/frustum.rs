// Frustum culling implementation

use glam::{Vec3, Vec4, Mat4};

#[derive(Debug, Clone, Copy)]
pub struct Plane {
    pub normal: Vec3,
    pub distance: f32,
}

impl Plane {
    pub fn new(normal: Vec3, distance: f32) -> Self {
        Self { normal, distance }
    }
    
    pub fn from_vec4(v: Vec4) -> Self {
        let xyz = Vec3::new(v.x, v.y, v.z);
        let len = xyz.length();
        Self {
            normal: xyz / len,
            distance: v.w / len,
        }
    }
    
    pub fn distance_to_point(&self, point: Vec3) -> f32 {
        self.normal.dot(point) + self.distance
    }
}

pub fn extract_frustum_planes(mvp: &Mat4) -> [Plane; 6] {
    let m = mvp.to_cols_array();
    
    [
        Plane::from_vec4(Vec4::new(m[3] + m[0], m[7] + m[4], m[11] + m[8], m[15] + m[12])),  // Left
        Plane::from_vec4(Vec4::new(m[3] - m[0], m[7] - m[4], m[11] - m[8], m[15] - m[12])),  // Right
        Plane::from_vec4(Vec4::new(m[3] + m[1], m[7] + m[5], m[11] + m[9], m[15] + m[13])),  // Bottom
        Plane::from_vec4(Vec4::new(m[3] - m[1], m[7] - m[5], m[11] - m[9], m[15] - m[13])),  // Top
        Plane::from_vec4(Vec4::new(m[3] + m[2], m[7] + m[6], m[11] + m[10], m[15] + m[14])), // Near
        Plane::from_vec4(Vec4::new(m[3] - m[2], m[7] - m[6], m[11] - m[10], m[15] - m[14])), // Far
    ]
}
