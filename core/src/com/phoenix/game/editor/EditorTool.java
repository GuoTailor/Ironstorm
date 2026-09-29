package com.phoenix.game.editor;

import com.badlogic.gdx.utils.IntArray;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.func.Boolf;
import com.phoenix.game.func.Cons;
import com.phoenix.game.game.Team;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.math.geom.Bresenham2;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Floor;
import com.phoenix.game.world.Pos;
import com.phoenix.game.world.Tile;

/**
 * 地图编辑器的工具（画笔/橡皮/填充/直线/取色/喷洒）。参照 Mindustry {@code mindustry.editor.EditorTool} 移植。
 *
 * <p>每个工具实现 {@link #touched}（单点）与/或 {@link #touchedLine}（拖拽直线）。
 * 工具自己的备用模式存在 {@link #altModes}，当前模式存在 {@link #mode}（-1 = 标准模式）。
 *
 * <p><b>与原版的差异（逐条）</b>
 * <ul>
 *   <li>{@code Structs.inBounds(x, y, w, h)} 本项目没有（只有数组版）→ 内联成
 *       {@code x >= 0 && y >= 0 && x < width && y < height}。</li>
 *   <li>{@code Bresenham2.line} arc 未移植 → 本项目新增了 {@link Bresenham2}（arc 替代层）。</li>
 *   <li>{@code Block.isOverlay()} / {@code Block.isFloor()} / {@code Block.asFloor()} 与
 *       {@code tile.clearOverlay()} 本项目均已具备（T8a 补齐，语义与原版逐字一致）→ 直接调用。</li>
 *   <li>{@code tile.setFloorUnder(floor)} 本项目没有 → 手工「先存 overlay → setFloor → 还原 overlay」。</li>
 *   <li><b>判方块一律用 {@code blockRaw()}/{@code isLinked()}</b>：原版 {@code tile.block()} 返回瓦片自己
 *       存的方块（所以 {@code instanceof BlockPart} 只命中卫星格）；本项目 {@code block()} 会解析多格链接
 *       → 照抄会让 {@code instanceof BlockPart} 永远不成立。</li>
 *   <li>{@code Mathf.chance(double)} 本项目只有 {@code chance(float)} → {@code spray} 的 0.012 写成 float。</li>
 *   <li>{@code getTeamID()} 本项目没有 → 用 {@code t.getTeam() == dest} 直接比对象。</li>
 * </ul>
 */
public enum EditorTool{
    zoom,
    pick{
        public void touched(MapEditor editor, int x, int y){
            if(!inBounds(x, y, editor.width(), editor.height())) return;

            Tile tile = editor.tile(x, y).link();
            editor.drawBlock = tile.block() == Blocks.air ? tile.overlay() == Blocks.air ? tile.floor() : tile.overlay() : tile.block();
        }
    },
    line("replace", "orthogonal"){

        @Override
        public void touchedLine(MapEditor editor, int x1, int y1, int x2, int y2){
            //straight
            if(mode == 1){
                if(Math.abs(x2 - x1) > Math.abs(y2 - y1)){
                    y2 = y1;
                }else{
                    x2 = x1;
                }
            }

            Bresenham2.line(x1, y1, x2, y2, (x, y) -> {
                if(mode == 0){
                    //replace
                    editor.drawBlocksReplace(x, y);
                }else{
                    //normal
                    editor.drawBlocks(x, y);
                }
            });
        }
    },
    pencil("replace", "square", "drawteams"){
        {
            edit = true;
            draggable = true;
        }

        @Override
        public void touched(MapEditor editor, int x, int y){
            if(mode == -1){
                //normal mode
                editor.drawBlocks(x, y);
            }else if(mode == 0){
                //replace mode
                editor.drawBlocksReplace(x, y);
            }else if(mode == 1){
                //square mode
                editor.drawBlocks(x, y, true, tile -> true);
            }else if(mode == 2){
                //draw teams
                editor.drawCircle(x, y, tile -> tile.link().setTeam(editor.drawTeam));
            }
        }
    },
    eraser("eraseores"){
        {
            edit = true;
            draggable = true;
        }

        @Override
        public void touched(MapEditor editor, int x, int y){
            editor.drawCircle(x, y, tile -> {
                if(mode == -1){
                    //erase block
                    tile.remove();
                }else if(mode == 0){
                    //erase ore
                    tile.clearOverlay();
                }
            });
        }
    },
    fill("replaceall", "fillteams"){
        {
            edit = true;
        }

        final IntArray stack = new IntArray();

        @Override
        public void touched(MapEditor editor, int x, int y){
            if(!inBounds(x, y, editor.width(), editor.height())) return;
            Tile tile = editor.tile(x, y);

            if(editor.drawBlock.isMultiblock()){
                //don't fill multiblocks, thanks
                pencil.touched(editor, x, y);
                return;
            }

            //mode 0 or 1, fill everything with the floor/tile or replace it
            if(mode == 0 || mode == -1){
                //can't fill parts or multiblocks
                //（本项目 block() 会解析多格链接 → 判卫星格必须用 isLinked()）
                if(tile.isLinked() || tile.blockRaw().isMultiblock()){
                    return;
                }

                Boolf<Tile> tester;
                Cons<Tile> setter;

                if(editor.drawBlock.isOverlay()){
                    Block dest = tile.overlay();
                    if(dest == editor.drawBlock) return;
                    tester = t -> t.overlay() == dest && !t.floor().isLiquid;
                    setter = t -> t.setOverlay(editor.drawBlock);
                }else if(editor.drawBlock.isFloor()){
                    Block dest = tile.floor();
                    if(dest == editor.drawBlock) return;
                    tester = t -> t.floor() == dest;
                    setter = t -> setFloorUnder(t, editor.drawBlock.asFloor());
                }else{
                    Block dest = tile.blockRaw();
                    if(dest == editor.drawBlock) return;
                    tester = t -> t.blockRaw() == dest;
                    setter = t -> t.setBlock(editor.drawBlock, editor.drawTeam);
                }

                //replace only when the mode is 0 using the specified functions
                fill(editor, x, y, mode == 0, tester, setter);
            }else if(mode == 1){ //mode 1 is team fill

                //only fill synthetic blocks, it's meaningless otherwise
                //（原版 = tile.link().synthetic()；本项目 Tile 没有 synthetic() → 用 block() 的，
                //  block() 已解析多格链接，等价于原版的 link().synthetic()）
                if(tile.block().synthetic()){
                    Team dest = tile.getTeam();
                    if(dest == editor.drawTeam) return;
                    fill(editor, x, y, false, t -> t.getTeam() == dest && t.block().synthetic(), t -> t.setTeam(editor.drawTeam));
                }
            }
        }

        void fill(MapEditor editor, int x, int y, boolean replace, Boolf<Tile> tester, Cons<Tile> filler){
            int width = editor.width(), height = editor.height();

            if(replace){
                //just do it on everything
                for(int cx = 0; cx < width; cx++){
                    for(int cy = 0; cy < height; cy++){
                        Tile tile = editor.tile(cx, cy);
                        if(tester.get(tile)){
                            filler.get(tile);
                        }
                    }
                }

            }else{
                //perform flood fill
                int x1;

                stack.clear();
                stack.add(Pos.get(x, y));

                while(stack.size > 0){
                    int popped = stack.pop();
                    x = Pos.x(popped);
                    y = Pos.y(popped);

                    x1 = x;
                    while(x1 >= 0 && tester.get(editor.tile(x1, y))) x1--;
                    x1++;
                    boolean spanAbove = false, spanBelow = false;
                    while(x1 < width && tester.get(editor.tile(x1, y))){
                        filler.get(editor.tile(x1, y));

                        if(!spanAbove && y > 0 && tester.get(editor.tile(x1, y - 1))){
                            stack.add(Pos.get(x1, y - 1));
                            spanAbove = true;
                        }else if(spanAbove && !tester.get(editor.tile(x1, y - 1))){
                            spanAbove = false;
                        }

                        if(!spanBelow && y < height - 1 && tester.get(editor.tile(x1, y + 1))){
                            stack.add(Pos.get(x1, y + 1));
                            spanBelow = true;
                        }else if(spanBelow && y < height - 1 && !tester.get(editor.tile(x1, y + 1))){
                            spanBelow = false;
                        }
                        x1++;
                    }
                }
            }
        }
    },
    spray("replace"){
        //刻意降级：原版是 double 0.012；本项目 Mathf.chance 只有 float 重载（数值一致）
        final float chance = 0.012f;

        {
            edit = true;
            draggable = true;
        }

        @Override
        public void touched(MapEditor editor, int x, int y){

            //floor spray
            if(editor.drawBlock.isFloor()){
                editor.drawCircle(x, y, tile -> {
                    if(Mathf.chance(chance)){
                        tile.setFloor(editor.drawBlock.asFloor());
                    }
                });
            }else if(mode == 0){ //replace-only mode, doesn't affect air
                editor.drawBlocks(x, y, tile -> Mathf.chance(chance) && tile.blockRaw() != Blocks.air);
            }else{
                editor.drawBlocks(x, y, tile -> Mathf.chance(chance));
            }
        }
    };

    /** All the internal alternate placement modes of this tool. */
    public final String[] altModes;
    /** The current alternate placement mode. -1 is the standard mode, no changes.*/
    public int mode = -1;
    /** Whether this tool causes canvas changes when touched.*/
    public boolean edit;
    /** Whether this tool should be dragged across the canvas when the mouse moves.*/
    public boolean draggable;

    EditorTool(){
        this(new String[]{});
    }

    EditorTool(String... altModes){
        this.altModes = altModes;
    }

    public void touched(MapEditor editor, int x, int y){}

    public void touchedLine(MapEditor editor, int x1, int y1, int x2, int y2){}

    /** 对应原版 {@code Structs.inBounds(x, y, width, height)}（本项目 Structs 只有数组版）。 */
    private static boolean inBounds(int x, int y, int width, int height){
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    /**
     * 对应原版 {@code Tile.setFloorUnder(floor)}：换地板但保留覆盖层。
     * <p>本项目 {@code Tile} 没有该方法 → 手工「先存 overlay → setFloor → 还原 overlay」。
     */
    private static void setFloorUnder(Tile tile, Floor floor){
        Block overlay = tile.overlay();
        tile.setFloor(floor);
        tile.setOverlay(overlay);
    }
}
