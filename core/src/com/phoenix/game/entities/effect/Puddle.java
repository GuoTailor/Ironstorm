package com.phoenix.game.entities.effect;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.content.Bullets;
import com.phoenix.game.content.Fx;
import com.phoenix.game.content.Liquids;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Fill;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.Units;
import com.phoenix.game.entities.traits.DrawTrait;
import com.phoenix.game.entities.traits.Entity;
import com.phoenix.game.entities.traits.SaveTrait;
import com.phoenix.game.entities.traits.SyncTrait;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.entities.type.SolidEntity;
import com.phoenix.game.game.Team;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.math.geom.Geometry;
import com.phoenix.game.math.geom.Point2;
import com.phoenix.game.type.Liquid;
import com.phoenix.game.world.Tile;

import java.io.*;

import static com.phoenix.game.Vars.tilesize;
import static com.phoenix.game.Vars.world;

/**
 * 液体水洼。参照 Mindustry mindustry.entities.effect.Puddle 移植。
 * <p>液体从建筑/管道里漏出来会在地面上积成水洼；水洼会缓慢蒸发、向邻格蔓延（最多 2 代）、
 * 给踩上去的地面单位挂状态效果，两种液体相遇还会反应（起火/蒸汽）。
 *
 * <p>刻意偏离（本项目结构 / 未移植依赖）：
 * <ul>
 *   <li><b>仿真仍以静态列表为准</b>：F6 起 {@code Vars.puddleGroup} 已建并接线（{@link #deposit} 里
 *       {@code puddle.add()}、{@code BaseEntity.remove()} 里自动解绑），但本项目的 update/draw 仍走
 *       静态 {@link #all} 列表 + {@link #updateAll()}/{@link #drawAll()}（对应原版 {@code puddleGroup.update()}）。
 *       组的用途是存档（{@code SaveVersion} 遍历 {@code Vars.entities.all()}）。</li>
 *   <li><b>不做对象池</b>：原版 {@code Pools.obtain(Puddle.class, Puddle::new)}，本项目直接 new。</li>
 *   <li><b>不做联机同步</b>：原版 {@code @Remote} 的 {@code onPuddleRemoved} 与 SyncTrait 的 write/read
 *       保留签名（@Remote 按项目约定注释掉），但本工程建筑/子弹逻辑两端各自仿真，水洼同样两端各自生成。</li>
 *   <li><b>液体地板反应已接上</b>：原版读 {@code tile.floor().liquidDrop} 做"液体倒进液体地板"的反应；
 *       本项目 {@code Floor.liquidDrop} **确实存在且已配置**（deepwater/taintedWater/sandWater/…=water，tar=oil，
 *       见 {@code content/Blocks.java:205/246}）→ {@link #deposit} 已照原版实现（water 地板未配 liquidDrop，
 *       判空后按"倒进同种液体"早退，与原版等价）。</li>
 *   <li><b>灯光已接线（S5b 批次）</b>：原版 draw 末尾 {@code renderer.lights.add(...)}（液体自发光）已补上，
 *       见 {@link #draw()} 末尾。</li>
 *   <li><b>@Remote 注解按项目约定注释掉</b>（注解处理器要求配套的 @WriteClass，会导致编译中断）。</li>
 * </ul>
 */
public class Puddle extends SolidEntity implements SaveTrait, com.badlogic.gdx.utils.Pool.Poolable, DrawTrait, SyncTrait{
    /** 场上所有水洼（对应原版 puddleGroup 的遍历）。 */
    public static final Array<Puddle> all = new Array<>();
    /** 按瓦片 pos 索引的水洼：同一格只会有一摊。 */
    private static final IntMap<Puddle> map = new IntMap<>();
    private static final float maxLiquid = 70f;
    private static final int maxGeneration = 2;
    private static final Color tmp = new Color();
    private static final Rectangle rect = new Rectangle();
    private static final Rectangle rect2 = new Rectangle();
    /** arc 的 {@code Color.shiftValue(f)}：三个通道同时加 f（libgdx 没有该方法）。 */
    private static final float colorShift = -0.05f;
    private static int seeds;

    private int loadedPosition = -1;

    private float updateTime;
    private float lastRipple;
    private Tile tile;
    private Liquid liquid;
    private float amount, targetAmount;
    private float accepting;
    private byte generation;

    /** Deserialization use only! */
    public Puddle(){
    }

    /** Deposists a puddle between tile and source. */
    public static void deposit(Tile tile, Tile source, Liquid liquid, float amount){
        deposit(tile, source, liquid, amount, 0);
    }

    /** Deposists a puddle at a tile. */
    public static void deposit(Tile tile, Liquid liquid, float amount){
        deposit(tile, tile, liquid, amount, 0);
    }

    /** Returns the puddle on the specified tile. May return null. */
    public static Puddle getPuddle(Tile tile){
        return map.get(tile.pos());
    }

    private static void deposit(Tile tile, Tile source, Liquid liquid, float amount, int generation){
        if(tile == null) return;

        //原版：if(tile.floor().isLiquid && !canStayOn(liquid, tile.floor().liquidDrop)){ ...反应... return; }
        //⚠ 本项目 Floor.liquidDrop **确实存在且已配置**（deepwater/taintedWater/sandWater/…=water，tar=oil，
        //   见 content/Blocks.java:205/246）→ 该分支已照原版接上。
        //   唯一例外：water 地板（Blocks.java:185）只设了 isLiquid 未设 liquidDrop，需判空；
        //   它等价于"把水倒进水里"，原版也只会生成水波、不积水洼 → 判空后同样早退。
        if(tile.floor().isLiquid){
            Liquid floorLiquid = tile.floor().liquidDrop;

            if(floorLiquid == null || !canStayOn(liquid, floorLiquid)){
                if(floorLiquid != null){
                    reactPuddle(floorLiquid, liquid, amount, tile,
                        (tile.worldx() + source.worldx()) / 2f, (tile.worldy() + source.worldy()) / 2f);
                }

                Puddle p = map.get(tile.pos());

                if(generation == 0 && p != null && p.lastRipple <= Time.time() - 40f){
                    Effects.effect(Fx.ripple, floorLiquid != null ? floorLiquid.color : liquid.color,
                        (tile.worldx() + source.worldx()) / 2f, (tile.worldy() + source.worldy()) / 2f);
                    p.lastRipple = Time.time();
                }
                return;
            }
            //canStayOn（油浮在水面）→ 落到下面通用分支，正常积成水洼
        }

        Puddle p = map.get(tile.pos());
        if(p == null){
            //刻意降级：原版 if(net.client()) return;（水洼是服务端权威）——本项目两端各自仿真，不做该判定

            Puddle puddle = new Puddle();
            puddle.tile = tile;
            puddle.liquid = liquid;
            puddle.amount = amount;
            puddle.generation = (byte)generation;
            puddle.set((tile.worldx() + source.worldx()) / 2f, (tile.worldy() + source.worldy()) / 2f);
            puddle.added();
            all.add(puddle);
            map.put(tile.pos(), puddle);
            puddle.add(); //登记进 puddleGroup（对应原版 Puddle.deposit 里的 puddle.add()）
        }else if(p.liquid == liquid){
            p.accepting = Math.max(amount, p.accepting);

            if(generation == 0 && p.lastRipple <= Time.time() - 40f && p.amount >= maxLiquid / 2f){
                Effects.effect(Fx.ripple, p.liquid.color, (tile.worldx() + source.worldx()) / 2f, (tile.worldy() + source.worldy()) / 2f);
                p.lastRipple = Time.time();
            }
        }else{
            p.amount += reactPuddle(p.liquid, liquid, amount, p.tile, p.x, p.y);
        }
    }

    /**
     * Returns whether the first liquid can 'stay' on the second one.
     * Currently, the only place where this can happen is oil on water.
     */
    private static boolean canStayOn(Liquid liquid, Liquid other){
        return liquid == Liquids.oil && other == Liquids.water;
    }

    /** Reacts two liquids together at a location. */
    private static float reactPuddle(Liquid dest, Liquid liquid, float amount, Tile tile, float x, float y){
        if((dest.flammability > 0.3f && liquid.temperature > 0.7f) ||
        (liquid.flammability > 0.3f && dest.temperature > 0.7f)){ //flammable liquid + hot liquid
            Fire.create(tile);
            if(Mathf.chance(0.006f * amount)){
                Bullet.create(Bullets.fireball, (Entity)null, Team.derelict, x, y, Mathf.random(360f), 1f, 1f, null);
            }
        }else if(dest.temperature > 0.7f && liquid.temperature < 0.55f){ //cold liquid poured onto hot puddle
            if(Mathf.chance(0.5f * amount)){
                Effects.effect(Fx.steam, x, y);
            }
            return -0.1f * amount;
        }else if(liquid.temperature > 0.7f && dest.temperature < 0.55f){ //hot liquid poured onto cold puddle
            if(Mathf.chance(0.8f * amount)){
                Effects.effect(Fx.steam, x, y);
            }
            return -0.4f * amount;
        }
        return 0f;
    }

    //刻意降级：原版是 @Remote(called = Loc.server)（本项目 @Remote 一律保持注释状态，否则注解处理器报 No @WriteClass method）
    /** 服务端要求移除某个水洼（按 id 找）。 */
    public static void onPuddleRemoved(int puddleid){
        for(int i = 0; i < all.size; i++){
            Puddle p = all.get(i);
            if(p.id == puddleid){
                p.remove();
                return;
            }
        }
    }

    public float getFlammability(){
        return liquid.flammability * amount;
    }

    @Override
    public com.phoenix.game.type.TypeID getTypeID(){
        return com.phoenix.game.content.TypeIDs.puddle;
    }

    /** @return 水洼归属 {@link com.phoenix.game.Vars#puddleGroup}（对应原版 {@code Puddle.targetGroup()}）。 */
    @Override
    public com.phoenix.game.entities.EntityGroup targetGroup(){
        return com.phoenix.game.Vars.puddleGroup;
    }

    @Override
    public byte version(){
        return 0;
    }

    @Override
    public void hitbox(Rectangle rect){
        rect.setCenter(x, y).setSize(tilesize);
    }

    @Override
    public void hitboxTile(Rectangle rect){
        rect.setCenter(x, y).setSize(0f);
    }

    @Override
    public void update(){
        //刻意降级：原版 if(net.client()) amount = Mathf.lerpDelta(amount, targetAmount, 0.15f);（客机插值）
        //     本项目不做联机同步，恒走下面这份服务端逻辑
        float addSpeed = accepting > 0 ? 3f : 0f;

        amount -= Time.delta() * (1f - liquid.viscosity) / (5f + addSpeed);

        amount += accepting;
        accepting = 0f;

        if(amount >= maxLiquid / 1.5f && generation < maxGeneration){
            float deposited = Math.min((amount - maxLiquid / 1.5f) / 4f, 0.3f) * Time.delta();
            for(GridPoint2 point : Geometry.d4){
                Tile other = world.tile(tile.x + point.x, tile.y + point.y);
                if(other != null && other.block() == Blocks.air){
                    deposit(other, tile, liquid, deposited, generation + 1);
                    amount -= deposited / 2f; //tweak to speed up/slow down puddle propagation
                }
            }
        }

        amount = Mathf.clamp(amount, 0, maxLiquid);

        if(amount <= 0f){
            onPuddleRemoved(getID());
        }

        //effects-only code
        if(amount >= maxLiquid / 2f && updateTime <= 0f){
            rect.setSize(Mathf.clamp(amount / (maxLiquid / 1.5f)) * 10f).setCenter(x, y);
            Units.nearby(rect.x, rect.y, rect.width, rect.height, unit -> {
                if(unit.isFlying()) return;

                unit.hitbox(rect2);
                if(!rect.overlaps(rect2)) return;

                unit.applyEffect(liquid.effect, 60 * 2);

                if(unit.velocity().len() > 0.1f){
                    Effects.effect(Fx.ripple, liquid.color, unit.x, unit.y);
                }
            });

            if(liquid.temperature > 0.7f && tile.link().entity != null && Mathf.chance(0.3f * Time.delta())){
                Fire.create(tile);
            }

            updateTime = 20f;
        }

        updateTime -= Time.delta();
    }

    /** 每帧更新所有水洼（对应原版 puddleGroup.update）。由 {@code core.Logic.update()} 调用。 */
    public static void updateAll(){
        //⚠ 不能照 Fire.updateAll 那样再判 isDead() 调 all.removeIndex(i)：
        //   本项目 BaseEntity.remove() 是**同步**回调 removed()，而 Puddle.removed() 里已 all.removeValue(this)，
        //   若再 removeIndex(i) 会误删被顶到 i 位置的另一个水洼（倒序遍历时那个已被更新过）。
        for(int i = all.size - 1; i >= 0; i--){
            all.get(i).update();
        }
    }

    /** 绘制所有水洼（对应原版 puddleGroup 的绘制）。由 {@code core.Renderer.draw()} 调用。 */
    public static void drawAll(){
        for(int i = 0; i < all.size; i++){
            all.get(i).draw();
        }
    }

    /** 清空所有水洼（回菜单/重开一局时用）。 */
    public static void clear(){
        all.clear();
        map.clear();
        //同步清空 puddleGroup（解绑 + 清队列）
        if(com.phoenix.game.Vars.puddleGroup != null) com.phoenix.game.Vars.puddleGroup.clear();
    }

    @Override
    public void draw(){
        seeds = id;
        boolean onLiquid = tile.floor().isLiquid;
        float f = Mathf.clamp(amount / (maxLiquid / 1.5f));
        float smag = onLiquid ? 0.8f : 0f;
        float sscl = 20f;

        tmp.set(Mathf.clamp(liquid.color.r + colorShift), Mathf.clamp(liquid.color.g + colorShift),
            Mathf.clamp(liquid.color.b + colorShift), liquid.color.a);
        Draw.color(tmp);
        Fill.circle(x + Mathf.sin(Time.time + seeds * 532, sscl, smag), y + Mathf.sin(Time.time + seeds * 53, sscl, smag), f * 8f);
        Angles.randLenVectors(id, 3, f * 6f, (ex, ey) -> {
            Fill.circle(x + ex + Mathf.sin(Time.time + seeds * 532, sscl, smag),
            y + ey + Mathf.sin(Time.time + seeds * 53, sscl, smag), f * 5f);
            seeds++;
        });
        Draw.color();

        //水洼自发光（对应原版 Puddle.draw 末尾，见原版 Puddle.java:237-242）；已接线（S5b 批次）
        if(liquid.lightColor.a > 0.001f && f > 0f){
            Color color = liquid.lightColor;
            float opacity = color.a * f;
            if(com.phoenix.game.Vars.renderer != null){
                //⚠ 坐标口径：原版 tile.drawx()/drawy() 是瓦片中心；本项目 worldx()/worldy() 是角点
                // → 用 tile.block().centerX/centerY（空瓦片是 Blocks.air，size=1/offset=0，等价于瓦片中心）
                com.phoenix.game.Vars.renderer.lights.add(tile.block().centerX(tile), tile.block().centerY(tile),
                    30f * f, color, opacity * 0.8f);
            }
        }
    }

    @Override
    public float drawSize(){
        return 20;
    }

    @Override
    public void writeSave(DataOutput stream) throws IOException{
        stream.writeInt(tile.pos());
        stream.writeFloat(x);
        stream.writeFloat(y);
        stream.writeByte(liquid.id);
        stream.writeFloat(amount);
        stream.writeByte(generation);
    }

    @Override
    public void readSave(DataInput stream, byte version) throws IOException{
        this.loadedPosition = stream.readInt();
        this.x = stream.readFloat();
        this.y = stream.readFloat();
        this.liquid = com.phoenix.game.Vars.content.liquid(stream.readByte());
        this.amount = stream.readFloat();
        this.generation = stream.readByte();
        added();
        all.add(this);
        add(); //登记进 puddleGroup（与 deposit 同口径）
    }

    @Override
    public void reset(){
        loadedPosition = -1;
        tile = null;
        liquid = null;
        amount = 0;
        generation = 0;
        accepting = 0;
    }

    @Override
    public void added(){
        if(loadedPosition != -1){
            map.put(loadedPosition, this);
            tile = world.tile(loadedPosition);
        }
    }

    @Override
    public void removed(){
        if(tile != null){
            map.remove(tile.pos());
        }
        all.removeValue(this, true);
        reset();
    }

    @Override
    public void write(DataOutput data) throws IOException{
        data.writeFloat(x);
        data.writeFloat(y);
        data.writeByte(liquid.id);
        data.writeShort((short)(amount * 4));
        data.writeInt(tile.pos());
    }

    @Override
    public void read(DataInput data) throws IOException{
        x = data.readFloat();
        y = data.readFloat();
        liquid = com.phoenix.game.Vars.content.liquid(data.readByte());
        targetAmount = data.readShort() / 4f;
        int pos = data.readInt();
        tile = world.tile(pos);

        map.put(pos, this);
    }
}
