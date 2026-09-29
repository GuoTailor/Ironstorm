package com.phoenix.game.entities.type;

import com.phoenix.game.Vars;
import com.phoenix.game.core.Time;
import com.phoenix.game.game.Team;
import com.phoenix.game.world.Tile;

/**
 * 最小实现：玩家。参照 Mindustry mindustry.entities.type.Player 移植。
 * 原版 Player 自身就是一个 Unit（并带整套 Mech 装备系统），这里简化为“持有一个被控制的单位引用”，
 * 只移植与操作相关的部分：位置、瞄准点、射击/加速状态、死亡后重生。
 */
public class Player implements com.phoenix.game.entities.traits.TypeTrait, com.phoenix.game.entities.traits.Entity{
    /** 实体 id 分配器（原版由 BaseEntity 提供；本项目 Player 不是 BaseEntity，自带一个）。 */
    private static int lastid;

    /** 实体 id（{@code playerGroup} 的映射表按它索引）。 */
    public int id = lastid++;
    /** 所属实体组（对应原版 {@code Entity.group}）。 */
    private transient com.phoenix.game.entities.EntityGroup group;

    /** 升级所需经验基数（每级 +200%）。 */
    public static final int baseXpNeed = 3;
    /** 最大等级。 */
    public static final int maxLevel = 10;

    public String name = "noname";
    public Team team = Team.sharded;
    /** 当前经验。 */
    public int xp;
    /** 当前等级（1 起）。 */
    public int level = 1;

    // ---- 联机/列表相关（F13b：对齐原版 Player 的同名字段；单机时除 isLocal 外多为默认值）----

    /** 连接标识（服务端从 ConnectPacket 填；单机本地玩家为 null）。对应原版 {@code Player.uuid/usid}。 */
    public String uuid, usid;
    /** 是否管理员（服务端按 {@code Admins.isAdmin(uuid)} 判定；对应原版 {@code Player.isAdmin}）。 */
    public boolean isAdmin;
    /** 是否本地玩家（本机操控的那个；对应原版 {@code Player.isLocal}）。 */
    public boolean isLocal;
    /** 名字颜色（玩家列表显示用；对应原版 {@code Player.color}，未上报时为白色）。 */
    public com.badlogic.gdx.graphics.Color color = new com.badlogic.gdx.graphics.Color(com.badlogic.gdx.graphics.Color.WHITE);
    /** 服务端连接（本地玩家/单机为 null；对应原版 {@code Player.con}）。 */
    public com.phoenix.game.net.NetConnection con;

    /** 被控制的单位（死亡后仍指向该单位，重生时替换） */
    public BaseUnit unit;
    /**
     * 当前玩家单位类型（重生/切换时据此创建单位）。
     * <p>默认 = 原版出生机甲 {@code Mechs.starter}（dart-ship：飞行 + 可采铜/铅）——原版
     * {@code Player.mech = Mechs.starter}（Player.java:56），本项目没有机甲系统，用同名 UnitType 承接。
     */
    public com.phoenix.game.type.UnitType selectedType = com.phoenix.game.content.UnitTypes.dartShip;
    /** 冲刺冷却（tick）。 */
    public float dashCooldown;

    /** 鼠标世界坐标（对应原版 pointerX/pointerY） */
    public float pointerX, pointerY;
    public boolean isShooting, isBoosting;

    /**
     * 玩家侧的建造开关（对应原版 {@code Player.isBuilding} **字段**，Player.java:51；默认 true）。
     * <p>⚠ 与 {@link #isBuilding()}（BuilderTrait 语义：建造队列非空）<b>同名不同义</b>——原版就是
     * 这两个并存：字段控制"要不要推进建造队列"（E 键 Binding.pause_building 切换），
     * 方法表示"当前有没有活干"（buildautopause 自动暂停判据用）。
     */
    public boolean isBuilding = true;
    /** 上次暂停是否来自 buildautopause 自动暂停（对应原版 {@code Player.buildWasAutoPaused}）；
     *  E 键手动切换时清零。 */
    public boolean buildWasAutoPaused = false;

    // ---- 背包（对应原版 Unit.item：只能携带一种物品；容量由单位类型决定）----
    //刻意偏离：原版背包是 Unit 上的**可变** ItemStack（item.item / item.amount）；
    //本项目 Player 不是 Unit，且 type/ItemStack 是 final 不可变值对象（还被方块配方常量共享，
    //见 type/Zone.java 里"创建新对象代替改 amount"的先例）→ 这里拆成两个可变字段，API 仍按原版命名。
    /** 背包内的物品类型（null 表示空）。对应原版 {@code Unit.item.item}。 */
    public com.phoenix.game.type.Item item;
    /** 背包内的物品数量。对应原版 {@code Unit.item.amount}。 */
    public int itemAmount;
    /** 背包物品的显示渐变 0~1（对应原版 {@code Player.itemtime}，背上物品的出现/消失动画）。 */
    public float itemtime;
    /** 是否正在向方块投递物品（对应原版 {@code Player.isTransferring}，防止重复投递）。 */
    public boolean isTransferring;

    // ---- 玩家发言气泡（对应原版 Player 的 textFadeTime/lastText/isTyping）----

    /** 头顶气泡剩余显示时间（收到聊天消息时置 1f，之后按 {@code Time.delta()/(60*5)} 衰减）。 */
    public float textFadeTime;
    /** 头顶气泡里显示的最后一条发言（无发言时为 null）。 */
    public String lastText;
    //TODO 原版：isTyping 由 NetServer 的 chatting 包设置（本项目无该包字段，降级为恒 false）
    /** 是否正在输入聊天。原版由 NetServer 的 chatting 包设置；本项目无该包字段 → 恒为 false。 */
    public boolean isTyping;

    /** 当前位置（死亡后保留最后位置，用于查询最近核心） */
    public float x, y;
    /** 已死亡等待时间（tick） */
    public float deadTime;
    /** 本波剩余重生次数（对应原版 {@code Player.respawns}；每波由 {@code Logic.runWave} 按 rules.respawns 重置）。 */
    public int respawns;

    public Player(){
        //重生次数按规则初始化（对应原版 Player.reset() 里的 respawns = state.rules.respawns）
        respawns = Vars.state == null || Vars.state.rules == null ? 5 : Vars.state.rules.respawns;
    }

    public boolean isDead(){
        return unit == null || unit.isDead();
    }

    /** @return 本实体的同步类型（对应原版 {@code Player.getTypeID()}）。 */
    @Override
    public com.phoenix.game.type.TypeID getTypeID(){
        return com.phoenix.game.content.TypeIDs.player;
    }

    public BaseUnit unit(){
        return unit;
    }

    /**
     * @return 玩家当前单位的完整图标（对应原版 {@code Player.getIconRegion()}，原版取 {@code mech.icon(full)}）。
     * <p>本项目没有机甲系统 → 取被控单位类型的 full 图标；没有单位时返回 null（列表里会留空位）。
     */
    public com.badlogic.gdx.graphics.g2d.TextureRegion getIconRegion(){
        return unit == null || unit.getType() == null ? null
            : unit.getType().icon(com.phoenix.game.ui.Cicon.full);
    }

    /**
     * 在世界里绘制玩家名字（对应原版 {@code Player.drawName}）：非本地玩家头顶画一行黑底 + 名字色文字，
     * 管理员再叠一枚 {@code icon-admin-small} 徽章。
     * <p>用 {@code Fonts.def}（libgdx 的 {@code BitmapFont}）。
     * <p>⚠ <b>与原版一致</b>：只有 {@code !isLocal} 才画 → <b>单机下什么都不显示</b>（联机才看得到别人的名字）。
     * <p><b>降级/偏离原版</b>：
     * <ul>
     *   <li>{@code font.draw(...)}：原版调 arc 的 {@code Font.draw(str, x, y, targetWidth, align, wrap)}
     *       （隐含使用当前批次）；libgdx 的 {@code BitmapFont} 必须显式传 batch → 改用
     *       {@code font.draw(Core.batch, ...)}，参数语义一致（{@code targetWidth = 0} + {@code Align.center}
     *       时以 x 为文字中心，与原版相同）。</li>
     *   <li>{@code GlyphLayout}：原版 {@code Pools.obtain}，这里直接 {@code new}（单次绘制无需池化）。</li>
     *   <li>原版末尾的 {@code Draw.reset()} 本项目没有 → 用 {@code Draw.color()} 复位到白色。</li>
     *   <li>原版 {@code Icon.adminSmall.getRegion()} 走 {@code Icon} 枚举；本项目改为
     *       {@code Core.atlas.findRegion("icon-admin-small")} + 判空（与 {@code MinimapRenderer} 同口径）。</li>
     * </ul>
     */
    public void drawName(){
        //⚠ 原版顺序：先画名字（if(!isLocal) 块），再画发言气泡。气泡那段复用了同一个 layout 与已缩放的 font，
        //  所以不能放在这里（此处 font 还没设 scale，且 layout 随后会被名字测量覆盖）→ 挪到名字块之后，见下方。

        com.badlogic.gdx.graphics.g2d.BitmapFont font = com.phoenix.game.core.Fonts.def;
        if(font == null) return;

        final float nameHeight = 11f;
        //气泡的基准高度（对应原版 drawName 里的 final float textHeight = 15）
        final float textHeight = 15f;
        com.badlogic.gdx.graphics.g2d.GlyphLayout layout = new com.badlogic.gdx.graphics.g2d.GlyphLayout();

        boolean ints = font.usesIntegerPositions();
        font.setUseIntegerPositions(false);
        font.getData().setScale(0.25f / com.phoenix.game.core.Scl.scl(1f));
        layout.setText(font, name);

        if(!isLocal){
            //黑底：原版用 arc 的 Fill.rect（ShapeRenderer）。⚠ 本项目**不能**在这里用 Fill：
            //  实测 Fill.rect 之后紧跟的 font.draw/Draw.rect（SpriteBatch）会整段丢失（文字完全不出现；
            //  去掉 Fill 或改成同批次的 Draw.rect 就正常）—— 见 转换.md 378。
            //  → 降级为用 atlas 的 white 贴图 + Draw.rect（同为"中心 + 宽高"语义），全程走 SpriteBatch。
            com.badlogic.gdx.graphics.g2d.TextureRegion white = com.phoenix.game.core.Core.atlas == null
                ? null : com.phoenix.game.core.Core.atlas.findRegion("white");
            if(white != null){
                com.phoenix.game.core.Draw.color(0f, 0f, 0f, 0.3f);
                com.phoenix.game.core.Draw.rect(white, x, y + nameHeight - layout.height / 2f,
                    layout.width + 2f, layout.height + 3f);
                com.phoenix.game.core.Draw.color();
            }

            font.setColor(color);
            font.draw(com.phoenix.game.core.Core.batch, name, x, y + nameHeight, 0f,
                com.badlogic.gdx.utils.Align.center, false);

            if(isAdmin){
                float s = 3f;
                com.badlogic.gdx.graphics.g2d.TextureRegion admin = com.phoenix.game.core.Core.atlas == null
                    ? null : com.phoenix.game.core.Core.atlas.findRegion("icon-admin-small");
                if(admin != null){
                    //原版是「半透明深色徽章 + 本色徽章」错位 0.5 叠出立体感
                    com.phoenix.game.core.Draw.color(color.r * 0.5f, color.g * 0.5f, color.b * 0.5f, 1f);
                    com.phoenix.game.core.Draw.rect(admin, x + layout.width / 2f + 3f, y + nameHeight - 1.5f, s, s);
                    com.phoenix.game.core.Draw.color(color);
                    com.phoenix.game.core.Draw.rect(admin, x + layout.width / 2f + 3f, y + nameHeight - 1f, s, s);
                }
            }
        }

        //玩家发言气泡（对应原版 Player.drawName:415-426）。原版用 Fill.rect 画黑底，
        //⚠ 本项目不能在这里用 Fill/Lines（ShapeRenderer）—— 会让后面的 font.draw 整段丢失（转换.md 378）
        //  → 降级为 atlas 的 white 贴图 + Draw.rect，全程走 SpriteBatch。
        //注：本块要放在名字块之后，原版也是这个顺序（气泡黑底压住名字黑底）。
        if(com.phoenix.game.core.Core.settings.getBool("playerchat")
            && ((textFadeTime > 0f && lastText != null) || isTyping)){
            String text = textFadeTime <= 0f || lastText == null
                ? "[LIGHT_GRAY]" + animatedDots() : lastText;
            float width = 100f;
            float visualFadeTime = 1f - com.phoenix.game.math.Mathf.curve(1f - textFadeTime, 0.9f);
            float alpha = textFadeTime <= 0f || lastText == null ? 1f : visualFadeTime;

            //原版 font.setColor(1,1,1,alpha)：libgdx 的 BitmapFont.setColor 就是设到内部 cache 的 color 上，
            //font.draw(...) 会用它当字形基色，语义一致
            font.setColor(1f, 1f, 1f, alpha);
            layout.setText(font, text, com.badlogic.gdx.graphics.Color.WHITE, width,
                com.badlogic.gdx.utils.Align.bottom, true);

            com.badlogic.gdx.graphics.g2d.TextureRegion white = com.phoenix.game.core.Core.atlas == null
                ? null : com.phoenix.game.core.Core.atlas.findRegion("white");
            if(white != null){
                com.phoenix.game.core.Draw.color(0f, 0f, 0f, 0.3f * alpha);
                com.phoenix.game.core.Draw.rect(white, x, y + textHeight + layout.height - layout.height / 2f,
                    layout.width + 2f, layout.height + 3f);
                com.phoenix.game.core.Draw.color();
            }

            font.draw(com.phoenix.game.core.Core.batch, text, x - width / 2f, y + textHeight + layout.height,
                width, com.badlogic.gdx.utils.Align.center, true);
        }

        //复位（本项目没有 Draw.reset()）
        com.phoenix.game.core.Draw.color();
        font.getData().setScale(1f);
        font.setColor(com.badlogic.gdx.graphics.Color.WHITE);
        font.setUseIntegerPositions(ints);
    }

    /**
     * 头顶气泡里"正在输入"的动画点号。
     * <p><b>降级</b>：原版用 {@code Strings.animated(Time.time(), 4, 15f, ".")}（arc 的
     * {@code new String(new char[Math.abs((int)(time / scale) % length)]).replace("\0", replacement)}），
     * 本项目没有 {@code Strings.animated} → 在这里按同样的算法内联：每 15 秒多一个点，0~3 个循环。
     */
    private static String animatedDots(){
        int count = Math.abs((int)(Time.time() / 15f) % 4);
        StringBuilder sb = new StringBuilder(count);
        for(int i = 0; i < count; i++) sb.append('.');
        return sb.toString();
    }

    /** 接管一个单位（对应原版 Player 自身就是单位，这里改为引用）。 */
    public void unit(BaseUnit unit){
        this.unit = unit;

        if(unit != null){
            unit.isPlayer = true;
            //小地图给其他玩家显示名字用（原版 Player 就是单位，名字直接挂在单位上）
            unit.playerName = name;
            unit.setTeam(team);
            x = unit.x;
            y = unit.y;
            //重生/接管时补满血（maxHealth() 已含等级加成与 rules.playerHealthMultiplier）
            unit.health(unit.maxHealth());
        }

        deadTime = 0f;
        isShooting = isBoosting = false;
        //原版 Player.reset() 里 isShooting = isBoosting = isTransferring = isTyping = false
        isTransferring = false;
    }

    public Team getTeam(){
        return team;
    }

    public float getX(){
        return x;
    }

    public float getY(){
        return y;
    }

    // ---- 实体接口（对应原版 Entity；Player 在原版就是 Unit，是 Entity）----

    @Override
    public void setX(float x){
        this.x = x;
    }

    @Override
    public void setY(float y){
        this.y = y;
    }

    @Override
    public int getID(){
        return id;
    }

    @Override
    public void resetID(int id){
        this.id = id;
    }

    /** 重新分配一个 id（对应原版 BaseEntity.incrementID，池化实体用）。 */
    public void incrementID(){
        id = lastid++;
    }

    @Override
    public com.phoenix.game.entities.EntityGroup getGroup(){
        return group;
    }

    @Override
    public void setGroup(com.phoenix.game.entities.EntityGroup group){
        this.group = group;
    }

    /** @return 玩家归属 {@link Vars#playerGroup}（对应原版 {@code Player.targetGroup()}）。 */
    @Override
    public com.phoenix.game.entities.EntityGroup targetGroup(){
        return Vars.playerGroup;
    }

    public Tile getClosestCore(){
        return Vars.state.teams == null ? null : Vars.state.teams.closestCore(x, y, team);
    }

    // ---- 背包 API（对应原版 Unit.java:333-357；原版 Player 覆写 getItemCapacity 返回 mech.itemCapacity）----

    /**
     * @return 背包容量。对应原版 {@code Player.getItemCapacity() → mech.itemCapacity}。
     * <p>刻意偏离：本项目没有机甲系统（{@code MechPad} 把机甲映射成 {@link com.phoenix.game.type.UnitType}，
     * 见该类类注释）→ 取当前单位类型的 {@code itemCapacity}（默认 30，与原版机甲容量同量级）。
     */
    public int getItemCapacity(){
        return selectedType == null ? 0 : selectedType.itemCapacity;
    }

    /** 能否再收下该物品（对应原版 {@code Unit.acceptsItem}）：背包为空，或同种物品且未超容量。 */
    public boolean acceptsItem(com.phoenix.game.type.Item item){
        return itemAmount <= 0 || (this.item == item && itemAmount <= getItemCapacity());
    }

    /** 收下 1 个物品（对应原版 {@code Unit.addItem(Item)}）。 */
    public void addItem(com.phoenix.game.type.Item item){
        addItem(item, 1);
    }

    /** 收下物品（对应原版 {@code Unit.addItem(Item,int)}）：换物品类型时数量重置，最后夹到容量。 */
    public void addItem(com.phoenix.game.type.Item item, int amount){
        this.itemAmount = this.item == item ? this.itemAmount + amount : amount;
        this.item = item;
        this.itemAmount = com.phoenix.game.math.Mathf.clamp(this.itemAmount, 0, getItemCapacity());
    }

    /** 清空背包（对应原版 {@code Unit.clearItem}）。 */
    public void clearItem(){
        itemAmount = 0;
    }

    /**
     * @return 还能再收下多少个该物品（对应原版 {@code Unit.maxAccepted}）。
     * <p>刻意补的 API：原版在 {@code InputHandler} 里直接写 {@code player.item().amount -= accepted}，
     * 本项目背包不是可变 ItemStack → 用 {@link #removeItem(int)} 承担同样的语义。
     */
    public int maxAccepted(com.phoenix.game.type.Item item){
        return this.item != item && itemAmount > 0 ? 0 : getItemCapacity() - itemAmount;
    }

    /** 从背包取走指定数量（本项目自定，替代原版 {@code item().amount -= amount} 的写法）。 */
    public void removeItem(int amount){
        itemAmount = com.phoenix.game.math.Mathf.clamp(itemAmount - amount, 0, getItemCapacity());
    }

    /**
     * 在玩家单位背面绘制背包物品（对应原版 {@code Player.drawBackItems() → Unit.drawBackItems(itemtime, isLocal)}）。
     * <p>原版调用链是 {@code Mech.drawStats → player.drawBackItems()}；本项目没有机甲系统 →
     * 由 {@code BaseUnit.drawStats()} 对"本机玩家当前控制的单位"调用一次。
     * <p>绘制本体放在 {@link BaseUnit#drawBackItems}（静态）：单位自己的载物背包与玩家背包共用同一套画法，
     * 只有数据来源与位置不同。
     */
    public void drawBackItems(){
        if(unit == null) return;
        //原版玩家版带数量显示（number = isLocal）
        BaseUnit.drawBackItems(item, itemAmount, itemtime, isLocal, unit.x, unit.y, unit.rotation);
    }

    // ---- 建造队列（真正的队列挂在被控制的单位上，见 BaseUnit#updateBuilding） ----

    /** 追加一条建造/拆除请求。玩家死亡（没有单位）时无法建造。 */
    public void addBuildRequest(com.phoenix.game.world.BuildRequest req){
        if(unit != null) unit.addBuildRequest(req);
    }

    public void clearBuilding(){
        if(unit != null) unit.clearBuilding();
    }

    public boolean isBuilding(){
        return unit != null && unit.isBuilding();
    }

    // ---- 采矿（真正的实现挂在被控制的单位上，见 BaseUnit implements MinerTrait）----

    /** @return 正在开采的瓦片（对应原版 {@code Player.getMineTile()}）。 */
    public Tile getMineTile(){
        return unit == null ? null : unit.getMineTile();
    }

    /** 设置正在开采的瓦片（对应原版 {@code Player.setMineTile(Tile)}）。 */
    public void setMineTile(Tile tile){
        if(unit != null) unit.setMineTile(tile);
    }

    /** @return 当前单位能否开采该物品（对应原版 {@code Player.canMine(Item)}）。 */
    public boolean canMine(com.phoenix.game.type.Item item){
        return unit != null && unit.canMine(item);
    }

    /** 每帧更新：同步位置 + 死亡后在最近核心处重生（对应原版 updateRespawning）。 */
    public void update(){
        //头顶发言气泡淡出（对应原版 Player.update 里的 textFadeTime -= Time.delta() / (60 * 5)）
        textFadeTime -= Time.delta() / (60 * 5);

        //背包物品的出现/消失渐变（对应原版 Player.update 里的
        //itemtime = lerpDelta(itemtime, Mathf.num(item.amount > 0), 0.1f)）
        itemtime = com.phoenix.game.math.Mathf.lerpDelta(itemtime, itemAmount > 0 ? 1f : 0f, 0.1f);

        if(!isDead()){
            x = unit.x;
            y = unit.y;
            deadTime = 0f;
            return;
        }

        //死亡：背包物品被消耗（对应原版 Unit.onDeath 里的 item.amount = 0 —— 原版玩家死亡不掉落物品）
        clearItem();

        //没有核心就无法复活（与原版一致）
        Tile core = getClosestCore();
        if(core == null) return;

        deadTime += Time.delta();

        //重生等待时间来自规则（对应原版 state.rules.respawnTime；sandbox/editor 为 0 → 立即复活）
        com.phoenix.game.game.Rules rules = Vars.state == null ? null : Vars.state.rules;
        float respawnTime = rules == null ? 60f * 4f : rules.respawnTime;
        //限制重生次数（对应原版 update 里的 respawns > 0 || !state.rules.limitedRespawns）
        boolean canRespawn = rules == null || respawns > 0 || !rules.limitedRespawns;

        if(deadTime >= respawnTime && canRespawn && Vars.control != null){
            respawns--;
            Vars.control.spawnPlayerUnit(team);
        }
    }

    /** 增加经验；满级不再积累。 */
    public void addXp(int amount){
        if(level >= maxLevel) return;
        xp += amount;
        while(level < maxLevel && xp >= xpNeed()){
            xp -= xpNeed();
            level++;
        }
    }

    /** @return 升到下一级所需经验。 */
    public int xpNeed(){
        return (int)(baseXpNeed * Math.pow(2, level - 1));
    }

    /** @return 玩家命中加成倍率（每级 +10%，再乘 rules.playerDamageMultiplier）。 */
    public float damageMultiplier(){
        float rules = Vars.state == null || Vars.state.rules == null ? 1f : Vars.state.rules.playerDamageMultiplier;
        return (1f + (level - 1) * 0.1f) * rules;
    }

    /** @return 玩家最大生命倍率（每级 +10%，再乘 rules.playerHealthMultiplier）。 */
    public float healthMultiplier(){
        float rules = Vars.state == null || Vars.state.rules == null ? 1f : Vars.state.rules.playerHealthMultiplier;
        return (1f + (level - 1) * 0.1f) * rules;
    }
}
