package com.phoenix.game.game;

import com.badlogic.gdx.utils.Array;
import com.phoenix.game.content.UnitTypes;

/**
 * 最小实现：默认波次编成。参照 Mindustry mindustry.game.DefaultWaves 移植。
 * 原版有 20 组（含飞行单位），这里只挑已移植的地面单位，波次推进逐步加码。
 */
public class DefaultWaves{
    private static Array<SpawnGroup> spawns;

    public Array<SpawnGroup> get(){
        if(spawns == null && UnitTypes.dagger != null){
            spawns = new Array<>();

            //开局主力：dagger，数量每 2 波 +1
            spawns.add(new SpawnGroup(UnitTypes.dagger){{
                end = 10;
                unitScaling = 2f;
            }});

            //第 4 波起：crawler（快速小单位）
            spawns.add(new SpawnGroup(UnitTypes.crawler){{
                begin = 4;
                end = 13;
                unitAmount = 2;
                unitScaling = 1.5f;
            }});

            //第 8 波起：titan
            spawns.add(new SpawnGroup(UnitTypes.titan){{
                begin = 8;
                unitScaling = 3f;
                max = 6;
            }});

            //第 12 波起：eruptor，每隔一波
            spawns.add(new SpawnGroup(UnitTypes.eruptor){{
                begin = 12;
                unitAmount = 2;
                unitScaling = 3f;
                spacing = 2;
                max = 8;
            }});

            //第 20 波起：fortress（重装），每隔两波
            spawns.add(new SpawnGroup(UnitTypes.fortress){{
                begin = 20;
                unitAmount = 2;
                unitScaling = 4f;
                spacing = 3;
                max = 6;
            }});

            //第 5 波起：flare（飞行），每波增长，激活 WaveSpawner.flySpawns 分支
            spawns.add(new SpawnGroup(UnitTypes.flare){{
                begin = 5;
                unitAmount = 3;
                unitScaling = 2f;
                max = 15;
            }});

            //第 10 波起：wraith（飞行快攻）
            spawns.add(new SpawnGroup(UnitTypes.wraith){{
                begin = 10;
                unitAmount = 2;
                unitScaling = 2f;
                max = 12;
            }});

            //第 14 波起：ghoul（飞行重轰炸），每隔两波
            spawns.add(new SpawnGroup(UnitTypes.ghoul){{
                begin = 14;
                unitAmount = 2;
                unitScaling = 3f;
                spacing = 2;
                max = 8;
            }});

            //第 9 波起：grenadier（地面榴弹），隔波
            spawns.add(new SpawnGroup(UnitTypes.grenadier){{
                begin = 9;
                unitAmount = 2;
                unitScaling = 2.5f;
                spacing = 2;
                max = 10;
            }});

            //第 16 波起：tank（重型地面），每隔两波
            spawns.add(new SpawnGroup(UnitTypes.tank){{
                begin = 16;
                unitAmount = 2;
                unitScaling = 4f;
                spacing = 2;
                max = 8;
            }});

            //Boss：chaos-array，第 40 波起每 30 波单独出 1 个（远程 4 连发散射）
            spawns.add(new SpawnGroup(UnitTypes.chaosArray){{
                begin = 40;
                unitAmount = 1;
                unitScaling = 1f;
                spacing = 30;
            }});

            //终极 Boss：eradicator，第 70 波起每 50 波出 1 个（厚血 9000）
            spawns.add(new SpawnGroup(UnitTypes.eradicator){{
                begin = 70;
                unitAmount = 1;
                unitScaling = 1f;
                spacing = 50;
            }});
        }

        return spawns == null ? new Array<>() : spawns;
    }
}
