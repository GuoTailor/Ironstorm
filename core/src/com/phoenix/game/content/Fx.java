package com.phoenix.game.content;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Drawf;
import com.phoenix.game.core.Fill;
import com.phoenix.game.core.Lines;
import com.phoenix.game.core.Tmp;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.Item;

import static com.phoenix.game.Vars.tilesize;

/**
 * 特效定义集合。参照 Mindustry mindustry.content.Fx 逐条移植（Build 126.2）。
 *
 * <p>绘制原语：原版用 {@code Lines}/{@code Fill}/{@code Drawf} 直接写三角形，本工程对应
 * {@link Lines}/{@link Fill}/{@link Drawf}（内部用 ShapeRenderer 实现，见 {@link Drawf} 的类注释）。
 *
 * <p>与原版的刻意偏离（均不影响玩法，只影响观感）：
 * <ul>
 *   <li>{@code unitDrop}/{@code unitLand}/{@code unitPickup}/{@code landShock}/{@code shellEject*}
 *       原版是 {@code GroundEffect}（把特效按地面透视压扁、并排在地面层）；本工程未移植地面层，
 *       一律退化成普通 {@link Effects.Effect}。</li>
 *   <li>{@code fire} 原版会往光照系统塞一个光源；本工程无光照系统，跳过那一步。</li>
 *   <li>{@code freezing}/{@code melting}/{@code wet}/{@code oily} 原版取 {@code Liquids.*.color}；
 *       液体系统未移植，这里用原版液体的字面色值代替（见下方 {@code *Color} 常量）。</li>
 *   <li>{@code bubble} 原版用 {@code shiftValue(0.1f)} 提亮；libgdx 的 Color 没有该 API，用 {@code mul(1.2f)} 近似。</li>
 * </ul>
 */
public class Fx{

    //液体系统未移植：这几个色值取自原版 Liquids 对应液体的 color，仅为让粒子有正确颜色
    private static final Color cryofluidColor = Color.valueOf("8ff4f4");
    private static final Color slagColor = Color.valueOf("ffa166");
    private static final Color waterColor = Color.valueOf("596ab8");
    private static final Color oilColor = Color.valueOf("313131");

    // ---------------- 基础 ----------------

    public static final Effects.Effect none = new Effects.Effect(0f, 0f, e -> {});

    /** 单位出生：图标由大缩小（原版用单位图标贴图）。 */
    public static final Effects.Effect unitSpawn = new Effects.Effect(30f, 28f, e -> {
        if(!(e.data instanceof BaseUnit)) return;

        Draw.alpha(e.fin());

        float scl = 1f + e.fout() * 2f;

        BaseUnit unit = (BaseUnit)e.data;
        TextureRegion icon = unit.getIconRegion();
        if(icon != null){
            Draw.rect(icon, e.x, e.y, icon.getRegionWidth() * Draw.scl * scl,
                icon.getRegionWidth() * Draw.scl * scl, 180f);
        }
    });

    /** 下达指令：一圈扩散的指挥色圆环。 */
    public static final Effects.Effect commandSend = new Effects.Effect(28f, 28f, e -> {
        Draw.color(Pal.command);
        Lines.stroke(e.fout() * 2f);
        Lines.circle(e.x, e.y, 4f + e.finpow() * 120f);
    });

    /** RTS 移动命令确认（对应 v8 Fx.moveCommand）：一圈扩散的指挥色圆环。 */
    public static final Effects.Effect moveCommand = new Effects.Effect(28f, 28f, e -> {
        Draw.color(Pal.command);
        Lines.stroke(e.fout() * 2f);
        Lines.circle(e.x, e.y, e.finpow() * 70f);
    });

    /** RTS 攻击命令确认（对应 v8 Fx.attackCommand）：旋转收缩的红色四方框。 */
    public static final Effects.Effect attackCommand = new Effects.Effect(28f, 28f, e -> {
        Draw.color(Pal.remove);
        Lines.stroke(e.fout() * 2f);
        Lines.square(e.x, e.y, 4f + e.finpow() * 40f, e.fin() * 90f);
    });

    /** 放置方块：一圈向外张开的方框（e.rotation = 方块边长）。 */
    public static final Effects.Effect placeBlock = new Effects.Effect(16f, 28f, e -> {
        Draw.color(Pal.accent);
        Lines.stroke(3f - e.fin() * 2f);
        Lines.square(e.x, e.y, tilesize / 2f * e.rotation + e.fin() * 3f);
    });

    /** 点击方块：一圈扩散的圆（e.rotation = 方块边长）。 */
    public static final Effects.Effect tapBlock = new Effects.Effect(12f, 28f, e -> {
        Draw.color(Pal.accent);
        Lines.stroke(3f - e.fin() * 2f);
        Lines.circle(e.x, e.y, 4f + (tilesize / 1.5f * e.rotation) * e.fin());
    });

    /** 拆除方块：红色方框 + 飞散的碎块（e.rotation = 方块边长）。 */
    public static final Effects.Effect breakBlock = new Effects.Effect(12f, 28f, e -> {
        Draw.color(Pal.remove);
        Lines.stroke(3f - e.fin() * 2f);
        Lines.square(e.x, e.y, tilesize / 2f * e.rotation + e.fin() * 3f);

        Angles.randLenVectors(e.id, 3 + (int)(e.rotation * 3), e.rotation * 2f + (tilesize * e.rotation) * e.finpow(), (x, y) -> {
            Fill.square(e.x + x, e.y + y, 1f + e.fout() * (3f + e.rotation));
        });
    });

    /** 选中：一圈向外扩散的细环。 */
    public static final Effects.Effect select = new Effects.Effect(23f, 28f, e -> {
        Draw.color(Pal.accent);
        Lines.stroke(e.fout() * 3f);
        Lines.circle(e.x, e.y, 3f + e.fin() * 14f);
    });

    /** 烟：一颗逐渐缩小的灰球。 */
    public static final Effects.Effect smoke = new Effects.Effect(100f, 28f, e -> {
        Draw.color(Color.GRAY, Pal.darkishGray, e.fin());
        float size = 7f - e.fin() * 7f;
        rect("circle", e.x, e.y, size, size);
    });

    /** 岩浆烟：地面上鼓起的灰球。 */
    public static final Effects.Effect magmasmoke = new Effects.Effect(110f, 28f, e -> {
        Draw.color(Color.GRAY);
        Fill.circle(e.x, e.y, e.fslope() * 6f);
    });

    /** 生成：一圈方框。 */
    public static final Effects.Effect spawn = new Effects.Effect(30f, 28f, e -> {
        Lines.stroke(2f * e.fout());
        Draw.color(Pal.accent);
        Lines.poly(e.x, e.y, 4, 5f + e.fin() * 12f);
    });

    /** 发射台点火：一圈快速放大的方框。 */
    public static final Effects.Effect padlaunch = new Effects.Effect(10f, 28f, e -> {
        Lines.stroke(4f * e.fout());
        Draw.color(Pal.accent);
        Lines.poly(e.x, e.y, 4, 5f + e.fin() * 60f);
    });

    /** 悬浮单位尾焰：沿朝向喷出的小火点。 */
    public static final Effects.Effect vtolHover = new Effects.Effect(40f, 28f, e -> {
        float len = e.finpow() * 10f;
        float ang = e.rotation + Mathf.randomSeedRange(e.id, 30f);
        Draw.color(Pal.lightFlame, Pal.lightOrange, e.fin());
        Fill.circle(e.x + Angles.trnsx(ang, len), e.y + Angles.trnsy(ang, len), 2f * e.fout());
    });

    /** 单位落地：向外扬起的土粒。 */
    public static final Effects.Effect unitDrop = new Effects.Effect(30f, 28f, e -> {
        Draw.color(Pal.lightishGray);
        Angles.randLenVectors(e.id, 9, 3 + 20f * e.finpow(), (x, y) -> {
            Fill.circle(e.x + x, e.y + y, e.fout() * 4f + 0.4f);
        });
    });

    /** 单位着陆：向外的土粒，颜色取单位自身。 */
    public static final Effects.Effect unitLand = new Effects.Effect(30f, 28f, e -> {
        Draw.color(Tmp.c1.set(e.color).mul(1.1f));
        Angles.randLenVectors(e.id, 6, 17f * e.finpow(), (x, y) -> {
            Fill.circle(e.x + x, e.y + y, e.fout() * 4f + 0.3f);
        });
    });

    /** 单位被装载：向内收拢的方框。 */
    public static final Effects.Effect unitPickup = new Effects.Effect(18f, 28f, e -> {
        Draw.color(Pal.lightishGray);
        Lines.stroke(e.fin() * 2f);
        Lines.poly(e.x, e.y, 4, 13f * e.fout());
    });

    /** 落地冲击：一圈十二边形。 */
    public static final Effects.Effect landShock = new Effects.Effect(12f, 28f, e -> {
        Draw.color(Pal.lancerLaser);
        Lines.stroke(e.fout() * 3f);
        Lines.poly(e.x, e.y, 12, 20f * e.fout());
    });

    /** 拾取：六根向外的小刺。 */
    public static final Effects.Effect pickup = new Effects.Effect(18f, 28f, e -> {
        Draw.color(Pal.lightishGray);
        Lines.stroke(e.fout() * 2f);
        Lines.spikes(e.x, e.y, 1f + e.fin() * 6f, e.fout() * 4f, 6);
    });

    /** 治疗波：扩散的绿环。 */
    public static final Effects.Effect healWave = new Effects.Effect(22f, 28f, e -> {
        Draw.color(Pal.heal);
        Lines.stroke(e.fout() * 2f);
        Lines.circle(e.x, e.y, 4f + e.finpow() * 60f);
    });

    /** 治疗：小绿环。 */
    public static final Effects.Effect heal = new Effects.Effect(11f, 28f, e -> {
        Draw.color(Pal.heal);
        Lines.stroke(e.fout() * 2f);
        Lines.circle(e.x, e.y, 2f + e.finpow() * 7f);
    });

    // ---------------- 命中 ----------------

    /** 小口径子弹命中。 */
    public static final Effects.Effect hitBulletSmall = new Effects.Effect(14f, 28f, e -> {
        Draw.color(Color.WHITE, Pal.lightOrange, e.fin());

        e.scaled(7f, s -> {
            Lines.stroke(0.5f + s.fout());
            Lines.circle(e.x, e.y, s.fin() * 5f);
        });

        Lines.stroke(0.5f + e.fout());

        Angles.randLenVectors(e.id, 5, e.fin() * 15f, (x, y) -> {
            float ang = Mathf.angle(x, y);
            Lines.lineAngle(e.x + x, e.y + y, ang, e.fout() * 3 + 1f);
        });
    });

    /** 保险丝弹命中（surge 色）。 */
    public static final Effects.Effect hitFuse = new Effects.Effect(14f, 28f, e -> {
        Draw.color(Color.WHITE, Pal.surge, e.fin());

        e.scaled(7f, s -> {
            Lines.stroke(0.5f + s.fout());
            Lines.circle(e.x, e.y, s.fin() * 7f);
        });

        Lines.stroke(0.5f + e.fout());

        Angles.randLenVectors(e.id, 6, e.fin() * 15f, (x, y) -> {
            float ang = Mathf.angle(x, y);
            Lines.lineAngle(e.x + x, e.y + y, ang, e.fout() * 3 + 1f);
        });
    });

    /** 大口径子弹命中：沿弹道方向的一束溅射。 */
    public static final Effects.Effect hitBulletBig = new Effects.Effect(13f, 28f, e -> {
        Draw.color(Color.WHITE, Pal.lightOrange, e.fin());
        Lines.stroke(0.5f + e.fout() * 1.5f);

        Angles.randLenVectors(e.id, 8, e.finpow() * 30f, e.rotation, 50f, (x, y) -> {
            float ang = Mathf.angle(x, y);
            Lines.lineAngle(e.x + x, e.y + y, ang, e.fout() * 4 + 1.5f);
        });
    });

    /** 火焰弹命中。 */
    public static final Effects.Effect hitFlameSmall = new Effects.Effect(14f, 28f, e -> {
        Draw.color(Pal.lightFlame, Pal.darkFlame, e.fin());
        Lines.stroke(0.5f + e.fout());

        Angles.randLenVectors(e.id, 2, e.fin() * 15f, e.rotation, 50f, (x, y) -> {
            float ang = Mathf.angle(x, y);
            Lines.lineAngle(e.x + x, e.y + y, ang, e.fout() * 3 + 1f);
        });
    });

    /** 液体弹命中：向反方向溅开的小圆点（e.color = 液体颜色）。 */
    public static final Effects.Effect hitLiquid = new Effects.Effect(16f, 28f, e -> {
        Draw.color(e.color);

        Angles.randLenVectors(e.id, 5, e.fin() * 15f, e.rotation + 180f, 60f, (x, y) -> {
            Fill.circle(e.x + x, e.y + y, e.fout() * 2f);
        });
    });

    /** 激光命中（lancer 炮塔）。 */
    public static final Effects.Effect hitLancer = new Effects.Effect(12f, 28f, e -> {
        Draw.color(Color.WHITE);
        Lines.stroke(e.fout() * 1.5f);

        Angles.randLenVectors(e.id, 8, e.finpow() * 17f, e.rotation, 360f, (x, y) -> {
            float ang = Mathf.angle(x, y);
            Lines.lineAngle(e.x + x, e.y + y, ang, e.fout() * 4 + 1f);
        });
    });

    /** 熔毁炮命中。 */
    public static final Effects.Effect hitMeltdown = new Effects.Effect(12f, 28f, e -> {
        Draw.color(Pal.meltdownHit);
        Lines.stroke(e.fout() * 2f);

        Angles.randLenVectors(e.id, 6, e.finpow() * 18f, e.rotation, 360f, (x, y) -> {
            float ang = Mathf.angle(x, y);
            Lines.lineAngle(e.x + x, e.y + y, ang, e.fout() * 4 + 1f);
        });
    });

    /** 激光命中（小）。 */
    public static final Effects.Effect hitLaser = new Effects.Effect(8f, 28f, e -> {
        Draw.color(Color.WHITE, Pal.heal, e.fin());
        Lines.stroke(0.5f + e.fout());
        Lines.circle(e.x, e.y, e.fin() * 5f);
    });

    /** 消失：沿朝向散开的短线。 */
    public static final Effects.Effect despawn = new Effects.Effect(12f, 28f, e -> {
        Draw.color(Pal.lighterOrange, Color.GRAY, e.fin());
        Lines.stroke(e.fout());

        Angles.randLenVectors(e.id, 7, e.fin() * 7f, e.rotation, 40f, (x, y) -> {
            float ang = Mathf.angle(x, y);
            Lines.lineAngle(e.x + x, e.y + y, ang, e.fout() * 2 + 1f);
        });
    });

    // ---------------- 爆炸 ----------------

    /** 通用爆炸：白球 + 灰烟 + 橙色溅射。 */
    public static final Effects.Effect explosion = new Effects.Effect(30f, 28f, e -> {
        e.scaled(7f, i -> {
            Lines.stroke(3f * i.fout());
            Lines.circle(e.x, e.y, 3f + i.fin() * 10f);
        });

        Draw.color(Color.GRAY);

        Angles.randLenVectors(e.id, 6, 2f + 19f * e.finpow(), (x, y) -> {
            Fill.circle(e.x + x, e.y + y, e.fout() * 3f + 0.5f);
            Fill.circle(e.x + x / 2f, e.y + y / 2f, e.fout() * 1f);
        });

        Draw.color(Pal.lighterOrange, Pal.lightOrange, Color.GRAY, e.fin());
        Lines.stroke(1.5f * e.fout());

        Angles.randLenVectors(e.id + 1, 8, 1f + 23f * e.finpow(), (x, y) -> {
            Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), 1f + e.fout() * 3f);
        });
    });

    /** 可变强度爆炸（e.rotation = 强度倍数）。 */
    public static final Effects.Effect dynamicExplosion = new Effects.Effect(30f, 28f, e -> {
        float intensity = e.rotation;

        e.scaled(5 + intensity * 2, i -> {
            Lines.stroke(3.1f * i.fout());
            Lines.circle(e.x, e.y, (3f + i.fin() * 14f) * intensity);
        });

        Draw.color(Color.GRAY);

        Angles.randLenVectors(e.id, e.finpow(), (int)(6 * intensity), 21f * intensity, (x, y, in, out) -> {
            Fill.circle(e.x + x, e.y + y, out * (2f + intensity) * 3 + 0.5f);
            Fill.circle(e.x + x / 2f, e.y + y / 2f, out * (intensity) * 3);
        });

        Draw.color(Pal.lighterOrange, Pal.lightOrange, Color.GRAY, e.fin());
        Lines.stroke((1.7f * e.fout()) * (1f + (intensity - 1f) / 2f));

        Angles.randLenVectors(e.id + 1, e.finpow(), (int)(9 * intensity), 40f * intensity, (x, y, in, out) -> {
            Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), 1f + out * 4 * (3f + intensity));
        });
    });

    /** 建筑爆炸。 */
    public static final Effects.Effect blockExplosion = new Effects.Effect(30f, 28f, e -> {
        e.scaled(7f, i -> {
            Lines.stroke(3.1f * i.fout());
            Lines.circle(e.x, e.y, 3f + i.fin() * 14f);
        });

        Draw.color(Color.GRAY);

        Angles.randLenVectors(e.id, 6, 2f + 19f * e.finpow(), (x, y) -> {
            Fill.circle(e.x + x, e.y + y, e.fout() * 3f + 0.5f);
            Fill.circle(e.x + x / 2f, e.y + y / 2f, e.fout() * 1f);
        });

        Draw.color(Pal.lighterOrange, Pal.lightOrange, Color.GRAY, e.fin());
        Lines.stroke(1.7f * e.fout());

        Angles.randLenVectors(e.id + 1, 9, 1f + 23f * e.finpow(), (x, y) -> {
            Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), 1f + e.fout() * 3f);
        });
    });

    /** 建筑爆炸的烟（单独播，寿命比火球长）。 */
    public static final Effects.Effect blockExplosionSmoke = new Effects.Effect(30f, 28f, e -> {
        Draw.color(Color.GRAY);

        Angles.randLenVectors(e.id, 6, 4f + 30f * e.finpow(), (x, y) -> {
            Fill.circle(e.x + x, e.y + y, e.fout() * 3f);
            Fill.circle(e.x + x / 2f, e.y + y / 2f, e.fout() * 1f);
        });
    });

    /** 破片爆炸。 */
    public static final Effects.Effect flakExplosion = new Effects.Effect(20f, 28f, e -> {
        Draw.color(Pal.bulletYellow);
        e.scaled(6f, i -> {
            Lines.stroke(3f * i.fout());
            Lines.circle(e.x, e.y, 3f + i.fin() * 10f);
        });

        Draw.color(Color.GRAY);

        Angles.randLenVectors(e.id, 5, 2f + 23f * e.finpow(), (x, y) -> {
            Fill.circle(e.x + x, e.y + y, e.fout() * 3f + 0.5f);
        });

        Draw.color(Pal.lighterOrange);
        Lines.stroke(1f * e.fout());

        Angles.randLenVectors(e.id + 1, 4, 1f + 23f * e.finpow(), (x, y) -> {
            Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), 1f + e.fout() * 3f);
        });
    });

    /** 大破片爆炸。 */
    public static final Effects.Effect flakExplosionBig = new Effects.Effect(30f, 28f, e -> {
        Draw.color(Pal.bulletYellowBack);
        e.scaled(6f, i -> {
            Lines.stroke(3f * i.fout());
            Lines.circle(e.x, e.y, 3f + i.fin() * 25f);
        });

        Draw.color(Color.GRAY);

        Angles.randLenVectors(e.id, 6, 2f + 23f * e.finpow(), (x, y) -> {
            Fill.circle(e.x + x, e.y + y, e.fout() * 4f + 0.5f);
        });

        Draw.color(Pal.bulletYellow);
        Lines.stroke(1f * e.fout());

        Angles.randLenVectors(e.id + 1, 4, 1f + 23f * e.finpow(), (x, y) -> {
            Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), 1f + e.fout() * 3f);
        });
    });

    /** 塑钢爆炸。 */
    public static final Effects.Effect plasticExplosion = new Effects.Effect(24f, 28f, e -> {
        Draw.color(Pal.plastaniumFront);
        e.scaled(7f, i -> {
            Lines.stroke(3f * i.fout());
            Lines.circle(e.x, e.y, 3f + i.fin() * 24f);
        });

        Draw.color(Color.GRAY);

        Angles.randLenVectors(e.id, 7, 2f + 28f * e.finpow(), (x, y) -> {
            Fill.circle(e.x + x, e.y + y, e.fout() * 4f + 0.5f);
        });

        Draw.color(Pal.plastaniumBack);
        Lines.stroke(1f * e.fout());

        Angles.randLenVectors(e.id + 1, 4, 1f + 25f * e.finpow(), (x, y) -> {
            Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), 1f + e.fout() * 3f);
        });
    });

    /** 塑钢破片爆炸。 */
    public static final Effects.Effect plasticExplosionFlak = new Effects.Effect(28f, 28f, e -> {
        Draw.color(Pal.plastaniumFront);
        e.scaled(7f, i -> {
            Lines.stroke(3f * i.fout());
            Lines.circle(e.x, e.y, 3f + i.fin() * 34f);
        });

        Draw.color(Color.GRAY);

        Angles.randLenVectors(e.id, 7, 2f + 30f * e.finpow(), (x, y) -> {
            Fill.circle(e.x + x, e.y + y, e.fout() * 4f + 0.5f);
        });

        Draw.color(Pal.plastaniumBack);
        Lines.stroke(1f * e.fout());

        Angles.randLenVectors(e.id + 1, 4, 1f + 30f * e.finpow(), (x, y) -> {
            Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), 1f + e.fout() * 3f);
        });
    });

    /** 冲击爆炸（导弹用）。 */
    public static final Effects.Effect blastExplosion = new Effects.Effect(22f, 28f, e -> {
        Draw.color(Pal.missileYellow);
        e.scaled(6f, i -> {
            Lines.stroke(3f * i.fout());
            Lines.circle(e.x, e.y, 3f + i.fin() * 15f);
        });

        Draw.color(Color.GRAY);

        Angles.randLenVectors(e.id, 5, 2f + 23f * e.finpow(), (x, y) -> {
            Fill.circle(e.x + x, e.y + y, e.fout() * 4f + 0.5f);
        });

        Draw.color(Pal.missileYellowBack);
        Lines.stroke(1f * e.fout());

        Angles.randLenVectors(e.id + 1, 4, 1f + 23f * e.finpow(), (x, y) -> {
            Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), 1f + e.fout() * 3f);
        });
    });

    // ---------------- 弹道拖尾 ----------------

    /** 炮弹拖尾（e.rotation = 初始半径）。 */
    public static final Effects.Effect artilleryTrail = new Effects.Effect(50f, 28f, e -> {
        Draw.color(e.color);
        Fill.circle(e.x, e.y, e.rotation * e.fout());
    });

    /** 燃烧弹拖尾。 */
    public static final Effects.Effect incendTrail = new Effects.Effect(50f, 28f, e -> {
        Draw.color(Pal.lightOrange);
        Fill.circle(e.x, e.y, e.rotation * e.fout());
    });

    /** 导弹拖尾。 */
    public static final Effects.Effect missileTrail = new Effects.Effect(50f, 28f, e -> {
        Draw.color(e.color);
        Fill.circle(e.x, e.y, e.rotation * e.fout());
    });

    /** 护盾吸收：向内收的细环。 */
    public static final Effects.Effect absorb = new Effects.Effect(12f, 28f, e -> {
        Draw.color(Pal.accent);
        Lines.stroke(2f * e.fout());
        Lines.circle(e.x, e.y, 5f * e.fout());
    });

    // ---------------- 燃烧 / 状态粒子 ----------------

    /** 燃烧：向上飘的火点。 */
    public static final Effects.Effect burning = new Effects.Effect(35f, 28f, e -> {
        Draw.color(Pal.lightFlame, Pal.darkFlame, e.fin());

        Angles.randLenVectors(e.id, 3, 2f + e.fin() * 7f, (x, y) -> {
            Fill.circle(e.x + x, e.y + y, 0.1f + e.fout() * 1.4f);
        });
    });

    /** 火焰：更旺的火点（原版还会往光照系统塞一个光源，本工程无光照系统，跳过）。 */
    public static final Effects.Effect fire = new Effects.Effect(50f, 28f, e -> {
        Draw.color(Pal.lightFlame, Pal.darkFlame, e.fin());

        Angles.randLenVectors(e.id, 2, 2f + e.fin() * 9f, (x, y) -> {
            Fill.circle(e.x + x, e.y + y, 0.2f + e.fslope() * 1.5f);
        });

        Draw.color();
    });

    /** 火焰烟。 */
    public static final Effects.Effect fireSmoke = new Effects.Effect(35f, 28f, e -> {
        Draw.color(Color.GRAY);

        Angles.randLenVectors(e.id, 1, 2f + e.fin() * 7f, (x, y) -> {
            Fill.circle(e.x + x, e.y + y, 0.2f + e.fslope() * 1.5f);
        });
    });

    /** 蒸汽。 */
    public static final Effects.Effect steam = new Effects.Effect(35f, 28f, e -> {
        Draw.color(Color.LIGHT_GRAY);

        Angles.randLenVectors(e.id, 2, 2f + e.fin() * 7f, (x, y) -> {
            Fill.circle(e.x + x, e.y + y, 0.2f + e.fslope() * 1.5f);
        });
    });

    /** 火球烟。 */
    public static final Effects.Effect fireballsmoke = new Effects.Effect(25f, 28f, e -> {
        Draw.color(Color.GRAY);

        Angles.randLenVectors(e.id, 1, 2f + e.fin() * 7f, (x, y) -> {
            Fill.circle(e.x + x, e.y + y, 0.2f + e.fout() * 1.5f);
        });
    });

    /** 火球。 */
    public static final Effects.Effect ballfire = new Effects.Effect(25f, 28f, e -> {
        Draw.color(Pal.lightFlame, Pal.darkFlame, e.fin());

        Angles.randLenVectors(e.id, 2, 2f + e.fin() * 7f, (x, y) -> {
            Fill.circle(e.x + x, e.y + y, 0.2f + e.fout() * 1.5f);
        });
    });

    /** 冰冻（原版取 cryofluid 的颜色）。 */
    public static final Effects.Effect freezing = new Effects.Effect(40f, 28f, e -> {
        Draw.color(cryofluidColor);

        Angles.randLenVectors(e.id, 2, 1f + e.fin() * 2f, (x, y) -> {
            Fill.circle(e.x + x, e.y + y, e.fout() * 1.2f);
        });
    });

    /** 熔化（原版取 slag 的颜色）。 */
    public static final Effects.Effect melting = new Effects.Effect(40f, 28f, e -> {
        Draw.color(slagColor, Color.WHITE, e.fout() / 5f + Mathf.randomSeedRange(e.id, 0.12f));

        Angles.randLenVectors(e.id, 2, 1f + e.fin() * 3f, (x, y) -> {
            Fill.circle(e.x + x, e.y + y, .2f + e.fout() * 1.2f);
        });
    });

    /** 潮湿（原版取 water 的颜色）。 */
    public static final Effects.Effect wet = new Effects.Effect(40f, 28f, e -> {
        Draw.color(waterColor);

        Angles.randLenVectors(e.id, 2, 1f + e.fin() * 2f, (x, y) -> {
            Fill.circle(e.x + x, e.y + y, e.fout() * 1f);
        });
    });

    /** 沾油（原版取 oil 的颜色）。 */
    public static final Effects.Effect oily = new Effects.Effect(42f, 28f, e -> {
        Draw.color(oilColor);

        Angles.randLenVectors(e.id, 2, 1f + e.fin() * 2f, (x, y) -> {
            Fill.circle(e.x + x, e.y + y, e.fout() * 1f);
        });
    });

    /** 超频中：橙色小方块。 */
    public static final Effects.Effect overdriven = new Effects.Effect(20f, 28f, e -> {
        Draw.color(Pal.accent);

        Angles.randLenVectors(e.id, 2, 1f + e.fin() * 2f, (x, y) -> {
            Fill.square(e.x + x, e.y + y, e.fout() * 2.3f + 0.5f);
        });
    });

    /** 掉落物品：物品图标沿 e.rotation 抛出（e.data = Item）。 */
    public static final Effects.Effect dropItem = new Effects.Effect(20f, 28f, e -> {
        float length = 20f * e.finpow();
        float size = 7f * e.fout();

        if(e.data instanceof Item){
            TextureRegion icon = ((Item)e.data).icon();
            if(icon != null){
                Draw.rect(icon, e.x + Angles.trnsx(e.rotation, length), e.y + Angles.trnsy(e.rotation, length), size, size);
            }
        }
    });

    // ---------------- 冲击波 ----------------

    /** 小冲击波。 */
    public static final Effects.Effect shockwave = new Effects.Effect(10f, 80f, e -> {
        Draw.color(Color.WHITE, Color.LIGHT_GRAY, e.fin());
        Lines.stroke(e.fout() * 2f + 0.2f);
        Lines.circle(e.x, e.y, e.fin() * 28f);
    });

    /** 大冲击波。 */
    public static final Effects.Effect bigShockwave = new Effects.Effect(10f, 80f, e -> {
        Draw.color(Color.WHITE, Color.LIGHT_GRAY, e.fin());
        Lines.stroke(e.fout() * 3f);
        Lines.circle(e.x, e.y, e.fin() * 50f);
    });

    /** 核弹冲击波。 */
    public static final Effects.Effect nuclearShockwave = new Effects.Effect(10f, 200f, e -> {
        Draw.color(Color.WHITE, Color.LIGHT_GRAY, e.fin());
        Lines.stroke(e.fout() * 3f + 0.2f);
        Lines.circle(e.x, e.y, e.fin() * 140f);
    });

    /** 陨石冲击波。 */
    public static final Effects.Effect impactShockwave = new Effects.Effect(13f, 300f, e -> {
        Draw.color(Pal.lighterOrange, Color.LIGHT_GRAY, e.fin());
        Lines.stroke(e.fout() * 4f + 0.2f);
        Lines.circle(e.x, e.y, e.fin() * 200f);
    });

    /** 出生冲击波（e.rotation = 额外半径）。 */
    public static final Effects.Effect spawnShockwave = new Effects.Effect(20f, 400f, e -> {
        Draw.color(Color.WHITE, Color.LIGHT_GRAY, e.fin());
        Lines.stroke(e.fout() * 3f + 0.5f);
        Lines.circle(e.x, e.y, e.fin() * (e.rotation + 50f));
    });

    // ---------------- 开火 ----------------

    /** 小口径炮口闪光。 */
    public static final Effects.Effect shootSmall = new Effects.Effect(8f, 28f, e -> {
        Draw.color(Pal.lighterOrange, Pal.lightOrange, e.fin());
        float w = 1f + 5 * e.fout();
        Drawf.tri(e.x, e.y, w, 15f * e.fout(), e.rotation);
        Drawf.tri(e.x, e.y, w, 3f * e.fout(), e.rotation + 180f);
    });

    /** 治疗弹炮口闪光。 */
    public static final Effects.Effect shootHeal = new Effects.Effect(8f, 28f, e -> {
        Draw.color(Pal.heal);
        float w = 1f + 5 * e.fout();
        Drawf.tri(e.x, e.y, w, 17f * e.fout(), e.rotation);
        Drawf.tri(e.x, e.y, w, 4f * e.fout(), e.rotation + 180f);
    });

    /** 小口径炮口烟。 */
    public static final Effects.Effect shootSmallSmoke = new Effects.Effect(20f, 28f, e -> {
        Draw.color(Pal.lighterOrange, Color.LIGHT_GRAY, Color.GRAY, e.fin());

        Angles.randLenVectors(e.id, 5, e.finpow() * 6f, e.rotation, 20f, (x, y) -> {
            Fill.circle(e.x + x, e.y + y, e.fout() * 1.5f);
        });
    });

    /** 大口径炮口闪光。 */
    public static final Effects.Effect shootBig = new Effects.Effect(9f, 28f, e -> {
        Draw.color(Pal.lighterOrange, Pal.lightOrange, e.fin());
        float w = 1.2f + 7 * e.fout();
        Drawf.tri(e.x, e.y, w, 25f * e.fout(), e.rotation);
        Drawf.tri(e.x, e.y, w, 4f * e.fout(), e.rotation + 180f);
    });

    /** 大口径炮口闪光（灰色调）。 */
    public static final Effects.Effect shootBig2 = new Effects.Effect(10f, 28f, e -> {
        Draw.color(Pal.lightOrange, Color.GRAY, e.fin());
        float w = 1.2f + 8 * e.fout();
        Drawf.tri(e.x, e.y, w, 29f * e.fout(), e.rotation);
        Drawf.tri(e.x, e.y, w, 5f * e.fout(), e.rotation + 180f);
    });

    /** 大口径炮口烟。 */
    public static final Effects.Effect shootBigSmoke = new Effects.Effect(17f, 28f, e -> {
        Draw.color(Pal.lighterOrange, Color.LIGHT_GRAY, Color.GRAY, e.fin());

        Angles.randLenVectors(e.id, 8, e.finpow() * 19f, e.rotation, 10f, (x, y) -> {
            Fill.circle(e.x + x, e.y + y, e.fout() * 2f + 0.2f);
        });
    });

    /** 大口径炮口烟（更浓）。 */
    public static final Effects.Effect shootBigSmoke2 = new Effects.Effect(18f, 28f, e -> {
        Draw.color(Pal.lightOrange, Color.LIGHT_GRAY, Color.GRAY, e.fin());

        Angles.randLenVectors(e.id, 9, e.finpow() * 23f, e.rotation, 20f, (x, y) -> {
            Fill.circle(e.x + x, e.y + y, e.fout() * 2.4f + 0.2f);
        });
    });

    /** 火焰喷射。 */
    public static final Effects.Effect shootSmallFlame = new Effects.Effect(32f, 28f, e -> {
        Draw.color(Pal.lightFlame, Pal.darkFlame, Color.GRAY, e.fin());

        Angles.randLenVectors(e.id, 8, e.finpow() * 60f, e.rotation, 10f, (x, y) -> {
            Fill.circle(e.x + x, e.y + y, 0.65f + e.fout() * 1.5f);
        });
    });

    /** 硫火喷射（pyra 炮塔）。 */
    public static final Effects.Effect shootPyraFlame = new Effects.Effect(33f, 28f, e -> {
        Draw.color(Pal.lightPyraFlame, Pal.darkPyraFlame, Color.GRAY, e.fin());

        Angles.randLenVectors(e.id, 10, e.finpow() * 70f, e.rotation, 10f, (x, y) -> {
            Fill.circle(e.x + x, e.y + y, 0.65f + e.fout() * 1.6f);
        });
    });

    /** 液体喷射（e.color = 液体颜色）。 */
    public static final Effects.Effect shootLiquid = new Effects.Effect(40f, 28f, e -> {
        Draw.color(e.color, Color.WHITE, e.fout() / 6f + Mathf.randomSeedRange(e.id, 0.1f));

        Angles.randLenVectors(e.id, 6, e.finpow() * 60f, e.rotation, 11f, (x, y) -> {
            Fill.circle(e.x + x, e.y + y, 0.5f + e.fout() * 2.5f);
        });
    });

    // ---------------- 抛壳 ----------------

    /** 抛小弹壳。 */
    public static final Effects.Effect shellEjectSmall = new Effects.Effect(30f, 400f, e -> {
        Draw.color(Pal.lightOrange, Color.LIGHT_GRAY, Pal.lightishGray, e.fin());
        float rot = Math.abs(e.rotation) + 90f;

        int i = Mathf.sign(e.rotation);

        float len = (2f + e.finpow() * 6f) * i;
        float lr = rot + e.fin() * 30f * i;
        Fill.rect(e.x + Angles.trnsx(lr, len) + Mathf.randomSeedRange(e.id + i + 7, 3f * e.fin()),
            e.y + Angles.trnsy(lr, len) + Mathf.randomSeedRange(e.id + i + 8, 3f * e.fin()),
            1f, 2f, rot + e.fin() * 50f * i);
    });

    /** 抛中弹壳。 */
    public static final Effects.Effect shellEjectMedium = new Effects.Effect(34f, 400f, e -> {
        Draw.color(Pal.lightOrange, Color.LIGHT_GRAY, Pal.lightishGray, e.fin());
        float rot = e.rotation + 90f;
        for(int i : Mathf.signs){
            float len = (2f + e.finpow() * 10f) * i;
            float lr = rot + e.fin() * 20f * i;
            rect("casing",
                e.x + Angles.trnsx(lr, len) + Mathf.randomSeedRange(e.id + i + 7, 3f * e.fin()),
                e.y + Angles.trnsy(lr, len) + Mathf.randomSeedRange(e.id + i + 8, 3f * e.fin()),
                2f, 3f, rot);
        }

        Draw.color(Color.LIGHT_GRAY, Color.GRAY, e.fin());

        for(int i : Mathf.signs){
            float ex = e.x, ey = e.y, fout = e.fout();
            Angles.randLenVectors(e.id, 4, 1f + e.finpow() * 11f, e.rotation + 90f * i, 20f, (x, y) -> {
                Fill.circle(ex + x, ey + y, fout * 1.5f);
            });
        }
    });

    /** 抛大弹壳。 */
    public static final Effects.Effect shellEjectBig = new Effects.Effect(22f, 400f, e -> {
        Draw.color(Pal.lightOrange, Color.LIGHT_GRAY, Pal.lightishGray, e.fin());
        float rot = e.rotation + 90f;
        for(int i : Mathf.signs){
            float len = (4f + e.finpow() * 8f) * i;
            float lr = rot + Mathf.randomSeedRange(e.id + i + 6, 20f * e.fin()) * i;
            rect("casing",
                e.x + Angles.trnsx(lr, len) + Mathf.randomSeedRange(e.id + i + 7, 3f * e.fin()),
                e.y + Angles.trnsy(lr, len) + Mathf.randomSeedRange(e.id + i + 8, 3f * e.fin()),
                2.5f, 4f,
                rot + e.fin() * 30f * i + Mathf.randomSeedRange(e.id + i + 9, 40f * e.fin()));
        }

        Draw.color(Color.LIGHT_GRAY);

        for(int i : Mathf.signs){
            float ex = e.x, ey = e.y, fout = e.fout();
            Angles.randLenVectors(e.id, 4, -e.finpow() * 15f, e.rotation + 90f * i, 25f, (x, y) -> {
                Fill.circle(ex + x, ey + y, fout * 2f);
            });
        }
    });

    // ---------------- 激光 / 闪电 ----------------

    /** lancer 炮塔开火：两片向两侧张开的三角。 */
    public static final Effects.Effect lancerLaserShoot = new Effects.Effect(21f, 28f, e -> {
        Draw.color(Pal.lancerLaser);

        for(int i : Mathf.signs){
            Drawf.tri(e.x, e.y, 4f * e.fout(), 29f, e.rotation + 90f * i);
        }
    });

    /** lancer 开火的烟。 */
    public static final Effects.Effect lancerLaserShootSmoke = new Effects.Effect(26f, 28f, e -> {
        Draw.color(Pal.lancerLaser);

        Angles.randLenVectors(e.id, 7, 80f, e.rotation, 0f, (x, y) -> {
            Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), e.fout() * 9f);
        });
    });

    /** lancer 蓄力（末段）。 */
    public static final Effects.Effect lancerLaserCharge = new Effects.Effect(38f, 28f, e -> {
        Draw.color(Pal.lancerLaser);

        Angles.randLenVectors(e.id, 2, 1f + 20f * e.fout(), e.rotation, 120f, (x, y) -> {
            Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), e.fslope() * 3f + 1f);
        });
    });

    /** lancer 蓄力（起始：一个逐渐变大的实心点）。 */
    public static final Effects.Effect lancerLaserChargeBegin = new Effects.Effect(71f, 28f, e -> {
        Draw.color(Pal.lancerLaser);
        Fill.circle(e.x, e.y, e.fin() * 3f);

        Draw.color();
        Fill.circle(e.x, e.y, e.fin() * 2f);
    });

    /** 闪电蓄力。 */
    public static final Effects.Effect lightningCharge = new Effects.Effect(38f, 28f, e -> {
        Draw.color(Pal.lancerLaser);

        Angles.randLenVectors(e.id, 2, 1f + 20f * e.fout(), e.rotation, 120f, (x, y) -> {
            Drawf.tri(e.x + x, e.y + y, e.fslope() * 3f + 1, e.fslope() * 3f + 1, Mathf.angle(x, y));
        });
    });

    /** 闪电开火。 */
    public static final Effects.Effect lightningShoot = new Effects.Effect(12f, 28f, e -> {
        Draw.color(Color.WHITE, Pal.lancerLaser, e.fin());
        Lines.stroke(e.fout() * 1.2f + 0.5f);

        Angles.randLenVectors(e.id, 7, 25f * e.finpow(), e.rotation, 50f, (x, y) -> {
            Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), e.fin() * 5f + 2f);
        });
    });

    // ---------------- 反应堆 / 核 ----------------

    /** 反应堆烟。 */
    public static final Effects.Effect reactorsmoke = new Effects.Effect(17f, 28f, e -> {
        Angles.randLenVectors(e.id, 4, e.fin() * 8f, (x, y) -> {
            float size = 1f + e.fout() * 5f;
            Draw.color(Color.LIGHT_GRAY, Color.GRAY, e.fin());
            rect("circle", e.x + x, e.y + y, size, size);
        });
    });

    /** 核烟。 */
    public static final Effects.Effect nuclearsmoke = new Effects.Effect(40f, 28f, e -> {
        Angles.randLenVectors(e.id, 4, e.fin() * 13f, (x, y) -> {
            float size = e.fslope() * 4f;
            Draw.color(Color.LIGHT_GRAY, Color.GRAY, e.fin());
            rect("circle", e.x + x, e.y + y, size, size);
        });
    });

    /** 核云：大范围的黄绿色云团。 */
    public static final Effects.Effect nuclearcloud = new Effects.Effect(90f, 200f, e -> {
        Angles.randLenVectors(e.id, 10, e.finpow() * 90f, (x, y) -> {
            float size = e.fout() * 14f;
            Draw.color(Color.LIME, Color.GRAY, e.fin());
            rect("circle", e.x + x, e.y + y, size, size);
        });
    });

    /** 陨石烟。 */
    public static final Effects.Effect impactsmoke = new Effects.Effect(60f, 28f, e -> {
        Angles.randLenVectors(e.id, 7, e.fin() * 20f, (x, y) -> {
            float size = e.fslope() * 4f;
            Draw.color(Color.LIGHT_GRAY, Color.GRAY, e.fin());
            rect("circle", e.x + x, e.y + y, size, size);
        });
    });

    /** 陨石云。 */
    public static final Effects.Effect impactcloud = new Effects.Effect(140f, 400f, e -> {
        Angles.randLenVectors(e.id, 20, e.finpow() * 160f, (x, y) -> {
            float size = e.fout() * 15f;
            Draw.color(Pal.lighterOrange, Color.LIGHT_GRAY, e.fin());
            rect("circle", e.x + x, e.y + y, size, size);
        });
    });

    // ---------------- 生产 / 采矿 ----------------

    /** 红色发电火花。 */
    public static final Effects.Effect redgeneratespark = new Effects.Effect(18f, 28f, e -> {
        Angles.randLenVectors(e.id, 5, e.fin() * 8f, (x, y) -> {
            float len = e.fout() * 4f;
            Draw.color(Pal.redSpark, Color.GRAY, e.fin());
            rect("circle", e.x + x, e.y + y, len, len);
        });
    });

    /** 发电火花。 */
    public static final Effects.Effect generatespark = new Effects.Effect(18f, 28f, e -> {
        Angles.randLenVectors(e.id, 5, e.fin() * 8f, (x, y) -> {
            float len = e.fout() * 4f;
            Draw.color(Pal.orangeSpark, Color.GRAY, e.fin());
            rect("circle", e.x + x, e.y + y, len, len);
        });
    });

    /** 燃料燃烧。 */
    public static final Effects.Effect fuelburn = new Effects.Effect(23f, 28f, e -> {
        Angles.randLenVectors(e.id, 5, e.fin() * 9f, (x, y) -> {
            float len = e.fout() * 4f;
            Draw.color(Color.LIGHT_GRAY, Color.GRAY, e.fin());
            rect("circle", e.x + x, e.y + y, len, len);
        });
    });

    /** 塑钢燃烧。 */
    public static final Effects.Effect plasticburn = new Effects.Effect(40f, 28f, e -> {
        Angles.randLenVectors(e.id, 5, 3f + e.fin() * 5f, (x, y) -> {
            Draw.color(Color.valueOf("e9ead3"), Color.GRAY, e.fin());
            Fill.circle(e.x + x, e.y + y, e.fout() * 1f);
        });
    });

    /** 粉碎（石粉）。 */
    public static final Effects.Effect pulverize = new Effects.Effect(40f, 28f, e -> {
        Angles.randLenVectors(e.id, 5, 3f + e.fin() * 8f, (x, y) -> {
            Draw.color(Pal.stoneGray);
            Fill.square(e.x + x, e.y + y, e.fout() * 2f + 0.5f, 45f);
        });
    });

    /** 粉碎（红粉）。 */
    public static final Effects.Effect pulverizeRed = new Effects.Effect(40f, 28f, e -> {
        Angles.randLenVectors(e.id, 5, 3f + e.fin() * 8f, (x, y) -> {
            Draw.color(Pal.redDust, Pal.stoneGray, e.fin());
            Fill.square(e.x + x, e.y + y, e.fout() * 2f + 0.5f, 45f);
        });
    });

    /** 粉碎（深红粉）。 */
    public static final Effects.Effect pulverizeRedder = new Effects.Effect(40f, 28f, e -> {
        Angles.randLenVectors(e.id, 5, 3f + e.fin() * 9f, (x, y) -> {
            Draw.color(Pal.redderDust, Pal.stoneGray, e.fin());
            Fill.square(e.x + x, e.y + y, e.fout() * 2.5f + 0.5f, 45f);
        });
    });

    /** 粉碎（小）。 */
    public static final Effects.Effect pulverizeSmall = new Effects.Effect(30f, 28f, e -> {
        Angles.randLenVectors(e.id, 3, e.fin() * 5f, (x, y) -> {
            Draw.color(Pal.stoneGray);
            Fill.square(e.x + x, e.y + y, e.fout() * 1f + 0.5f, 45f);
        });
    });

    /** 粉碎（中）。 */
    public static final Effects.Effect pulverizeMedium = new Effects.Effect(30f, 28f, e -> {
        Angles.randLenVectors(e.id, 5, 3f + e.fin() * 8f, (x, y) -> {
            Draw.color(Pal.stoneGray);
            Fill.square(e.x + x, e.y + y, e.fout() * 1f + 0.5f, 45f);
        });
    });

    /** 生产烟（白色小方块）。 */
    public static final Effects.Effect producesmoke = new Effects.Effect(12f, 28f, e -> {
        Angles.randLenVectors(e.id, 8, 4f + e.fin() * 18f, (x, y) -> {
            Draw.color(Color.WHITE, Pal.accent, e.fin());
            Fill.square(e.x + x, e.y + y, 1f + e.fout() * 3f, 45f);
        });
    });

    /** 熔炼烟（e.color = 产物颜色）。 */
    public static final Effects.Effect smeltsmoke = new Effects.Effect(15f, 28f, e -> {
        Angles.randLenVectors(e.id, 6, 4f + e.fin() * 5f, (x, y) -> {
            Draw.color(Color.WHITE, e.color, e.fin());
            Fill.square(e.x + x, e.y + y, 0.5f + e.fout() * 2f, 45f);
        });
    });

    /** 成型烟。 */
    public static final Effects.Effect formsmoke = new Effects.Effect(40f, 28f, e -> {
        Angles.randLenVectors(e.id, 6, 5f + e.fin() * 8f, (x, y) -> {
            Draw.color(Pal.plasticSmoke, Color.LIGHT_GRAY, e.fin());
            Fill.square(e.x + x, e.y + y, 0.2f + e.fout() * 2f, 45f);
        });
    });

    /** 爆破烟。 */
    public static final Effects.Effect blastsmoke = new Effects.Effect(26f, 28f, e -> {
        Angles.randLenVectors(e.id, 12, 1f + e.fin() * 23f, (x, y) -> {
            float size = 2f + e.fout() * 6f;
            Draw.color(Color.LIGHT_GRAY, Color.DARK_GRAY, e.fin());
            rect("circle", e.x + x, e.y + y, size, size);
        });
    });

    /** 岩浆。 */
    public static final Effects.Effect lava = new Effects.Effect(18f, 28f, e -> {
        Angles.randLenVectors(e.id, 3, 1f + e.fin() * 10f, (x, y) -> {
            float size = e.fslope() * 4f;
            Draw.color(Color.ORANGE, Color.GRAY, e.fin());
            rect("circle", e.x + x, e.y + y, size, size);
        });
    });

    /** 门开（e.rotation = 尺寸倍数）。 */
    public static final Effects.Effect dooropen = new Effects.Effect(10f, 28f, e -> {
        Lines.stroke(e.fout() * 1.6f);
        Lines.square(e.x, e.y, tilesize / 2f + e.fin() * 2f);
    });

    /** 门关。 */
    public static final Effects.Effect doorclose = new Effects.Effect(10f, 28f, e -> {
        Lines.stroke(e.fout() * 1.6f);
        Lines.square(e.x, e.y, tilesize / 2f + e.fout() * 2f);
    });

    /** 大门开。 */
    public static final Effects.Effect dooropenlarge = new Effects.Effect(10f, 28f, e -> {
        Lines.stroke(e.fout() * 1.6f);
        Lines.square(e.x, e.y, tilesize + e.fin() * 2f);
    });

    /** 大门关。 */
    public static final Effects.Effect doorcloselarge = new Effects.Effect(10f, 28f, e -> {
        Lines.stroke(e.fout() * 1.6f);
        Lines.square(e.x, e.y, tilesize + e.fout() * 2f);
    });

    /** 净化（水）。 */
    public static final Effects.Effect purify = new Effects.Effect(10f, 28f, e -> {
        Draw.color(Color.ROYAL, Color.GRAY, e.fin());
        Lines.stroke(2f);
        Lines.spikes(e.x, e.y, e.fin() * 4f, 2, 6);
    });

    /** 净化（油）。 */
    public static final Effects.Effect purifyoil = new Effects.Effect(10f, 28f, e -> {
        Draw.color(Color.BLACK, Color.GRAY, e.fin());
        Lines.stroke(2f);
        Lines.spikes(e.x, e.y, e.fin() * 4f, 2, 6);
    });

    /** 净化（石）。 */
    public static final Effects.Effect purifystone = new Effects.Effect(10f, 28f, e -> {
        Draw.color(Color.ORANGE, Color.GRAY, e.fin());
        Lines.stroke(2f);
        Lines.spikes(e.x, e.y, e.fin() * 4f, 2, 6);
    });

    /** 发电。 */
    public static final Effects.Effect generate = new Effects.Effect(11f, 28f, e -> {
        Draw.color(Color.ORANGE, Color.YELLOW, e.fin());
        Lines.stroke(1f);
        Lines.spikes(e.x, e.y, e.fin() * 5f, 2, 8);
    });

    /** 采矿（e.color = 矿物颜色）。 */
    public static final Effects.Effect mine = new Effects.Effect(20f, 28f, e -> {
        Angles.randLenVectors(e.id, 6, 3f + e.fin() * 6f, (x, y) -> {
            Draw.color(e.color, Color.LIGHT_GRAY, e.fin());
            Fill.square(e.x + x, e.y + y, e.fout() * 2f, 45f);
        });
    });

    /** 采矿（大）。 */
    public static final Effects.Effect mineBig = new Effects.Effect(30f, 28f, e -> {
        Angles.randLenVectors(e.id, 6, 4f + e.fin() * 8f, (x, y) -> {
            Draw.color(e.color, Color.LIGHT_GRAY, e.fin());
            Fill.square(e.x + x, e.y + y, e.fout() * 2f + 0.2f, 45f);
        });
    });

    /** 采矿（巨大）。 */
    public static final Effects.Effect mineHuge = new Effects.Effect(40f, 28f, e -> {
        Angles.randLenVectors(e.id, 8, 5f + e.fin() * 10f, (x, y) -> {
            Draw.color(e.color, Color.LIGHT_GRAY, e.fin());
            Fill.square(e.x + x, e.y + y, e.fout() * 2f + 0.5f, 45f);
        });
    });

    /** 熔炼（e.color = 产物颜色）。 */
    public static final Effects.Effect smelt = new Effects.Effect(20f, 28f, e -> {
        Angles.randLenVectors(e.id, 6, 2f + e.fin() * 5f, (x, y) -> {
            Draw.color(Color.WHITE, e.color, e.fin());
            Fill.square(e.x + x, e.y + y, 0.5f + e.fout() * 2f, 45f);
        });
    });

    // ---------------- 传送 ----------------

    /** 传送激活。 */
    public static final Effects.Effect teleportActivate = new Effects.Effect(50f, 28f, e -> {
        Draw.color(e.color);

        e.scaled(8f, e2 -> {
            Lines.stroke(e2.fout() * 4f);
            Lines.circle(e2.x, e2.y, 4f + e2.fin() * 27f);
        });

        Lines.stroke(e.fout() * 2f);

        Angles.randLenVectors(e.id, 30, 4f + 40f * e.fin(), (x, y) -> {
            Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), e.fin() * 4f + 1f);
        });
    });

    /** 传送中。 */
    public static final Effects.Effect teleport = new Effects.Effect(60f, 28f, e -> {
        Draw.color(e.color);
        Lines.stroke(e.fin() * 2f);
        Lines.circle(e.x, e.y, 7f + e.fout() * 8f);

        Angles.randLenVectors(e.id, 20, 6f + 20f * e.fout(), (x, y) -> {
            Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), e.fin() * 4f + 1f);
        });
    });

    /** 传送离开。 */
    public static final Effects.Effect teleportOut = new Effects.Effect(20f, 28f, e -> {
        Draw.color(e.color);
        Lines.stroke(e.fout() * 2f);
        Lines.circle(e.x, e.y, 7f + e.fin() * 8f);

        Angles.randLenVectors(e.id, 20, 4f + 20f * e.fin(), (x, y) -> {
            Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), e.fslope() * 4f + 1f);
        });
    });

    /** 水面涟漪（e.color = 水色）。 */
    public static final Effects.Effect ripple = new Effects.Effect(30f, 28f, e -> {
        Draw.color(Tmp.c1.set(e.color).mul(1.2f));
        Lines.stroke(e.fout() + 0.4f);
        Lines.circle(e.x, e.y, 2f + e.fin() * 4f);
    });

    /** 气泡（e.color = 水色）。 */
    public static final Effects.Effect bubble = new Effects.Effect(20f, 28f, e -> {
        //原版用 shiftValue(0.1f) 提亮，libgdx 的 Color 没有该 API，用 mul 近似
        Draw.color(Tmp.c1.set(e.color).mul(1.2f));
        Lines.stroke(e.fout() + 0.2f);
        Angles.randLenVectors(e.id, 2, 8f, (x, y) -> {
            Lines.circle(e.x + x, e.y + y, 1f + e.fin() * 3f);
        });
    });

    /** 发射（指挥中心）。 */
    public static final Effects.Effect launch = new Effects.Effect(28f, 28f, e -> {
        Draw.color(Pal.command);
        Lines.stroke(e.fout() * 2f);
        Lines.circle(e.x, e.y, 4f + e.finpow() * 120f);
    });

    // ---------------- 范围波 / 方块高亮 ----------------

    /** 治疗波（投影器）：e.color = 颜色，e.rotation = 最大半径。 */
    public static final Effects.Effect healWaveMend = new Effects.Effect(40f, 28f, e -> {
        Draw.color(e.color);
        Lines.stroke(e.fout() * 2f);
        Lines.circle(e.x, e.y, e.finpow() * e.rotation);
    });

    /** 超频波：e.color = 颜色，e.rotation = 最大半径。 */
    public static final Effects.Effect overdriveWave = new Effects.Effect(50f, 28f, e -> {
        Draw.color(e.color);
        Lines.stroke(e.fout() * 1f);
        Lines.circle(e.x, e.y, e.finpow() * e.rotation);
    });

    /** 治疗建筑：向内收的方框（e.rotation = 方块尺寸倍数）。 */
    public static final Effects.Effect healBlock = new Effects.Effect(20f, 28f, e -> {
        Draw.color(Pal.heal);
        Lines.stroke(2f * e.fout() + 0.5f);
        Lines.square(e.x, e.y, 1f + (e.fin() * e.rotation * tilesize / 2f - 1f));
    });

    /** 治疗建筑（满血闪光）：e.color = 颜色，e.rotation = 方块尺寸倍数。 */
    public static final Effects.Effect healBlockFull = new Effects.Effect(20f, 28f, e -> {
        Draw.color(e.color);
        Draw.alpha(e.fout());
        Fill.square(e.x, e.y, e.rotation * tilesize / 2f);
    });

    /** 超频建筑（满效闪光）。 */
    public static final Effects.Effect overdriveBlockFull = new Effects.Effect(60f, 28f, e -> {
        Draw.color(e.color);
        Draw.alpha(e.fslope() * 0.4f);
        Fill.square(e.x, e.y, e.rotation * tilesize);
    });

    /** 护盾破碎：六边形碎环（e.rotation = 初始半径）。 */
    public static final Effects.Effect shieldBreak = new Effects.Effect(40f, 28f, e -> {
        Draw.color(Pal.accent);
        Lines.stroke(3f * e.fout());
        Lines.poly(e.x, e.y, 6, e.rotation + e.fin(), 90f);
    });

    /** 核心着陆（原版留空占位）。 */
    public static final Effects.Effect coreLand = new Effects.Effect(120f, 28f, e -> {});

    // ==================================================================
    // 以下是本工程自行补充、原版没有对应项的特效（已被建筑/单位逻辑调用）
    // ==================================================================

    /** 火花：一小段白色短线（本工程补充，原版无此项）。 */
    public static final Effects.Effect spark = new Effects.Effect(18f, 10f, e -> {
        float f = e.fin();
        Draw.color(e.color, Color.WHITE, f);
        Draw.alpha(1f - f);
        rect("white", e.x, e.y, 12f * (1f - f) + 2f, 3f, e.rotation);
        Draw.color();
    });

    /** 通用命中：一小圈白点（本工程补充，原版按口径分了 hitBulletSmall/Big）。 */
    public static final Effects.Effect hit = new Effects.Effect(10f, 10f, e -> {
        Draw.color(e.color, Color.WHITE, e.fin());
        Lines.stroke(1f + e.fout());
        Lines.circle(e.x, e.y, 2f + e.fin() * 5f);
    });

    /** 岩壁碎裂：飞散的碎石（本工程补充）。 */
    public static final Effects.Effect rockBreak = new Effects.Effect(18f, 30f, e -> {
        Draw.color(Pal.stoneGray, Color.DARK_GRAY, e.fin());
        Angles.randLenVectors(e.id, 8, 3f + 10f * e.fin(), (x, y) -> {
            Fill.circle(e.x + x, e.y + y, 4f * e.fout() + 1f);
        });
    });

    /** 单位死亡爆炸（本工程补充）。 */
    public static final Effects.Effect unitDeath = new Effects.Effect(22f, 40f, e -> {
        Draw.color(e.color, Color.WHITE, e.fin());
        Draw.alpha(1f - e.fin());
        Fill.circle(e.x, e.y, 36f * e.fin() + 4f);
        Draw.color();
    });

    /** 单位死亡碎片：向外抛洒并下落的碎块（本工程补充）。 */
    public static final Effects.Effect unitDebris = new Effects.Effect(24f, 20f, e -> {
        Draw.color(e.color, Color.DARK_GRAY, e.fin());
        Draw.alpha(1f - e.fin());
        for(int i = 0; i < 6; i++){
            float ang = e.rotation + i * 60f + (e.rand(i) - 0.5f) * 60f;
            float dist = 4f + 16f * e.fin();
            float size = 4f * (1f - e.fin()) + 1f;
            Fill.square(e.x + Angles.trnsx(ang, dist), e.y + Angles.trnsy(ang, dist) - e.fin() * e.fin() * 5f,
                size, ang + e.fin() * 180f);
        }
        Draw.color();
    });

    /** 反应堆核爆：白 → 橙 → 暗红的巨大球体（本工程补充）。 */
    public static final Effects.Effect reactorExplosion = new Effects.Effect(60f, 160f, e -> {
        float f = e.fin();
        Draw.color(Color.WHITE, Pal.lightFlame, Mathf.clamp(f * 2.5f));
        Draw.color(Pal.lightFlame, Pal.darkFlame, f);
        Draw.alpha(1f - f * 0.85f);
        Fill.circle(e.x, e.y, 160f * f + 8f);
        Draw.color();
    });

    /** 浓烟：多个上浮的灰色圆（本工程补充）。 */
    public static final Effects.Effect smokeCloud = new Effects.Effect(60f, 40f, e -> {
        Draw.color(Pal.darkishGray, Color.DARK_GRAY, e.fin());
        Draw.alpha((1f - e.fin()) * 0.5f);
        for(int i = 0; i < 3; i++){
            float off = (e.rand(i) - 0.5f) * 10f;
            float size = 14f * e.fin() + 6f;
            Fill.circle(e.x + off, e.y + e.fin() * 10f + i * 3f, size / 2f);
        }
        Draw.color();
    });

    /** 小爆炸（本工程补充）。 */
    public static final Effects.Effect explosionSmall = new Effects.Effect(18f, 30f, e -> {
        Draw.color(e.color, Color.WHITE, e.fin());
        Draw.alpha(1f - e.fin());
        Fill.circle(e.x, e.y, 15f * e.fin() + 3f);
        Draw.color();
    });

    /** 大爆炸（本工程补充）。 */
    public static final Effects.Effect explosionBig = new Effects.Effect(45f, 80f, e -> {
        Draw.color(e.color, Color.WHITE, e.fin());
        Draw.alpha(1f - e.fin());
        Fill.circle(e.x, e.y, 40f * e.fin() + 6f);
        Draw.color();
    });

    // ---------------- 贴图辅助 ----------------

    private static void rect(String sprite, float x, float y, float w, float h){
        TextureRegion region = region(sprite);
        if(region != null) Draw.rect(region, x, y, w, h);
    }

    private static void rect(String sprite, float x, float y, float w, float h, float rotation){
        TextureRegion region = region(sprite);
        if(region != null) Draw.rect(region, x, y, w, h, rotation);
    }

    private static TextureRegion region(String name){
        if(Core.atlas == null) return null;
        TextureRegion region = Core.atlas.findRegion(name);
        if(region == null) region = Core.atlas.findRegion("blank");
        return region;
    }
}
