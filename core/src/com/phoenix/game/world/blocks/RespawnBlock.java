package com.phoenix.game.world.blocks;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Fill;
import com.phoenix.game.core.Lines;
import com.phoenix.game.entities.type.Player;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.graphics.Shaders;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.Mech;
import com.phoenix.game.ui.Cicon;
import com.phoenix.game.world.Tile;

/**
 * 复活点绘制工具。参照 Mindustry mindustry.world.blocks.RespawnBlock 移植。
 *
 * <p><b>注意：原版这个类**不是方块**</b>（没有继承 Block、没有方块名、也没有在 {@code Blocks} 里注册），
 * 它只是 {@code CoreBlock}/{@code MechPad} 复用的一个静态绘制方法：
 * 画复活/换机甲时的"四角光环 + 进度条 + 机甲剪影"。
 * 所以本批**没有**把它注册进 {@code Blocks}。
 *
 * <p>与原版的差异（每处都有 {@code //TODO} 标注）：
 * <ul>
 *     <li>{@code Shaders.build}（原版用 arc 着色器做"按进度径向显现机甲剪影"）：已接线（S7a）
 *         —— 本项目新增了 {@code Shaders.build}（{@code UnitBuild}）+ {@code applyUniforms()}。</li>
 *     <li>{@code Lines.getStroke()}：本项目 {@code Lines} 没有该方法，改为用局部变量记录当前线宽。</li>
 *     <li>{@code CapStyle.none}（原版线帽参数）：本项目 {@code Lines.line} 没有线帽参数，直接调 4 参版本。</li>
 *     <li>{@code Draw.rect("circle-shadow", ...)}（arc 的按名取贴图重载）：本项目 {@code Draw} 只有
 *         {@code TextureRegion} 版本，改为 {@code Core.atlas.findRegion("circle-shadow")} + null 守卫。</li>
 *     <li>{@code tile.block().drawPlaceText(...)}（联机时把玩家名画在复活点上）：{@code Block.drawPlaceText}
 *         本项目**已存在**（{@code Block.java:525}），已按原版接线；但前提 {@code net.active()} 不成立
 *         （{@code Vars.net} 恒 null，联机链路未接），故当前不会执行。</li>
 *     <li>{@code net.active()} → {@code Vars.net != null && Vars.net.active()}（本项目 {@code Vars.net} 可能为 null）。</li>
 * </ul>
 */
public class RespawnBlock{

    public static void drawRespawn(Tile tile, float heat, float progress, float time, Player player, Mech to){
        progress = Mathf.clamp(progress);

        float x = tile.block().centerX(tile), y = tile.block().centerY(tile);

        Draw.color(Pal.darkMetal);
        Lines.stroke(2f * heat);
        Fill.poly(x, y, 4, 10f * heat);

        //本项目 Draw 没有 reset()，用 color() 复位颜色（等价）
        Draw.color();
        if(player != null){
            TextureRegion region = to == null ? null : to.icon(Cicon.full);

            if(region != null){
                Draw.color(0f, 0f, 0f, 0.4f * progress);
                TextureRegion shadow = Core.atlas == null ? null : Core.atlas.findRegion("circle-shadow");
                if(shadow != null){
                    Draw.rect(shadow, x, y, region.getRegionWidth() / 3f, region.getRegionWidth() / 3f);
                }
                Draw.color();

                //原版 RespawnBlock.java:30-35：用 Shaders.build 按进度径向显现机甲剪影
                //（原版只设 region/progress/color/time，没有额外设 color.a —— Pal.accent 自带 alpha=1）
                Shaders.init();
                if(Shaders.build != null){
                    Shaders.build.region = region;
                    Shaders.build.progress = progress;
                    Shaders.build.color.set(Pal.accent);
                    Shaders.build.time = -time / 10f;

                    Draw.shader(Shaders.build);
                    //⚠ libgdx 没有 arc 的 Shader.apply() 钩子 → 显式灌 uniform（顺序：先 bind 再 setUniformf）
                    Shaders.build.applyUniforms();
                    Draw.rect(region, x, y);
                    Draw.shader();
                }

                Draw.color(Pal.accentBack);
                float pos = Mathf.sin(time, 6f, 8f);
                Lines.lineAngleCenter(x + pos, y, 90, 16f - Math.abs(pos) * 2f);
                Draw.color();
            }
        }

        Lines.stroke(2f * heat);

        Draw.color(Pal.accentBack);
        Lines.poly(x, y, 4, 8f * heat);

        float oy = -7f, len = 6f * heat;

        //线宽在下面几段里变化，本项目 Lines 没有 getStroke()，用局部变量记
        float stroke = 5f;
        Lines.stroke(stroke);
        Draw.color(Pal.darkMetal);
        Lines.line(x - len, y + oy, x + len, y + oy);
        for(int i : Mathf.signs){
            Fill.tri(x + len * i, y + oy - stroke / 2f, x + len * i, y + oy + stroke / 2f,
                x + (len + stroke * heat) * i, y + oy);
        }

        stroke = 3f;
        Lines.stroke(stroke);
        Draw.color(Pal.accent);
        Lines.line(x - len, y + oy, x - len + len * 2 * progress, y + oy);
        for(int i : Mathf.signs){
            Fill.tri(x + len * i, y + oy - stroke / 2f, x + len * i, y + oy + stroke / 2f,
                x + (len + stroke * heat) * i, y + oy);
        }
        Draw.color();

        //对应原版结尾：联机时把玩家名画在复活点上（Block.drawPlaceText 本项目已存在）。
        //⚠ 前提 Vars.net.active() 当前恒不成立（Vars.net 全工程从未赋值 → 恒 null），故这段不会执行；
        //   drawRespawn 本身也暂无调用方（MechPad 的换装绘制尚未接过来）
        if(Vars.net != null && Vars.net.active() && player != null){
            tile.block().drawPlaceText(player.name, tile.x, tile.y - Math.max((tile.block().size - 1) / 2, 0), true);
        }
    }
}
