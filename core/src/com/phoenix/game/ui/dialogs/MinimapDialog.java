package com.phoenix.game.ui.dialogs;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Scl;
import com.phoenix.game.graphics.MinimapRenderer;
import com.phoenix.game.ui.ElementGestureListener;
import io.anuke.mindustry.gen.Tex;

/**
 * 全屏小地图（滚轮缩放视野、双指捏合缩放）。参照 Mindustry mindustry.ui.dialogs.MinimapDialog。
 *
 * <p><b>注意</b>：126.2 原版里这个对话框其实<b>没有任何地方实例化</b>（{@code UI.java} 里的
 * {@code //public MinimapDialog minimap;} 是注释掉的），全屏小地图由 {@code MinimapFragment} 自己画。
 * 本项目同样已有 {@code MinimapFragment.FullmapActor} 做全屏小地图 —— 本类是照原版补齐，暂不需要接线。
 *
 * <p><b>与本项目的差异</b>：
 * <ul>
 *   <li>arc 的 {@code Table.addRect(DrawRect)} libgdx 没有 → 自建一个 {@link Actor}（{@code grow()} 填满单元格），
 *       在它的 {@code draw(batch, parentAlpha)} 里画小地图（做法与 {@code ui.Minimap} 一致）。
 *       注意 Actor 的坐标是<b>父 Group 局部坐标</b>，直接用自己的 {@code getX()/getY()} 即可。</li>
 *   <li>{@code Core.scene} 本项目没有 → 用 {@link FloatingDialog#scene}（启动器注入的全局 Stage），
 *       {@code Core.app.post(...)} → {@code Gdx.app.postRunnable(...)}。</li>
 *   <li>本项目 {@code MinimapRenderer.getRegion()} 返回 {@link TextureRegion}（原版返回 Drawable），
 *       {@code drawEntities(x, y, w, h)} 的四参重载已存在。</li>
 *   <li>{@code Vars.renderer} 未进入游戏时为 null → 绘制与缩放都做了 null 守卫。</li>
 * </ul>
 */
public class MinimapDialog extends FloatingDialog{

    public MinimapDialog(){
        super("$minimap");
        setFillParent(true);

        shown(this::setup);

        addCloseButton();
        shouldPause = true;
        //原版：titleTable.remove()（全屏小地图没有标题栏；同 TechTreeDialog/ZoneInfoDialog 的处理）
        titleTable.remove();
        onResize(this::setup);
    }

    void setup(){
        //注意：libgdx 的 clear() 会连监听器一起清掉（Group.clear → Actor.clear → clearListeners），
        //所以两个输入监听器必须挂在 clear() 之后 —— 这正是原版的顺序，别"优化"到构造器里去
        cont.clear();

        //原版 cont.table(Tex.pane, t -> t.addRect(...).grow())：libgdx 没有带回调的 table(...) → 自建
        Table frame = new Table();
        frame.setBackground(Tex.pane);

        frame.add(new Actor(){
            @Override
            public void draw(Batch batch, float parentAlpha){
                super.draw(batch, parentAlpha);

                MinimapRenderer minimap = minimap();
                if(minimap == null) return;

                //先把脏像素上传成纹理再取子区域（对应 ui.Minimap 的做法）
                minimap.flush();
                TextureRegion region = minimap.getRegion();
                if(region == null) return;

                Draw.color(Color.WHITE);
                Draw.alpha(parentAlpha);
                Draw.rect(region, getX() + getWidth() / 2f, getY() + getHeight() / 2f, getWidth(), getHeight());

                if(minimap.getTexture() != null){
                    minimap.drawEntities(getX(), getY(), getWidth(), getHeight());
                }
            }
        }).grow();

        //原版 .size(Math.min(...) / Scl.scl(1f))：本项目 Scl.scl(v) 的入参就是 UI 单位，按原式换算
        cont.add(frame).size(Scl.scl(Math.min(Core.graphics.getWidth() / 1.1f, Core.graphics.getHeight() / 1.3f) / Scl.scl(1f)))
            .padTop(Scl.scl(-20f));

        //滚轮缩放（对应原版 cont.addListener(InputListener.scrolled)）
        cont.addListener(new InputListener(){
            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountx, float amounty){
                MinimapRenderer minimap = minimap();
                if(minimap != null) minimap.zoomBy(amounty);
                return true;
            }
        });

        //双指捏合缩放（对应原版 ElementGestureListener；arc 的 KeyCode button 参数在 libgdx 是 int）
        cont.addListener(new ElementGestureListener(){
            float lzoom = -1f;

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button){
                MinimapRenderer minimap = minimap();
                if(minimap != null) lzoom = minimap.getZoom();
            }

            @Override
            public void zoom(InputEvent event, float initialDistance, float distance){
                MinimapRenderer minimap = minimap();
                if(minimap == null) return;
                if(lzoom < 0){
                    lzoom = minimap.getZoom();
                }
                minimap.setZoom(initialDistance / distance * lzoom);
            }
        });

        //原版 Core.app.post(() -> Core.scene.setScrollFocus(cont))
        Gdx.app.postRunnable(() -> {
            if(FloatingDialog.scene != null){
                FloatingDialog.scene.setScrollFocus(cont);
            }
        });
    }

    /** @return 小地图渲染器；未进入游戏（无 Renderer）时返回 null。 */
    private static MinimapRenderer minimap(){
        return Vars.renderer == null ? null : Vars.renderer.minimap;
    }
}
