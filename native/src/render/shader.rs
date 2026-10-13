use gl::types::*;
use std::ffi::CString;
use std::ptr;

pub struct Shader {
    pub program: GLuint,
}

impl Shader {
    pub fn new(vertex_src: &str, fragment_src: &str) -> Result<Self, String> {
        unsafe {
            let vertex_shader = Self::compile_shader(vertex_src, gl::VERTEX_SHADER)?;
            let fragment_shader = Self::compile_shader(fragment_src, gl::FRAGMENT_SHADER)?;
            
            let program = gl::CreateProgram();
            gl::AttachShader(program, vertex_shader);
            gl::AttachShader(program, fragment_shader);
            gl::LinkProgram(program);
            
            let mut success = 0;
            gl::GetProgramiv(program, gl::LINK_STATUS, &mut success);
            
            if success == 0 {
                let mut len = 0;
                gl::GetProgramiv(program, gl::INFO_LOG_LENGTH, &mut len);
                let mut buffer = vec![0u8; len as usize];
                gl::GetProgramInfoLog(program, len, ptr::null_mut(), buffer.as_mut_ptr() as *mut _);
                return Err(String::from_utf8_lossy(&buffer).to_string());
            }
            
            gl::DeleteShader(vertex_shader);
            gl::DeleteShader(fragment_shader);
            
            Ok(Self { program })
        }
    }
    
    unsafe fn compile_shader(src: &str, shader_type: GLenum) -> Result<GLuint, String> {
        let shader = gl::CreateShader(shader_type);
        let c_str = CString::new(src).unwrap();
        gl::ShaderSource(shader, 1, &c_str.as_ptr(), ptr::null());
        gl::CompileShader(shader);
        
        let mut success = 0;
        gl::GetShaderiv(shader, gl::COMPILE_STATUS, &mut success);
        
        if success == 0 {
            let mut len = 0;
            gl::GetShaderiv(shader, gl::INFO_LOG_LENGTH, &mut len);
            let mut buffer = vec![0u8; len as usize];
            gl::GetShaderInfoLog(shader, len, ptr::null_mut(), buffer.as_mut_ptr() as *mut _);
            return Err(String::from_utf8_lossy(&buffer).to_string());
        }
        
        Ok(shader)
    }
    
    pub fn use_program(&self) {
        unsafe {
            gl::UseProgram(self.program);
        }
    }
    
    pub fn set_mat4(&self, name: &str, mat: &glam::Mat4) {
        unsafe {
            let c_name = CString::new(name).unwrap();
            let location = gl::GetUniformLocation(self.program, c_name.as_ptr());
            gl::UniformMatrix4fv(location, 1, gl::FALSE, mat.to_cols_array().as_ptr());
        }
    }
    
    pub fn set_vec3(&self, name: &str, vec: &[f32; 3]) {
        unsafe {
            let c_name = CString::new(name).unwrap();
            let location = gl::GetUniformLocation(self.program, c_name.as_ptr());
            gl::Uniform3fv(location, 1, vec.as_ptr());
        }
    }
    
    pub fn set_int(&self, name: &str, value: i32) {
        unsafe {
            let c_name = CString::new(name).unwrap();
            let location = gl::GetUniformLocation(self.program, c_name.as_ptr());
            gl::Uniform1i(location, value);
        }
    }
}

impl Drop for Shader {
    fn drop(&mut self) {
        unsafe {
            gl::DeleteProgram(self.program);
        }
    }
}

pub fn create_chunk_shader() -> Result<Shader, String> {
    let vertex_src = r#"
        #version 330 core
        
        layout (location = 0) in vec3 aPos;
        layout (location = 1) in vec2 aTexCoord;
        layout (location = 2) in vec4 aColor;
        layout (location = 3) in vec2 aLight;
        layout (location = 4) in vec3 aNormal;
        
        uniform mat4 uProjection;
        uniform mat4 uView;
        uniform mat4 uModel;
        
        out vec2 vTexCoord;
        out vec4 vColor;
        out vec2 vLight;
        out vec3 vNormal;
        out vec3 vFragPos;
        
        void main() {
            vec4 worldPos = uModel * vec4(aPos, 1.0);
            gl_Position = uProjection * uView * worldPos;
            
            vTexCoord = aTexCoord;
            vColor = aColor;
            vLight = aLight / 255.0;
            vNormal = mat3(uModel) * aNormal;
            vFragPos = worldPos.xyz;
        }
    "#;
    
    let fragment_src = r#"
        #version 330 core
        
        in vec2 vTexCoord;
        in vec4 vColor;
        in vec2 vLight;
        in vec3 vNormal;
        in vec3 vFragPos;
        
        uniform sampler2D uTexture;
        uniform vec3 uCameraPos;
        
        out vec4 FragColor;
        
        void main() {
            vec4 texColor = texture(uTexture, vTexCoord);
            
            float blockLight = vLight.x;
            float skyLight = vLight.y;
            float brightness = max(blockLight, skyLight * 0.8);
            
            vec3 lightColor = vec3(brightness);
            
            vec3 norm = normalize(vNormal);
            float ambientOcclusion = 1.0 - (0.3 * (1.0 - abs(norm.y)));
            
            vec3 finalColor = texColor.rgb * vColor.rgb * lightColor * ambientOcclusion;
            
            FragColor = vec4(finalColor, texColor.a * vColor.a);
            
            if (FragColor.a < 0.1) {
                discard;
            }
        }
    "#;
    
    Shader::new(vertex_src, fragment_src)
}
