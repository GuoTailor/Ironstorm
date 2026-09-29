package com.phoenix.game.entities.traits;

/**
 * Marks an entity as serializable.
 * <p>注意：{@link TypeTrait} 由本批的另一个任务负责移植，本文件依赖它存在。
 */
public interface SaveTrait extends Entity, TypeTrait, Saveable{
    byte version();
}
