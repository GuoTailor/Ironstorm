package com.phoenix.game.core;
import com.phoenix.game.game.Rules;
import com.phoenix.game.game.Teams;
import com.phoenix.game.game.Team;
import com.phoenix.game.game.DefaultWaves;
import com.phoenix.game.game.EventType;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.Vars;
import com.phoenix.game.entities.type.BaseUnit;
import com.phoenix.game.entities.type.Player;
import com.phoenix.game.input.DesktopInput;
import com.phoenix.game.input.InputHandler;
import com.phoenix.game.entities.type.Bullet;
import com.phoenix.game.entities.Effects;
import com.phoenix.game.content.UnitTypes;
import com.phoenix.game.entities.Units;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.content.Items;
import com.phoenix.game.type.ItemStack;
import com.phoenix.game.world.Build;
import com.phoenix.game.world.Tile;

/**
 * 最小实现：游戏控制（开始战役、加载地图）。参照 Mindustry mindustry.core.Control 移植。
 */
public class Control{
    /** 战役地图尺寸（瓦片） */
    public static final int mapWidth = 120, mapHeight = 120;

    /** 当前输入处理器（对应原版 control.input） */
    public InputHandler input = new DesktopInput();

    /**
     * 加载内容相关初始化。对应原版 Control.loadAsync()，必须在 Core.atlas 就绪后调用一次。
     * 原版的世界贴图是按 4 倍分辨率导出的，scale_marker 的宽度即为缩放基准（4 -> Draw.scl = 0.25），
     * 不设置的话单位等按贴图原始尺寸绘制的对象会大 4 倍。
     */
    public void loadAsync(){
        TextureRegion marker = Core.atlas == null ? null : Core.atlas.findRegion("scale_marker");
        if(marker != null){
            Draw.scl = 1f / marker.getRegionWidth();
        }
    }

    /** 开始战役：生成地图、生成双方单位并进入游戏。 */
    public void play(){
        Vars.world = new World();
        Vars.world.createMap(mapWidth, mapHeight);

        //队伍与规则必须在放置核心前重置：核心实体初始化时会挂到本队共享库存上
        Vars.state.rules = new Rules();
        Vars.state.teams = new Teams();
        Vars.state.gameOver = false;

        //波次：默认编成 + 开局宽限（对应原版 Logic.play 的 waveSpacing * 2）
        Vars.state.wave = 1;
        Vars.state.enemies = 0;
        Vars.state.rules.spawns = new DefaultWaves().get();
        Vars.state.wavetime = Vars.state.rules.waveSpacing * 2f;

        //建造材料返还比例 + 开局材料（对应原版 Logic.play 的 starting items）
        Build.refundMultiplier = Vars.state.rules.deconstructRefundMultiplier;
        Vars.state.rules.loadout = new ItemStack[]{
            new ItemStack(Items.copper, 200), new ItemStack(Items.lead, 100), new ItemStack(Items.silicon, 60)
        };
        com.phoenix.game.world.modules.ItemModule startItems = Vars.state.teams.items(Team.sharded);
        for(ItemStack stack : Vars.state.rules.loadout){
            startItems.add(stack.item, stack.amount);
        }

        //双方核心：当前为单瓦片实体，Block.size=3 仅用于绘制尺寸；建筑系统完善后再接多格链接。
        placeCore(mapWidth / 2, (int)(mapHeight * 0.35f), Team.crux);
        placeCore(mapWidth / 2, (int)(mapHeight * 0.65f), Team.sharded);

        //开局放一个太阳能发电机（演示电网起点）
        com.phoenix.game.world.Tile solarTile = Vars.world.tile(mapWidth / 2 + 8, (int)(mapHeight * 0.65f));
        if(solarTile != null && solarTile.block() == Blocks.air){
            solarTile.setBlock(Blocks.solarPanel, Team.sharded, 0);
        }

        Vars.state.set(GameState.State.playing);

        Units.units.clear();
        Bullet.all.clear();
        Effects.clear();
        Time.clear(); //清掉上一局残留的延迟任务（武器连发等）

        //世界加载事件：触发寻路器重建网格、波次生成器扫描出生点
        Events.fire(new EventType.WorldLoadEvent());

        //开局不再预置双方部队：敌方只靠波次刷新，我方靠玩家自己造（单位工厂）

        //玩家：原版在地核处出生并接管一个单位（这里简化为“控制一个 dagger”）
        Vars.player = new Player();
        resetCamera();
        spawnPlayerUnit(Team.sharded);

        //切换到游戏输入（对应原版 control.input 接管输入）
        if(input != null){
            input.use();
        }

        //新战役不绑定已有槽位（独立于自动存档）
        Vars.saves.resetCurrent();

        System.out.println("战役模式：已加载地图 " + mapWidth + "x" + mapHeight);
    }

    /** 把当前局保存到当前槽（无槽则自动新建）。 */
    public void save(){
        try{
            Vars.saves.saveCurrent();
            System.out.println("已存档 -> " + (Vars.saves.getCurrent() == null ? "?" : Vars.saves.getCurrent().file.getName()));
        }catch(Exception e){
            System.err.println("存档失败: " + e);
        }
    }

    /**
     * 从指定槽读档并接管战场。
     * 读档会整体替换世界/状态/单位，随后重建寻路、出生点与玩家。
     */
    public void load(com.phoenix.game.game.Saves.SaveSlot slot){
        try{
            slot.load();
            Vars.state.rules.spawns = new DefaultWaves().get();
            Build.refundMultiplier = Vars.state.rules.deconstructRefundMultiplier;
            //上一局若已判定胜负（gameOver=true）会残留在状态里，
            //不清掉的话 Logic 不会刷波次也不判胜负，读档进来的这一局等于卡死
            Vars.state.gameOver = false;

            Units.units.clear();
            Bullet.all.clear();
            Effects.clear();
            Time.clear();
            Vars.player = new Player();

            //世界加载事件：重建寻路网格 + 出生点
            Events.fire(new EventType.WorldLoadEvent());
            //loadRestore 直接写瓦片，补建电网拓扑
            if(Vars.world != null){
                Vars.world.rebuildPowerGraphs();
            }

            Vars.state.set(GameState.State.playing);
            resetCamera();
            spawnPlayerUnit(Team.sharded);
            input.use();

            System.out.println("已读档：第 " + Vars.state.wave + " 波");
        }catch(Exception e){
            System.err.println("读档失败: " + e);
        }
    }

    /**
     * 在我方核心旁生成玩家控制的单位（对应原版玩家在地核重生）。
     * 开局与死亡重生都走这里；相机立即对准玩家，避免镜头飞过去。
     */
    public void spawnPlayerUnit(Team team){
        if(Vars.world == null) return;

        Tile core = Vars.state.teams.closestCore(Vars.world.unitWidth() / 2f, Vars.world.unitHeight() / 2f, team);
        //出生点放在核心靠己方一侧（敌核在地图另一端，避免一出生就进敌方火力范围）
        float x = core != null ? core.getX() : Vars.world.unitWidth() * 0.5f;
        float y = core != null ? core.getY() + 32f : Vars.world.unitHeight() * 0.5f;

        //用玩家当前选择的单位类型重生（默认 dagger）
        com.phoenix.game.type.UnitType type = Vars.player != null && Vars.player.selectedType != null
            ? Vars.player.selectedType : UnitTypes.dagger;
        BaseUnit unit = type.create();
        unit.setTeam(team);
        unit.set(x, y);
        unit.health(unit.maxHealth());
        unit.isPlayer = true;

        if(Vars.player != null){
            Vars.player.team = team;
            Vars.player.unit(unit);
        }

        if(Core.camera != null){
            Core.camera.position.set(x, y, 0f);
            Core.camera.update();
        }
    }

    /** 退出到菜单。 */
    public void menu(){
        Vars.state.set(GameState.State.menu);
        Effects.clear();
        //玩家只在战役中存在
        Vars.player = null;
        //清理战场残留（单位/子弹/建筑实体）
        Units.units.clear();
        Bullet.all.clear();
        //重置事件：停掉寻路后台线程
        Events.fire(new EventType.ResetEvent());
        Time.clear();
    }

    /** 在指定瓦片放置核心（带队营/旋转）。 */
    private void placeCore(int x, int y, Team team){
        Tile tile = Vars.world.tile(x, y);
        if(tile == null) return;
        tile.setBlock(Blocks.core, team, 0);
    }

    /**
     * 重置相机。原版在进入世界后把相机放到玩家（或其核心）位置，缩放保持 Renderer 的默认值；
     * 这里没有玩家单位，就放在我方出生点附近。
     */
    private void resetCamera(){
        if(Core.camera == null) return;

        Core.camera.position.set(Vars.world.unitWidth() * 0.42f, Vars.world.unitHeight() * 0.5f, 0f);

        if(Vars.renderer != null){
            Vars.renderer.clampScale();
            Vars.renderer.update();
        }

        Core.camera.update();
    }
}
