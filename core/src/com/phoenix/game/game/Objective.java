package com.phoenix.game.game;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.phoenix.game.type.Zone;
import com.phoenix.game.util.ArcAnnotate.Nullable;

/**
 * Defines a specific objective for a game.
 * <p><b>与本项目的差异</b>：无（原版的 {@code default Zone zone()} 已随 {@code Zone} 移植一并启用）。
 */
public interface Objective{

    /** @return whether this objective is met. */
    boolean complete();

    /** @return the string displayed when this objective is completed, in imperative form.
     * e.g. when the objective is 'complete 10 waves', this would display "complete 10 waves".
     * If this objective should not be displayed, should return null.*/
    @Nullable String display();

    /** Build a display for this zone requirement.*/
    default void build(Table table){

    }

    /** @return 该目标所属的区域；非区域目标返回 null（从 {@code ZoneObjective} 反查）。 */
    default Zone zone(){
        return this instanceof Objectives.ZoneObjective ? ((Objectives.ZoneObjective)this).zone : null;
    }
}
