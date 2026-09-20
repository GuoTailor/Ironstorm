package com.phoenix.game.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Scl;
import com.phoenix.game.graphics.MinimapRenderer;
import io.anuke.mindustry.gen.Tex;

/**
 * HUD 右上角小地图。参照 Mindustry mindustry.ui.Minimap 移植：
 * 鼠标悬停时滚轮缩放视野，点击打开全屏小地图（{@link com.phoenix.game.ui.fragments.MinimapFragment}）。
 */
public class Minimap extends Table{
    /** 复用的坐标临时量，避免每帧分配。 */
    private final Vector2 tmp = new Vector2();

    public Minimap(){
        setBackground(Tex.pane);
        float margin = 5f;
        setTouchable(Touchable.enabled);

        add(new Actor(){
            {
                //libgdx 的 Actor.setSize 需要两个参数（arc 有一个参数的版本）
                setSize(Scl.scl(140f), Scl.scl(140f));
            }

            @Override
            public void draw(com.badlogic.gdx.graphics.g2d.Batch batch, float parentAlpha){
                super.draw(batch, parentAlpha);
                MinimapRenderer minimap = minimap();
                if(minimap == null) return;

                minimap.flush();
                TextureRegion region = minimap.getRegion();
                if(region == null) return;

                Core.batch.setColor(Color.WHITE);
                Draw.rect(region, getX() + getWidth() / 2f, getY() + getHeight() / 2f, getWidth(), getHeight());
                if(minimap.getTexture() != null){
                    //HUD 视图固定 0.75 缩放（对应原版 Minimap 的 drawEntities(..., 0.75f, false)）
                    minimap.drawEntities(getX(), getY(), getWidth(), getHeight(), 0.75f, false);
                }
            }
        }).size(140f);

        pad(margin);

        //滚轮缩放小地图视野（对应原版 Minimap 的 InputListener.scrolled）
        addListener(new InputListener(){
            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountx, float amounty){
                MinimapRenderer minimap = minimap();
                if(minimap != null) minimap.zoomBy(amounty);
                return true;
            }
        });

        //点击打开/关闭全屏小地图（对应原版 ClickListener -> ui.minimapfrag.toggle()）
        addListener(new ClickListener(){
            {
                setTapSquareSize(Scl.scl(11f));
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button){
                //拖动过就不算点击（否则拖拽时会反复开关全屏地图）。
                //原版直接改私有字段，libgdx 里字段是 private，改用 cancel() 达到同样效果
                if(inTapSquare()){
                    super.touchUp(event, x, y, pointer, button);
                }else{
                    cancel();
                }
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer){
                if(!inTapSquare(x, y)){
                    invalidateTapSquare();
                }
                super.touchDragged(event, x, y, pointer);
            }

            @Override
            public void clicked(InputEvent event, float x, float y){
                if(Vars.hud != null) Vars.hud.minimapFragment().toggle();
            }
        });

        //每帧：鼠标悬停在小地图上时抢滚轮焦点，否则滚轮会漏给世界相机（对应原版 Minimap 的 update 块）。
        //libgdx 的 Stage.scrolled 只发给 scrollFocus（不做 hit 判定），所以必须显式指定。
        addActor(new com.phoenix.game.ui.fragments.MenuFragment.Act(() -> {
            Stage stage = getStage();
            if(stage == null) return;

            tmp.set(Gdx.input.getX(), Gdx.input.getY());
            stage.screenToStageCoordinates(tmp);
            Actor e = stage.hit(tmp.x, tmp.y, true);
            if(e != null && e.isDescendantOf(Minimap.this)){
                stage.setScrollFocus(Minimap.this);
            }else if(stage.getScrollFocus() == Minimap.this){
                stage.setScrollFocus(null);
            }
        }));
    }

    /** @return 小地图渲染器；未进入游戏（无 Renderer）时返回 null。 */
    private static MinimapRenderer minimap(){
        return Vars.renderer == null ? null : Vars.renderer.minimap;
    }
}
