package com.phoenix.game.mod;

/**
 * Mod listing as a data class.
 * <p>对应原版 {@code mindustry.mod.ModListing}，逐字移植（纯数据类，无任何依赖）。
 * <p>原版用于解析 Mods 浏览器（GitHub repo 列表）的 JSON；本项目没有该浏览 UI，
 * 所以本类目前<b>没有调用点</b>，属"只新增文件"。
 */
public class ModListing{
    public String repo, name, author, lastUpdated, description;
    public int stars;

    @Override
    public String toString(){
        return "ModListing{" +
        "repo='" + repo + '\'' +
        ", name='" + name + '\'' +
        ", author='" + author + '\'' +
        ", lastUpdated='" + lastUpdated + '\'' +
        ", description='" + description + '\'' +
        ", stars=" + stars +
        '}';
    }
}
