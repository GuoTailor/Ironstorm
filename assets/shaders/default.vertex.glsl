//对应原版 Mindustry core/assets/shaders/default.vertex.glsl（逐字照搬）
//SpriteBatch 自定义 shader 必须提供 u_projTrans / a_position / a_texCoord0 / a_color 这四个名字。
uniform mat4 u_projTrans;

attribute vec4 a_position;
attribute vec2 a_texCoord0;
attribute vec4 a_color;

varying vec4 v_color;
varying vec2 v_texCoord;

uniform vec2 u_viewportInverse;

void main() {
    gl_Position = u_projTrans * a_position;
    v_texCoord = a_texCoord0;
    v_color = a_color;
}
