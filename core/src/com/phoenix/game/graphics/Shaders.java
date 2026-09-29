package com.phoenix.game.graphics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Scl;
import com.phoenix.game.core.Time;

/**
 * 自定义着色器集合。参照 Mindustry {@code mindustry.graphics.Shaders} 移植。
 *
 * <p>本项目目前用到 {@link #fog}（方块投影与地图变暗共用的"把暗处转成不透明黑"的叠加 shader，
 * 见 {@link BlockRenderer}）与 {@link #light}（灯光叠加层，见 {@link LightRenderer}）。
 * 原版还有 shadow / blockbuild / shield / unitbuild / menu / water / tar 等，
 * 其中 {@code light}（S5a）、{@code build}（S7a）、{@code shield}（N2c2）已补；其余待补。
 *
 * <p><b>与 libgdx 的差异</b>：原版 {@code Shaders.init()} 在 {@code Renderer} 构造器里调用，且每个
 * shader 走 arc 的 {@code LoadShader}（支持 {@code #include} 拼接 + 从 {@code arc.files} 读）。
 * 本项目各 shader 都没有 {@code #include}，直接用 libgdx 的 {@code ShaderProgram(FileHandle, FileHandle)}；
 * 并且**不能在构造期建**（{@code Renderer} 是 {@code ClientLauncher} 的字段，其构造早于 GL 上下文就绪）
 * → 改成第一次真正要画时懒初始化（{@link #init()} 幂等）。
 */
public class Shaders{
    /** 方块投影 / 地图变暗用的叠加 shader（对应原版 {@code Shaders.fog}）。 */
    public static FogShader fog;

    /** 灯光叠加 shader（对应原版 {@code Shaders.light}）。 */
    public static LightShader light;

    /** 单位建造剪影 shader（对应原版 {@code Shaders.build}，即 {@code UnitBuild}）。 */
    public static UnitBuild build;

    /**
     * 护盾叠加 shader（对应原版 {@code Shaders.shield}，即 {@code Shield}）。
     * <p>⚠ 原版允许它为 {@code null}（{@code Shaders.init} 里 try/catch，加载失败就置空，
     * 调用方都判空；{@code SettingsMenuDialog} 也据此决定要不要注册 {@code animatedshields} 设置项）
     * → 本项目照抄该语义，本字段**可能为 null**。
     */
    public static ShieldShader shield;

    /**
     * 护盾 shader 是否已经尝试创建过（成功建出 → {@link #shield} 非空；创建失败 → 仍为 null）。
     * <p>用途：原版只在 {@code Shaders.init()} 里 try 一次；本项目的 init 是懒调用 + 幂等重入，
     * 没有这个标记就会"每帧重试一次失败的程序"（ShaderProgram 编译失败会打一堆日志）。
     */
    private static boolean shieldInitialized;

    /** 懒初始化（幂等）。必须在 GL 上下文就绪之后调用。 */
    public static void init(){
        //幂等：已经建过的不重建；任何一个为 null（例如只走过旧的 fog 路径）都能补建
        if(fog != null && light != null && build != null && shieldInitialized) return;
        if(fog == null) fog = new FogShader();
        if(light == null) light = new LightShader();
        if(build == null) build = new UnitBuild();
        //对应原版 Shaders.init 里 shield 的 try/catch（原版注释：don't load shield shader）
        if(!shieldInitialized){
            try{
                shield = new ShieldShader();
            }catch(Throwable t){
                shield = null;
                t.printStackTrace();
            }
            shieldInitialized = true;
        }
    }

    /**
     * 对应原版 {@code Shaders.FogShader}（{@code super("fog", "default")}）。
     * <p>没有自定义 uniform —— 顶点/纹理坐标由 {@code SpriteBatch} 自动喂。
     */
    public static class FogShader extends ShaderProgram{
        public FogShader(){
            super(Gdx.files.internal("shaders/default.vertex.glsl"),
                Gdx.files.internal("shaders/fog.fragment.glsl"));
        }
    }

    /**
     * 对应原版 {@code Shaders.LightShader}（{@code super("light", "default")} + {@code ambient} uniform）。
     * <p>⚠ 原版靠 arc 的 {@code LoadShader.apply()} 钩子在每次 {@code Draw.shader(...)} 时自动灌 uniform；
     * libgdx 的 {@code ShaderProgram} 没有该钩子 → 改由调用方（{@link LightRenderer#draw()}）显式调
     * {@link #applyUniforms()}。
     */
    public static class LightShader extends ShaderProgram{
        /** 环境光（对应原版 {@code LightShader.ambient}，默认值与 {@code Rules.ambientLight} 同源）。 */
        public final com.badlogic.gdx.graphics.Color ambient = new com.badlogic.gdx.graphics.Color(0.01f, 0.01f, 0.04f, 0.99f);

        public LightShader(){
            super(Gdx.files.internal("shaders/default.vertex.glsl"),
                Gdx.files.internal("shaders/light.fragment.glsl"));
        }

        /**
         * 灌 {@code u_ambient} uniform。
         * <p>⚠ 调用前该 shader 必须已被绑定（libgdx 的 {@code setUniformf} 要求 program 处于 bound 状态）。
         * 本项目里 {@code Draw.shader(Shaders.light)} → {@code SpriteBatch.setShader(...)} 在批次绘制中会
         * 先 flush 再 {@code bind()}，所以紧跟其后调用本方法即可。
         */
        public void applyUniforms(){
            setUniformf("u_ambient", ambient);
        }
    }

    /**
     * 单位建造剪影 shader（对应原版 {@code Shaders.build}，即 {@code UnitBuild}）。
     * <p>按 {@code progress} 径向显现单位/机甲的剪影，并叠一条 accent 扫描线。
     * <p>⚠ 同 {@link LightShader}：libgdx 没有 arc 的 {@code Shader.apply()} 钩子 → 由绘制方在
     * {@code Draw.shader(this)} 之后显式调 {@link #applyUniforms()}。
     */
    public static class UnitBuild extends ShaderProgram{
        public float progress, time;
        public com.badlogic.gdx.graphics.Color color = new com.badlogic.gdx.graphics.Color();
        public com.badlogic.gdx.graphics.g2d.TextureRegion region;

        public UnitBuild(){
            super(Gdx.files.internal("shaders/default.vertex.glsl"),
                Gdx.files.internal("shaders/unitbuild.fragment.glsl"));
        }

        /** 灌 uniform（对应原版 {@code UnitBuild.apply()}）。调用前该 shader 必须已被绑定。 */
        public void applyUniforms(){
            setUniformf("u_time", time);
            setUniformf("u_color", color);
            setUniformf("u_progress", progress);
            setUniformf("u_uv", region.getU(), region.getV());
            setUniformf("u_uv2", region.getU2(), region.getV2());
            setUniformf("u_texsize", region.getTexture().getWidth(), region.getTexture().getHeight());
        }
    }

    /**
     * 护盾叠加 shader（对应原版 {@code Shaders.Shield}，{@code super("shield", "default")}）。
     * <p>把 {@code Renderer} 的护盾缓冲（一批半透明六边形填充图）变成"六边形描边 + 内部缓慢流动的条纹"
     * 叠加回屏幕（原版调用点：{@code Renderer.java:297-301}）。
     * <p>⚠ 同 {@link LightShader}/{@link UnitBuild}：libgdx 没有 arc 的 {@code Shader.apply()} 钩子
     * → 由绘制方在 {@code Draw.shader(this)} 之后显式调 {@link #applyUniforms}。
     * <p>⚠ {@code u_texsize} <b>不是</b> libgdx 自动喂的：读 1.14.2 {@code SpriteBatch.setupMatrices()}
     * 确认它只设 {@code u_projTrans} 与 {@code u_texture} → 必须手动灌（同 {@code UnitBuild}）。
     */
    public static class ShieldShader extends ShaderProgram{
        public ShieldShader(){
            super(Gdx.files.internal("shaders/default.vertex.glsl"),
                Gdx.files.internal("shaders/shield.fragment.glsl"));
        }

        /**
         * 灌 uniform（对应原版 {@code Shield.apply()}）。
         * <p>⚠ 两个尺寸参数传的是**视口的世界尺寸**（原版写的 {@code Core.camera.width/height}），
         * 不是缓冲的像素尺寸 —— shader 里 {@code coords = T * u_texsize + u_offset} 得到的正是世界坐标。
         * <p>⚠ {@code u_dp} 原版传的是 {@code Scl.scl(1f)}（<b>不是</b> {@code Draw.scl}：后者在本项目
         * = 1/scale_marker = 0.25，是"贴图像素缩放"，传它会把条纹图案加密 16 倍）。
         * <p>调用前该 shader 必须已被绑定（{@code Draw.shader(Shaders.shield)} 之后）。
         */
        public void applyUniforms(float viewWidth, float viewHeight){
            setUniformf("u_dp", Scl.scl(1f));
            setUniformf("u_time", Time.time() / Scl.scl(1f));
            setUniformf("u_offset", Core.camera.position.x - viewWidth / 2f,
                Core.camera.position.y - viewHeight / 2f);
            setUniformf("u_texsize", viewWidth, viewHeight);
        }
    }
}
