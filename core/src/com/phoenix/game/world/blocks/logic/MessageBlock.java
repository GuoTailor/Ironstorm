package com.phoenix.game.world.blocks.logic;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextArea;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Pools;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Fill;
import com.phoenix.game.core.Fonts;
import com.phoenix.game.core.Scl;
import com.phoenix.game.entities.type.Player;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.meta.I18n;

import static com.phoenix.game.Vars.tilesize;

/**
 * 留言板。参照 Mindustry mindustry.world.blocks.logic.MessageBlock 移植。
 * <p>可配置文本，选中时把内容显示在方块上方；文本长度与换行数有上限。
 * <p>刻意降级（本项目与原版的差异）：
 * <ul>
 *     <li>（B 类）{@code @Remote setMessageBlockText} 的注解必须注释掉（本项目注解处理器会因缺 {@code @WriteClass}
 *         中断编译）→ 方法体保留原版全部校验（{@code Units.canInteract} / {@code net.server()} / {@code ValidateException}
 *         均已存在并接线），只是不广播；本地调用点 = 配置面板"确定"按钮。</li>
 *     <li>（C 类）配置界面：原版移动端走 {@code Core.input.getTextInput}、桌面端走 {@code FloatingDialog + TextArea}，
 *         本项目配置面板是常驻表格（见 {@code BlockConfigFragment}），这里直接用表格内的多行输入框 + 确定按钮，
 *         功能等价。</li>
 *     <li>{@code drawSelect} 已接线（本项目 Block 已补该钩子，{@code InputHandler.drawTop} 悬停友方建筑时调用）；
 *         {@code updateTableAlign} 仍未移植——本项目配置面板固定右上角，没有"跟随方块"的钩子
 *         （见下方注释）。{@code BitmapFont.draw} 在 libgdx 需要显式传 batch，已补上 {@code Core.batch}。</li>
 * </ul>
 */
public class MessageBlock extends Block{
    protected static int maxTextLength = 220;
    protected static int maxNewlines = 24;

    public MessageBlock(String name){
        super(name);
        configurable = true;
        solid = true;
        destructible = true;
        entityType = MessageBlockEntity::new;
    }

    /**
     * 设置留言板文本（对应原版 {@code @Remote setMessageBlockText}）。
     * <p>TODO（B 类）：原版是 {@code @Remote(targets = Loc.both, called = Loc.both, forward = true)}
     *     的网络方法，本项目 {@code @Remote} 未移植（保持注释状态），因此本方法只能本地调用
     *     （配置面板的"确定"按钮），不做网络广播。
     * <p>权限/长度校验已按原版还原：{@code Units.canInteract} 与 {@code net.server()} / {@code ValidateException}
     *     在项目内均存在（{@code Vars.net} 恒 null，需守卫）。
     */
    public static void setMessageBlockText(Player player, Tile tile, String text){
        if(!com.phoenix.game.entities.Units.canInteract(player, tile)) return;
        if(Vars.net != null && Vars.net.server() && text.length() > maxTextLength){
            throw new com.phoenix.game.net.ValidateException(player, "Player has gone above text limit.");
        }
        //can be broken while a player is typing
        if(!(tile.block() instanceof MessageBlock)){
            return;
        }

        StringBuilder result = new StringBuilder(text.length());
        text = text.trim();
        int count = 0;
        for(int i = 0; i < text.length(); i++){
            char c = text.charAt(i);
            if(c == '\n' || c == '\r'){
                count ++;
                if(count <= maxNewlines){
                    result.append('\n');
                }
            }else{
                result.append(c);
            }
        }

        TileEntity entity = tile.entity;
        if(entity instanceof MessageBlockEntity){
            MessageBlockEntity message = (MessageBlockEntity)entity;
            message.message = result.toString();
            message.lines = message.message.split("\n");
        }
    }

    /** 把留言内容画在方块上方（对应原版 {@code Block.drawSelect}；调用点 = {@code InputHandler.drawTop} 悬停友方建筑时）。 */
    @Override
    public void drawSelect(Tile tile){
        if(!(tile.entity instanceof MessageBlockEntity)) return;
        MessageBlockEntity entity = (MessageBlockEntity)tile.entity;

        BitmapFont font = Fonts.outline;
        if(font == null) return;

        GlyphLayout l = Pools.obtain(GlyphLayout.class);
        boolean ints = font.usesIntegerPositions();
        font.getData().setScale(1 / 4f / Scl.scl(1f));
        font.setUseIntegerPositions(false);

        String text = entity.message == null || entity.message.isEmpty() ? "[lightgray]" + I18n.get("empty", "empty") : entity.message;

        l.setText(font, text, Color.WHITE, 90f, Align.left, true);
        float offset = 1f;

        //⚠ 坐标口径修正：原版用 tile.drawx()/drawy()（原版口径下即方块中心）；本项目 Tile.drawx() 少半格
        //（= centerX(tile) - tilesize/2f），Fill.rect 与 font.draw 都是中心/左上语义 → 直接用会整体偏左下 4。
        Draw.color(0f, 0f, 0f, 0.2f);
        Fill.rect(centerX(tile), centerY(tile) - tilesize / 2f - l.height / 2f - offset, l.width + offset * 2f, l.height + offset * 2f);
        Draw.color();
        font.setColor(Color.WHITE);
        //libgdx 的 BitmapFont.draw 必须显式传 batch（arc 的版本隐含用当前批次）
        font.draw(Core.batch, text, centerX(tile) - l.width / 2f, centerY(tile) - tilesize / 2f - offset, 90f, Align.left, true);
        font.setUseIntegerPositions(ints);

        font.getData().setScale(1f);

        Pools.free(l);
    }

    @Override
    public void buildConfiguration(Tile tile, Table table){
        MessageBlockEntity entity = tile.entity instanceof MessageBlockEntity ? (MessageBlockEntity)tile.entity : null;

        //等价替代（C 类）：原版桌面端走 FloatingDialog + TextArea（$editmessage 标题），移动端走 Core.input.getTextInput；
        //     本项目配置面板是常驻表格，这里直接用表格内的输入框 + 确定按钮，功能等价（见类注释）
        TextArea area = new TextArea(entity == null ? "" : entity.message.replace("\n", "\r"), Styles.areaField);
        area.setMaxLength(maxTextLength);
        //libgdx 的 TextField 是 setTextFieldFilter(TextFieldFilter)（arc 叫 setFilter）
        area.setTextFieldFilter((textField, c) -> {
            if(c == '\n' || c == '\r'){
                int count = 0;
                for(int i = 0; i < textField.getText().length(); i++){
                    if(textField.getText().charAt(i) == '\n' || textField.getText().charAt(i) == '\r'){
                        count++;
                    }
                }
                return count < maxNewlines;
            }
            return true;
        });

        table.add(area).size(Scl.scl(200f), Scl.scl(90f)).row();

        TextButton ok = new TextButton(I18n.get("ok", "ok"), Styles.defaultt);
        ok.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                setMessageBlockText(Vars.player, tile, area.getText());
            }
        });
        table.add(ok).size(Scl.scl(90f), Scl.scl(30f)).row();
    }

    //TODO（B 类，缺钩子）：原版 updateTableAlign(Tile, Table) 让配置面板跟随方块位置（Core.input.mouseScreen）；
    //     本项目配置面板固定在右上角（BlockConfigFragment），没有该钩子，暂不移植。

    public class MessageBlockEntity extends TileEntity{
        public String message = "";
        public String[] lines = {""};

        @Override
        public void write(java.io.DataOutputStream out) throws java.io.IOException{
            super.write(out);
            out.writeUTF(message);
        }

        @Override
        public void read(java.io.DataInputStream in, byte revision) throws java.io.IOException{
            super.read(in, revision);
            message = in.readUTF();
            //lines 是 message 的派生量，读档后重算（原版只存 message）
            lines = message.split("\n");
        }
    }
}
