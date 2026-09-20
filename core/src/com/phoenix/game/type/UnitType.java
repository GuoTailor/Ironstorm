package com.phoenix.game.type;
import com.phoenix.game.entities.type.BaseUnit;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.I18NBundle;
import com.phoenix.game.core.Core;
import com.phoenix.game.ui.Cicon;

import java.util.function.Supplier;

/**
 * create by GYH on 2024/12/25
 */
public class UnitType {
    public final String name;
    public String description;
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
    public float buildPower = 0.3f, minePower = 0.7f;
    public float weaponOffsetY, engineOffset = 6f, engineSize = 2f;
    public Weapon weapon;
    /** 是否为 Boss 单位：独立血条、波次提示（原版按单位类型隐式区分，这里显式标记）。 */
    public boolean boss;

    public TextureRegion legRegion, baseRegion, region;

    /** 图标层缓存（按 Cicon 尺寸），对应原版 Content.cicons。 */
    private final TextureRegion[] cicons = new TextureRegion[Cicon.values().length];

    /**
     * 取指定尺寸的单位图标（对应原版 {@code UnlockableContent.icon(Cicon)}）。
     * <p>原版按 {@code unit-<name>-<尺寸>} → {@code unit-<name>-full} → {@code unit-<name>} → {@code <name>} 依次回退；
     * phoenix 没有打包管线（不生成 {@code unit-*} 前缀图标），实际会落到本体贴图 {@code <name>} 上。
     */
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
        this.name = name;
    }

    public <T extends BaseUnit> void create(Supplier<T> mainConstructor){
        this.constructor = mainConstructor;
        I18NBundle.setExceptionOnMissingKey(false);
        this.description = Core.bundle.get("unit." + name + ".description");
    }

    public void load(){
        weapon.load();
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
}
