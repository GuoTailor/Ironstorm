package com.phoenix.game.entities.effect;

import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Pools;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Fill;
import com.phoenix.game.core.Lines;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.traits.DrawTrait;
import com.phoenix.game.entities.type.TimedEntity;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.math.Position;
import com.phoenix.game.type.Item;
import com.phoenix.game.world.Tile;

/**
 * "物品飞向目标"的传输特效实体。参照 Mindustry mindustry.entities.effect.ItemTransfer 移植。
 * <p>从 {@code from} 沿一条抛物线插值飞向 {@code to}，到达生命周期末尾时执行 {@link #done} 回调
 * （原版就是用它把"物品真正入账"的时机绑在动画结束上）。
 * <p>刻意偏离（本项目结构）：
 * <ul>
 *     <li>原版的三个静态方法都带 {@code @Remote} 网络同步注解；本项目 {@code @Remote} 必须保持注释状态
 *         （注解处理器会因 {@code No @WriteClass method} 中断编译），所以去掉注解与 {@code Loc.server}。</li>
 *     <li>原版用 {@code arc.math.Interpolation.pow3}；本项目改用 libgdx 的 {@link Interpolation#pow3}（同为 a³）。</li>
 *     <li>原版 {@code Lines.stroke(width, color)} / {@code Draw.reset()} 本项目没有对应重载，
 *         拆成 {@code Draw.color(...) + Lines.stroke(width)} 与 {@code Draw.color()}。</li>
 * </ul>
 * <p>登记进 {@link com.phoenix.game.Vars#effectGroup}（对应原版 {@code Vars.effectGroup}）：由组每帧
 * update/draw，生命周期结束时 {@code remove()} → {@link #removed()} 里执行 {@link #done}
 * （原版就是把"物品真正入库/入包"的时机绑在动画结束上）。{@code Logic.update()} 调
 * {@code effectGroup.update()}、{@code Renderer.draw()} 调 {@code effectGroup.draw()}。
 * <p>注：{@code BaseUnit.addItem(Item)}（单位载物背包）已在"玩家背包/单位背包"批次补齐，
 * {@link #transferItemToUnit} 的回调不再是空实现。
 */
public class ItemTransfer extends TimedEntity implements DrawTrait{
    private Vector2 from = new Vector2();
    private Vector2 current = new Vector2();
    private Vector2 tovec = new Vector2();
    private Item item;
    private float seed;
    private Position to;
    private Runnable done;

    public ItemTransfer(){
    }

    /** 纯视觉转移（原版 {@code @Remote(called = Loc.server, unreliable = true)}，本项目去掉网络注解）。 */
    public static void transferItemEffect(Item item, float x, float y, BaseUnit to){
        if(to == null) return;
        create(item, x, y, to, () -> {
        });
    }

    /** 把物品加进单位载物背包（对应原版 {@code @Remote transferItemToUnit}）：动画结束时回调 {@code addItem}。 */
    public static void transferItemToUnit(Item item, float x, float y, BaseUnit to){
        if(to == null) return;
        create(item, x, y, to, () -> to.addItem(item));
    }

    /** 把物品转移进目标瓦片库存（核心的 entity.items 即队伍共享库存）+ 播放飞行动画。 */
    public static void transferItemTo(Item item, int amount, float x, float y, Tile tile){
        if(tile == null || tile.entity == null || tile.entity.items == null) return;
        for(int i = 0; i < Mathf.clamp(amount / 3, 1, 8); i++){
            Time.run(i * 3, () -> create(item, x, y, tile, () -> {
            }));
        }
        tile.entity.items.add(item, amount);
    }

    public static void create(Item item, float fromx, float fromy, Position to, Runnable done){
        //原版是 Pools.obtain(ItemTransfer.class, ItemTransfer::new)（arc 的 Supplier 重载）；libgdx 只有反射版
        ItemTransfer tr = Pools.obtain(ItemTransfer.class);
        tr.item = item;
        tr.from.set(fromx, fromy);
        tr.to = to;
        tr.done = done;
        tr.seed = Mathf.range(1f);
        //池化复用：Pools.free 的 reset() 不清 dead（BaseEntity 的字段），复用时必须显式复位
        tr.setDead(false);

        //原版：tr.add() 把实体登记进 Vars.effectGroup，由组每帧 update/draw，并在生命周期结束时
        //remove() → removed() 里执行 done（"物品真正入库/入包"的时机绑在动画结束上）。
        tr.add();
    }

    /**
     * 以**固定坐标**为目标创建特效（对应原版直接传 {@code new Vec2(x, y)} 当 Position 用的写法，
     * 例如 {@code InputHandler.transferInventory} 里物品飞向方块的落点）。
     */
    public static void create(Item item, float fromx, float fromy, float tox, float toy, Runnable done){
        create(item, fromx, fromy, new FixedPosition(tox, toy), done);
    }

    /**
     * 固定坐标的 {@link Position} 实现。
     * <p>本项目 {@code math.Position} 只有 {@link Tile} 一个实现（原版用 arc 的 {@code Vec2} 兼作 Position）
     * → 补一个轻量实现，供"飞向任意坐标"的调用点使用。
     */
    public static class FixedPosition implements Position{
        private final float x, y;

        public FixedPosition(float x, float y){
            this.x = x;
            this.y = y;
        }

        @Override
        public float getX(){
            return x;
        }

        @Override
        public float getY(){
            return y;
        }
    }

    @Override
    public float lifetime(){
        return 60;
    }

    @Override
    public void reset(){
        super.reset();
        item = null;
        to = null;
        done = null;
        from.setZero();
        current.setZero();
        tovec.setZero();
    }

    @Override
    public void removed(){
        if(done != null){
            done.run();
        }
        Pools.free(this);
    }

    @Override
    public void update(){
        if(to == null || isDead()){
            remove();
            return;
        }

        super.update();

        //⚠ 本项目 {@code removed()} 是**同步**调用的（原版由 {@code EntityGroup.updateEvents()} 延迟）：
        //   上面 super.update() 若因生命周期结束触发 remove()，会立刻 Pools.free(this) 并 reset()
        //   （to/item 被清空），而该实体要到**下一帧** updateEvents 才从组的成员数组里摘掉
        //   → 本帧后续代码必须再判一次，否则 to.getX() 会 NPE（原版靠延迟回收天然避开）。
        if(to == null || isDead()) return;

        current.set(from).interpolate(tovec.set(to.getX(), to.getY()), fin(), Interpolation.pow3);
        current.add(tovec.set(to.getX(), to.getY()).sub(from).nor().rotate90(1).scl(seed * fslope() * 10f));
        set(current.x, current.y);
    }

    @Override
    public void draw(){
        //⚠ 同 update()：实体被同步回收（item 清空）后，本帧仍可能被 effectGroup.draw() 遍历到
        //   （组数组下一帧才摘）→ 判空跳过，避免 NPE
        if(item == null) return;

        //原版：Lines.stroke(fslope() * 2f, Pal.accent) —— 本项目 Lines 只有单参 stroke，颜色单独设
        Draw.color(Pal.accent);
        Lines.stroke(fslope() * 2f);
        Lines.circle(x, y, fslope() * 2f);

        Draw.color(item.color);
        Fill.circle(x, y, fslope() * 1.5f);

        //原版 Draw.reset()；本项目用 Draw.color() 复位到白色
        Draw.color();
    }

    /** @return 0→1→0 的三角波（对应 {@code TimedEntity} 未提供的 {@code fslope()}）。 */
    protected float fslope(){
        float f = fin();
        return (0.5f - Math.abs(f - 0.5f)) * 2f;
    }

    /** @return 本特效归属 {@link com.phoenix.game.Vars#effectGroup}（对应原版 {@code ItemTransfer.targetGroup()}）。 */
    @Override
    public com.phoenix.game.entities.EntityGroup targetGroup(){
        return com.phoenix.game.Vars.effectGroup;
    }
}
