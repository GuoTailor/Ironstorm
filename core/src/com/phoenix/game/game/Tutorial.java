package com.phoenix.game.game;

import com.badlogic.gdx.utils.ObjectIntMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.content.Items;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Drawf;
import com.phoenix.game.core.Events;
import com.phoenix.game.core.Logic;
import com.phoenix.game.core.Lines;
import com.phoenix.game.core.Scl;
import com.phoenix.game.core.Strings;
import com.phoenix.game.core.Tmp;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.func.Boolp;
import com.phoenix.game.func.Func;
import com.phoenix.game.game.EventType.BlockBuildEndEvent;
import com.phoenix.game.game.EventType.BlockInfoEvent;
import com.phoenix.game.game.EventType.ClientLoadEvent;
import com.phoenix.game.game.EventType.CoreItemDeliverEvent;
import com.phoenix.game.game.EventType.DepositEvent;
import com.phoenix.game.game.EventType.LineConfirmEvent;
import com.phoenix.game.game.EventType.Trigger;
import com.phoenix.game.game.EventType.TurretAmmoDeliverEvent;
import com.phoenix.game.game.EventType.WithdrawEvent;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.input.DesktopInput;
import com.phoenix.game.math.Angles;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.struct.Array;
import com.phoenix.game.type.Item;
import com.phoenix.game.ui.dialogs.FloatingDialog;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.meta.I18n;

import static com.phoenix.game.Vars.*;

/**
 * Handles tutorial state.
 * <p><b>与本项目的差异（都是待补的依赖，不是刻意的玩法改动）</b>：
 * <ul>
 *   <li>当前实例通过 {@code Vars.control.tutorial} 获取（与 {@link TutorialStage} 的工具方法一致，
 *       与原版同构）：{@link Tutorial} 是 {@code Control} 的字段，本类内部反向引用它。</li>
 *   <li>{@code Core.scene}（arc 的 Scene）没有对应物 → outline/toggled 的元素查找用
 *       {@link #findVisible} 在 libgdx Stage 上按名 DFS（F15 起 UI 元素已按原版名字命名）；
 *       浮层由 {@code ClientLauncher} 在 HUD 绘制之后调 {@link #draw()}。</li>
 *   <li>{@code Teams.playerCores()} 已接线（S3 批次）→ 直接用玩家阵营的核心列表取核心。
 *       {@code state.rules.defaultTeam} <b>已存在</b>，放置阵营/读库存已改用它。</li>
 * </ul>
 */
public class Tutorial{
    private static final int mineCopper = 18;
    private static final int blocksToBreak = 3, blockOffset = -6;

    private ObjectSet<String> events = new ObjectSet<>();
    private ObjectIntMap<Block> blocksPlaced = new ObjectIntMap<>();
    private int sentence;
    public TutorialStage stage = TutorialStage.values()[0];

    /**
     * @return 当前教程实例（对应原版直接引用 {@code control.tutorial}）。
     * <p>本类构造发生在 {@code Vars.control} 赋值之前，所以只能<b>运行期</b>取 ——
     * 工具方法（text/event/placed）都在进游戏后才被调用，那时 {@code Vars.control} 已就绪。
     */
    static Tutorial inst(){
        return control == null ? null : control.tutorial;
    }

    public Tutorial(){
        //统计"已放置方块"（对应原版同名监听器）。F7 批补上 BlockBuildEndEvent 后这段才可用；
        //⚠ breaking=true 是"拆除完成"，不计入
        Events.on(BlockBuildEndEvent.class, event -> {
            if(!event.breaking){
                blocksPlaced.getAndIncrement(event.tile.block(), 0, 1);
            }
        });

        Events.on(LineConfirmEvent.class, event -> events.add("lineconfirm"));
        Events.on(TurretAmmoDeliverEvent.class, event -> events.add("ammo"));
        Events.on(CoreItemDeliverEvent.class, event -> events.add("coreitem"));
        Events.on(BlockInfoEvent.class, event -> events.add("blockinfo"));
        Events.on(DepositEvent.class, event -> events.add("deposit"));
        Events.on(WithdrawEvent.class, event -> events.add("withdraw"));

        Events.on(ClientLoadEvent.class, e -> {
            for(TutorialStage stage : TutorialStage.values()){
                stage.load();
            }
        });
    }

    /** update tutorial state, transition if needed */
    public void update(){
        if(stage.done.get() && !canNext()){
            next();
        }else{
            stage.update();
        }
    }

    /** draw UI overlay */
    public void draw(){
        //原版是 if(!Core.scene.hasDialog()) —— 本项目没有 Core.scene，用输入层的同款判定（F15 补）
        if(DesktopInput.hasDialog()) return;
        if(FloatingDialog.scene == null) return;

        //浮层画在 HUD（stage）之上，坐标系是 stage 的 1:1 屏幕坐标：
        //几何会话（Drawf.begin）会取"批次当前投影"，所以要自己开一个 batch 会话 ——
        //stage.draw() 刚结束时批次投影仍是 stage 矩阵，begin 后直接复用（对应原版
        //UI.update 里 scene.draw() 之后的 control.tutorial.draw(); Draw.flush()）
        Core.batch.begin();
        try{
            stage.draw();
        }finally{
            Drawf.end();
            Core.batch.end();
        }
    }

    /** Resets tutorial state. */
    public void reset(){
        stage = TutorialStage.values()[0];
        stage.begin();
        blocksPlaced.clear();
        events.clear();
        sentence = 0;
    }

    /** Goes on to the next tutorial step. */
    public void next(){
        //照抄原版：这里的上界是 values().length（越界一格），但最后一个阶段 done 恒为 false，走不到这里
        stage = TutorialStage.values()[Mathf.clamp(stage.ordinal() + 1, 0, TutorialStage.values().length)];
        stage.begin();
        blocksPlaced.clear();
        events.clear();
        sentence = 0;
    }

    public boolean canNext(){
        return sentence + 1 < stage.sentences.size;
    }

    public void nextSentence(){
        if(canNext()){
            sentence ++;
        }
    }

    public boolean canPrev(){
        return sentence > 0;
    }

    public void prevSentence(){
        if(canPrev()){
            sentence --;
        }
    }

    public enum TutorialStage{
        intro(
        line -> Strings.format(line, item(Items.copper), mineCopper),
        () -> item(Items.copper) >= mineCopper
        ),
        //注：原版是 Blocks.mechanicalDrill，本项目的机械钻头字段名叫 copperDrill（方块内部名仍是 mechanical-drill）
        drill(() -> placed(Blocks.copperDrill, 1)){
            void draw(){
                outline("category-production");
                outline("block-mechanical-drill");
                outline("confirmplace");
            }
        },
        blockinfo(() -> event("blockinfo")){
            void draw(){
                outline("category-production");
                outline("block-mechanical-drill");
                outline("blockinfo");
            }
        },
        conveyor(() -> placed(Blocks.conveyor, 2) && event("lineconfirm") && event("coreitem")){
            void draw(){
                outline("category-distribution");
                outline("block-conveyor");
            }
        },
        turret(() -> placed(Blocks.duo, 1)){
            void draw(){
                outline("category-turret");
                outline("block-duo");
            }
        },
        drillturret(() -> event("ammo")),
        pause(() -> state.isPaused()){
            void draw(){
                if(mobile){
                    outline("pause");
                }
            }
        },
        unpause(() -> !state.isPaused()){
            void draw(){
                if(mobile){
                    outline("pause");
                }
            }
        },
        breaking(TutorialStage::blocksBroken){
            void begin(){
                placeBlocks();
            }

            void draw(){
                if(mobile){
                    outline("breakmode");
                }
            }
        },
        withdraw(() -> event("withdraw")){
            void begin(){
                //原版 Tutorial.java:165 state.teams.playerCores().first().items.add(Items.copper, 10)（已接线 S3）
                //⚠ 本项目核心的 entity.items **就是**本队共享 ItemModule（CoreEntity.init 里赋的引用）
                //  → 往核心库存加 = 往本队共享库存加，行为与原版一致。
                if(state.teams != null){
                    Tile c = state.teams.playerCores().isEmpty() ? null : state.teams.playerCores().first();
                    if(c != null && c.entity != null){
                        c.entity.items.add(Items.copper, 10);
                    }
                }
            }
        },
        deposit(() -> event("deposit")),
        waves(() -> state.wave > 2 && state.enemies <= 0 && !spawner.isSpawning()){
            void begin(){
                state.rules.waveTimer = true;
                //原版写 logic.runWave()（Vars.logic 是 Logic 实例）；本项目 Logic 是静态工具类
                Logic.runWave();
            }

            void update(){
                if(state.wave > 2){
                    state.rules.waveTimer = false;
                }
            }
        },
        launch(() -> false){
            void begin(){
                state.rules.waveTimer = false;
                state.wave = 5;

                //end tutorial, never show it again
                Events.fire(Trigger.tutorialComplete);
                Core.settings.put("playedtutorial", true);
                Core.settings.save();
            }

            void draw(){
                outline("waves");
            }
        },;

        protected String line = "";
        protected final Func<String, String> text;
        protected Array<String> sentences;
        protected final Boolp done;

        TutorialStage(Func<String, String> text, Boolp done){
            this.text = text;
            this.done = done;
        }

        TutorialStage(Boolp done){
            this(line -> line, done);
        }

        /** displayed tutorial stage text.*/
        public String text(){
            if(sentences == null){
               load();
            }
            Tutorial self = inst();
            String line = sentences.get(self == null ? 0 : self.sentence);
            return line.contains("{") ? text.get(line) : line;
        }

        void load(){
            //注：原版是 Core.bundle.has("tutorial.<name>.mobile")；libgdx 的 I18NBundle 没有 has →
            // 用 I18n.get 的缺键返回 null 判定。
            this.line = I18n.get("tutorial." + name() + ".mobile", null) != null && mobile ? "tutorial." + name() + ".mobile" : "tutorial." + name();
            this.sentences = Array.select(Core.bundle.get(line).split("\n"), s -> !s.isEmpty());
        }

        /** called every frame when this stage is active.*/
        void update(){

        }

        /** called when a stage begins.*/
        void begin(){

        }

        /** called when a stage needs to draw itself, usually over highlighted UI elements. */
        void draw(){

        }

        //utility

        /** @return 玩家阵营的第一个核心实体。 */
        static TileEntity playerCore(){
            //原版 Tutorial.java:243/251 state.teams.playerCores().first()（已接线 S3）
            if(player == null || state.teams == null) return null;
            Tile core = state.teams.playerCores().isEmpty() ? null : state.teams.playerCores().first();
            return core == null ? null : core.entity;
        }

        static void placeBlocks(){
            TileEntity core = playerCore();
            if(core == null) return;

            for(int i = 0; i < blocksToBreak; i++){
                //放置阵营用 state.rules.defaultTeam（原版同此；Rules.defaultTeam 已存在）
                Tile target = world.ltile(core.tile.x + blockOffset, core.tile.y + i);
                if(target != null) target.remove();

                Tile tile = world.tile(core.tile.x + blockOffset, core.tile.y + i);
                if(tile != null) tile.setBlock(Blocks.scrapWall, state.rules.defaultTeam);
            }
        }

        static boolean blocksBroken(){
            TileEntity core = playerCore();
            if(core == null) return true;

            for(int i = 0; i < blocksToBreak; i++){
                Tile tile = world.tile(core.tile.x + blockOffset, core.tile.y + i);
                if(tile != null && tile.block() == Blocks.scrapWall){
                    return false;
                }
            }
            return true;
        }

        static boolean event(String name){
            Tutorial self = inst();
            return self != null && self.events.contains(name);
        }

        static boolean placed(Block block, int amount){
            return placed(block) >= amount;
        }

        static int placed(Block block){
            Tutorial self = inst();
            return self == null ? 0 : self.blocksPlaced.get(block, 0);
        }

        static int item(Item item){
            //原版 Tutorial.java:274 = state.rules.defaultTeam.data().noCores() ? 0 : state.rules.defaultTeam.core().items.get(item)
            //（已接线 S3：Teams.core(team) 无核心时返回 null，天然覆盖原版 noCores() 分支）
            if(state.rules == null) return 0;
            Team t = state.rules.defaultTeam;
            Tile c = state.teams == null ? null : state.teams.core(t);
            return (c == null || c.entity == null) ? 0 : c.entity.items.get(item);
        }

        static boolean toggled(String name){
            //对应原版 Core.scene.findVisible(name) instanceof Button && isChecked()
            //（F15：本项目 stage 元素已按原版名字命名，libgdx 的 Button 同样有 isChecked）
            Actor element = findVisible(name);
            return element instanceof Button && ((Button)element).isChecked();
        }

        static void outline(String name){
            //教程指引框：按名字找可见 UI 元素，画呼吸高亮框 + 从屏幕中心指向它的箭头（F15 补，原 TutorialStage.outline）
            Actor element = findVisible(name);
            if(element != null && !toggled(name)){
                element.localToStageCoordinates(Tmp.v1.setZero());
                float sin = Mathf.sin(11f, Scl.scl(4f));
                Lines.stroke(Scl.scl(7f), Pal.place);
                Lines.rect(Tmp.v1.x - sin, Tmp.v1.y - sin, element.getWidth() + sin*2, element.getHeight() + sin*2);

                float size = Math.max(element.getWidth(), element.getHeight()) + Mathf.absin(11f/2f, Scl.scl(18f));
                float angle = Angles.angle(Core.graphics.getWidth()/2f, Core.graphics.getHeight()/2f, Tmp.v1.x + element.getWidth()/2f, Tmp.v1.y + element.getHeight()/2f);
                //arc 的 Vector2.trns（极坐标设置方向+长度）在 libgdx 无对应 → 用本项目 Angles 等价
                Tmp.v2.set(Angles.trnsx(angle + 180f, size*1.4f), Angles.trnsy(angle + 180f, size*1.4f));
                float fs = Scl.scl(40f);
                float fs2 = Scl.scl(56f);

                Draw.color(Pal.gray);
                Drawf.tri(Tmp.v1.x + element.getWidth()/2f + Tmp.v2.x, Tmp.v1.y + element.getHeight()/2f + Tmp.v2.y, fs2, fs2, angle);
                Draw.color(Pal.place);
                Tmp.v2.setLength(Tmp.v2.len() - Scl.scl(4));
                Drawf.tri(Tmp.v1.x + element.getWidth()/2f + Tmp.v2.x, Tmp.v1.y + element.getHeight()/2f + Tmp.v2.y, fs, fs, angle);
                Draw.color();   //原版是 Draw.reset()
            }
        }

        /** @return 按名字在 HUD stage 上查找**可见**元素（对应原版 {@code Core.scene.findVisible}）。 */
        static Actor findVisible(String name){
            com.badlogic.gdx.scenes.scene2d.Stage scene = FloatingDialog.scene;
            if(scene == null) return null;
            return findVisible(scene.getRoot(), name);
        }

        /** 照 arc {@code Group.findVisible}：先查直接子节点，再递归**可见**子组。 */
        private static Actor findVisible(Group group, String name){
            com.badlogic.gdx.utils.Array<Actor> children = group.getChildren();
            for(int i = 0; i < children.size; i++){
                Actor child = children.get(i);
                if(name.equals(child.getName()) && child.isVisible()) return child;
            }
            for(int i = 0; i < children.size; i++){
                Actor child = children.get(i);
                if(child instanceof Group && child.isVisible()){
                    Actor found = findVisible((Group)child, name);
                    if(found != null) return found;
                }
            }
            return null;
        }
    }

}
