package com.phoenix.game.editor;

import com.phoenix.game.struct.Array;

/**
 * 地图编辑器的撤销/重做栈。参照 Mindustry {@code mindustry.editor.OperationStack} 移植。
 *
 * <p>语义与原版完全一致：栈里存 {@link DrawOperation}（一批瓦片编辑），{@code index} 表示
 * 「已经回退了几步」——{@code index == 0} 表示当前处于栈顶，负数表示正在回退。
 * {@link #add} 会把 index 之后（已被撤销掉）的条目截断，符合"撤销后再画一笔就丢弃重做分支"的直觉。
 *
 * <p><b>与原版的差异（逐条）</b>
 * <ul>
 *   <li>{@code arc.struct.Array} → {@link com.phoenix.game.struct.Array}（本项目 arc 替代层）。</li>
 *   <li>{@code stack.remove(0)} 原版是 arc 的「按下标删除」；libgdx 的 {@code Array} 没有单参
 *       {@code remove(int)}（只有 {@code removeIndex(int)} / {@code removeValue(T,boolean)}）
 *       → 改写成 {@code removeIndex(0)}。</li>
 * </ul>
 */
public class OperationStack{
    private final static int maxSize = 10;
    private final Array<DrawOperation> stack = new Array<>();
    private int index = 0;

    public OperationStack(){

    }

    public void clear(){
        stack.clear();
        index = 0;
    }

    public void add(DrawOperation action){
        stack.truncate(stack.size + index);
        index = 0;
        stack.add(action);

        if(stack.size > maxSize){
            //原版 = stack.remove(0)（arc 的按下标删除）→ libgdx 用 removeIndex
            stack.removeIndex(0);
        }
    }

    public boolean canUndo(){
        return !(stack.size - 1 + index < 0);
    }

    public boolean canRedo(){
        return !(index > -1 || stack.size + index < 0);
    }

    public void undo(){
        if(!canUndo()) return;

        stack.get(stack.size - 1 + index).undo();
        index--;
    }

    public void redo(){
        if(!canRedo()) return;

        index++;
        stack.get(stack.size - 1 + index).redo();
    }
}
