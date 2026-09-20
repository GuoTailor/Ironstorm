package com.phoenix.game.world.blocks.defense;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.core.Core;
import com.phoenix.game.entities.Units;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.world.Tile;

import static com.phoenix.game.Vars.tilesize;

/**
 * 门。参照 Mindustry mindustry.world.blocks.defense.Door 移植。
 * <p>与墙的区别：{@code solid = false}，是否阻挡通行由 {@link #isSolidFor} 按开关状态动态决定。
 * <p>简化点：原版是玩家点击开关（带 30 帧冷却 + 音效），本项目改为**有友方单位靠近时自动开**
 * ——行为等价（友军能过、敌人被挡），且不需要额外的输入钩子。
 */
public class Door extends Wall{
    /** 开门贴图（区域名 = 名称-open）。 */
    protected TextureRegion openRegion;

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

    public class DoorEntity extends TileEntity{
        /** 是否打开（打开时不阻挡通行）。 */
        public boolean open;

        @Override
        public void update(){
            //有友方单位贴到门口就开门（原版由玩家点击切换）
            boolean near = false;
            float cx = tile.worldx() + tilesize / 2f, cy = tile.worldy() + tilesize / 2f;
            float range = tilesize * 1.5f;

            for(int i = 0; i < Units.units.size; i++){
                BaseUnit unit = Units.units.get(i);
                if(unit.isDead() || unit.getTeam() != tile.getTeam()) continue;

                if(Math.abs(unit.x - cx) < range && Math.abs(unit.y - cy) < range){
                    near = true;
                    break;
                }
            }

            open = near;
        }
    }
}
