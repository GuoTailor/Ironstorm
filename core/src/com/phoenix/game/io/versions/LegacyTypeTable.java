package com.phoenix.game.io.versions;

import com.phoenix.game.entities.effect.Fire;
import com.phoenix.game.entities.effect.Lightning;
import com.phoenix.game.entities.effect.Puddle;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.entities.type.Player;
import com.phoenix.game.entities.type.base.BuilderDrone;
import com.phoenix.game.entities.type.base.FlyingUnit;
import com.phoenix.game.entities.type.base.GroundUnit;
import com.phoenix.game.entities.type.base.HoverUnit;
import com.phoenix.game.entities.type.base.MinerDrone;
import com.phoenix.game.entities.type.base.RepairDrone;
import com.phoenix.game.func.Prov;

/*
Latest data: [build 81]

0 = Player
1 = Fire
2 = Puddle
3 = MinerDrone
4 = RepairDrone
5 = BuilderDrone
6 = GroundUnit
7 = GroundUnit
8 = GroundUnit
9 = GroundUnit
10 = GroundUnit
11 = FlyingUnit
12 = FlyingUnit
13 = Revenant

Before removal of lightining/bullet: [build 80]

0 = Player
1 = Fire
2 = Puddle
3 = Bullet
4 = Lightning
5 = MinerDrone
6 = RepairDrone
7 = BuilderDrone
8 = GroundUnit
9 = GroundUnit
10 = GroundUnit
11 = GroundUnit
12 = GroundUnit
13 = FlyingUnit
14 = FlyingUnit
15 = Revenant

Before addition of new units: [build 79 and below]

0 = Player
1 = Fire
2 = Puddle
3 = Bullet
4 = Lightning
5 = RepairDrone
6 = GroundUnit
7 = GroundUnit
8 = GroundUnit
9 = GroundUnit
10 = GroundUnit
11 = FlyingUnit
12 = FlyingUnit
13 = BuilderDrone
14 = Revenant
*/

/**
 * 旧版存档的实体类型表。参照 Mindustry {@code mindustry.io.versions.LegacyTypeTable} 移植。
 *
 * <p>老存档里的实体类型只写一个 {@code byte} 的**下标**，而下标的含义随版本变过：
 * build 79 及以下 / build 80 / build 81 各有一套顺序（见上方的对照表）。
 * 本类就是"build 号 → 下标 → 构造器"的翻译表，{@link Save1} 读实体时用它把下标还原成对象。
 *
 * <p><b>本项目的 id 顺序关系（关键）</b>
 * <ul>
 *   <li>这张表**不是**内容 id 表，而是**旧存档里的字节下标**表 —— 下标由当年的 build 决定，
 *       与本项目 {@code ContentLoader} 当前分配的 id 顺序**没有关系**，所以不需要、
 *       也不能去跟本项目的 id 顺序对齐。</li>
 *   <li>它只要求"下标 → 具体 Java 类"这一层映射正确。本项目这些类都已移植且都有无参构造器：
 *       {@code Player}/{@code Fire}/{@code Puddle}/{@code Bullet}/{@code Lightning}/
 *       {@code MinerDrone}/{@code RepairDrone}/{@code BuilderDrone}/{@code GroundUnit}/
 *       {@code FlyingUnit}/{@code HoverUnit} → 表内容可以逐字照抄。</li>
 *   <li>原版表里的 {@code Revenant} 就是 {@code HoverUnit}（注释里的名字是旧名），
 *       本项目同样是 {@code HoverUnit::new}，与原版一致。</li>
 * </ul>
 *
 * <p><b>与原版的差异 + 刻意保留</b>
 * <ul>
 *   <li>原版用 {@code arc.func.Prov}（raw 数组），本项目用 {@code com.phoenix.game.func.Prov}
 *       （同名同签名）→ 写法逐字一致，raw 数组带来的 unchecked 警告与原版相同。</li>
 *   <li>原版 {@code GroundUnit}/{@code FlyingUnit} 是"多种单位共用同一个类"（靠运行时字段区分），
 *       本项目同样是这几个基类（{@code UnitType} 里也这么用）→ 语义一致。</li>
 *   <li>刻意保留：本类**刻意不接线** —— 只有 {@link Save1} 用，而 {@code Save1} 属于刻意保留的
 *       兼容层（本项目 {@code SaveIO} 是重写版，不读原版 MSAV，见 {@code SaveIO.readHeader}）。</li>
 * </ul>
 */
public class LegacyTypeTable{
    /*
    0 = Player
1 = Fire
2 = Puddle
3 = Draug
4 = Spirit
5 = Phantom
6 = Dagger
7 = Crawler
8 = Titan
9 = Fortress
10 = Eruptor
11 = Wraith
12 = Ghoul
13 = Revenant
     */
    private static final Prov[] build81Table = {
        Player::new,
        Fire::new,
        Puddle::new,
        MinerDrone::new,
        RepairDrone::new,
        BuilderDrone::new,
        GroundUnit::new,
        GroundUnit::new,
        GroundUnit::new,
        GroundUnit::new,
        GroundUnit::new,
        FlyingUnit::new,
        FlyingUnit::new,
        HoverUnit::new
    };

    private static final Prov[] build80Table = {
        Player::new,
        Fire::new,
        Puddle::new,
        Bullet::new,
        Lightning::new,
        MinerDrone::new,
        RepairDrone::new,
        BuilderDrone::new,
        GroundUnit::new,
        GroundUnit::new,
        GroundUnit::new,
        GroundUnit::new,
        GroundUnit::new,
        FlyingUnit::new,
        FlyingUnit::new,
        HoverUnit::new
    };

    private static final Prov[] build79Table = {
        Player::new,
        Fire::new,
        Puddle::new,
        Bullet::new,
        Lightning::new,
        RepairDrone::new,
        GroundUnit::new,
        GroundUnit::new,
        GroundUnit::new,
        GroundUnit::new,
        GroundUnit::new,
        FlyingUnit::new,
        FlyingUnit::new,
        BuilderDrone::new,
        HoverUnit::new
    };

    public static Prov[] getTable(int build){
        if(build == -1 || build == 81){
            //return most recent one since that's probably it; not guaranteed
            return build81Table;
        }else if(build == 80){
            return build80Table;
        }else{
            return build79Table;
        }
    }
}
