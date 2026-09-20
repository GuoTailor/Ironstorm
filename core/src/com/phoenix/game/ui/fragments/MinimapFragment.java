package com.phoenix.game.ui.fragments;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Scl;
import com.phoenix.game.graphics.MinimapRenderer;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.ui.Styles;
import io.anuke.mindustry.gen.Tex;

/**
 * 全屏小地图。参照 Mindustry mindustry.ui.fragments.MinimapFragment 移植：
 * 整张地图铺满屏幕，滚轮缩放（0.25~10 倍）、左键拖拽平移，Esc 或「返回」按钮关闭
 * （M 键开关在 {@code DesktopInput} 里，对应原版 {@code Binding.minimap}）。
 * <p>HUD 右上角那块在 {@link com.phoenix.game.ui.Minimap}。
 */
public class MinimapFragment extends Fragment{
    private boolean shown;
    private float panx, pany, zoom = 1f;
    /** 每格基础像素数（对应原版 MinimapFragment.baseSize）。 */
    private float baseSize = Scl.scl(5f);
    /** 全屏地图本体。 */
    private FullmapActor elem;
    /** 标题 + 返回按钮层。 */
    private Table ui;
    /** 上一次拖拽的坐标（libgdx 的 touchDragged 给的是绝对坐标，原版 arc 的 pan 直接给增量）。 */
    private float lastX, lastY;

    @Override
    public void build(Group parent){
        elem = new FullmapActor();
        elem.setVisible(false);
        elem.setTouchable(Touchable.enabled);
        parent.addActor(elem);

        //标题 + 返回按钮（对应原版 parent.fill(t -> { t.add("$minimap"); ... t.addImageTextButton("$back", ...); })）
        ui = new Table();
        ui.setFillParent(true);
        ui.setVisible(false);
        ui.top().pad(Scl.scl(10f));
        ui.add(new Label(Core.bundle.get("minimap"), Styles.outlineLabel));
        ui.row();
        ui.add().growY();
        ui.row();

        Button back = new Button(Styles.defaultb);
        back.add(new Label(Core.bundle.get("back"), Styles.defaultLabel)).pad(Scl.scl(5f));
        back.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                shown = false;
            }
        });
        ui.add(back).size(Scl.scl(220f), Scl.scl(60f)).pad(Scl.scl(10f));
        parent.addActor(ui);

        //elem.update(...)：每帧同步尺寸/可见性、抢滚轮焦点、Esc 关闭
        parent.addActor(new MenuFragment.Act(this::act));
    }

    /** @return 全屏小地图是否打开（DesktopInput 据此让出输入）。 */
    public boolean shown(){
        return shown;
    }

    public void toggle(){
        shown = !shown;
    }

    private void act(){
        if(elem == null) return;

        //对应原版 elem.setFillParent(true) + setBounds(0, 0, graphics.getWidth(), graphics.getHeight())
        elem.setBounds(0f, 0f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        //对应原版 elem.visible(() -> shown) / t.visible(() -> shown)
        elem.setVisible(shown);
        ui.setVisible(shown);

        Stage stage = elem.getStage();
        if(stage == null) return;

        if(shown){
            //对应原版 elem.requestKeyboard() / elem.requestScroll()。
            //libgdx 的 Stage.scrolled 只发给 scrollFocus（不做 hit 判定），不设的话滚轮会漏给世界相机
            stage.setScrollFocus(elem);
            if(Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)){
                shown = false;
            }
        }else if(stage.getScrollFocus() == elem){
            stage.setScrollFocus(null);
        }
    }

    /** @return 小地图渲染器；未进入游戏（无 Renderer）时返回 null。 */
    private static MinimapRenderer minimap(){
        return Vars.renderer == null ? null : Vars.renderer.minimap;
    }

    /** 全屏地图本体：黑底 + 整张地图 + 单位标记。 */
    private class FullmapActor extends Actor{
        public FullmapActor(){
            addListener(new InputListener(){
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, int button){
                    lastX = x;
                    lastY = y;
                    return true;
                }

                @Override
                public void touchDragged(InputEvent event, float x, float y, int pointer){
                    //原版 ElementGestureListener.pan：panx += deltaX / zoom
                    panx += (x - lastX) / zoom;
                    pany += (y - lastY) / zoom;
                    lastX = x;
                    lastY = y;
                }

                @Override
                public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY){
                    //原版：zoom = clamp(zoom - amountY / 10f * zoom, 0.25f, 10f)
                    zoom = Mathf.clamp(zoom - amountY / 10f * zoom, 0.25f, 10f);
                    return true;
                }
            });
        }

        @Override
        public void draw(Batch batch, float parentAlpha){
            super.draw(batch, parentAlpha);

            //原版用的是 lambda 里的 w/h，即整个屏幕尺寸
            float w = Gdx.graphics.getWidth();
            float h = Gdx.graphics.getHeight();
            float size = baseSize * zoom * Vars.world.width;

            //原版：Draw.color(Color.black); Fill.crect(x, y, w, h)
            batch.setColor(Color.BLACK);
            batch.draw(Tex.whiteui.getRegion(), getX(), getY(), w, h);

            MinimapRenderer minimap = minimap();
            if(minimap != null && minimap.getTexture() != null){
                minimap.flush();

                batch.setColor(Color.WHITE);
                float ratio = (float)minimap.getTexture().getHeight() / minimap.getTexture().getWidth();
                TextureRegion reg = Draw.wrap(minimap.getTexture());
                //phoenix 的 Draw.rect 与原版一样以中心点定位
                Draw.rect(reg, w / 2f + panx * zoom, h / 2f + pany * zoom, size, size * ratio);
                minimap.drawEntities(w / 2f + panx * zoom - size / 2f, h / 2f + pany * zoom - size / 2f * ratio, size, size * ratio, zoom, true);
            }

            batch.setColor(Color.WHITE);
        }
    }
}
