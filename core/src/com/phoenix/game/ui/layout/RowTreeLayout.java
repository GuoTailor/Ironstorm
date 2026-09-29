package com.phoenix.game.ui.layout;

import com.badlogic.gdx.utils.IntArray;

/**
 * 行式树布局。参照 Mindustry mindustry.ui.layout.RowTreeLayout 移植。
 * <p>每个节点占据"节点高度 × 5"的格子，第 depth 层往 y 方向下移、同层往 x 方向排开，
 * 用 {@code nexts[depth]} 记录该层已排到第几列（对应原版注释里的 python 伪码 minimum_ws）。
 * <p><b>与 arc 的差异</b>：{@code arc.struct.IntArray} 按映射表直接用 libgdx
 * {@code com.badlogic.gdx.utils.IntArray}（{@code ensureCapacity}/{@code size}/{@code incr} 语义一致）。
 */
public class RowTreeLayout implements TreeLayout{

    @Override
    public void layout(TreeNode root){
       layout(root, 0, new IntArray());

        /*
        def minimum_ws(tree, depth=0):
    tree.x = nexts[depth]
    tree.y = depth
    nexts[depth] += 1
    for c in tree.children:
        minimum_ws(tree, c)
         */
    }

    void layout(TreeNode node, int depth, IntArray nexts){
        float size = node.height * 5f;

        if(nexts.size < depth + 1){
            nexts.ensureCapacity(depth + 1);
            nexts.size = depth + 1;
        }

        node.x = size * nexts.get(depth);
        node.y = size * depth;
        nexts.incr(depth, 1);
        for(TreeNode child : node.children){
            layout(child, depth + 1, nexts);
        }
    }
}
