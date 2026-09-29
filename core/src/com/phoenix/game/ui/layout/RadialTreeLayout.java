package com.phoenix.game.ui.layout;

import com.badlogic.gdx.utils.ObjectSet;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.struct.Queue;

/**
 * 辐射状树布局。参照 Mindustry mindustry.ui.layout.RadialTreeLayout 移植。
 * <p>先 BFS 统计每个节点的叶子数，再按"每个子树分到的角度与其叶子数成正比"递归把子节点摆到圆环上
 * （根在第 0 环、每深一层半径加 {@code delta}）。
 * <p><b>与 arc 的差异</b>：
 * <ul>
 *   <li>{@code arc.struct.ObjectSet} → libgdx {@code com.badlogic.gdx.utils.ObjectSet}
 *       （{@code add} 返回 boolean、拷贝构造器语义一致）；</li>
 *   <li>{@code arc.struct.Queue} → 本项目的 {@link Queue}（{@code addFirst/addLast/removeFirst/isEmpty/clear} 齐全）。</li>
 *   <li><b>三角函数</b>：原版写 {@code Mathf.cos(...)} / {@code Mathf.sin(...)} / {@code Mathf.degRad}，
 *       本项目 {@code com.phoenix.game.math.Mathf} 已有同签名的单参 {@code sin(float)}/{@code cos(float)}
 *       （同为弧度制）与 {@code degRad(float)} → 直接照原版写法（T11c 接线）。
 *       ⚠ 差异：arc 的 {@code Mathf.degRad} 是**常量**，本项目是**方法** → 写 {@code Mathf.degRad(角度)}。</li>
 * </ul>
 */
public class RadialTreeLayout implements TreeLayout{
    private static ObjectSet<TreeNode> visited = new ObjectSet<>();
    private static Queue<TreeNode> queue = new Queue<>();

    public float startRadius, delta;

    @Override
    public void layout(TreeNode root){
        startRadius = root.height * 2.4f;
        delta = root.height * 20.4f;

        bfs(root, true);

        ObjectSet<TreeNode> all = new ObjectSet<>(visited);
        for(TreeNode node : all){
            node.leaves = bfs(node, false);
        }

        radialize(root, startRadius, 0, 360);
    }

    void radialize(TreeNode root, float radius, float from, float to){
        float angle = from;

        for(TreeNode child : root.children){
            float nextAngle = angle + ((float)child.leaves / root.leaves * (to - from));

            //原版：Mathf.cos / Mathf.sin（本项目 Mathf 已有单参版本，同为弧度制）。
            //⚠ arc 的 Mathf.degRad 是常量，本项目是方法 → 写成 Mathf.degRad(角度)
            float mid = Mathf.degRad((angle + nextAngle) / 2f);
            float x = radius * Mathf.cos(mid);
            float y = radius * Mathf.sin(mid);

            child.x = x;
            child.y = y;

            if(child.children.length > 0) radialize(child, radius + delta, angle, nextAngle);
            angle = nextAngle;
        }
    }

    int bfs(TreeNode node, boolean assign){
        visited.clear();
        queue.clear();
        if(assign) node.number = 0;
        int leaves = 0;

        visited.add(node);
        queue.addFirst(node);

        while(!queue.isEmpty()){
            TreeNode current = queue.removeFirst();
            if(current.children.length == 0) leaves++;

            for(TreeNode child : current.children){
                if(assign) child.number = current.number + 1;
                if(visited.add(child)){
                    queue.addLast(child);
                }
            }
        }

        return leaves;
    }
}
