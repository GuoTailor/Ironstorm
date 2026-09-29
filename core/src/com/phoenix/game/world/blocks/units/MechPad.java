package com.phoenix.game.world.blocks.units;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.Vars;
import com.phoenix.game.content.UnitTypes;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.entities.type.Player;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.math.geom.Geometry;
import com.phoenix.game.type.Mech;
import com.phoenix.game.type.UnitType;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.meta.BlockFlag;

import java.util.EnumSet;

import static com.phoenix.game.Vars.tilesize;

/**
 * 机甲平台。参照 Mindustry mindustry.world.blocks.units.MechPad 移植。
 * <p>站在平台上点击它，平台会用 {@link #buildTime} 帧"造"出该型号并把你换成它
 * （对应原版换机甲：建造期间玩家被固定在平台上，完成后以新机甲重生）。
 * <p>与原版的差异：原版换的是 {@code Mech}（带独立的血量/武器/建造力），
 * 本工程没有 Mech 系统，改为**直接换成对应的 {@link UnitType}**（即 {@code player.selectedType}）。
 * <p>未接线：平台上的重生进度动画（原版 {@code drawLayer} → {@code RespawnBlock.drawRespawn}，本项目该类
 * <b>已移植</b>，只差其中的机甲剪影着色器 {@code Shaders.build} —— {@code graphics/Shaders} 类存在、
 * 缺 {@code build} 成员，且尚未接到本类）与 {@code Fx.spawn} 特效。
 */
public class MechPad extends Block{
    /** 要换成的单位类型（直接引用）。 */
    public UnitType unitType;
    /** 单位类型名：Blocks 早于 UnitTypes 加载，用它延迟解析。 */
    public String unitTypeName;
    /**
     * 原版的机甲型号（{@code Mechs.javelin} 等），对应原版 {@code MechPad.mech}。
     * <p>⚠ 本项目 {@link Player} 只是"被控单位的持有者"，没有原版的 {@code player.mech} 装备系统
     * （原版 {@code onMechFactoryDone} 做的是 {@code player.mech = mech} + 让玩家以该机甲重生），
     * 所以这个字段目前**只是平台身份 + 降级换装的线索**：
     * <ul>
     *   <li>4 个普通机甲平台（dart/delta/tau/omega）走 {@link #unitTypeName}，不设 mech；</li>
     *   <li>3 个 ship-pad 没有对应的原版 UnitType，改在 {@code Blocks.shipPad} 里同时设
     *       {@code unitTypeName = mech.name}，由 {@link #unitType()} 按同名 UnitType 延迟解析
     *       （见 {@code content/UnitTypes} 的 javelin-ship / trident-ship / glaive-ship）。</li>
     * </ul>
     * <p>TODO 等 Player 的机甲系统移植后，让 {@code mech != null} 的平台改走原版分支
     * （把玩家的 {@code mech} 换成它、按 {@code Mech.health/weapon/buildPower} 生效）。
     */
    public Mech mech;
    /** 换机甲所需帧数（原版 5 秒）。 */
    public float buildTime = 60f * 5f;

    /** 选中时四向箭头的贴图（图集里的 transfer-arrow）。 */
    public TextureRegion arrowRegion;

    public MechPad(String name){
        super(name);
        update = true;
        //平台本身不挡路（原版 solid = false）
        solid = false;
        destructible = true;
        health = 160;
        size = 2;
        hasPower = true;
        flags = EnumSet.of(BlockFlag.mechPad);
        entityType = MechPadEntity::new;
    }

    @Override
    public void load(){
        super.load();
        arrowRegion = Core.atlas == null ? null : Core.atlas.findRegion("transfer-arrow");
    }

    /**
     * 悬停平台时在四边画朝内的呼吸箭头（对应原版 {@code MechPad.drawSelect}）。
     * <p><b>与原版的差异</b>：
     * <ul>
     *     <li>原版 {@code Draw.rect("transfer-arrow", ...)} 是按名取图；本项目 {@code Draw} 只有 TextureRegion 版本，
     *         故在 {@link #load()} 里查好 {@link #arrowRegion}。</li>
     *     <li>⚠ 坐标口径：原版 {@code tile.drawx()/drawy()} 是贴图中心，本项目改用
     *         {@link Block#centerX}/{@link Block#centerY}，并先 {@code link()} 到锚点（平台是 2x2，光标可能落在卫星瓦片上）。</li>
     *     <li>⚠ d4 旋向：本项目 {@code Geometry.d4} = {上,右,下,左}（原版 {右,上,左,下}）。
     *         四向都画，所以位置集合相同；但箭头要朝内，角度需换算：
     *         原版 {@code (i + 2) * 90}（i 是原版索引）→ 本项目 {@code (3 - i) * 90}。</li>
     * </ul>
     */
    @Override
    public void drawSelect(Tile tile){
        if(arrowRegion == null) return;

        Tile anchor = tile.link();
        Draw.color(Pal.accent);

        for(int i = 0; i < 4; i++){
            float length = tilesize * size / 2f + 3 + Mathf.absin(Time.time, 5f, 2f);
            Draw.rect(arrowRegion,
                centerX(anchor) + Geometry.d4[i].x * length,
                centerY(anchor) + Geometry.d4[i].y * length,
                (3 - i) * 90f);
        }

        Draw.color();
    }

    /**
     * @return 要换成的单位类型；名字是延迟解析的。
     * <p>⚠ 降级：本项目没有 Mech 系统，所以这里**只认 {@link #unitTypeName} 能解析到的单位类型**。
     * 3 个 ship-pad 由 {@code Blocks.shipPad} 设成 {@code unitTypeName = mech.name}，从而解析到同名
     * UnitType（javelin-ship / trident-ship / glaive-ship）。名字解析不到（例如平台只设了 mech 而没设
     * unitTypeName）时返回 null → {@link #validTap} 会拒绝点击，避免把玩家"抓"上平台再于下一帧放掉。
     */
    public UnitType unitType(){
        if(unitType == null && unitTypeName != null){
            for(UnitType type : UnitTypes.all){
                if(type.name.equals(unitTypeName)){
                    unitType = type;
                    break;
                }
            }
        }
        return unitType;
    }

    /** 点击平台：开始换机甲。 */
    @Override
    public boolean tapped(Tile tile, Player player){
        if(!(tile.entity instanceof MechPadEntity) || player == null || player.isDead()) return false;
        if(!validTap(tile, player)) return false;

        ((MechPadEntity)tile.entity).player = player;
        return true;
    }

    /** 能否开始换：同队、玩家贴着平台、有电、平台当前空闲、且平台能解析出要换成的单位。 */
    private boolean validTap(Tile tile, Player player){
        if(tile.getTeam() != player.getTeam()) return false;

        //平台解析不出单位类型（既没设 unitTypeName、或名字在 UnitTypes 里找不到）→ 别把玩家"抓"上平台再于下一帧放掉
        if(unitType() == null) return false;

        MechPadEntity entity = (MechPadEntity)tile.entity;
        if(entity == null || entity.player != null) return false;
        if(!entity.cons.valid()) return false;

        float cx = centerX(tile), cy = centerY(tile);
        return Math.abs(player.getX() - cx) <= size * tilesize && Math.abs(player.getY() - cy) <= size * tilesize;
    }

    public class MechPadEntity extends TileEntity{
        /** 正在换机甲的玩家；null 表示空闲。 */
        public Player player;
        /** 换装进度 0~1。 */
        public float progress;
        /** 预热（绘制用）。 */
        public float heat;

        @Override
        public void update(){
            if(player == null || player.isDead() || player.unit() == null){
                player = null;
                progress = 0f;
                heat = Mathf.lerpDelta(heat, 0f, 0.1f * delta());
                return;
            }

            UnitType type = unitType();
            if(type == null){
                player = null;
                return;
            }

            //建造期间把玩家固定在平台中心（对应原版 entity.player.set(tile.drawx(), tile.drawy())）
            float cx = block.centerX(tile), cy = block.centerY(tile);
            player.unit().set(cx, cy);
            player.unit().velocity().set(0f, 0f);

            heat = Mathf.lerpDelta(heat, 1f, 0.1f * delta());
            progress += delta() / buildTime;

            if(progress >= 1f){
                progress = 0f;
                Player target = player;
                player = null;

                if(Vars.control != null){
                    BaseUnit unit = Vars.control.spawnPlayerUnitAt(target.getTeam(), type, cx, cy);
                    if(unit != null) unit.health(unit.maxHealth());
                }
            }
        }
    }
}
