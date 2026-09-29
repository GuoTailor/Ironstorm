package com.phoenix.game.world.blocks.power;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.phoenix.game.content.Fx;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Events;
import com.phoenix.game.core.Fill;
import com.phoenix.game.core.Interval;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.Damage;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.game.EventType.Trigger;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.Item;
import com.phoenix.game.type.Liquid;
import com.phoenix.game.ui.Bar;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.consumers.ConsumeItems;
import com.phoenix.game.world.consumers.ConsumeLiquid;
import com.phoenix.game.world.consumers.ConsumeType;
import com.phoenix.game.world.meta.BlockStat;
import com.phoenix.game.world.meta.I18n;
import com.phoenix.game.world.meta.StatUnit;
import io.anuke.mindustry.gen.Sounds;

import static com.phoenix.game.Vars.tilesize;

/**
 * 钍反应堆（原版方块名 {@code thorium-reactor}）。参照 Mindustry
 * mindustry.world.blocks.power.NuclearReactor 移植。
 * <p>烧钍发电：发电效率 = 库存钍 / 容量；每 {@link #itemDuration} 帧消耗一个钍。
 * 堆芯会随燃烧升温（{@link #heating}），靠低温流体冷却；热量超过 {@link #smokeThreshold} 冒烟，
 * 到 1.0 熔毁自爆。被摧毁时若"燃料 ≥ 5 或热量 ≥ 0.5"则核爆。
 *
 * <p><b>已注册为内容名 {@code thorium-reactor}</b>（见 {@code Blocks.java}）：原版没有单独的
 * "ThoriumReactor" 类，{@code thorium-reactor} 就是用本类注册的；本工程早先那个同名的简化版
 * {@code world/blocks/power/ThoriumReactor.java}（无液体、堆芯不升温、不注册）已随 **R2 批**删除。
 * <p>⚠ 因此钍反应堆现在**必须供低温流体**（{@code consumes.liquid(Liquids.cryofluid, 0.04f)}）：
 * 没有冷却液时堆芯每帧升温，热量到 1 就熔毁自爆（原版行为）。
 *
 * <p>与原版的差异（每处都有 {@code //TODO} 标注）：
 * <ul>
 *     <li>{@code rebuildable = false}（原版构造器，B 类）：本项目 {@code Block} 没有该字段；
 *         它的消费方是原版 {@code Logic} 的 {@code BlockDestroyEvent} 监听（把可重建方块推进
 *         {@code TeamData.brokenBlocks} 队列），而本项目的 {@code Teams} 没有 {@code TeamData/BrokenBlock}
 *         → 无消费方。</li>
 *     <li>{@code timers++} 的计时器槽位：本项目 {@code Block} 没有 {@code timers}，
 *         改为实体自带 {@link Interval}（槽位常量 {@link #timerFuel} 保留以对齐原版命名）。</li>
 *     <li>{@code onDestroyed(Tile)}：本项目 {@code Block} 没有该钩子，核爆逻辑写在实体 {@code kill()} 里
 *         （与既有 {@code ImpactReactor} 一致）。</li>
 *     <li>{@code state.rules.reactorExplosions}：T2 批已补该字段，核爆前判它（关掉则堆芯不爆）。</li>
 *     <li>{@code drawLight(Tile)}（{@code renderer.lights.add}）：<b>已接线（S5a 批次）</b>，
 *         见 {@link #drawLight(Tile)}。</li>
 *     <li>{@code Sounds.explosionbig.at(tile)}：本项目音效没有位置衰减，改成 {@code play(1f)}。</li>
 *     <li>{@code Damage.damage(x, y, radius, damage)}（不分敌我）→ 本项目对应 {@code Damage.damageAll}。</li>
 *     <li>原版 {@code setBars()} 里那条 heat 条 → 改写成覆写 {@link #displayBars(Tile, Table)}。</li>
 *     <li>实体内部类照 {@code Drill}/{@code Pump} 的写法改成**非静态**内部类。</li>
 * </ul>
 */
public class NuclearReactor extends PowerGenerator{
    /** 燃料消耗计时器槽位（原版 {@code timers++}；本项目计时器挂在实体上，这里只是槽位常量）。 */
    public final int timerFuel = 0;

    /** 爆炸特效的随机偏移复用向量（对应原版 {@code Vec2 tr}）。 */
    public final Vector2 tr = new Vector2();

    public Color lightColor = Color.valueOf("7f19ea");
    public Color coolColor = new Color(1, 1, 1, 0f);
    public Color hotColor = Color.valueOf("ff9575a3");
    /** 消耗一个燃料所需帧数。 */
    public float itemDuration = 120;
    /** 每帧升温量（乘燃料充满度）。 */
    public float heating = 0.01f;
    /** 开始冒烟的热量阈值。 */
    public float smokeThreshold = 0.3f;
    /** 爆炸半径（世界单位）。 */
    public int explosionRadius = 40;
    public int explosionDamage = 1350;
    /** 灯光开始闪烁的热量阈值。 */
    public float flashThreshold = 0.46f;
    /** 每单位热量需要消耗的冷却液。 */
    public float coolantPower = 0.5f;

    public TextureRegion topRegion, lightsRegion;

    public NuclearReactor(String name){
        super(name);
        itemCapacity = 30;
        liquidCapacity = 30;
        hasItems = true;
        hasLiquids = true;
        entityType = NuclearReactorEntity::new;
        //TODO（B 类）原版 rebuildable = false：本项目 Block 没有该字段，也没有读它的
        //     Logic 重建队列（Teams 无 TeamData/BrokenBlock）→ 无消费方
    }

    @Override
    public void setStats(){
        super.setStats();

        if(hasItems){
            stats.add(BlockStat.productionTime, itemDuration / 60f, StatUnit.seconds);
        }
    }

    @Override
    public void load(){
        super.load();
        if(Core.atlas == null) return;

        topRegion = Core.atlas.findRegion(name + "-center");
        lightsRegion = Core.atlas.findRegion(name + "-lights");
    }

    /** 对应原版 {@code setBars()} 里的 heat 条（本项目没有 bars，改在这里追加）。 */
    @Override
    public void displayBars(Tile tile, Table table){
        super.displayBars(tile, table);
        if(!(tile.entity instanceof NuclearReactorEntity)) return;

        NuclearReactorEntity entity = (NuclearReactorEntity)tile.entity;
        table.add(new Bar(I18n.get("bar.heat", "Heat"), Pal.lightOrange, () -> entity.heat))
            .height(18f).growX().pad(4f);
        table.row();
    }

    /** 堆芯发光（对应原版 {@code NuclearReactor.drawLight}，见原版 NuclearReactor.java:153-158）。 */
    @Override
    public void drawLight(Tile tile){
        if(!(tile.entity instanceof NuclearReactorEntity)) return;
        if(com.phoenix.game.Vars.renderer == null) return;

        NuclearReactorEntity entity = (NuclearReactorEntity)tile.entity;
        float fract = entity.productionEfficiency;
        //原版用 tile.drawx()/drawy()（方块中心）；本项目 worldx()/worldy() 是瓦片角点 → 用 centerX/centerY
        com.phoenix.game.Vars.renderer.lights.add(centerX(tile), centerY(tile),
            (90f + Mathf.absin(5, 5f)) * fract,
            com.phoenix.game.core.Tmp.c1.set(lightColor).lerp(Color.SCARLET, entity.heat),
            0.6f * fract);
    }

    @Override
    public void draw(Tile tile){
        super.draw(tile);

        if(!(tile.entity instanceof NuclearReactorEntity)) return;
        NuclearReactorEntity entity = (NuclearReactorEntity)tile.entity;

        Draw.color(coolColor, hotColor, entity.heat);
        Fill.rect(centerX(tile), centerY(tile), size * tilesize, size * tilesize);

        Draw.color(entity.liquids.current().color);
        Draw.alpha(entity.liquids.currentAmount() / liquidCapacity);
        if(topRegion != null) Draw.rect(topRegion, centerX(tile), centerY(tile));

        if(entity.heat > flashThreshold){
            float flash = 1f + ((entity.heat - flashThreshold) / (1f - flashThreshold)) * 5.4f;
            entity.flash += flash * Time.delta();
            Draw.color(Color.RED, Color.YELLOW, Mathf.absin(entity.flash, 9f, 1f));
            Draw.alpha(0.6f);
            if(lightsRegion != null) Draw.rect(lightsRegion, centerX(tile), centerY(tile));
        }

        //本项目 Draw 没有 reset()，用 color() 复位颜色（等价）
        Draw.color();
    }

    /** 钍反应堆实体（原版 {@code NuclearReactorEntity}）。 */
    public class NuclearReactorEntity extends GeneratorEntity{
        public float heat;
        public float flash;

        /** 燃料消耗计时器（对应原版 {@code entity.timer.get(timerFuel, ...)}）。 */
        public final Interval timer = new Interval(1);

        /** 防止爆炸伤害把同一座堆反复引爆（kill → 爆炸 → 再次伤害 → kill 的递归）。 */
        private boolean exploded;

        /** 对应原版 {@code NuclearReactor.update(Tile)}（本项目统一写在实体里）。 */
        @Override
        public void update(){
            ConsumeLiquid cliquid = consumes.get(ConsumeType.liquid);
            //原版直接取 consumes.get(item).items[0].item；这里容错：没配消耗器时不产热
            ConsumeItems consumeItem = consumes.get(ConsumeType.item);
            Item item = consumeItem == null ? null : consumeItem.items[0].item;

            int fuel = (item == null || items == null) ? 0 : items.get(item);
            float fullness = (float)fuel / itemCapacity;
            productionEfficiency = fullness;

            if(fuel > 0){
                heat += fullness * heating * Math.min(delta(), 4f);

                if(timer.get(timerFuel, itemDuration / timeScale)){
                    cons.trigger();
                }
            }

            if(cliquid != null && liquids != null){
                Liquid liquid = cliquid.liquid;

                if(heat > 0){
                    float maxUsed = Math.min(liquids.get(liquid), heat / coolantPower);
                    heat -= maxUsed * coolantPower;
                    liquids.remove(liquid, maxUsed);
                }
            }

            if(heat > smokeThreshold){
                //从 1.0 到 2.0 之间
                float smoke = 1.0f + (heat - smokeThreshold) / (1f - smokeThreshold);
                if(Mathf.chance(smoke / 20f * delta())){
                    Effects.effect(Fx.reactorsmoke, block.centerX(tile) + Mathf.range(size * tilesize / 2f),
                        block.centerY(tile) + Mathf.random(size * tilesize / 2f));
                }
            }

            heat = Mathf.clamp(heat);

            if(heat >= 0.999f){
                Events.fire(Trigger.thoriumReactorOverheat);
                kill();
            }
        }

        /**
         * 对应原版 {@code onDestroyed(Tile)}：堆芯够热或还有燃料就核爆。
         * <p>本项目没有 {@code Block.onDestroyed} 钩子，所以写在这里（先爆炸再移除）。
         */
        @Override
        public void kill(){
            if(!exploded && tile != null){
                exploded = true;

                ConsumeItems consumeItem = consumes.get(ConsumeType.item);
                Item item = consumeItem == null ? null : consumeItem.items[0].item;
                int fuel = (item == null || items == null) ? 0 : items.get(item);

                //原版判据：(fuel < 5 && heat < 0.5f) || !state.rules.reactorExplosions 时不爆
                if((fuel >= 5 || heat >= 0.5f) && com.phoenix.game.Vars.state.rules.reactorExplosions){
                    float cx = block.centerX(tile), cy = block.centerY(tile);

                    //原版 Sounds.explosionbig.at(tile)（本项目音效无位置衰减，直接播）
                    Sounds.explosionbig.play(1f);

                    Effects.shake(6f, 16f, cx, cy);
                    Effects.effect(Fx.nuclearShockwave, cx, cy);
                    for(int i = 0; i < 6; i++){
                        Time.run(Mathf.random(40f), () -> Effects.effect(Fx.nuclearcloud, cx, cy));
                    }

                    Damage.damageAll(cx, cy, explosionRadius * tilesize, explosionDamage * 4f);

                    for(int i = 0; i < 20; i++){
                        Time.run(Mathf.random(50f), () -> {
                            tr.set(Mathf.random(40f), 0f).rotateDeg(Mathf.random(360f));
                            Effects.effect(Fx.explosion, tr.x + cx, tr.y + cy);
                        });
                    }

                    for(int i = 0; i < 70; i++){
                        Time.run(Mathf.random(80f), () -> {
                            tr.set(Mathf.random(120f), 0f).rotateDeg(Mathf.random(360f));
                            Effects.effect(Fx.nuclearsmoke, tr.x + cx, tr.y + cy);
                        });
                    }
                }
            }
            super.kill();
        }

        @Override
        public void write(java.io.DataOutputStream out) throws java.io.IOException{
            super.write(out);
            out.writeFloat(heat);
        }

        @Override
        public void read(java.io.DataInputStream in, byte revision) throws java.io.IOException{
            super.read(in, revision);
            heat = in.readFloat();
            //productionEfficiency 由库存重算；flash/exploded 是瞬时量，不入档
        }
    }
}
