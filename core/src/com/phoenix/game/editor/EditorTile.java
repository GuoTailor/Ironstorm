package com.phoenix.game.editor;

import com.phoenix.game.Vars;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.editor.DrawOperation.OpType;
import com.phoenix.game.game.Team;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Floor;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.blocks.OverlayFloor;
import io.anuke.mindustry.gen.TileOp;

/**
 * 编辑器专用瓦片：在改动之前把「旧值」记进撤销栈。参照 Mindustry {@code mindustry.editor.EditorTile} 移植。
 *
 * <p>所有覆写的 setter 都是同一个套路：先 {@code op(type, 旧值)}，再调 {@code super}。
 * {@code op(...)} 最终落到 {@code MapEditor.addTileOp(...)}，由它把操作累积进当前 {@link DrawOperation}。
 *
 * <p><b>与原版的差异（逐条）</b>
 * <ul>
 *   <li><b>构造器按 id 反查</b>：原版把三个 id 交给 {@code Tile(int,int,int,int,int)}；本项目
 *       {@code Tile} 只有「对象」构造器 → 这里自己用 {@code Vars.content.block(id)} 反查。
 *       （{@code Blocks.air.id == 0}，所以 overlay 传 0 就是空气。）</li>
 *   <li>{@code setOverlayID(short)}：本项目 {@code Tile} 没有该方法（原版有）→ 本类补一个同名方法，
 *       语义与原版 {@code EditorTile.setOverlayID} 一致（不是覆写，因为父类没有）。</li>
 *   <li>{@code preChanged()}：本项目 {@code Tile} 没有这个钩子（原版在 {@code setBlock} 前调它把队伍
 *       重置为 derelict）→ <b>整段省略</b>；编辑器的队伍由 {@link MapEditor#drawTeam} 显式设置。</li>
 *   <li><b>多格展开</b>：原版把卫星格铺开放在 {@code Tile.set(block, team)} 里；本项目把这段内联在
 *       {@code Tile.setBlock(Block,Team,int)} → {@link #setBlock(Block, Team, int)} 末尾补调一次
 *       {@code super.setBlock(type, team, rotation)}，卫星格会再次走本方法，从而逐格记 op。</li>
 *   <li>{@code changed()}：原版在这里重建实体并挂 cons/items/liquids/power 模块；本项目实体初始化依赖
 *       {@code block.update} 且模块由 {@code TileEntity.init} 分配，编辑器又不做仿真
 *       → 只保留「清空实体 + 兜底 block/floor 非 null」，不建实体（见方法内说明）。</li>
 *   <li>{@code op(...)} 原版走 {@code ui.editor.editor.addTileOp(...)} → 本项目用
 *       {@link Vars#editor}（{@code MapEditorDialog} 构造时赋值）作为等价入口。</li>
 * </ul>
 */
public class EditorTile extends Tile{

    public EditorTile(int x, int y, int floor, int overlay, int wall){
        super(x, y);
        //原版把 id 交给 Tile 的 id 构造器；本项目 Tile 只有对象构造器 → 按 id 反查
        this.floor = (Floor)Vars.content.block(floor);
        this.overlay = (Floor)Vars.content.block(overlay);
        this.block = Vars.content.block(wall);
    }

    @Override
    public void setFloor(Floor type){
        if(Vars.state.isPlaying()){
            super.setFloor(type);
            return;
        }

        if(type instanceof OverlayFloor){
            //don't place on liquids
            if(!floor.isLiquid){
                setOverlayID(type.id);
            }
            return;
        }

        if(floor == type && overlayID() == 0) return;
        if(overlayID() != 0) op(OpType.overlay, overlayID());
        if(floor != type) op(OpType.floor, floor.id);
        super.setFloor(type);
    }

    @Override
    public void setBlock(Block type){
        if(Vars.state.isPlaying()){
            super.setBlock(type);
            return;
        }

        //注意用字段 block（不解析多格链接）：卫星瓦片走 block() 会转发到中心方块，判等会失真
        if(block == type) return;
        op(OpType.block, block.id);
        if(rotation != 0) op(OpType.rotation, rotation);
        if(team != 0) op(OpType.team, team);
        super.setBlock(type);
    }

    @Override
    public void setBlock(Block type, Team team, int rotation){
        if(Vars.state.isPlaying()){
            super.setBlock(type, team, rotation);
            return;
        }

        setBlock(type);
        setTeam(team);
        rotation(rotation);

        //多格展开：原版由 Tile.set(block, team) 负责；本项目内联在 Tile.setBlock(Block,Team,int) 里。
        //卫星格会再次进入本方法 → 逐格记 op，撤销时能完整回滚整座多格建筑。
        if(type.isMultiblock()){
            super.setBlock(type, team, rotation);
        }
    }

    @Override
    public void setTeam(Team team){
        if(Vars.state.isPlaying()){
            super.setTeam(team);
            return;
        }

        //用字段 team（原始值，不解析多格链接）记录旧值，与原版 getTeamID() 一致
        if(this.team == team.id) return;
        op(OpType.team, this.team);
        super.setTeam(team);
    }

    @Override
    public void rotation(int rotation){
        if(Vars.state.isPlaying()){
            super.rotation(rotation);
            return;
        }

        if(rotation == rotation()) return;
        op(OpType.rotation, rotation());
        super.rotation(rotation);
    }

    @Override
    public void setOverlay(Block overlay){
        setOverlayID(overlay.id);
    }

    /**
     * 设置覆盖层（矿脉/出生点）。对应原版 {@code EditorTile.setOverlayID}。
     * <p>本项目 {@code Tile} 没有 {@code setOverlayID} → 本方法不是覆写，只是同名替代。
     */
    public void setOverlayID(short overlay){
        if(Vars.state.isPlaying()){
            this.overlay = (Floor)Vars.content.block(overlay);
            return;
        }

        if(floor.isLiquid) return;
        if(overlayID() == overlay) return;
        op(OpType.overlay, this.overlay.id);
        this.overlay = (Floor)Vars.content.block(overlay);
    }

    /** @return 覆盖层 id（原版 {@code Tile.overlayID()}）。 */
    public short overlayID(){
        return overlay.id;
    }

    /** @return 地板 id（原版 {@code Tile.floorID()}）。 */
    public short floorID(){
        return floor.id;
    }

    /** @return 本瓦片**实际存储**的方块 id（原版 {@code Tile.blockID()}；不解析多格链接）。 */
    public short blockID(){
        return block.id;
    }

    @Override
    protected void changed(){
        if(Vars.state.isPlaying()){
            super.changed();
            return;
        }

        entity = null;

        if(block == null){
            block = Blocks.air;
        }

        if(floor == null){
            floor = Blocks.air;
        }

        //刻意降级：原版在这里还会 newEntity().init(this, false) 并挂上 ConsumeModule/ItemModule/LiquidModule/PowerModule；
        //     本项目实体初始化需要 block.update 标志、模块由 TileEntity.init 统一分配，且编辑器不跑仿真
        //     （没有 updateTiles 调用）→ 这里只保证「无实体」，不重建模块。
    }

    private void op(OpType type, short value){
        //原版 = ui.editor.editor.addTileOp(TileOp.get(...))；本项目用 Vars.editor 作为等价入口
        //（赋值时机见 Vars.editor 的注释：MapEditorDialog 构造时 = editor）
        if(Vars.editor != null){
            Vars.editor.addTileOp(TileOp.get(x, y, (byte)type.ordinal(), value));
        }
    }
}
