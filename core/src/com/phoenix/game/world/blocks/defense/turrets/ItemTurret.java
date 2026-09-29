package com.phoenix.game.world.blocks.defense.turrets;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.OrderedMap;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Fx;
import com.phoenix.game.content.Items;
import com.phoenix.game.core.Events;
import com.phoenix.game.core.Scl;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.Predict;
import com.phoenix.game.entities.bullet.BulletType;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.game.EventType;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.struct.Array;
import com.phoenix.game.type.Item;
import com.phoenix.game.ui.Bar;
import com.phoenix.game.ui.Cicon;
import com.phoenix.game.ui.ItemImage;
import com.phoenix.game.ui.MultiReqImage;
import com.phoenix.game.ui.ReqImage;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.consumers.ConsumeItemFilter;
import com.phoenix.game.world.meta.BlockStat;
import com.phoenix.game.world.meta.BlockStats;
import com.phoenix.game.world.meta.I18n;
import com.phoenix.game.world.meta.values.AmmoListValue;

import static com.phoenix.game.Vars.tilesize;

/**
 * 弹药可换的炮塔：把不同物品装进同一个炮塔，打出不同的子弹。参照 Mindustry
 * mindustry.world.blocks.defense.turrets.ItemTurret 移植。
 * <p>弹药按"最后装的那种优先"排列（原版用 swap 把最近装填的换到末尾，{@code peekAmmo()} 取末尾）。
 * <p>刻意偏离（本项目结构）：
 * <ul>
 *     <li>原版把 {@code peekAmmo/useAmmo/hasAmmo/updateShooting/shoot/effects} 写在**方块**上、由
 *         {@code Block.update(Tile)} 调用；本项目统一写在实体内部类（见 {@code Drill}/{@code GenericCrafter}），
 *         所以这些覆写落在 {@link ItemTurretEntity} 上。</li>
 *     <li>原版 {@code Turret.TurretEntity} 自带 {@code ammo/totalAmmo/shots} 字段与静态内部类
 *         {@code Turret.AmmoEntry}；本项目已有的 {@code Turret} 是简化版（单一 {@code bullet} 字段），
 *         这些字段与 {@link AmmoEntry} 只能放在本类里。</li>
 *     <li>原版在 {@code setStats()} 里 {@code consumes.add(new ConsumeItemFilter(...))}；本项目的
 *         {@code Block.init()} 会先把 {@code consumes} 拍平成数组再调 {@code setStats()}，
 *         晚加的消耗器会被漏掉，所以改成在**构造函数**里登记。</li>
 *     <li>原版用 {@code OrderedMap.of(objects)}；libgdx 的 {@code OrderedMap} 没有该静态方法，手写成对插入。</li>
 * </ul>
 * <p>依赖说明（T7b1 复核后）：
 * <ul>
 *     <li>{@code Tile.isEnemyCheat()}：本项目 {@code Tile} 无此方法，已在 {@link Turret#isEnemyCheat(Tile)} 等价实现
 *         （{@code rules.enemyCheat && team == waveTeam}）；</li>
 *     <li>{@code Block.acceptStack/handleStack}：本项目 {@code Block} 不继承 {@code BlockStorage}（两方法只在
 *         {@link com.phoenix.game.world.BlockStorage} 上，且无调用方），{@link #acceptStack}/{@link #handleStack}
 *         仅为对齐原版 API 保留，当前无人调用；</li>
 *     <li>{@code MultiReqImage/ReqImage/ItemImage}：已存在（{@code ui/}），{@code Block.displayConsumption}
 *         与 {@code data.isUnlocked} 已补齐（S4b 批）→ 弹药图标行（构造器里 {@code ConsumeItemFilter}
 *         匿名子类的 {@code build} 覆写）已按原版实现。</li>
 * </ul>
 */
public class ItemTurret extends CooledTurret{
    public int maxAmmo = 30;
    public ObjectMap<Item, BulletType> ammo = new ObjectMap<>();

    public ItemTurret(String name){
        super(name);
        hasItems = true;
        //刻意偏离：原版写在 setStats() 里；本项目 consumes 在 init() 时拍平，必须提前到构造函数登记
        consumes.add(new ConsumeItemFilter(i -> ammo.containsKey(i)){
            @Override
            public void build(Tile tile, Table table){
                //原版 world/blocks/defense/turrets/ItemTurret.java:46-52
                MultiReqImage image = new MultiReqImage();
                //Vars.content.items() 是 libgdx 的 Array（没有 each(Boolf, Cons)）→ 包一层 struct.Array
                new Array<Item>(Vars.content.items()).each(
                    i -> filter.get(i) && (!Vars.world.isZone() || Vars.data.isUnlocked(i)),
                    item -> image.add(new ReqImage(new ItemImage(item.icon(Cicon.medium)),
                        //原版写 tile.<ItemTurretEntity>ent().ammo.peek()；本项目按 displayBars 的前例先判 instanceof
                        () -> tile.entity instanceof ItemTurretEntity && !((ItemTurretEntity)tile.entity).ammo.isEmpty()
                            && ((ItemEntry)((ItemTurretEntity)tile.entity).ammo.peek()).item == item)));
                table.add(image).size(Scl.scl(8 * 4));
            }

            @Override
            public boolean valid(TileEntity entity){
                //valid when there's any ammo in the turret
                return entity instanceof ItemTurretEntity && !((ItemTurretEntity)entity).ammo.isEmpty();
            }

            @Override
            public void display(BlockStats stats){
                //don't display
            }
        });
        entityType = ItemTurretEntity::new;
    }

    /** Initializes accepted ammo map. Format: [item1, bullet1, item2, bullet2...] */
    protected void ammo(Object... objects){
        //原版：ammo = OrderedMap.of(objects)
        ammo = new OrderedMap<>();
        for(int i = 0; i + 1 < objects.length; i += 2){
            ammo.put((Item)objects[i], (BulletType)objects[i + 1]);
        }
    }

    @Override
    public void setStats(){
        super.setStats();

        stats.remove(BlockStat.itemCapacity);
        stats.add(BlockStat.ammo, new AmmoListValue<>(ammo));
        //原版这里给 ConsumeItemFilter 覆写 build(Tile, Table) 显示弹药图标行；本项目因 consumes 提前登记，
        //该覆写写在构造函数里（结构偏离，见类注释）。Block.displayConsumption 与 data.isUnlocked 已补齐
        //（S4b 批），详情面板由 PlacementFragment.addTileDetail 调用。
    }

    @Override
    public void onProximityAdded(Tile tile){
        super.onProximityAdded(tile);

        //add first ammo item to cheaty blocks so they can shoot properly
        //（原版 Tile.isEnemyCheat()；等价实现见 Turret.isEnemyCheat(Tile)）
        if(isEnemyCheat(tile) && ammo.size > 0){
            handleItem(ammo.entries().next().key, tile, tile);
        }
    }

    @Override
    public void displayBars(Tile tile, Table bars){
        super.displayBars(tile, bars);

        if(!(tile.entity instanceof ItemTurretEntity)) return;
        ItemTurretEntity entity = (ItemTurretEntity)tile.entity;

        bars.add(new Bar(I18n.get("blocks.ammo", "Ammo"), Pal.ammo, () -> (float)entity.totalAmmo / maxAmmo)).growX();
        bars.row();
    }

    /**
     * 单位卸货时能接收的数量。
     * <p>刻意降级（B 类）：本项目 {@code Block} 不继承 {@code BlockStorage}（{@code acceptStack} 只在
     * {@link com.phoenix.game.world.BlockStorage} 上，且全项目无 {@code .acceptStack(} 调用方），
     * 本方法目前无人调用（保留以对齐原版 API）。
     */
    public int acceptStack(Item item, int amount, Tile tile, BaseUnit source){
        if(!(tile.entity instanceof ItemTurretEntity)) return 0;
        ItemTurretEntity entity = (ItemTurretEntity)tile.entity;

        BulletType type = ammo.get(item);

        if(type == null) return 0;

        return Math.min((int)((maxAmmo - entity.totalAmmo) / type.ammoMultiplier), amount);
    }

    /** 刻意降级（B 类）：同 {@link #acceptStack} —— 本项目 {@code Block} 没有 {@code handleStack} 钩子。 */
    public void handleStack(Item item, int amount, Tile tile, BaseUnit source){
        for(int i = 0; i < amount; i++){
            handleItem(item, tile, null);
        }
    }

    //currently can't remove items from turrets.
    @Override
    public int removeStack(Tile tile, Item item, int amount){
        return 0;
    }

    @Override
    public void handleItem(Item item, Tile tile, Tile source){
        if(!(tile.entity instanceof ItemTurretEntity)) return;
        ItemTurretEntity entity = (ItemTurretEntity)tile.entity;

        if(item == Items.pyratite){
            Events.fire(EventType.Trigger.flameAmmo);
        }

        BulletType type = ammo.get(item);
        if(type == null) return;

        entity.totalAmmo += type.ammoMultiplier;

        //find ammo entry by type
        for(int i = 0; i < entity.ammo.size; i++){
            ItemEntry entry = (ItemEntry)entity.ammo.get(i);

            //if found, put it to the right
            if(entry.item == item){
                entry.amount += type.ammoMultiplier;
                entity.ammo.swap(i, entity.ammo.size - 1);
                return;
            }
        }

        //must not be found
        entity.ammo.add(new ItemEntry(item, (int)type.ammoMultiplier));

        //fire events for the tutorial
        //（Rules.tutorial 与 EventType.TurretAmmoDeliverEvent 均已就绪；Tutorial 监听该事件记 "ammo"）
        if(Vars.state != null && Vars.state.rules.tutorial){
            Events.fire(new EventType.TurretAmmoDeliverEvent());
        }
    }

    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source){
        if(!(tile.entity instanceof ItemTurretEntity)) return false;
        ItemTurretEntity entity = (ItemTurretEntity)tile.entity;

        return ammo != null && ammo.get(item) != null && entity.totalAmmo + ammo.get(item).ammoMultiplier <= maxAmmo;
    }

    /** 弹药条目基类（原版是 {@code Turret} 的静态内部类；本项目 Turret 未移植，故放这里）。 */
    public static abstract class AmmoEntry{
        public int amount;

        public abstract BulletType type();
    }

    /** 炮塔实体：把父类的单一 {@code bullet} 换成弹药表。 */
    public class ItemTurretEntity extends CooledTurretEntity{
        /** 已装填的弹药（末尾 = 最近装的 = 优先使用）。 */
        public final Array<AmmoEntry> ammo = new Array<>();
        /** 弹药总量（按 ammoMultiplier 计）。 */
        public int totalAmmo;
        /** 已射击次数（原版 TurretEntity.shots，DoubleTurret 用它左右交替）。 */
        public int shots;

        //cx()/cy()（瓦片中心）已提到 TileEntity 上，这里不再重复定义

        /** @return 下一次开火会用的弹药；没有弹药返回 null（原版 {@code Turret.peekAmmo}）。 */
        public BulletType peekAmmo(){
            return ammo.isEmpty() ? null : ammo.peek().type();
        }

        /** 原版 {@code PowerTurret.baseReloadSpeed} 的挂钩点（本项目 Turret 未移植该钩子）。 */
        protected float baseReloadSpeed(){
            return 1f;
        }

        @Override
        public boolean hasAmmo(){
            return !ammo.isEmpty() && ammo.peek().amount >= ammoPerShot;
        }

        @Override
        protected void useAmmo(){
            if(ammo.isEmpty()) return;

            AmmoEntry entry = ammo.peek();
            entry.amount -= ammoPerShot;
            if(entry.amount <= 0) ammo.pop();
            totalAmmo -= ammoPerShot;
        }

        @Override
        protected void updateShooting(){
            BulletType type = peekAmmo();
            if(type == null) return;

            if(reload >= ItemTurret.this.reload){
                shoot();
                reload = 0f;
            }else{
                reload += Time.delta() * type.reloadMultiplier * baseReloadSpeed();
            }

            //冷却液加成：本方法完全覆写了 CooledTurretEntity.updateShooting（后者才会调 updateCoolant），
            //必须自己补一次，否则物品炮塔的冷却液完全不起作用（原版 ItemTurret 不覆写 updateShooting，没这个坑）
            updateCoolant();
        }

        /**
         * 覆写父类的开火逻辑：父类用 {@code Turret.bullet}（单一弹药），这里换成 {@code peekAmmo()}。
         * <p>原版对应 {@code Turret.shoot(Tile, BulletType)}。
         */
        @Override
        protected void shoot(){
            BulletType type = peekAmmo();
            if(type == null) return;

            recoil = ItemTurret.this.recoil;
            heat = 1f;

            //炮口横向随机抖动（对应原版 tr.trns(rotation, size * tilesize / 2, Mathf.range(xRand))）
            float xr = Mathf.range(xRand);
            float ox = Angles.trnsx(rotation, size * tilesize / 2f, xr);
            float oy = Angles.trnsy(rotation, size * tilesize / 2f, xr);

            for(int i = 0; i < ItemTurret.this.shots; i++){
                float angle = rotation + Mathf.range(inaccuracy + type.inaccuracy) + (i - ItemTurret.this.shots / 2) * spread;
                Bullet.create(type, this, getTeam(), cx() + ox, cy() + oy, angle);
            }

            effects(cx() + ox, cy() + oy);
            useAmmo();
        }

        /** 炮口特效退回当前弹药自带的（原版 {@code Turret.effects} 用 {@code peekAmmo(tile)}）。 */
        @Override
        protected void effects(float x, float y){
            BulletType type = peekAmmo();
            if(type == null) return;

            Effects.Effect shoot = ItemTurret.this.shootEffect == Fx.none ? type.shootEffect : ItemTurret.this.shootEffect;
            Effects.Effect smoke = ItemTurret.this.smokeEffect == Fx.none ? type.smokeEffect : ItemTurret.this.smokeEffect;

            Effects.effect(shoot, x, y, rotation);
            Effects.effect(smoke, x, y, rotation);

            if(shootSound != null) shootSound.play(1f);

            //开火震屏（对应原版 Turret.effects 里的 shootShake）
            if(shootShake > 0f){
                Effects.shake(shootShake, shootShake, cx(), cy());
            }

            scheduleEject();
        }

        /**
         * 覆写父类的每帧逻辑：父类靠 {@code Turret.bullet != null} 决定能否工作，
         * 这里换成"弹药表里有没有弹"。
         */
        @Override
        public void update(){
            recoil = Mathf.lerpDelta(recoil, 0f, restitution);
            heat = Mathf.lerpDelta(heat, 0f, cooldown);

            BulletType type = peekAmmo();
            if(type == null || isDead()) return;

            if(!validateTarget()) target = null;
            if(!hasAmmo()) return;

            if(timer.get(timerTarget, targetInterval)){
                findTarget();
            }

            if(!validateTarget()) return;

            float speed = type.speed < 0.1f ? 9999999f : type.speed;
            Vector2 aim = Predict.intercept(cx(), cy(), target.getX(), target.getY(),
                    target.getTargetVelocityX(), target.getTargetVelocityY(), speed);
            float targetRot = Angles.angle(cx(), cy(), aim.x, aim.y);

            rotation = Angles.moveToward(rotation, targetRot, rotatespeed * Time.delta() * baseReloadSpeed());

            if(Angles.angleDist(rotation, targetRot) < shootCone){
                updateShooting();
            }
        }

        @Override
        public void write(java.io.DataOutputStream stream) throws java.io.IOException{
            super.write(stream);
            stream.writeByte(ammo.size);
            for(int i = 0; i < ammo.size; i++){
                ItemEntry entry = (ItemEntry)ammo.get(i);
                stream.writeByte(entry.item.id);
                stream.writeShort(entry.amount);
            }
        }

        @Override
        public void read(java.io.DataInputStream stream, byte revision) throws java.io.IOException{
            super.read(stream, revision);
            byte amount = stream.readByte();
            for(int i = 0; i < amount; i++){
                Item item = Vars.content.item(stream.readByte());
                short a = stream.readShort();
                totalAmmo += a;
                ammo.add(new ItemEntry(item, a));
            }
        }
    }

    class ItemEntry extends AmmoEntry{
        protected Item item;

        ItemEntry(Item item, int amount){
            this.item = item;
            this.amount = amount;
        }

        @Override
        public BulletType type(){
            return ammo.get(item);
        }
    }
}
