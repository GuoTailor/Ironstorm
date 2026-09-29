package com.phoenix.game.math.geom;

import com.badlogic.gdx.math.Rectangle;
import com.phoenix.game.func.Cons;
import com.phoenix.game.struct.Array;

/**
 * 四叉树。参照 {@code arc.math.geom.QuadTree} 移植（原版用于实体碰撞的粗筛）。
 * <p>libgdx 没有四叉树实现。arc 的 {@code Rect} 在本项目统一映射为 libgdx 的 {@link Rectangle}。
 * <p>结构：每个节点持有 {@code objects}；元素数超过 {@code maxObjectsPerNode} 时四分，
 * 能完整落进某个子节点的元素下放，跨界的留在本节点。
 */
public class QuadTree<T extends QuadTree.QuadTreeObject>{

    /** 可放进四叉树的对象：需要能回报自己的包围盒（对应 arc {@code QuadTree.QuadTreeObject}）。 */
    public interface QuadTreeObject{
        void hitbox(Rectangle out);
    }

    private static final Rectangle tmp = new Rectangle();
    private static final int maxObjectsPerNode = 8;

    public Rectangle bounds;
    public Array<T> objects = new Array<>(8);
    public QuadTree<T> botLeft;
    public QuadTree<T> botRight;
    public QuadTree<T> topLeft;
    public QuadTree<T> topRight;
    public boolean leaf = true;

    public QuadTree(Rectangle bounds){
        this.bounds = bounds;
    }

    private void split(){
        float hw = bounds.width / 2f, hh = bounds.height / 2f;
        float x = bounds.x, y = bounds.y;
        botLeft = new QuadTree<>(new Rectangle(x, y, hw, hh));
        botRight = new QuadTree<>(new Rectangle(x + hw, y, hw, hh));
        topLeft = new QuadTree<>(new Rectangle(x, y + hh, hw, hh));
        topRight = new QuadTree<>(new Rectangle(x + hw, y + hh, hw, hh));
        leaf = false;
    }

    private void unsplit(){
        leaf = true;
        botLeft = botRight = topLeft = topRight = null;
    }

    public void insert(T object){
        if(!leaf){
            insertIntoFittingChild(object);
            return;
        }

        objects.add(object);

        if(objects.size > maxObjectsPerNode){
            split();
            for(int i = 0; i < objects.size; i++){
                insertIntoFittingChild(objects.get(i));
            }
            objects.clear();
        }
    }

    private void insertIntoFittingChild(T object){
        object.hitbox(tmp);
        QuadTree<T> child = getFittingChild(tmp);
        if(child == null){
            objects.add(object);
        }else{
            child.insert(object);
        }
    }

    /** @return 能完整包含 rect 的子节点；跨界时返回 null（对应 arc {@code getFittingChild}）。 */
    private QuadTree<T> getFittingChild(Rectangle rect){
        if(botLeft == null) return null;
        if(botLeft.bounds.contains(rect)) return botLeft;
        if(botRight.bounds.contains(rect)) return botRight;
        if(topLeft.bounds.contains(rect)) return topLeft;
        if(topRight.bounds.contains(rect)) return topRight;
        return null;
    }

    public void remove(T object){
        objects.removeValue(object, true);
        if(!leaf){
            botLeft.remove(object);
            botRight.remove(object);
            topLeft.remove(object);
            topRight.remove(object);
        }
    }

    public void clear(){
        objects.clear();
        if(!leaf){
            botLeft.clear();
            botRight.clear();
            topLeft.clear();
            topRight.clear();
            unsplit();
        }
    }

    /** 把与给定矩形相交的对象交给 consumer（对应 arc {@code getIntersect(Cons, Rect)}）。 */
    public void getIntersect(Cons<T> consumer, Rectangle rect){
        for(int i = 0; i < objects.size; i++){
            T object = objects.get(i);
            object.hitbox(tmp);
            if(tmp.overlaps(rect)) consumer.get(object);
        }

        if(!leaf){
            if(botLeft.bounds.overlaps(rect)) botLeft.getIntersect(consumer, rect);
            if(botRight.bounds.overlaps(rect)) botRight.getIntersect(consumer, rect);
            if(topLeft.bounds.overlaps(rect)) topLeft.getIntersect(consumer, rect);
            if(topRight.bounds.overlaps(rect)) topRight.getIntersect(consumer, rect);
        }
    }

    public void getIntersect(Cons<T> consumer, float x, float y, float width, float height){
        getIntersect(consumer, new Rectangle(x, y, width, height));
    }

    /** 把与给定矩形相交的对象收进 out（对应 arc {@code getIntersect(Array, Rect)}）。 */
    public void getIntersect(Array<T> out, Rectangle rect){
        getIntersect(out::add, rect);
    }

    /** @return 本节点与全部子节点的对象总数。 */
    public int getTotalObjectCount(){
        int count = objects.size;
        if(!leaf){
            count += botLeft.getTotalObjectCount();
            count += botRight.getTotalObjectCount();
            count += topLeft.getTotalObjectCount();
            count += topRight.getTotalObjectCount();
        }
        return count;
    }
}
