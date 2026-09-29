package com.phoenix.game.world.blocks.power;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Tmp;
import com.phoenix.game.entities.type.Player;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;

/**
 * 照明灯。参照 Mindustry mindustry.world.blocks.power.LightBlock 移植。
 * <p>耗电的点光源：通电时在 {@code -top} 图层叠加一层加色混合的灯罩，颜色可由玩家配置，
 * 并沿用"上一次选过的颜色"（新放置的灯自动用同一颜色）。
 * <p>与本项目的差异（未消化的以 {@code //TODO} 标注）：
 * <ul>
 *     <li>原版 {@code Draw.blend(Blending.additive)}：映射表未覆盖 arc 的 {@code Blending}，
 *         这里用 libgdx 的 {@code batch.setBlendFunction(GL_SRC_ALPHA, GL_ONE)} 等价实现（画完恢复默认混合）；</li>
 *     <li>{@code drawLight(Tile)} 往 {@code renderer.lights} 里加光源 —— <b>已接线（S5a 批次）</b>：
 *         本项目现已移植灯光管线（{@code graphics/LightRenderer}），见 {@link #drawLight(Tile)}；</li>
 *     <li>配置界面已接线 原版 {@code table.addImageButton(Icon.pencil)} → {@code ui.picker}（取色盘）：
 *         {@code Vars.ui.picker} 与 {@link com.phoenix.game.ui.dialogs.ColorPicker} 均已存在，
 *         点击铅笔按钮即打开取色盘（见 {@link #buildConfiguration}）。</li>
 * </ul>
 */
public class LightBlock extends Block{
    private static int lastColor = 0;

    public float brightness = 0.9f;
    public float radius = 200f;
    public int topRegion;

    public LightBlock(String name){
        super(name);
        hasPower = true;
        update = true;
        topRegion = reg("-top");
        configurable = true;
        entityType = LightEntity::new;
    }

    @Override
    public void playerPlaced(Tile tile){
        if(lastColor != 0){
            tile.configure(lastColor);
        }
    }

    @Override
    public void draw(Tile tile){
        super.draw(tile);
        LightEntity entity = (LightEntity)tile.entity;

        //⚠ 本项目图集缺 illuminator/illuminator-top 贴图 → reg(topRegion) 为 null，
        //   而 Draw.rect 会取 region.getRegionWidth() → 直接 NPE（每帧崩渲染）。
        //   原版 Core.atlas.find 缺图会回退 error 区域，本项目必须自己判空。
        TextureRegion top = reg(topRegion);
        if(top == null) return;

        //刻意降级（等价实现）：arc 的 Draw.blend(Blending.additive) 未移植；libgdx 下用批次的加色混合代替
        Core.batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        Draw.color(Tmp.c1.set(entity.color), entity.efficiency() * 0.3f);
        Draw.rect(top, centerX(tile), centerY(tile));
        Draw.color();
        Core.batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    @Override
    public void buildConfiguration(Tile tile, Table table){
        //已接线 原版：table.addImageButton(Icon.pencil, () -> ui.picker.show(...)).size(40f)
        //（Icon 与 Vars.ui.picker 都已存在；libgdx 的 Table 没有 addImageButton → 自建 ImageButton）
        LightEntity entity = tile.entity instanceof LightEntity ? (LightEntity)tile.entity : null;
        if(entity == null) return;

        com.badlogic.gdx.scenes.scene2d.ui.ImageButton.ImageButtonStyle style =
            new com.badlogic.gdx.scenes.scene2d.ui.ImageButton.ImageButtonStyle(com.phoenix.game.ui.Styles.emptyi);
        style.imageUp = io.anuke.mindustry.gen.Icon.pencil;
        com.badlogic.gdx.scenes.scene2d.ui.ImageButton button = new com.badlogic.gdx.scenes.scene2d.ui.ImageButton(style);
        button.getImage().setDrawable(io.anuke.mindustry.gen.Icon.pencil);
        button.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener(){
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y){
                if(com.phoenix.game.Vars.ui == null || com.phoenix.game.Vars.ui.picker == null) return;
                //原版把当前色按 0.5 alpha 传进取色盘（alpha 不参与最终渲染，绘制时由 efficiency 决定）。
                //⚠ libgdx 的 Color 没有 arc 的实例方法 a(float) → 直接改公有字段
                Tmp.c1.set(entity.color);
                Tmp.c1.a = 0.5f;
                com.phoenix.game.Vars.ui.picker.show(Tmp.c1, false, res -> {
                    entity.color = Color.rgba8888(res);
                    lastColor = entity.color;
                });
                //对应原版 control.input.frag.config.hideConfig()：本项目把配置目标置空即隐藏面板
                if(com.phoenix.game.Vars.control != null && com.phoenix.game.Vars.control.input != null){
                    com.phoenix.game.Vars.control.input.configTile = null;
                }
            }
        });
        table.add(button).size(com.phoenix.game.core.Scl.scl(40f)).left();
    }

    @Override
    public void configured(Tile tile, Player player, int value){
        if(tile.entity instanceof LightEntity){
            ((LightEntity)tile.entity).color = value;
        }
    }

    /** 把灯作为光源注册给渲染器（对应原版 {@code LightBlock.drawLight}，见原版 LightBlock.java:69-73）。 */
    @Override
    public void drawLight(Tile tile){
        if(!(tile.entity instanceof LightEntity)) return;
        if(com.phoenix.game.Vars.renderer == null) return;

        LightEntity entity = (LightEntity)tile.entity;
        //⚠ 本项目 entity.color 是 int（rgba8888）；arc 的 Color.set(int) 能直接解，libgdx 要用静态的 rgba8888ToColor
        Color.rgba8888ToColor(Tmp.c1, entity.color);
        //原版用 tile.drawx()/drawy()（方块中心）；本项目 worldx()/worldy() 是瓦片角点 → 用 centerX/centerY
        com.phoenix.game.Vars.renderer.lights.add(centerX(tile), centerY(tile), radius, Tmp.c1,
            brightness * entity.efficiency());
    }

    public class LightEntity extends TileEntity{
        //libgdx 的 Color 只有静态 rgba8888(Color)（没有 arc 的实例方法）→ 显式传色
        public int color = Color.rgba8888(Pal.accent);

        @Override
        public int config(){
            return color;
        }

        @Override
        public void write(java.io.DataOutputStream out) throws java.io.IOException{
            super.write(out);
            out.writeInt(color);
        }

        @Override
        public void read(java.io.DataInputStream in, byte revision) throws java.io.IOException{
            super.read(in, revision);
            color = in.readInt();
        }
    }

    //（原版 {@code renderer.lights} 依赖项已由 S5a 批次补上，drawLight 已接线，见上方）
}
