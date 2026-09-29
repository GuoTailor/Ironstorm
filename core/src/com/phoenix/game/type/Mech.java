package com.phoenix.game.type;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.core.Core;
import com.phoenix.game.entities.type.Player;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.util.ArcAnnotate.*;

/**
 * 机甲（玩家装备）。参照 Mindustry mindustry.type.Mech 移植。
 * <p>⚠ 继承链与原版**一致**：原版 126.2 的 {@code Mech} 就是 {@code extends UnlockableContent}
 * （不是 UnitType）。
 * <p>原版 {@code Player} 自身就是一个单位、且带一整套机甲装备系统（换机甲 = 换 Player 的属性与绘制）；
 * 本项目把 Player 简化成"持有一个被控单位的引用"（见 {@code Player} 类注释），
 * 所以这里的绘制/能力钩子（{@link #drawStats(Player)} / {@link #draw(Player)}）都是空实现。
 * <p>⚠ 本项目没有 {@code player.mech} 装备系统，本类的 {@code updateAlt/draw/onLand/getExtraArmor/
 * spreadX/getRotationAlpha/canShoot} **没有任何调用方**；机甲行为被搬到了 {@code content/UnitTypes}
 * 的同名 UnitType（见 {@code Mechs} 类注释的"钩子的实际调用方"一条）。
 * <p>刻意降级（依赖缺失，等 Player 机甲系统移植后再接线）：
 * <ul>
 *   <li>{@code ContentDisplay.displayMech(table, this)}（内容信息对话框的机甲页）本项目未移植
 *       → 不覆写 {@code displayInfo}。</li>
 *   <li>{@link #drawStats(Player)} 依赖 Player 的 {@code healthf()} / {@code getPowerCellRegion()} /
 *       {@code drawBackItems()} / {@code drawLight()}，本项目 Player 没有这些接口。</li>
 * </ul>
 */
public class Mech extends com.phoenix.game.ctype.UnlockableContent{
    public boolean flying;
    public float speed = 1.1f;
    public float maxSpeed = 10f;
    public float boostSpeed = 0.75f;
    public float drag = 0.4f;
    public float mass = 1f;
    public float shake = 0f;
    public float health = 200f;

    public float hitsize = 6f;
    public float cellTrnsY = 0f;
    public float mineSpeed = 1f;
    public int drillPower = -1;
    public float buildPower = 1f;
    public Color engineColor = Pal.boostTo;
    public int itemCapacity = 30;
    public boolean turnCursor = true;
    public boolean canHeal = false;
    public float compoundSpeed, compoundSpeedBoost;

    /** draw the health and team indicator */
    public boolean drawCell = true;
    /** draw the items on its back */
    public boolean drawItems = true;
    /** draw the engine light if it's flying/boosting */
    public boolean drawLight = true;

    public float weaponOffsetX, weaponOffsetY, engineOffset = 5f, engineSize = 2.5f;
    public @NonNull Weapon weapon;

    public TextureRegion baseRegion, legRegion, region;

    public Mech(String name, boolean flying){
        super(name);
        this.flying = flying;
    }

    public Mech(String name){
        this(name, false);
    }

    public void updateAlt(Player player){
    }

    public void draw(Player player){
    }

    /**
     * 画血量格/背包/引擎灯。
     * <p>刻意降级：原版依赖 {@code player.healthf()}、{@code player.getPowerCellRegion()}、
     * {@code player.drawBackItems()}、{@code player.drawLight()}，本项目 Player 只是"被控单位的持有者"，
     * 这些绘制接口尚未移植，所以暂时什么都不画（详见类注释）。
     */
    public void drawStats(Player player){
    }

    public float getExtraArmor(Player player){
        return 0f;
    }

    public float spreadX(Player player){
        return 0f;
    }

    public float getRotationAlpha(Player player){
        return 1f;
    }

    public boolean canShoot(Player player){
        return true;
    }

    public void onLand(Player player){
    }

    @Override
    public void init(){
        super.init();

        for(int i = 0; i < 500; i++){
            compoundSpeed *= (1f - drag);
            compoundSpeed += speed;
        }

        for(int i = 0; i < 500; i++){
            compoundSpeedBoost *= (1f - drag);
            compoundSpeedBoost += boostSpeed;
        }
    }

    @Override
    public void load(){
        weapon.load();
        //libgdx 的 TextureAtlas 只有 findRegion（没有 arc 的 find）
        if(!flying){
            legRegion = Core.atlas == null ? null : Core.atlas.findRegion(name + "-leg");
            baseRegion = Core.atlas == null ? null : Core.atlas.findRegion(name + "-base");
        }

        region = Core.atlas == null ? null : Core.atlas.findRegion(name);
    }

    @Override
    public com.phoenix.game.ctype.ContentType getContentType(){
        return com.phoenix.game.ctype.ContentType.mech;
    }

    /** 对应原版 {@code Mech.displayInfo}（原版 Mech.java:117-119）：交给 ContentDisplay.displayMech。 */
    @Override
    public void displayInfo(com.badlogic.gdx.scenes.scene2d.ui.Table table){
        com.phoenix.game.ui.ContentDisplay.displayMech(table, this);
    }

    @Override
    public String toString(){
        return localizedName;
    }
}
