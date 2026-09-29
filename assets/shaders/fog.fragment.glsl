//对应原版 Mindustry core/assets/shaders/fog.fragment.glsl（逐字照搬）
//作用：把一张"白=无遮挡 / 暗=有遮挡"的缓冲转成"暗处不透明的黑色"叠加层。
//      方块投影（shadows）与地图变暗（fog）两张缓冲都用它。
#ifdef GL_ES
precision mediump float;
precision mediump int;
#endif

uniform sampler2D u_texture;

varying vec4 v_color;
varying vec2 v_texCoord;

void main(){
    vec4 color = texture2D(u_texture, v_texCoord.xy);
    gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0 - color.r);
}
