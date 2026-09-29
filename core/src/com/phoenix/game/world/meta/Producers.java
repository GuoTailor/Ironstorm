package com.phoenix.game.world.meta;

import com.phoenix.game.ctype.Content;

/**
 * "生产者"标记。参照 Mindustry mindustry.world.meta.Producers 移植。
 * <p>原版这个类（19 行）**不是内容注册表、也不是 ContentList**，只是一个持有单个 {@link Content} 的小包装：
 * {@code set/get/is} 三个方法，用来在方块上标记"本方块产出的是哪个内容"。
 * 全工程只有它自己引用自己（原版也没有任何调用点），属于尚未接线的遗留工具类。
 * <p>因此本类**照原版语义最小实现**：同名同字段同方法，不做任何结构改造，
 * 也不进 {@code ContentLoader.lists()}（它不注册任何内容，不进链就不会影响内容 id 顺序）。
 * <p>注意区分：{@code world/producers/Produce}、{@code ProduceItem} 是另一个包（原版
 * {@code mindustry.world.producers}）下的空基类占位，与本类无关。
 */
public class Producers{
    private Content output;

    public void set(Content content){
        this.output = content;
    }

    public Content get(){
        return output;
    }

    public boolean is(Content content){
        return content == output;
    }
}
