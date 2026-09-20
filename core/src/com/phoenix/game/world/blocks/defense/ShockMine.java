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
    public float tileDamage = 5f;
    /** 对单位的伤害。 */
    public float damage = 13f;
    /** 闪电长度（像素）。 */
    public int length = 10;
    /** 触发半径（世界单位）。 */
    public float range = 10f;

    public ShockMine(String name){
        super(name);
        update = true;
        solid = true;
        destructible = true;
        health = 50;
        entityType = MineEntity::new;
    }

    @Override
    public boolean outputsItems(){
        return false;
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

                //放电：朝随机方向（对应原版 Mathf.random(360f)）
                Lightning.createLighting(com.phoenix.game.core.Time.nanos(), tile.getTeam(),
                    com.phoenix.game.graphics.Pal.surge, damage, cx, cy, Mathf.random(360f), length);

                unit.damage(damage);
                //自伤（踩雷后地雷受损）
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
