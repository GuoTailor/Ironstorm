package com.phoenix.game.type;
import com.phoenix.game.entities.type.BaseUnit;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.I18NBundle;
import com.badlogic.gdx.utils.ObjectSet;
import com.phoenix.game.content.Items;
import com.phoenix.game.core.Core;
import com.phoenix.game.ui.Cicon;

import java.util.function.Supplier;

/**
 * 单位类型。参照 Mindustry mindustry.type.UnitType 移植。
 * <p>继承 {@link com.phoenix.game.ctype.UnlockableContent}：名字/本地化名/描述/图标缓存来自它，
 * 构造时自动注册进 {@code Vars.content}。
 */
public class UnitType extends com.phoenix.game.ctype.UnlockableContent{
    public Supplier<? extends BaseUnit> constructor;
    public float health = 60;
    public float hitsize = 7f;
    public float hitsizeTile = 4f;
    public float speed = 0.4f;
    public float range = 0, attackLength = 150f;
    public float rotatespeed = 0.2f;
    public float baseRotateSpeed = 0.1f;
    public float shootCone = 15f;
    public float mass = 1f;
    public boolean flying;
    public boolean targetAir = true;
    public boolean rotateWeapon = false;
    public float drag = 0.1f;
    public float maxVelocity = 5f;
    public float retreatPercent = 0.6f;
    public int itemCapacity = 30;
    /**
     * 该单位可开采的物品集合（采矿单位用）。照抄原版 {@code UnitType.java:38} 的默认值。
     * <p>⚠ 字段初始化里引用了 {@code Items.lead} —— 安全性依赖 {@code ContentLoader.lists()} 的顺序
     * （Items 在 UnitTypes 之前，load() 时静态字段已赋值）；若日后调整内容顺序导致这里取到 null，
     * 改成延迟初始化（首次访问时补默认值）。
     */
    public ObjectSet<Item> toMine = ObjectSet.with(Items.lead, Items.copper);
    /**
     * 免疫的状态效果集合（原版 {@code UnitType.java:42}）。
     * <p>消费点：{@code BaseUnit.isImmune} → {@code Fire}（不点燃）与 {@code Statuses.handleApply}（跳过施加）。
     */
    public ObjectSet<StatusEffect> immunities = new ObjectSet<>();
    public float buildPower = 0.3f, minePower = 0.7f;
    public float weaponOffsetY, engineOffset = 6f, engineSize = 2f;
    public Weapon weapon;
    /**
     * 死亡音效（对应原版 {@code UnitType.deathSound}，mindustry/type/UnitType.java:43，默认 {@code Sounds.bang}）。
     * <p>播放点：{@code BaseUnit.onDeath}。⚠ 与 {@code Blocks.java} 里 {@code shootSound} 的既有做法一致：
     * 直接持有 {@code Sounds} 静态引用（音频未启用前是 MockSound 空实现，播放即静音）。
     */
    public com.badlogic.gdx.audio.Sound deathSound = io.anuke.mindustry.gen.Sounds.bang;
    /** 是否为 Boss 单位：独立血条、波次提示（原版按单位类型隐式区分，这里显式标记）。 */
    public boolean boss;

    public TextureRegion legRegion, baseRegion, region;

    @Override
    public com.phoenix.game.ctype.ContentType getContentType(){
        return com.phoenix.game.ctype.ContentType.unit;
    }

    /** 对应原版 {@code UnitType.displayInfo}（原版 UnitType.java:63-65）：交给 ContentDisplay.displayUnit。 */
    @Override
    public void displayInfo(com.badlogic.gdx.scenes.scene2d.ui.Table table){
        com.phoenix.game.ui.ContentDisplay.displayUnit(table, this);
    }


    /**
     * 取指定尺寸的单位图标（覆写 {@code UnlockableContent.icon(Cicon)}）。
     * <p>原版按 {@code unit-<name>-<尺寸>} → {@code unit-<name>-full} → {@code unit-<name>} → {@code <name>} 依次回退；
     * phoenix 没有打包管线（不生成 {@code unit-*} 前缀图标），实际会落到本体贴图 {@code <name>} 上。
     */
    @Override
    public TextureRegion icon(Cicon cicon){
        int index = cicon.ordinal();
        if(cicons[index] == null){
            TextureRegion found = null;
            if(Core.atlas != null){
                found = Core.atlas.findRegion("unit-" + name + "-" + cicon.name());
                if(found == null) found = Core.atlas.findRegion("unit-" + name + "-full");
                if(found == null) found = Core.atlas.findRegion("unit-" + name);
                if(found == null) found = Core.atlas.findRegion(name);
                if(found == null) found = Core.atlas.findRegion(name + "1");
            }
            cicons[index] = found != null ? found : region;
        }
        return cicons[index];
    }


    public <T extends BaseUnit> UnitType(String name, Supplier<T> mainConstructor){
        this(name);
        create(mainConstructor);
    }

    public UnitType(String name){
        super(name);
    }

    public <T extends BaseUnit> void create(Supplier<T> mainConstructor){
        this.constructor = mainConstructor;
        //名字/描述由 UnlockableContent 的构造函数统一从 bundle 取（含缺键双形态判定）
    }

    /** 加载贴图（由 ContentLoader.load() 统一调用）。 */
    @Override
    public void load(){
        if(weapon != null) weapon.load();
        region = Core.atlas.findRegion(name);
        legRegion = Core.atlas.findRegion(name + "-leg");
        baseRegion = Core.atlas.findRegion(name + "-base");
        //贴图重载后图标缓存失效（对应原版 Content.loadIcon 里的 Arrays.fill(cicons, null)）
        java.util.Arrays.fill(cicons, null);
    }

    public BaseUnit create(){
        BaseUnit unit = constructor.get();
        unit.init(this);
        return unit;
    }

    // ---- 能力钩子（对应原版 Mech 挂在 Player 上的那几个钩子，F12c）----
    //玩家单位的能力在原版来自 Mechs（Player.mech.canShoot/draw/updateAlt）；本项目玩家只是"持有被控单位"，
    //所以这些钩子挂在 UnitType 上，由 InputHandler/渲染/update 在对应时机调用（默认实现不影响任何既有单位）。

    /**
     * 玩家开火前的判定（对应原版 {@code Mech.canShoot(Player)}）。
     * <p>例：{@code trident-ship} 要求移动速度 &gt; 1.2 才投弹。
     */
    public boolean canShoot(BaseUnit unit){
        return true;
    }

    /**
     * 单位本体绘制之后的附加绘制（对应原版 {@code Mech.draw(Player)}）。
     * <p>例：{@code javelin-ship} 的高速护盾。调用时 {@code Draw} 状态未定义，实现里请自行设色并在结束时复位。
     */
    public void drawOverlay(BaseUnit unit){
    }

    /**
     * 每帧的额外逻辑（对应原版 {@code Mech.updateAlt(Player)}）。
     * <p>例：{@code javelin-ship} 的随机雷击。玩家单位由输入驱动、AI 单位走 behavior，
     * 这个钩子两者都会执行（远端代理单位不执行）。
     */
    public void updateAlt(BaseUnit unit){
    }
}
