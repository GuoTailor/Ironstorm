package com.phoenix.game.world.blocks.defense;

import com.phoenix.game.entities.Units;
import com.phoenix.game.entities.effect.Lightning;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;

import static com.phoenix.game.Vars.tilesize;

/**
 * 地雷。参照 Mindustry mindustry.world.blocks.defense.ShockMine 移植。
 * <p>敌方单位靠近时放电（对单位造成伤害）并自伤。
 * <p>简化：原版靠 {@code Block.unitOn}（单位站上瓦片时回调），本项目改为每帧检测附近敌方单位
 * ——不需要改单位的移动代码，行为等价。
 */
public class ShockMine extends Block{
    /** 触发后的冷却（帧）。 */
    public float cooldown = 80f;
    /** 每次触发的自伤量。 */
    public float tileDamage = 5f;  //原版 ShockMine.java:15 类默认 5f；Blocks.java:894 覆盖为 7f
    /** 对单位的伤害。 */
    public float damage = 13f;     //原版 ShockMine.java:16 类默认 13（int 字面量）；Blocks.java:893 覆盖为 11
    /** 闪电长度（像素）。 */
    public int length = 10;
    /** 每次触发放出的闪电条数（原版 ShockMine.java:18 类默认 6；Blocks.java:896 覆盖为 5）。 */
    public int tendrils = 6;
    /** 触发半径（世界单位）。 */
    public float range = 10f;

    public ShockMine(String name){
        super(name);
        update = true;
        solid = true;
        destructible = true;
        health = 40;   //原版 Blocks.java:892 health = 40
        entityType = MineEntity::new;
    }

    @Override
    public boolean outputsItems(){
        return false;
    }

    /** 地雷不画队伍色边框（对应原版 {@code ShockMine.drawTeam(Tile)} 的空覆写，原版注释就是 "no."）。 */
    @Override
    public void drawTeam(Tile tile){
    }

    public class MineEntity extends TileEntity{
        /** 距下次可触发的计时（帧）。 */
        public float timer;

        @Override
        public void update(){
            if(timer > 0f){
                timer -= delta();
                return;
            }

            float cx = tile.worldx() + tilesize / 2f, cy = tile.worldy() + tilesize / 2f;

            for(int i = 0; i < Units.units.size; i++){
                BaseUnit unit = Units.units.get(i);
                if(unit.isDead() || !unit.getTeam().isEnemy(tile.getTeam())) continue;
                if(Mathf.dst(unit.x, unit.y, cx, cy) > range) continue;

                //原版 ShockMine.java:45-47：循环 tendrils 次，每次朝随机方向放一道闪电
                //⚠ 变量名用 j 不用 i：外层单位遍历已占用 i，Java 禁止同方法内局部变量遮蔽（用 i 会编译报错）
                for(int j = 0; j < tendrils; j++){
                    Lightning.createLighting(Lightning.nextSeed(), tile.getTeam(),
                        com.phoenix.game.graphics.Pal.lancerLaser, damage, cx, cy, Mathf.random(360f), length);
                }

                handleDamage(tileDamage);

                timer = cooldown;
                break;
            }
        }

        @Override
        public void write(java.io.DataOutputStream out) throws java.io.IOException{
            super.write(out);
            out.writeFloat(timer);
        }

        @Override
        public void read(java.io.DataInputStream in, byte revision) throws java.io.IOException{
            super.read(in, revision);
            timer = in.readFloat();
        }
    }
}
