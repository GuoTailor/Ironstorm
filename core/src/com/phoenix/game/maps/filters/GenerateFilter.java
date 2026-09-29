package com.phoenix.game.maps.filters;

import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.math.Simplex;
import com.phoenix.game.math.noise.RidgedPerlin;
import com.phoenix.game.util.Pack;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Floor;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.meta.I18n;

/**
 * 地图生成过滤器基类。参照 Mindustry {@code mindustry.maps.filters.GenerateFilter} 移植。
 *
 * <p>每个滤镜在「一个瓦片坐标」上被调用：{@link GenerateInput} 带着该坐标的
 * 输入地板/方块/矿脉，滤镜就地改写它们（输出即改写后的值）。
 *
 * <p><b>与原版的差异（逐条）</b>
 * <ul>
 *   <li>{@link #name()} 原版走 {@code Core.bundle.get(...)}；本项目 bundle 是裁剪过的、缺键返回
 *       {@code ???key???}，所以刻意降级为走 {@link I18n#get} 的双形态判定。</li>
 *   <li>{@link #rnoise} 原版用 {@code arc.util.noise.RidgedPerlin}；本项目已有等价实现
 *       {@link RidgedPerlin}（照抄 arc 算法、仅改包名）→ 已按原版接线。</li>
 *   <li>{@code GenerateInput.noise} 原版是无参 {@code new Simplex()}；本项目 {@link Simplex} 只有带种子
 *       构造器 → {@code new Simplex(0)}，随后由 {@link GenerateInput#begin} 重设种子。</li>
 *   <li>{@code GenerateInput.pnoise} 是 {@link RidgedPerlin}（对应原版同名字段），在 begin 里重设种子。</li>
 *   <li>{@code draw(Image)} 的参数是 libgdx 的 {@code com.badlogic.gdx.scenes.scene2d.ui.Image}（arc 同名）。</li>
 * </ul>
 */
public abstract class GenerateFilter{
    protected transient float o = (float)(Math.random() * 10000000.0);
    protected transient long seed;
    protected transient GenerateInput in;

    public transient boolean buffered = false;
    public transient FilterOption[] options;

    public final void apply(GenerateInput in){
        this.in = in;
        apply();
        //remove extra ores on liquids
        if(((Floor)in.floor).isLiquid){
            in.ore = Blocks.air;
        }
    }

    /** sets up the options; this is necessary since the constructor can't access subclass variables. */
    protected void options(FilterOption... options){
        this.options = options;
    }

    /** apply the actual filter on the input */
    protected abstract void apply();

    /** draw any additional guides */
    public void draw(Image image){}

    /** localized display name */
    public String name(){
        //原版 = Core.bundle.get("filter." + simpleName.toLowerCase().replace("filter", ""), fallback)；
        //刻意降级：本项目 bundle 缺键返回 ???key??? → 用 I18n.get 的双形态判定
        return I18n.get("filter." + getClass().getSimpleName().toLowerCase().replace("filter", ""), getClass().getSimpleName().replace("Filter", ""));
    }

    /** set the seed to a random number */
    public void randomize(){
        seed = Mathf.random(99999999);
    }

    //utility generation functions

    protected float noise(float x, float y, float scl, float mag){
        return in.noise.octaveNoise2D(1, 0f, 1f / scl, x + o, y + o) * mag;
    }

    protected float noise(float x, float y, float scl, float mag, float octaves, float persistence){
        return in.noise.octaveNoise2D((int)octaves, persistence, 1f / scl, x + o, y + o) * mag;
    }

    protected float rnoise(float x, float y, float scl, float mag){
        //原版 GenerateFilter.java:61 = in.pnoise.getValue((int)(x + o), (int)(y + o), 1f / scl) * mag
        return in.pnoise.getValue((int)(x + o), (int)(y + o), 1f / scl) * mag;
    }

    protected float chance(){
        return Mathf.randomSeed(Pack.longInt(in.x, in.y + (int)seed));
    }

    /** an input for generating at a certain coordinate. should only be instantiated once. */
    public static class GenerateInput{

        /** input size parameters */
        public int x, y, width, height;

        /** output parameters */
        public Block floor, block, ore;

        Simplex noise = new Simplex(0);
        //原版 GenerateFilter.java:78 = RidgedPerlin pnoise = new RidgedPerlin(0, 1)
        RidgedPerlin pnoise = new RidgedPerlin(0, 1);
        TileProvider buffer;

        public void apply(int x, int y, Block floor, Block block, Block ore){
            this.floor = floor;
            this.block = block;
            this.ore = ore;
            this.x = x;
            this.y = y;
        }

        public void begin(GenerateFilter filter, int width, int height, TileProvider buffer){
            this.buffer = buffer;
            this.width = width;
            this.height = height;
            noise.setSeed((int)filter.seed);
            //原版 GenerateFilter.java:94 = pnoise.setSeed((int)(filter.seed + 1))
            pnoise.setSeed((int)(filter.seed + 1));
        }

        Tile tile(float x, float y){
            return buffer.get(Mathf.clamp((int)x, 0, width - 1), Mathf.clamp((int)y, 0, height - 1));
        }

        public interface TileProvider{
            Tile get(int x, int y);
        }
    }
}
