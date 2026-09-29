package com.phoenix.game.entities;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.IntMap;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.entities.traits.DrawTrait;
import com.phoenix.game.entities.traits.Entity;
import com.phoenix.game.func.Boolf;
import com.phoenix.game.func.Cons;
import com.phoenix.game.math.geom.QuadTree;
import com.phoenix.game.struct.Array;

import java.util.Iterator;

/**
 * Represents a group of a certain type of entity.
 * <p>参照原版 mindustry.entities.EntityGroup 移植。归属信息挂在实体的 {@code Entity.getGroup()} 上：
 * {@link #add} 靠它判重、{@link #remove}/{@link #clear} 靠它解绑（与原版一致）。
 *
 * <p><b>刻意偏离：{@code updateEvents()} 不再回调 {@code added()}/{@code removed()}</b>。
 * 原版 {@code removed()} 由 {@code updateEvents()} 延迟调用，正好把池化实体的"回收"推迟到
 * 队列处理之后，从而不会串；本项目 {@code BaseEntity.remove()} 是**同步** {@code removed()}
 * （池化 {@code Bullet} 会立刻 {@code Pools.free} 并被 {@code Pools.obtain} 复用），
 * 若组内再回调一次就会把复用后的"新实体"一起清掉。所以这里只维护成员数组，并做了两处回收防护：
 * <ul>
 *     <li>添加时：只登记"当前确实归属本组（{@code getGroup() == this}）且尚未在数组里"的实体 ——
 *         被回收并重新 add 进来的同引用实体会走这条路径重新登记；</li>
 *     <li>移除时：跳过"已重新归属本组"的实体 —— 那条移除记录属于上一个世代，丢弃即可。</li>
 * </ul>
 * {@code added()}/{@code removed()} 仍由实体自身的生命周期（{@code Bullet.create} /
 * {@code BaseEntity.remove()}）同步调用，语义不变。
 */
@SuppressWarnings("unchecked")
public class EntityGroup<T extends Entity> implements Iterable<T>{
    private final boolean useTree;
    private final int id;
    private final Class<T> type;
    private final Array<T> entityArray = new Array<>(false, 32);
    private final Array<T> entitiesToRemove = new Array<>(false, 32);
    private final Array<T> entitiesToAdd = new Array<>(false, 32);
    private final Array<T> intersectArray = new Array<>();
    private final Rectangle intersectRect = new Rectangle();
    private IntMap<T> map;
    private QuadTree tree;
    private Cons<T> removeListener;
    private Cons<T> addListener;

    private final Rectangle viewport = new Rectangle();
    private int count = 0;

    public EntityGroup(int id, Class<T> type, boolean useTree){
        this.useTree = useTree;
        this.id = id;
        this.type = type;

        if(useTree){
            tree = new QuadTree<>(new Rectangle(0, 0, 0, 0));
        }
    }

    public void update(){
        updateEvents();

        if(useTree()){
            Vars.collisions.updatePhysics(entityArray);
        }

        for(Entity e : all()){
            e.update();
        }
    }

    public int countInBounds(){
        count = 0;
        draw(e -> true, e -> count++);
        return count;
    }

    public void draw(){
        draw(e -> true);
    }

    public void draw(Boolf<T> toDraw){
        draw(toDraw, t -> ((DrawTrait)t).draw());
    }

    public void draw(Boolf<T> toDraw, Cons<T> cons){
        Camera cam = Core.camera;
        //原版用 arc 相机的 width/height（= 屏幕宽 / camerascale）；本项目相机是 libgdx 的 OrthographicCamera，
        //可见世界宽 = viewportWidth * zoom（Renderer 里 zoom = 1 / camerascale），两者等价。
        float zoom = cam instanceof OrthographicCamera ? ((OrthographicCamera)cam).zoom : 1f;
        float w = cam.viewportWidth * zoom;
        float h = cam.viewportHeight * zoom;
        viewport.set(cam.position.x - w / 2f, cam.position.y - h / 2f, w, h);

        //热路径：下标循环，避免 libgdx Array 迭代器嵌套
        for(int i = 0; i < entityArray.size; i++){
            Entity e = entityArray.get(i);
            if(!(e instanceof DrawTrait) || !toDraw.get((T)e) || !isAdded((T)e)) continue;
            DrawTrait draw = (DrawTrait)e;

            if(overlaps(viewport, draw.getX() - draw.drawSize() / 2f, draw.getY() - draw.drawSize() / 2f, draw.drawSize(), draw.drawSize())){
                cons.get((T)e);
            }
        }
    }

    /** libgdx 的 Rectangle 没有 overlaps(x, y, w, h) 重载，这里手写。 */
    private static boolean overlaps(Rectangle rect, float x, float y, float width, float height){
        return rect.x < x + width && rect.x + rect.width > x && rect.y < y + height && rect.y + rect.height > y;
    }

    public boolean useTree(){
        return useTree;
    }

    public void setRemoveListener(Cons<T> removeListener){
        this.removeListener = removeListener;
    }

    public void setAddListener(Cons<T> addListener){
        this.addListener = addListener;
    }

    public EntityGroup<T> enableMapping(){
        map = new IntMap<>();
        return this;
    }

    public boolean mappingEnabled(){
        return map != null;
    }

    public Class<T> getType(){
        return type;
    }

    public int getID(){
        return id;
    }

    /**
     * 处理延迟的 add/remove 队列（对应原版同名方法）。
     * <p>⚠ 与原版不同：这里**不回调** {@code e.added()}/{@code e.removed()}（理由见类注释），
     * 只维护 {@code entityArray}/{@code map}，并做了池化实体回收防护。
     */
    public void updateEvents(){
        //添加：只认"当前确实归属本组、且尚未登记"的实体
        for(int i = 0; i < entitiesToAdd.size; i++){
            T e = entitiesToAdd.get(i);
            if(e == null)
                continue;
            //已被解绑 / 被回收后归属了别的组 → 这条添加记录作废
            if(e.getGroup() != this) continue;
            if(entityArray.contains(e, true)) continue;

            entityArray.add(e);

            if(map != null){
                map.put(e.getID(), e);
            }
        }

        entitiesToAdd.clear();

        //移除：跳过"已重新归属本组"的实体（池化实体被回收后立刻复用并重新 add 的情形）
        for(int i = 0; i < entitiesToRemove.size; i++){
            T e = entitiesToRemove.get(i);
            if(e == null)
                continue;
            if(e.getGroup() == this) continue;

            if(entityArray.removeValue(e, true) && map != null){
                map.remove(e.getID());
            }
        }

        entitiesToRemove.clear();
    }

    public T getByID(int id){
        if(map == null) throw new RuntimeException("Mapping is not enabled for group " + id + "!");
        return map.get(id);
    }

    public void removeByID(int id){
        if(map == null) throw new RuntimeException("Mapping is not enabled for group " + id + "!");
        T t = map.get(id);
        if(t != null){ //remove if present in map already
            remove(t);
        }else{ //maybe it's being queued?
            for(int i = 0; i < entitiesToAdd.size; i++){
                T check = entitiesToAdd.get(i);
                if(check.getID() == id){ //if it is indeed queued, remove it
                    entitiesToAdd.removeValue(check, true);
                    if(removeListener != null){
                        removeListener.get(check);
                    }
                    break;
                }
            }
        }
    }

    public void intersect(float x, float y, float width, float height, Cons<? super T> out){
        //don't waste time for empty groups
        if(isEmpty()) return;
        tree().getIntersect(out, x, y, width, height);
    }

    public Array<T> intersect(float x, float y, float width, float height){
        intersectArray.clear();
        //don't waste time for empty groups
        if(isEmpty()) return intersectArray;
        tree().getIntersect(intersectArray, intersectRect.set(x, y, width, height));
        return intersectArray;
    }

    public QuadTree tree(){
        if(!useTree) throw new RuntimeException("This group does not support quadtrees! Enable quadtrees when creating it.");
        return tree;
    }

    /** Resizes the internal quadtree, if it is enabled. */
    public void resize(float x, float y, float w, float h){
        if(useTree){
            tree = new QuadTree<>(new Rectangle(x, y, w, h));
        }
    }

    public boolean isEmpty(){
        return entityArray.size == 0;
    }

    public int size(){
        return entityArray.size;
    }

    public int count(Boolf<T> pred){
        int count = 0;
        for(int i = 0; i < entityArray.size; i++){
            if(pred.get(entityArray.get(i))) count++;
        }
        return count;
    }

    public void add(T type){
        if(type == null) throw new RuntimeException("Cannot add a null entity!");
        //归属信息在实体自己身上（对应原版）：已在任何组里就不重复添加
        if(type.getGroup() != null) return;
        type.setGroup(this);
        entitiesToAdd.add(type);

        if(mappingEnabled()){
            map.put(type.getID(), type);
        }

        if(addListener != null){
            addListener.get(type);
        }
    }

    public void remove(T type){
        if(type == null) throw new RuntimeException("Cannot remove a null entity!");
        //立刻解绑（对应原版），队列里只留一条"稍后从成员数组里摘掉"的记录
        type.setGroup(null);
        entitiesToRemove.add(type);

        if(removeListener != null){
            removeListener.get(type);
        }
    }

    /** @return 该实体是否已在本组（入队待添加也算）。对应原版 {@code Entity.getGroup() != null}。 */
    public boolean contains(T type){
        return entityArray.contains(type, true) || entitiesToAdd.contains(type, true);
    }

    /** @return 该实体是否已正式登记进本组。对应原版 {@code Entity.isAdded()}。 */
    public boolean isAdded(T type){
        return entityArray.contains(type, true);
    }

    public void clear(){
        for(int i = 0; i < entityArray.size; i++){
            T e = entityArray.get(i);
            e.removed();
            //解绑，否则清空后这些实体仍以为自己在组里，后续再也 add 不进来
            e.setGroup(null);
        }

        for(int i = 0; i < entitiesToAdd.size; i++){
            entitiesToAdd.get(i).setGroup(null);
        }
        for(int i = 0; i < entitiesToRemove.size; i++){
            entitiesToRemove.get(i).setGroup(null);
        }

        entitiesToAdd.clear();
        entitiesToRemove.clear();
        entityArray.clear();
        if(map != null)
            map.clear();
    }

    public T find(Boolf<T> pred){

        for(int i = 0; i < entityArray.size; i++){
            if(pred.get(entityArray.get(i))) return entityArray.get(i);
        }

        return null;
    }

    /** Returns the array for iteration. */
    public Array<T> all(){
        return entityArray;
    }

    @Override
    public Iterator<T> iterator(){
        return entityArray.iterator();
    }
}
