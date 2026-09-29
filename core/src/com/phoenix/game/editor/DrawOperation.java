package com.phoenix.game.editor;

import com.badlogic.gdx.utils.LongArray;
import com.phoenix.game.Vars;
import com.phoenix.game.game.Team;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Floor;
import com.phoenix.game.world.Tile;
import io.anuke.mindustry.gen.TileOp;

/**
 * 一次「绘制动作」里对若干瓦片的编辑集合，可整体撤销/重做。
 * 参照 Mindustry {@code mindustry.editor.DrawOperation} 移植。
 *
 * <p>每条记录是一个 long（{@link TileOp} 位打包）：坐标 + 被改的字段类型 + **改之前的值**。
 * {@link #undo()} 逆序、{@link #redo()} 正序地逐条回放：每次回放先把「当前值」写回记录
 * （这样同一条记录可以被反复撤销/重做），再把「记录值」应用到瓦片上。
 *
 * <p><b>与原版的差异（逐条）</b>
 * <ul>
 *   <li>{@code arc.struct.LongArray} → libgdx {@code com.badlogic.gdx.utils.LongArray}（同名同语义）。</li>
 *   <li>{@code tile.floorID()/blockID()/overlayID()/getTeamID()} 本项目 {@code Tile} 没有这些 id 访问器
 *       → 分别用 {@code tile.floor().id} / {@code tile.blockRaw().id} / {@code tile.overlay().id} /
 *       {@code tile.getTeam().id}。<b>方块必须用 {@code blockRaw()}</b>：本项目 {@code Tile.block()}
 *       会解析多格链接（卫星瓦片转发到中心方块），照抄会把卫星格记成中心方块、撤销时拆散多格建筑。</li>
 *   <li>{@code tile.setOverlayID(to)} 本项目没有 → 用 {@code tile.setOverlay(content.block(to))}。</li>
 *   <li>原版 {@code @Struct class TileOpStruct} 由注解处理器生成 {@code TileOp}；本项目不启用该处理器
 *       → {@link TileOp} 已手写，这里只保留结构体定义做参照（{@code @Struct} 按硬约束保持注释状态）。</li>
 * </ul>
 */
public class DrawOperation{
    private final MapEditor editor;
    private final LongArray array = new LongArray();

    public DrawOperation(MapEditor editor){
        this.editor = editor;
    }

    public boolean isEmpty(){
        return array.isEmpty();
    }

    public void addOperation(long op){
        array.add(op);
    }

    public void undo(){
        for(int i = array.size - 1; i >= 0; i--){
            updateTile(i);
        }
    }

    public void redo(){
        for(int i = 0; i < array.size; i++){
            updateTile(i);
        }
    }

    private void updateTile(int i){
        long l = array.get(i);
        array.set(i, TileOp.get(TileOp.x(l), TileOp.y(l), TileOp.type(l), getTile(editor.tile(TileOp.x(l), TileOp.y(l)), TileOp.type(l))));
        setTile(editor.tile(TileOp.x(l), TileOp.y(l)), TileOp.type(l), TileOp.value(l));
    }

    short getTile(Tile tile, byte type){
        if(tile == null) return 0;

        if(type == OpType.floor.ordinal()){
            return tile.floor().id;
        }else if(type == OpType.block.ordinal()){
            //本项目 block() 会解析多格链接 → 必须用 blockRaw()（原版 blockID() 就是原始值）
            return tile.blockRaw().id;
        }else if(type == OpType.rotation.ordinal()){
            return tile.rotation();
        }else if(type == OpType.team.ordinal()){
            return (short)tile.getTeam().id;
        }else if(type == OpType.overlay.ordinal()){
            return tile.overlay().id;
        }
        throw new IllegalArgumentException("Invalid type.");
    }

    void setTile(Tile tile, byte type, short to){
        if(tile == null) return;

        //load(...) 期间 loading = true，从而回放本身不会被再次记进撤销栈（对应原版 editor.load）
        editor.load(() -> {
            if(type == OpType.floor.ordinal()){
                tile.setFloor((Floor)Vars.content.block(to));
            }else if(type == OpType.block.ordinal()){
                Block block = Vars.content.block(to);
                tile.setBlock(block, tile.getTeam(), tile.rotation());
            }else if(type == OpType.rotation.ordinal()){
                tile.rotation(to);
            }else if(type == OpType.team.ordinal()){
                tile.setTeam(Team.get(to));
            }else if(type == OpType.overlay.ordinal()){
                tile.setOverlay(Vars.content.block(to));
            }
        });
        editor.renderer().updatePoint(tile.x, tile.y);
    }

    //说明：原版是 @Struct class TileOpStruct{ short x; short y; byte type; short value; }，
    //     由注解处理器生成 io.anuke.mindustry.gen.TileOp；本项目已手写该类，这里仅保留结构体定义做参照
    //     （@Struct 注解按硬约束保持注释状态）
    class TileOpStruct{
        short x;
        short y;
        byte type;
        short value;
    }

    public enum OpType{
        floor,
        block,
        rotation,
        team,
        overlay
    }
}
