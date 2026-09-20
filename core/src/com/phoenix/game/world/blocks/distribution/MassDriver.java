package com.phoenix.game.world.blocks.distribution;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Bullets;
import com.phoenix.game.content.Fx;
import com.phoenix.game.content.Items;
import com.phoenix.game.core.Core;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.entities.type.Player;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.Item;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Pos;
import com.phoenix.game.world.Tile;

import static com.phoenix.game.Vars.tilesize;

/**
 * 质量驱动器。参照 Mindustry mindustry.world.blocks.distribution.MassDriver 移植。
 * <p>两台驱动器用 {@code link} 互相配对，各自把库存里的物品装进一颗炮弹（{@link DriverBulletData}）
 * 打过去，命中后由对端接收。发射需要两台互相瞄准（角度差 &lt; 2°）且都装填完毕，因此是"排队发射"。
 * <p>状态机（对应原版 DriverState）：
 * <ul>
 *     <li>{@code idle}：没目标也没人瞄准我，物品倒给邻居；</li>
 *     <li>{@code accepting}：有人瞄着我（在 waitingShooters 里），把炮口对准它并接收物品；</li>
 *     <li>{@code shooting}：我有目标，装填并等对端也准备好，然后开火。</li>
 * </ul>
 */
public class MassDriver extends Block{
    /** 最大连接距离（世界单位）。 */
    public float range = 440f;
    /** 炮口转向速度。 */
    public float rotateSpeed = 0.04f;
    /** 炮弹出膛位置偏移（世界单位）。 */
    public float translation = 7f;
    /** 一次发射的最少物品数（同时也是对端需要的空余）。 */
    public int minDistribute = 10;
    /** 后坐力视觉位移。 */
    public float knockback = 4f;
    /** 装填时间（帧）。 */
    public float reloadTime = 200f;
    /** 发射震动强度。 */
    public float shake = 3f;

    /** 底座贴图（区域名 = 名称-base）。 */
    public TextureRegion baseRegion;

    public MassDriver(String name){
        super(name);
        update = true;
        solid = true;
        posConfig = true;
        configurable = true;
        hasItems = true;
        itemCapacity = 120;
        hasPower = true;
        entityType = MassDriverEntity::new;
    }

    @Override
    public void load(){
        super.load();
        baseRegion = Core.atlas == null ? null : Core.atlas.findRegion(name + "-base");
    }

    @Override
    public void configured(Tile tile, Player player, int value){
        if(tile.entity instanceof MassDriverEntity){
            ((MassDriverEntity)tile.entity).link = value;
        }
    }

    /** 绘制底座（对应原版 draw：炮身单独在 drawLayer 里按角度旋转）。 */
    @Override
    public void draw(Tile tile){
        if(baseRegion == null){
            super.draw(tile);
            return;
        }

        float size = this.size * tilesize;
        Core.batch.draw(baseRegion,
            tile.worldx() + (tilesize - size) / 2f + offset(),
            tile.worldy() + (tilesize - size) / 2f + offset(), size, size);
    }

    /** 绘制可旋转的炮身（对应原版 drawLayer），装填中带后坐位移。 */
    @Override
    public void drawLayer(Tile tile){
        if(region == null || !(tile.entity instanceof MassDriverEntity)) return;
        MassDriverEntity e = (MassDriverEntity)tile.entity;

        float cx = tile.worldx() + tilesize / 2f + Angles.trnsx(e.barrelRotation + 180f, e.reload * knockback);
        float cy = tile.worldy() + tilesize / 2f + Angles.trnsy(e.barrelRotation + 180f, e.reload * knockback);

        //贴图基准朝向是"右"，本项目角度制：角度 = 90 - 炮口朝向
        Core.batch.draw(region, cx - tilesize / 2f, cy - tilesize / 2f, tilesize / 2f, tilesize / 2f,
            tilesize, tilesize, 1f, 1f, 90f - e.barrelRotation);
    }

    /** 只有"连着目标"的驱动器才收物品（对应原版 acceptItem）。 */
    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source){
        return tile.entity != null && tile.entity.items != null
            && tile.entity.items.total() < itemCapacity && linkValid(tile);
    }

    /** @return 本驱动器的连接是否有效（同型 + 同队 + 距离内）。 */
    public boolean linkValid(Tile tile){
        if(tile == null || !(tile.entity instanceof MassDriverEntity)) return false;
        MassDriverEntity e = (MassDriverEntity)tile.entity;
        if(e.link == Pos.invalid) return false;

        Tile link = Vars.world == null ? null : Vars.world.tile(e.link);
        return link != null && link.block() instanceof MassDriver
            && link.getTeam() == tile.getTeam() && tile.dst(link) <= range;
    }

    /** 发射：把库存全部装进炮弹数据并创建炮弹（对应原版 fire）。 */
    protected void fire(Tile tile, Tile target){
        MassDriverEntity e = (MassDriverEntity)tile.entity;
        MassDriverEntity other = (MassDriverEntity)target.entity;

        e.reload = 1f;

        DriverBulletData data = new DriverBulletData();
        data.from = e;
        data.to = other;

        int totalUsed = 0;
        for(int i = 0; i < Items.all.size; i++){
            Item item = Items.all.get(i);
            int maxTransfer = Math.min(e.items.get(item), itemCapacity - totalUsed);
            data.items[i] = maxTransfer;
            totalUsed += maxTransfer;
            e.items.remove(item, maxTransfer);
        }

        float angle = tile.angleTo(target);
        float x = tile.worldx() + tilesize / 2f + Angles.trnsx(angle, translation);
        float y = tile.worldy() + tilesize / 2f + Angles.trnsy(angle, translation);

        Bullet.create(Bullets.driverBolt, e, e.getTeam(), x, y, angle, 1f, 1f, data);

        Effects.effect(Fx.shootSmall, x, y, angle);
        Effects.effect(Fx.smoke, x, y, angle);
        Effects.shake(shake, shake);
    }

    /** 命中目标：把炮弹里的物品倒进去（对应原版 handlePayload）。 */
    protected void handlePayload(MassDriverEntity entity, Bullet bullet, DriverBulletData data){
        int totalItems = entity.items.total();

        for(int i = 0; i < data.items.length; i++){
            int maxAdd = Math.min(data.items[i], itemCapacity * 2 - totalItems);
            if(maxAdd <= 0) continue;

            entity.items.add(Items.all.get(i), maxAdd);
            data.items[i] -= maxAdd;
            totalItems += maxAdd;

            if(totalItems >= itemCapacity * 2) break;
        }

        Effects.shake(shake, shake);
        Effects.effect(Fx.explosion, bullet.x, bullet.y, bullet.rot());

        entity.reload = 1f;
        bullet.remove();
    }

    /** @return other 是否为本驱动器（tile）的合法"发射方"（对应原版 shooterValid）。 */
    protected boolean shooterValid(Tile tile, Tile other){
        if(other == null) return true;
        if(!(other.block() instanceof MassDriver) || !(other.entity instanceof MassDriverEntity)) return false;

        MassDriverEntity e = (MassDriverEntity)other.entity;
        return e.link == tile.pos() && tile.dst(other) <= range;
    }

    /** 炮弹承载的数据（对应原版 DriverBulletData）：谁发的、发给谁、装了什么。 */
    public static class DriverBulletData{
        public MassDriverEntity from, to;
        public int[] items = new int[Items.all.size];
    }

    /** 驱动器状态。 */
    public enum DriverState{
        /** 没目标也没人瞄着我。 */
        idle,
        /** 有人瞄着我，正在接收物品并对准它。 */
        accepting,
        /** 我有目标，正在装填/等待开火。 */
        shooting,
        unloading
    }

    public class MassDriverEntity extends TileEntity{
        /** 配对的对端驱动器位置（{@link Pos} 打包坐标）。 */
        public int link = Pos.invalid;

        @Override
        public int config(){
            return link;
        }
        /** 炮口朝向（度）。注意不能叫 rotation——{@link TileEntity} 已有 byte rotation（瓦片朝向）。 */
        public float barrelRotation = 90f;
        /** 装填进度（1 = 刚发射，0 = 可发射）。 */
        public float reload;
        /** 当前状态。 */
        public DriverState state = DriverState.idle;
        /** 正在瞄准本驱动器的其他驱动器（保持插入顺序，队首优先）。 */
        public final Array<Tile> waitingShooters = new Array<>();

        public Tile currentShooter(){
            return waitingShooters.size == 0 ? null : waitingShooters.first();
        }

        /** 由炮弹回调，把物品投递进来。 */
        public void handlePayload(Bullet bullet, DriverBulletData data){
            ((MassDriver)block).handlePayload(this, bullet, data);
        }

        /** @return 供电效率（无电模块时恒为 1）。 */
        public float efficiency(){
            return !hasPower || power == null ? 1f : power.status;
        }

        @Override
        public void update(){
            Tile target = (Vars.world == null || link == Pos.invalid) ? null : Vars.world.tile(link);
            boolean hasLink = linkValid(tile);

            //装填与状态无关，一直推进
            if(reload > 0f){
                reload = Mathf.clamp(reload - delta() / reloadTime * efficiency());
            }

            //清理失效的发射方
            Tile shooter = currentShooter();
            if(!shooterValid(tile, shooter)){
                waitingShooters.removeValue(shooter, true);
            }

            //状态切换
            if(state == DriverState.idle){
                if(waitingShooters.size > 0 && (itemCapacity - items.total() >= minDistribute)){
                    state = DriverState.accepting;
                }else if(hasLink){
                    state = DriverState.shooting;
                }
            }

            //空闲/接收时把物品倒给邻居
            if(state == DriverState.idle || state == DriverState.accepting){
                tryDump(tile);
            }

            //没电就不工作（对应原版 cons.valid() 守卫）
            if(hasPower && (power == null || power.status <= 0.001f)) return;

            if(state == DriverState.accepting){
                if(shooter == null || (itemCapacity - items.total() < minDistribute)){
                    state = DriverState.idle;
                    return;
                }

                //把炮口对准发射方
                barrelRotation = Mathf.slerpDelta(barrelRotation, tile.angleTo(shooter), rotateSpeed * efficiency());
            }else if(state == DriverState.shooting){
                //有东西瞄着我且我装得下，就先让位给接收
                if(!hasLink || (waitingShooters.size > 0 && (itemCapacity - items.total() >= minDistribute))){
                    state = DriverState.idle;
                    return;
                }

                float targetRotation = tile.angleTo(target);

                if(items.total() >= minDistribute
                    && target.entity != null && target.entity.items != null
                    && target.block().itemCapacity - target.entity.items.total() >= minDistribute){

                    MassDriverEntity other = (MassDriverEntity)target.entity;
                    //注意去重：原版用 OrderedSet，本项目用 Array，每帧 add 会无限增长（帧率暴跌）
                    if(!other.waitingShooters.contains(tile, true)){
                        other.waitingShooters.add(tile);
                    }

                    if(reload <= 0.0001f){
                        //先对准目标
                        barrelRotation = Mathf.slerpDelta(barrelRotation, targetRotation, rotateSpeed * efficiency());

                        //队列轮到我 + 双方角度都到位 → 开火
                        if(other.currentShooter() == tile
                            && other.state == DriverState.accepting
                            && Angles.near(barrelRotation, targetRotation, 2f)
                            && Angles.near(other.barrelRotation, targetRotation + 180f, 2f)){

                            fire(tile, target);
                            other.waitingShooters.removeValue(tile, true);
                            state = DriverState.idle;
                            other.state = DriverState.idle;
                        }
                    }
                }
            }
        }

        @Override
        public void write(java.io.DataOutputStream out) throws java.io.IOException{
            super.write(out);
            out.writeInt(link);
            out.writeFloat(barrelRotation);
            out.writeFloat(reload);
            out.writeByte(state.ordinal());
        }

        @Override
        public void read(java.io.DataInputStream in, byte revision) throws java.io.IOException{
            super.read(in, revision);
            link = in.readInt();
            barrelRotation = in.readFloat();
            reload = in.readFloat();
            int stateIndex = in.readUnsignedByte();
            state = stateIndex < DriverState.values().length ? DriverState.values()[stateIndex] : DriverState.idle;
            //排队者不入档：读档后由各驱动器重新发起
            waitingShooters.clear();
        }
    }
}
