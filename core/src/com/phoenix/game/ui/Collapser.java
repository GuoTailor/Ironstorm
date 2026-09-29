package com.phoenix.game.ui;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.phoenix.game.func.Cons;

/**
 * 可折叠容器。参照 {@code arc.scene.ui.layout.Collapser} 移植。
 * <p>libgdx 没有对应类型（只有一个功能更弱的 {@code Collapsible} 概念），而原版 UI 大量用它做
 * "展开/收起"区块（异常堆栈、服务器列表、物品统计）。
 * <p>行为：高度在 0 与内容 prefHeight 之间按 {@code seconds} 线性过渡；完全收起时跳过绘制
 * （本项目 stencil 为 0，无法用裁剪，只能靠不画来避免内容溢出）。
 */
public class Collapser extends WidgetGroup{

    private Table table;
    private boolean collapsed;
    private float currentHeight;
    private float seconds = 0.4f;

    /** 用回调填充内容（对应 arc {@code Collapser(Cons<Table>, boolean)}）。 */
    public Collapser(Cons<Table> builder, boolean collapsed){
        this(new Table(), collapsed);
        builder.get(table);
        currentHeight = collapsed ? 0f : table.getPrefHeight();
    }

    public Collapser(Table table, boolean collapsed){
        this.table = table;
        this.collapsed = collapsed;
        addActor(table);
        currentHeight = collapsed ? 0f : table.getPrefHeight();
    }

    public Collapser setDuration(float seconds){
        this.seconds = seconds;
        return this;
    }

    public void toggle(){
        toggle(!collapsed);
    }

    public void toggle(boolean animate){
        setCollapsed(!collapsed, animate);
    }

    public void setCollapsed(boolean collapsed, boolean animate){
        this.collapsed = collapsed;
        if(!animate){
            currentHeight = collapsed ? 0f : table.getPrefHeight();
            invalidateHierarchy();
        }
    }

    public void setCollapsed(boolean collapsed){
        setCollapsed(collapsed, true);
    }

    public boolean isCollapsed(){
        return collapsed;
    }

    public void setTable(Table table){
        this.table = table;
        clearChildren();
        addActor(table);
    }

    public Table getTable(){
        return table;
    }

    @Override
    public void act(float delta){
        super.act(delta);

        float target = collapsed ? 0f : table.getPrefHeight();
        if(currentHeight != target){
            if(seconds <= 0f){
                currentHeight = target;
            }else{
                float step = Math.max(table.getPrefHeight(), 1f) * delta / seconds;
                currentHeight = currentHeight < target
                        ? Math.min(target, currentHeight + step)
                        : Math.max(target, currentHeight - step);
            }
            invalidateHierarchy();
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha){
        // 完全收起时不绘制：本项目没有 stencil，内容会溢出容器
        if(collapsed && currentHeight <= 0.5f) return;
        super.draw(batch, parentAlpha);
    }

    @Override
    public void layout(){
        if(table == null) return;
        table.setBounds(0f, 0f, getWidth(), currentHeight);
    }

    @Override
    public float getPrefWidth(){
        return table == null ? 0f : table.getPrefWidth();
    }

    @Override
    public float getPrefHeight(){
        return currentHeight;
    }

    @Override
    public float getMinWidth(){
        return 0f;
    }

    @Override
    public float getMinHeight(){
        return currentHeight;
    }
}
