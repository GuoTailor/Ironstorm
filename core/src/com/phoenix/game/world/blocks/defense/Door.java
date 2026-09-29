package com.phoenix.game.world.blocks.defense;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.content.Fx;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.Units;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.Vars;
import com.phoenix.game.world.Tile;
import io.anuke.mindustry.gen.Sounds;

import static com.phoenix.game.Vars.tilesize;

/**
 * 门。参照 Mindustry mindustry.world.blocks.defense.Door 移植。
 * <p>与墙的区别：{@code solid = false}，是否阻挡通行由 {@link #isSolidFor} 按开关状态动态决定。
 * <p>原版结构（原版 Door.java:20-46）：
 * <ul>
 *   <li>{@code consumesTap = true} + {@code tapped()} 手动点击开合 —— 本项目点击层没有
 *       {@code consumesTap} 字段，等价机制是 {@link #tapped} 返回 true（消费这次点击，不让它当开火）；</li>
 *   <li>{@code onDoorToggle}（{@code @Remote}，本项目保持注释态）→ 退化为本地方法 {@link #toggle}；</li>
 *   <li>开关后 {@code pathfinder.updateTile(tile)} 刷新寻路 + openfx/closefx 特效 + {@code Sounds.door} 音效
 *       （原版 {@code Sound.at(tile)} 按位置播放；本项目 Sound 是 libgdx 的 {@code com.badlogic.gdx.audio.Sound}，
 *       无 at 重载 → 降级为无定位 {@code play}）；</li>
 *   <li>本项目保留此前自加的"友方单位靠近自动开、离开自动关"（类注释原声明为简化点），与手动开关
 *       共用 {@code open} 字段：手动开门后单位离开不再自动关；手动关门后单位走近会再次自动开。</li>
 * </ul>
 * <p>缺省：原版构造器里的 {@code solidifes = true}（方块可见性变化时刷新缓存，arc Block 字段），
 * 本项目 Block 无该字段；原版 {@code getCursor()} 手型光标，本项目 Block 无该回调。均为缺资源/缺回调，
 * 不另行造接口。
 */
public class Door extends Wall{
    /** 开门贴图（区域名 = 名称-open）。 */
    protected TextureRegion openRegion;

    /** 开门特效（对应原版 {@code Door.openfx}）。 */
    public Effects.Effect openfx = Fx.dooropen;
    /** 关门特效（对应原版 {@code Door.closefx}）。 */
    public Effects.Effect closefx = Fx.doorclose;

    public Door(String name){
        super(name);
        //实心与否交给 isSolidFor 动态判断
        solid = false;
        update = true;
        entityType = DoorEntity::new;
    }

    @Override
    public void load(){
        super.load();
        openRegion = Core.atlas == null ? null : Core.atlas.findRegion(name + "-open");
    }

    /**
     * 切换门的开关状态（对应原版 {@code Door.onDoorToggle}，原版 Door.java:37-52）。
     * <p>原版是 {@code @Remote(called = Loc.server)} 的网络方法，本项目 @Remote 保持注释态 →
     * 本地直接调用（单机无第二端）。
     */
    private static void toggle(Tile tile, boolean open){
        if(!(tile.entity instanceof DoorEntity)) return;
        DoorEntity entity = (DoorEntity)tile.entity;

        entity.open = open;
        Door door = (Door)tile.block();

        //开关后刷新该格寻路网格（单位不再绕路/卡门）
        if(Vars.pathfinder != null){
            Vars.pathfinder.updateTile(tile);
        }
        //特效位置用方块几何中心（door-large 是 2×2；原版 tile.drawx() 在原版口径下即中心）
        if(!entity.open){
            Effects.effect(door.openfx, door.centerX(tile), door.centerY(tile));
        }else{
            Effects.effect(door.closefx, door.centerX(tile), door.centerY(tile));
        }
        if(Sounds.door != null){
            Sounds.door.play(1f);
        }
    }

    /** 关门时视为实心（挡单位/挡子弹），开门时放行。 */
    @Override
    public boolean isSolidFor(Tile tile){
        return !(tile.entity instanceof DoorEntity) || !((DoorEntity)tile.entity).open;
    }

    /** 按开关状态换贴图。 */
    @Override
    public void draw(Tile tile){
        TextureRegion reg = region;
        if(tile.entity instanceof DoorEntity && ((DoorEntity)tile.entity).open && openRegion != null){
            reg = openRegion;
        }
        if(reg == null) return;

        float size = this.size * tilesize;
        Core.batch.draw(reg,
            tile.worldx() + (tilesize - size) / 2f + offset(),
            tile.worldy() + (tilesize - size) / 2f + offset(), size, size);
    }

    /**
     * 手动点击开合（对应原版 {@code Door.tapped}，原版 Door.java:82-91）。
     * <p>原版判据逐字：门口站着单位且门开着 → 不能关（防夹）；点击冷却未到 → 忽略
     * （原版 {@code tile.entity.timer.get(timerToggle, 30f)}，本项目无 Timer 模块 →
     * {@link DoorEntity#toggleCool} 自计）。返回 true 表示消费这次点击（对应原版 {@code consumesTap}）。
     */
    @Override
    public boolean tapped(Tile tile, com.phoenix.game.entities.type.Player player){
        if(!(tile.entity instanceof DoorEntity)) return false;
        DoorEntity entity = (DoorEntity)tile.entity;

        if((Units.anyEntities(tile) && entity.open) || entity.toggleCool > 0f){
            return false;
        }

        //手动开关：清掉自动标记，玩家亲自控制（单位离开时不会自动关）
        entity.auto = false;
        entity.toggleCool = 30f;
        toggle(tile, !entity.open);
        return true;
    }

    public class DoorEntity extends TileEntity{
        /** 是否打开（打开时不阻挡通行）。 */
        public boolean open;
        /** 当前"开"状态是否来自友军靠近的自动判定（手动开关会清掉）。 */
        public boolean auto;
        /** 点击冷却剩余帧（对应原版 {@code timer.get(timerToggle, 30f)} 的 30 帧冷却；本项目无 Timer 模块）。 */
        public float toggleCool;

        @Override
        public void update(){
            if(toggleCool > 0f) toggleCool -= Time.delta();

            //自动开合（友方单位贴到门口就开门，离开自动关门）。
            //用方块几何中心：door-large 是 2×2，只取锚点瓦片中心会偏半格
            boolean near = false;
            float cx = this.cx(), cy = this.cy();
            float range = tilesize * 1.5f;

            for(int i = 0; i < Units.units.size; i++){
                BaseUnit unit = Units.units.get(i);
                if(unit.isDead() || unit.getTeam() != tile.getTeam()) continue;

                if(Math.abs(unit.x - cx) < range && Math.abs(unit.y - cy) < range){
                    near = true;
                    break;
                }
            }

            //与手动开关共用 open 字段，且走同一条 toggle 路径（特效/音效/寻路刷新一致）
            if(near && !open){
                auto = true;
                toggle(tile, true);
            }else if(!near && open && auto){
                auto = false;
                toggle(tile, false);
            }
        }

        @Override
        public void write(java.io.DataOutputStream out) throws java.io.IOException{
            super.write(out);
            out.writeBoolean(open);
        }

        @Override
        public void read(java.io.DataInputStream in, byte revision) throws java.io.IOException{
            super.read(in, revision);
            open = in.readBoolean();
        }
    }
}
