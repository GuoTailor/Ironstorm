package com.phoenix.game.world;
import com.phoenix.game.content.Blocks;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.phoenix.game.Vars;
import com.phoenix.game.game.Team;
import com.phoenix.game.world.blocks.BlockPart;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.entities.traits.TargetTrait;
import com.phoenix.game.math.Position;
import com.phoenix.game.math.geom.Geometry;

import static com.phoenix.game.Vars.tilesize;

/**
 * 最小实现：地图瓦片。参照 Mindustry mindustry.world.Tile 移植（去掉多格建筑/邻接/网络同步）。
 */
public class Tile implements Position, TargetTrait {
    /** Tile traversal cost. */
    public byte cost = 1;
    /** Tile entity, usually null. */
    public TileEntity entity;
    public short x, y;
    protected Block block;
    protected Floor floor;
    protected Floor overlay;
    /** Rotation, 0-3. */
    protected byte rotation;
    /** Team ordinal. */
    protected byte team;

    public Tile(int x, int y){
        this.x = (short)x;
        this.y = (short)y;
        block = floor = overlay = Blocks.air;
    }

    public Tile(int x, int y, Floor floor, Floor overlay, Block block){
        this.x = (short)x;
        this.y = (short)y;
        this.floor = floor;
        this.overlay = overlay;
        this.block = block;
    }

    /** Returns this tile's position as a packed int. */
    public int pos(){
        return Pos.get(x, y);
    }

    public float worldx(){
        return x * tilesize;
    }

    public float worldy(){
        return y * tilesize;
    }

    public float drawx(){
        return block().offset() + worldx();
    }

    public float drawy(){
        return block().offset() + worldy();
    }

    public Floor floor(){
        return floor;
    }

    /** 多格链接：返回本瓦片链接到的中心瓦片；非多格返回自身。 */
    public Tile link(){
        return block.linked(this);
    }

    public Block block(){
        //卫星瓦片转发到中心瓦片，保证事件/碰撞/绘制统一按中心建筑处理
        return link().block;
    }

    /** @return 本瓦片实际存储的方块（不解析多格链接）。序列化用；卫星瓦片返回 BlockPart。 */
    public Block blockRaw(){
        return block;
    }

    /** @return 本瓦片是否为多格建筑的卫星瓦片（BlockPart）：它不单独绘制/更新。 */
    public boolean isLinked(){
        return block instanceof BlockPart;
    }

    public Floor overlay(){
        return overlay;
    }

    /**
     * @return 本瓦片的矿物产出（对应原版 Tile.drop）：
     * 矿脉 overlay 优先，其次地板自带产出（如沙地掉沙）；都没有返回 null。
     */
    public com.phoenix.game.type.Item drop(){
        return overlay == Blocks.air || overlay.itemDrop == null ? floor.itemDrop : overlay.itemDrop;
    }

    public byte rotation(){
        return rotation;
    }

    public void rotation(int rotation){
        this.rotation = (byte)rotation;
    }

    public Team getTeam(){
        return Team.get(team);
    }

    public void setTeam(Team team){
        this.team = (byte)team.id;
    }

    public void setBlock(Block type, Team team, int rotation){
        this.block = type;
        this.team = (byte)team.id;
        this.rotation = (byte)(rotation % 4);

        //多格建筑：以本瓦片为锚点铺开卫星瓦片（BlockPart，链接回中心）
        //范围 = 锚点 + (0..size-1) + (-(size-1)/2)：奇数时关于锚点对称，偶数时锚点在最靠近原点的角
        //（与原版 Tile.set 一致；偶数尺寸也能正确占满 size x size 格）
        //注意顺序：**先铺卫星瓦片再 changed()**（与原版一致）——中心瓦片算邻接表时四周必须已经就位，
        //否则会把"还没被占用的格子"也当成邻居记进 proximity
        if(type.isMultiblock() && !(type instanceof BlockPart) && Vars.world != null){
            int offsetx = -(type.size - 1) / 2, offsety = -(type.size - 1) / 2;

            for(int dx = 0; dx < type.size; dx++){
                for(int dy = 0; dy < type.size; dy++){
                    int wx = x + dx + offsetx, wy = y + dy + offsety;
                    if(wx == x && wy == y) continue;

                    Tile other = Vars.world.tile(wx, wy);
                    if(other != null){
                        other.setBlock(BlockPart.get(dx + offsetx, dy + offsety), team, 0);
                    }
                }
            }
        }

        changed();
    }

    public void setBlock(Block type, Team team){
        setBlock(type, team, 0);
    }

    public void setBlock(Block type){
        if(type == null) throw new IllegalArgumentException("Block cannot be null.");
        this.block = type;
        this.rotation = 0;
        changed();
    }

    /** This resets the overlay! */
    public void setFloor(Floor type){
        this.floor = type;
        this.overlay = Blocks.air;
    }

    public void setOverlay(Block block){
        this.overlay = (Floor)block;
    }

    /**
     * 读档专用：直接还原瓦片的方块/地板/队伍/朝向，跳过 setBlock 的多格自动铺开与 notify 链。
     * 只用于彻底重建世界（整张地图一次载入），避免逐瓦片触发卫星瓦片重铺。
     * @param type 方块
     * @param rotation 朝向（0-3）
     * @param floor 地板
     * @param overlay 覆盖层（可为 null）
     */
    public void loadRestore(Block type, int rotation, com.phoenix.game.game.Team team, Floor floor, Floor overlay){
        this.block = type;
        this.team = (byte)team.id;
        this.rotation = (byte)(rotation % 4);
        this.floor = floor;
        this.overlay = overlay == null ? Blocks.air : overlay;
        this.cost = 1;

        //重建实体
        if(block.hasEntity()){
            entity = block.newEntity();
            if(entity != null){
                entity.init(this, block.update);
            }
        }else{
            entity = null;
        }
    }
    public void remove(){
        Tile center = link();
        if(center != this){
            center.remove();
            return;
        }

        Block current = this.block;
        if(current.isMultiblock() && !(current instanceof BlockPart) && Vars.world != null){
            //与 setBlock 的铺开范围保持一致（支持偶数尺寸）
            int offsetx = -(current.size - 1) / 2, offsety = -(current.size - 1) / 2;

            for(int dx = 0; dx < current.size; dx++){
                for(int dy = 0; dy < current.size; dy++){
                    Tile other = Vars.world.tile(x + dx + offsetx, y + dy + offsety);
                    if(other != null){
                        other.setBlock(Blocks.air);
                    }
                }
            }
        }else{
            setBlock(Blocks.air);
        }
    }

    /** 最小实现：切换方块时重置实体。 */
    protected void changed(){
        //实体拆除前先从邻居的邻接表里摘掉自己（对应原版 TileEntity.remove → removeFromProximity）
        if(entity != null){
            entity.removeFromProximity();
            entity.remove();
            entity = null;
        }

        boolean generating = Vars.world != null && Vars.world.isGenerating();

        //注意用字段 block（不解析多格链接）：卫星瓦片若走 block() 会转发到中心方块，被误判成"有实体"
        if(block.hasEntity()){
            entity = block.newEntity();
            if(entity != null){
                entity.init(this, block.update);
                if(!generating){
                    entity.updateProximity();
                }
            }
        }else if(!(block instanceof BlockPart) && !generating && Vars.world != null){
            //本瓦片没有实体：手工通知四周邻居刷新邻接表（对应原版 changed 的 else 分支）
            for(GridPoint2 p : Geometry.d4){
                Tile other = Vars.world.ltile(x + p.x, y + p.y);
                if(other != null && other != this){
                    other.block().onProximityUpdate(other);
                }
            }
        }

        updateOcclusion();
        if(Vars.world != null){
            Vars.world.notifyChanged(this);
        }
    }

    public void updateOcclusion(){
        cost = 1;
    }

    public boolean passable(){
        return !solid();
    }

    public boolean solid(){
        return block.solid || block.isSolidFor(this);
    }

    /**
     * 这一格能不能被玩家拆（对应原版 {@code Tile.breakable}）。
     * <p>判据是「有血量 **或** 可拆 **或** 每帧更新」：原版里机器（传送带/发电机）都没写
     * {@code destructible}，只写了 {@code update}，只看 destructible 会导致它们拆不掉。
     * <p>多格建筑的卫星格要转发到中心格判断。
     */
    public boolean breakable(){
        return !isLinked() ? (block.destructible || block.breakable || block.update) : link().breakable();
    }

    /**
     * 该瓦片能否与指定队伍的建筑互通（物流/电力邻接的门槛）。
     * <p>对应原版 {@code state.teams.canInteract(team, getTeam())}；本项目简化为"同队即可"。
     */
    public boolean interactable(Team team){
        return team == getTeam();
    }

    public Tile getNearby(int dx, int dy){
        return Vars.world == null ? null : Vars.world.tile(x + dx, y + dy);
    }

    /**
     * 向本瓦片上的建筑下发配置值（对应原版 {@code Tile.configure} → {@code Call.onTileConfig}）。
     * <p>本项目暂未接入配置同步，直接本地生效（服务端与单机行为一致；联机广播待后续补）。
     * @param value 配置值；-1 表示清除
     */
    public void configure(int value){
        block().configured(this, Vars.player, value);
    }

    /**
     * 按方向索引取相邻瓦片。
     * <p>索引与 {@link com.phoenix.game.math.geom.Geometry#d4} 一致：0=+y（上）、1=+x（右）、2=-y（下）、3=-x（左）。
     * <p>注意：原版 arc 的 d4 是 {+x,+y,-x,-y}，本项目是 {+y,+x,-y,-x}（整体顺时针），
     * 因此本方法与 {@link #relativeTo} 成对使用，方向索引自洽；但移植原版"左/右"运算时要留意顺序差异。
     */
    public Tile getNearby(int rotation){
        GridPoint2 dir = Geometry.d4[rotation & 3];
        return getNearby(dir.x, dir.y);
    }

    /** 同 {@link #getNearby(int)}，但把多格建筑的卫星瓦片解析到中心瓦片。 */
    public Tile getNearbyLink(int rotation){
        Tile tile = getNearby(rotation);
        return tile == null ? null : tile.link();
    }

    /**
     * 取"从本瓦片指向 (cx,cy)"的方向索引；不相邻返回 -1。
     * <p>索引约定与 {@link #getNearby(int)} 相同（0=+y,1=+x,2=-y,3=-x）。
     * <p>典型用法：{@code source.relativeTo(tile.x, tile.y)} 得到"物品从哪边进来"，
     * 再 {@code tile.getNearby(relative)} 就是"直行穿越"的出口（Junction 语义）。
     */
    public int relativeTo(int cx, int cy){
        if(x == cx && y == cy - 1) return 0;
        if(x == cx && y == cy + 1) return 2;
        if(x == cx - 1 && y == cy) return 1;
        if(x == cx + 1 && y == cy) return 3;
        return -1;
    }

    public int relativeTo(Tile tile){
        return relativeTo(tile.x, tile.y);
    }

    /** 静态版本（不依赖实例），语义同 {@link #relativeTo(int, int)}。 */
    public static int relativeTo(int x, int y, int cx, int cy){
        if(x == cx && y == cy - 1) return 0;
        if(x == cx && y == cy + 1) return 2;
        if(x == cx - 1 && y == cy) return 1;
        if(x == cx + 1 && y == cy) return 3;
        return -1;
    }

    /**
     * 取"从本瓦片指向 (cx,cy)"的方向索引，**不限距离**（只要求同行或同列）；
     * 不同行也不同列返回 -1。对应原版 {@code Tile.absoluteRelativeTo}。
     * <p>物品桥（ItemBridge）用它判断"对端在哪个方向"，从而禁止物品从对端方向回灌。
     */
    public int absoluteRelativeTo(int cx, int cy){
        if(x == cx && y <= cy - 1) return 0;
        if(x == cx && y >= cy + 1) return 2;
        if(x <= cx - 1 && y == cy) return 1;
        if(x >= cx + 1 && y == cy) return 3;
        return -1;
    }

    /** 静态版本，语义同 {@link #absoluteRelativeTo(int, int)}。 */
    public static int absoluteRelativeTo(int x, int y, int cx, int cy){
        if(x == cx && y <= cy - 1) return 0;
        if(x == cx && y >= cy + 1) return 2;
        if(x <= cx - 1 && y == cy) return 1;
        if(x >= cx + 1 && y == cy) return 3;
        return -1;
    }

    /** @return 正前方（按 rotation 朝向）的瓦片；越界返回 null。 */
    public Tile front(){
        return front(0);
    }

    /** @return 右手边（面朝 rotation 时）的瓦片；本项目 d4 顺时针，故 +1 为右。 */
    public Tile right(){
        return getNearbyLink((rotation + 1) % 4);
    }

    /** @return 左手边（面朝 rotation 时）的瓦片。 */
    public Tile left(){
        return getNearbyLink((rotation + 3) % 4);
    }

    /** @return 正后方（面朝 rotation 时）的瓦片。 */
    public Tile back(){
        return getNearbyLink((rotation + 2) % 4);
    }

    /**
     * 取前方第 offset 格瓦片。
     * @param offset 相对前方偏移格数（0 为紧邻）
     * @return 前方瓦片；越界返回 null
     */
    public Tile front(int offset){
        GridPoint2 dir = Geometry.d4[rotation & 3];
        int dist = offset + 1;
        Tile tile = getNearby(dir.x * dist, dir.y * dist);
        //解析多格链接（对应原版 front() = getNearbyLink）：多格建筑只有中心瓦片有实体，
        //直接返回卫星瓦片会让 acceptItem/handleItem 因为 entity == null 而失败
        return tile == null ? null : tile.link();
    }

    //Position / TargetTrait 实现

    @Override
    public boolean isDead(){
        return entity == null;
    }

    @Override
    public Vector2 velocity(){
        return Vector2.Zero;
    }

    @Override
    public float getX(){
        return drawx();
    }

    @Override
    public void setX(float x){
        throw new IllegalArgumentException("Tile position cannot change.");
    }

    @Override
    public float getY(){
        return drawy();
    }

    @Override
    public void setY(float y){
        throw new IllegalArgumentException("Tile position cannot change.");
    }

    @Override
    public String toString(){
        return floor.name + ":" + block.name + "[" + x + "," + y + "] team=" + getTeam();
    }
}
