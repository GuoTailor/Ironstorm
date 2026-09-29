package io.anuke.mindustry;

import com.phoenix.game.math.Mathf;
import com.phoenix.game.math.noise.RidgedPerlin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 构建期贴图生成器（对应原版 Mindustry {@code tools/src/mindustry/tools/Generators.java}）。
 *
 * <p>原版把生成器挂在 {@code ImagePacker.generate(name, runnable)} 里、跑在游戏进程内
 * （那样才能遍历 {@code Vars.content}）；本项目改为**独立的 main**，由 Gradle 任务
 * {@code :spriteGen} 驱动（见根 build.gradle），输出直接落到
 * {@code core/assets-raw/sprites/...}，再由 {@code gradlew texturePacker} 打进图集。
 *
 * <p>⚠ 用 AWT（{@code BufferedImage}/{@code ImageIO}）而不是 libgdx 的 {@code Pixmap}：
 * {@code Pixmap} 依赖 gdx 的**本地库**（Gdx2DPixmap），在没有 {@code Gdx.app} 的纯构建进程里加载不可靠；
 * 本项目的 {@code tools/build.gradle} 本来就用 AWT 处理图片。
 *
 * <p>输出（全部相对仓库根，Gradle 任务的 workingDir 设为仓库根）：
 * <ul>
 *   <li>{@code cracks-<size>-<i>.png} —— 建筑受损裂纹（size 1..5，i 0..7），共 40 张。</li>
 *   <li>{@code floor-<name>-edge.png} —— 见 {@link #generateEdges()}（待实现）。</li>
 * </ul>
 */
public class Generators{

    /** 环境贴图目录（地板 / 矿脉 / 边缘 / 裂纹都放这里，与 edge-stencil.png 同级）。 */
    private static final String ENV_OUT = "core/assets-raw/sprites/blocks/environment/";

    /** 地板方块定义所在文件：用来抓出"哪些地板需要边缘贴图"。 */
    private static final String BLOCKS_JAVA = "core/src/com/phoenix/game/content/Blocks.java";

    /**
     * 不生成边缘贴图的地板：原版里这些地板的 {@code blendGroup != this}（融合进另一组），
     * 生成器会跳过，运行时它们的 {@code edges()} 取 {@code blendGroup} 那一套
     * （见 {@code content/Blocks} 里的 {@code blendGroup} 赋值）。
     */
    private static final java.util.Set<String> EDGE_EXCLUDE = new java.util.HashSet<>(
        java.util.Arrays.asList("char", "craters", "hotrock", "magmarock"));

    public static void main(String[] args) throws Exception{
        generateCracks();
        generateEdges();
        System.out.println("DBG SPRITEGEN done");
    }

    /**
     * 生成建筑受损裂纹贴图（对应原版 {@code Generators.generate()} 里的 {@code ImagePacker.generate("cracks", ...)}）。
     *
     * <p>算法照抄原版：
     * <ol>
     *   <li>用 {@code RidgedPerlin(1, 3)} 噪声 + 到中心的距离衰减，在 {@code size*32} 见方的画布上点白像素
     *       （{@code dst < 1.2 && noise - dst*(1-fract) > 0.16}）；</li>
     *   <li>过一个 6x6（{@code rad=3}，{@code cx/cy < rad}）的中值滤波去掉噪点；</li>
     *   <li>存成 {@code cracks-<size>-<i>}，{@code fract = i/steps} 让裂纹随 i 增大而变密。</li>
     * </ol>
     * 与运行时 {@code Block.crackRegions = 8, maxCrackSize = 5} 对应。
     */
    private static void generateCracks() throws Exception{
        final int crackRegions = 8, maxCrackSize = 5;
        RidgedPerlin r = new RidgedPerlin(1, 3);

        for(int size = 1; size <= maxCrackSize; size++){
            int dim = size * 32;
            int steps = crackRegions;

            for(int i = 0; i < steps; i++){
                float fract = i / (float)steps;

                BufferedImage image = new BufferedImage(dim, dim, BufferedImage.TYPE_INT_ARGB);
                for(int x = 0; x < dim; x++){
                    for(int y = 0; y < dim; y++){
                        float dst = Mathf.dst((float)x / dim, (float)y / dim, 0.5f, 0.5f) * 2f;
                        if(dst < 1.2f && r.getValue(x, y, 1f / 40f) - dst * (1f - fract) > 0.16f){
                            image.setRGB(x, y, 0xffffffff);
                        }
                    }
                }

                BufferedImage output = new BufferedImage(dim, dim, BufferedImage.TYPE_INT_ARGB);
                int rad = 3;

                //median filter
                for(int x = 0; x < dim; x++){
                    for(int y = 0; y < dim; y++){
                        int whites = 0, clears = 0;
                        for(int cx = -rad; cx < rad; cx++){
                            for(int cy = -rad; cy < rad; cy++){
                                int wx = Mathf.clamp(cx + x, 0, dim - 1), wy = Mathf.clamp(cy + y, 0, dim - 1);
                                if(((image.getRGB(wx, wy) >>> 24) & 0xff) > 127){
                                    whites++;
                                }else{
                                    clears++;
                                }
                            }
                        }
                        output.setRGB(x, y, whites >= clears ? 0xffffffff : 0x00000000);
                    }
                }

                File file = new File(ENV_OUT + "cracks-" + size + "-" + i + ".png");
                file.getParentFile().mkdirs();
                ImageIO.write(output, "png", file);
            }
        }
        System.out.println("DBG SPRITEGEN cracks=" + (maxCrackSize * crackRegions));
    }

    /**
     * 生成地板边缘过渡贴图（对应原版 {@code Generators.generate()} 里的 {@code ImagePacker.generate("edges", ...)}）。
     *
     * <p>做法：把 {@code edge-stencil.png}（96x96 = 3x3 个 32px 小图，是边缘形状的遮罩）与地板贴图
     * **逐像素相乘**（地板贴图按 32px 平铺填满 96x96），得到 {@code <name>-edge}。
     * 运行时 {@code Floor.load()} 把它按 32px 切成 3x3，按邻接方向取对应那一格。
     *
     * <p>⚠ 原版靠 {@code content.blocks()} 遍历地板；本项目生成器是独立 main、拿不到 content
     * → 改为**从 {@code content/Blocks.java} 正则抓 {@code floor("...")} 的名字**
     * （overlay/矿脉地板用的是 {@code new OverlayFloor/OreFloor(...)}，天然不会被抓到），
     * 再排除 {@link #EDGE_EXCLUDE}。
     */
    private static void generateEdges() throws Exception{
        BufferedImage stencil = ImageIO.read(new File(ENV_OUT + "edge-stencil.png"));
        if(stencil == null) throw new IllegalStateException("missing edge-stencil.png");

        java.util.List<String> floors = new java.util.ArrayList<>();
        String src = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(BLOCKS_JAVA)),
            java.nio.charset.StandardCharsets.UTF_8);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("floor\\(\"([a-z0-9-]+)\"").matcher(src);
        while(matcher.find()){
            String name = matcher.group(1);
            if(!floors.contains(name)) floors.add(name);
        }

        int made = 0;
        for(String name : floors){
            if(EDGE_EXCLUDE.contains(name)) continue;

            File base = new File(ENV_OUT + name + ".png");
            if(!base.exists()) base = new File(ENV_OUT + name + "1.png");
            if(!base.exists()){
                System.out.println("DBG SPRITEGEN edges skip (no texture): " + name);
                continue;
            }

            BufferedImage image = ImageIO.read(base);
            BufferedImage result = new BufferedImage(stencil.getWidth(), stencil.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
            for(int x = 0; x < stencil.getWidth(); x++){
                for(int y = 0; y < stencil.getHeight(); y++){
                    result.setRGB(x, y, mul(stencil.getRGB(x, y),
                        image.getRGB(x % image.getWidth(), y % image.getHeight())));
                }
            }
            ImageIO.write(result, "png", new File(ENV_OUT + name + "-edge.png"));
            made++;
        }
        System.out.println("DBG SPRITEGEN edges=" + made + " floors=" + floors.size());
    }

    /** 逐通道相乘（对应 arc 的 {@code Color.mul(Color)}，含 alpha；AWT 与 arc 都是直通 alpha）。 */
    private static int mul(int a, int b){
        int ar = (a >>> 16) & 0xff, ag = (a >>> 8) & 0xff, ab = a & 0xff, aa = (a >>> 24) & 0xff;
        int br = (b >>> 16) & 0xff, bg = (b >>> 8) & 0xff, bb = b & 0xff, ba = (b >>> 24) & 0xff;
        int r = ar * br / 255, g = ag * bg / 255, bl = ab * bb / 255, al = aa * ba / 255;
        return (al << 24) | (r << 16) | (g << 8) | bl;
    }
}
