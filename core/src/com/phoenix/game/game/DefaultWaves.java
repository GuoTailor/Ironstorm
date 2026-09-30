package com.phoenix.game.game;

import com.badlogic.gdx.utils.Array;
import com.phoenix.game.content.Items;
import com.phoenix.game.content.StatusEffects;
import com.phoenix.game.content.UnitTypes;
import com.phoenix.game.type.ItemStack;

/**
 * 默认波次编成。参照 Mindustry mindustry.game.DefaultWaves 移植。
 * <p>G9-D8：已照原版 {@code game/DefaultWaves.java:12-187} 逐组补齐全部 24 组
 * （原先只挑已移植的地面单位写了 10 组；现确认 {@code UnitTypes.revenant/lich} 均已存在，
 * {@code SpawnGroup} 也支持 {@code items}/{@code effect}，故不再需要最小实现）。
 */
public class DefaultWaves{
    private static Array<SpawnGroup> spawns;

    public Array<SpawnGroup> get(){
        if(spawns == null && UnitTypes.dagger != null){
            spawns = new Array<>();

            spawns.add(new SpawnGroup(UnitTypes.dagger){{
                end = 10;
                unitScaling = 2f;
            }});

            spawns.add(new SpawnGroup(UnitTypes.crawler){{
                begin = 4;
                end = 13;
                unitAmount = 2;
                unitScaling = 1.5f;
            }});

            spawns.add(new SpawnGroup(UnitTypes.wraith){{
                begin = 12;
                end = 16;
                unitScaling = 1f;
            }});

            spawns.add(new SpawnGroup(UnitTypes.dagger){{
                begin = 11;
                unitScaling = 1.7f;
                spacing = 2;
                max = 4;
            }});

            spawns.add(new SpawnGroup(UnitTypes.titan){{
                begin = 7;
                spacing = 3;
                unitScaling = 2;

                end = 30;
            }});

            spawns.add(new SpawnGroup(UnitTypes.dagger){{
                begin = 8;
                unitScaling = 1;
                unitAmount = 4;
                spacing = 2;
            }});

            spawns.add(new SpawnGroup(UnitTypes.titan){{
                begin = 28;
                spacing = 3;
                unitScaling = 1;
                end = 40;
            }});

            spawns.add(new SpawnGroup(UnitTypes.titan){{
                begin = 45;
                spacing = 3;
                unitScaling = 2;
                effect = StatusEffects.overdrive;
            }});

            spawns.add(new SpawnGroup(UnitTypes.titan){{
                begin = 120;
                spacing = 2;
                unitScaling = 3;
                unitAmount = 5;
                effect = StatusEffects.overdrive;
            }});

            spawns.add(new SpawnGroup(UnitTypes.wraith){{
                begin = 16;
                unitScaling = 1;
                spacing = 2;
            }});

            spawns.add(new SpawnGroup(UnitTypes.dagger){{
                begin = 82;
                spacing = 3;
                unitAmount = 4;
                unitScaling = 3;
                effect = StatusEffects.overdrive;
            }});

            spawns.add(new SpawnGroup(UnitTypes.dagger){{
                begin = 41;
                spacing = 5;
                unitAmount = 1;
                unitScaling = 3;
                effect = StatusEffects.shielded;
            }});

            spawns.add(new SpawnGroup(UnitTypes.fortress){{
                begin = 40;
                spacing = 5;
                unitAmount = 2;
                unitScaling = 2;
                max = 20;
            }});

            spawns.add(new SpawnGroup(UnitTypes.dagger){{
                begin = 35;
                spacing = 3;
                unitAmount = 4;
                effect = StatusEffects.overdrive;
                items = new ItemStack(Items.blastCompound, 60);
                end = 60;
            }});

            spawns.add(new SpawnGroup(UnitTypes.dagger){{
                begin = 42;
                spacing = 3;
                unitAmount = 4;
                effect = StatusEffects.overdrive;
                items = new ItemStack(Items.pyratite, 100);
                end = 130;
            }});

            spawns.add(new SpawnGroup(UnitTypes.ghoul){{
                begin = 40;
                unitAmount = 2;
                spacing = 2;
                unitScaling = 2;
            }});

            spawns.add(new SpawnGroup(UnitTypes.wraith){{
                begin = 50;
                unitAmount = 4;
                unitScaling = 3;
                spacing = 5;
                effect = StatusEffects.overdrive;
            }});

            spawns.add(new SpawnGroup(UnitTypes.revenant){{
                begin = 50;
                unitAmount = 2;
                unitScaling = 3;
                spacing = 5;
                max = 16;
            }});

            spawns.add(new SpawnGroup(UnitTypes.ghoul){{
                begin = 53;
                unitAmount = 2;
                unitScaling = 3;
                spacing = 4;
            }});

            spawns.add(new SpawnGroup(UnitTypes.eruptor){{
                begin = 31;
                unitAmount = 4;
                unitScaling = 1;
                spacing = 3;
            }});

            spawns.add(new SpawnGroup(UnitTypes.chaosArray){{
                begin = 41;
                unitAmount = 1;
                unitScaling = 1;
                spacing = 30;
            }});

            spawns.add(new SpawnGroup(UnitTypes.eradicator){{
                begin = 81;
                unitAmount = 1;
                unitScaling = 1;
                spacing = 40;
            }});

            spawns.add(new SpawnGroup(UnitTypes.lich){{
                begin = 131;
                unitAmount = 1;
                unitScaling = 1;
                spacing = 40;
            }});

            spawns.add(new SpawnGroup(UnitTypes.ghoul){{
                begin = 90;
                unitAmount = 2;
                unitScaling = 3;
                spacing = 4;
            }});
        }

        return spawns == null ? new Array<>() : spawns;
    }
}
