package com.phoenix.game.game;

import com.phoenix.game.core.Strings;
import com.phoenix.game.type.Zone;
import com.phoenix.game.util.ArcAnnotate.NonNull;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.meta.I18n;

/** Holds objective classes. */
public class Objectives{

    //TODO
    public static class Wave implements Objective{
        public int wave;

        public Wave(int wave){
            this.wave = wave;
        }

        protected Wave(){}

        @Override
        public boolean complete(){
            return false;
        }

        @Override
        public String display(){
            //TODO
            return null;
        }
    }

    public static class Unlock implements Objective{
        public @NonNull Block block;

        public Unlock(Block block){
            this.block = block;
        }

        protected Unlock(){}

        @Override
        public boolean complete(){
            return block.unlocked();
        }

        @Override
        public String display(){
            //原版是 Core.bundle.format("requirement.unlock", block.localizedName)；本项目 bundle 缺
            //requirement.* 键（缺键返回 ???key???）→ 走 I18n 双形态判定 + 原版英文文本兜底。
            return Strings.format(I18n.get("requirement.unlock", "Unlock {0}"), block.localizedName);
        }
    }

    public static class ZoneWave extends ZoneObjective{
        public int wave;

        public ZoneWave(Zone zone, int wave){
            this.zone = zone;
            this.wave = wave;
        }

        protected ZoneWave(){}

        @Override
        public boolean complete(){
            return zone.bestWave() >= wave;
        }

        @Override
        public String display(){
            //原版是 Core.bundle.format("requirement.wave", wave, zone.localizedName)；本项目 bundle 缺
            //requirement.wave 键（缺键返回 ???key???）→ 走 I18n 双形态判定 + 原版英文文本兜底。
            return Strings.format(I18n.get("requirement.wave", "Reach Wave {0} in {1}"), wave, zone.localizedName);
        }
    }

    public static class Launched extends ZoneObjective{

        public Launched(Zone zone){
            this.zone = zone;
        }

        protected Launched(){}

        @Override
        public boolean complete(){
            return zone.hasLaunched();
        }

        @Override
        public String display(){
            //同 ZoneWave：本项目缺 requirement.core 键 → I18n 双形态判定 + 原版英文文本兜底。
            return Strings.format(I18n.get("requirement.core", "Destroy Enemy Core in {0}"), zone.localizedName);
        }
    }

    public abstract static class ZoneObjective implements Objective{
        public @NonNull Zone zone;
    }
}
