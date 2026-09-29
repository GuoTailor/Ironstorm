package com.phoenix.game.ui.layout;

/**
 * 树布局算法接口。参照 Mindustry mindustry.ui.layout.TreeLayout 移植。
 * <p>把一棵由 {@link TreeNode} 组成的树按某种算法摆到平面上（写回每个节点的 {@code x}/{@code y}）。
 * 原版用科技树（TechTreeDialog）与区块图（PlanetDialog）绘制节点连线，本项目科技树尚未移植，
 * 这组类先落地备用。
 * <p>无 arc 依赖，本文件与原版逐字一致。
 */
public interface TreeLayout{
    void layout(TreeNode root);

    class TreeNode<T extends TreeNode>{
        public float width, height, x, y;

        //should be initialized by user
        public T[] children;
        public T parent;

        //internal stuff
        public float mode, prelim, change, shift;
        public int number = -1, leaves;
        public TreeNode thread, ancestor;

        public boolean isLeaf(){
            return children == null || children.length == 0;
        }
    }
}
