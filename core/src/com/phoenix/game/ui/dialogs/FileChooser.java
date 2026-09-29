package com.phoenix.game.ui.dialogs;

import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Pools;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Fonts;
import com.phoenix.game.core.Scl;
import com.phoenix.game.func.Boolf;
import com.phoenix.game.func.Cons;
import com.phoenix.game.ui.Label;
import com.phoenix.game.ui.Styles;
import com.phoenix.game.util.Fi;
import io.anuke.mindustry.gen.Icon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 文件选择对话框，参照 Mindustry mindustry.ui.dialogs.FileChooser。
 *
 * <p><b>与本项目的差异</b>：
 * <ul>
 *   <li>{@link Fi} 不继承 FileHandle，且没有 {@code list(Boolf)} / {@code getName()} →
 *       用 {@code list()} 取全部再手工过滤（见 {@link #getFileNames()}），名字取 {@code name()}。</li>
 *   <li>libgdx 的 {@code TextField} 没有 {@code changed(Runnable)}/{@code change()} →
 *       改挂 {@link ChangeListener}；{@code clearText()} → {@code setText("")}。</li>
 *   <li>arc 的 {@code Table.addImageButton(icon, action)} / {@code button.clicked(Runnable)} /
 *       {@code setDisabled(Boolp)} libgdx 都没有 → 分别用 {@link #iconButton}、{@link ClickListener}、
 *       在 {@link #updateFiles} 末尾显式刷新（原版是按需求值的 {@code Boolp}）。</li>
 *   <li>TODO {@code platform.addDialog(filefield)}（移动端软键盘）依赖 {@code Vars.platform}，本项目未提供 → 跳过。</li>
 *   <li>静态的 homeDirectory/lastDirectory 改成懒初始化：本项目 {@code Core.files} 在类初始化阶段可能还是 null。</li>
 * </ul>
 */
public class FileChooser extends FloatingDialog{
    private static Fi homeDirectory, lastDirectory;

    private Table files;
    private Fi directory = homeDirectory();
    private ScrollPane pane;
    private TextField navigation, filefield;
    private TextButton ok;
    /** 前进/后退按钮：原版用 {@code setDisabled(Boolp)} 按需求值，libgdx 只有 boolean 版，需要持有引用手动刷新。 */
    private ImageButton backButton, forwardButton;
    private final FileHistory stack = new FileHistory();
    private final Boolf<Fi> filter;
    private final Cons<Fi> selectListener;
    private final boolean open;

    /**
     * 打开文件选择器（对应原版 {@code Platform.showFileChooser(open, extension, cons)} 的 default 实现）。
     * <p>⚠ <b>保存模式（{@code open=false}）会把结果强制改成 {@code 原名 + "." + extension}</b> —— 原版就是
     * 这么写的：用户只输 {@code abc} 时得到 {@code abc.msch}，而不是无扩展名的 {@code abc}（否则游戏读不到）。
     * 打开模式则原样返回用户选中的文件。
     */
    public static void showFileChooser(boolean open, String extension, Cons<Fi> result){
        new FileChooser(open ? "$open" : "$save",
            file -> file.extension().equalsIgnoreCase(extension), open, file -> {
                if(!open){
                    result.get(file.parent().child(file.nameWithoutExtension() + "." + extension));
                }else{
                    result.get(file);
                }
            }).show();
    }

    public FileChooser(String title, Boolf<Fi> filter, boolean open, Cons<Fi> result){
        super(title);
        this.open = open;
        this.filter = filter;
        this.selectListener = result;

        onResize(() -> {
            cont.clear();
            setupWidgets();
        });

        shown(() -> {
            cont.clear();
            setupWidgets();
        });
    }

    /** @return 外部存储根目录（对应原版静态字段 homeDirectory，改成懒初始化）。 */
    private static Fi homeDirectory(){
        if(homeDirectory == null){
            homeDirectory = new Fi(Core.files.absolute(Core.files.getExternalStoragePath()));
        }
        if(lastDirectory == null){
            lastDirectory = homeDirectory;
        }
        return homeDirectory;
    }

    private void setupWidgets(){
        cont.pad(Scl.scl(-10f));

        Table content = new Table();

        filefield = new TextField("", Styles.defaultField);
        filefield.setOnlyFontChars(false);
        //TODO 原版：if(!open) platform.addDialog(filefield)（移动端软键盘；Vars.platform 未提供）
        filefield.setDisabled(open);

        ok = new TextButton(bundle(open ? "$load" : "$save"), Styles.defaultt);

        ok.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                if(ok.isDisabled()) return;
                if(selectListener != null)
                    selectListener.get(directory.child(filefield.getText()));
                hide();
            }
        });

        filefield.addListener(new ChangeListener(){
            @Override
            public void changed(ChangeEvent event, Actor actor){
                ok.setDisabled(filefield.getText().replace(" ", "").isEmpty());
            }
        });

        TextButton cancel = new TextButton(bundle("$cancel"), Styles.defaultt);
        cancel.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                hide();
            }
        });

        navigation = new TextField("", Styles.defaultField);
        navigation.setTouchable(Touchable.disabled);

        files = new Table();
        files.padRight(Scl.scl(10f)).padLeft(Scl.scl(3f));

        pane = new ScrollPane(files, Styles.defaultPane);
        pane.setOverscroll(false, false);
        pane.setFadeScrollBars(false);

        updateFiles(true);

        Table icontable = new Table();

        ImageButton up = iconButton(Icon.folderParent);
        up.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                directory = directory.parent();
                updateFiles(true);
            }
        });

        ImageButton back = iconButton(Icon.arrowLeft);
        ImageButton forward = iconButton(Icon.arrowRight);
        backButton = back;
        forwardButton = forward;

        forward.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                stack.forward();
            }
        });
        back.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                stack.back();
            }
        });
        //原版是 setDisabled(Boolp) 按需求值；libgdx 只有 setDisabled(boolean)，改为每次刷新目录后同步（见 updateFiles）
        forward.setDisabled(!stack.canForward());
        back.setDisabled(!stack.canBack());

        ImageButton home = iconButton(Icon.home);
        home.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                directory = homeDirectory();
                lastDirectory = directory;
                updateFiles(true);
            }
        });

        icontable.defaults().height(Scl.scl(60f)).growX().padTop(Scl.scl(5f)).uniform();
        icontable.add(home);
        icontable.add(back);
        icontable.add(forward);
        icontable.add(up);

        Table fieldcontent = new Table();
        fieldcontent.bottom().left().add(new Label(bundle("$filename"), Styles.defaultLabel));
        fieldcontent.add(filefield).height(Scl.scl(40f)).fillX().expandX().padLeft(Scl.scl(10f));

        Table buttons = new Table();
        buttons.defaults().growX().height(Scl.scl(60f));
        buttons.add(cancel);
        buttons.add(ok);

        content.top().left();
        content.add(icontable).expandX().fillX();
        content.row();

        content.add(pane).colspan(3).grow();
        content.row();

        if(!open){
            content.bottom().left().add(fieldcontent).colspan(3).grow().padTop(Scl.scl(-2f)).padBottom(Scl.scl(2f));
            content.row();
        }

        content.add(buttons).growX();

        cont.add(content).grow();
    }

    private void updateFileFieldStatus(){
        if(!open){
            ok.setDisabled(filefield.getText().replace(" ", "").isEmpty());
        }else{
            ok.setDisabled(!directory.child(filefield.getText()).exists() || directory.child(filefield.getText()).isDirectory());
        }
    }

    private Fi[] getFileNames(){
        //原版是 directory.list(Boolf)；本项目 Fi 没有该重载，取全部再手工过滤隐藏文件
        List<Fi> list = new ArrayList<>();
        for(Fi file : directory.list()){
            if(!file.name().startsWith(".")){
                list.add(file);
            }
        }

        Fi[] handles = list.toArray(new Fi[0]);

        Arrays.sort(handles, (a, b) -> {
            if(a.isDirectory() && !b.isDirectory()) return -1;
            if(!a.isDirectory() && b.isDirectory()) return 1;
            return String.CASE_INSENSITIVE_ORDER.compare(a.name(), b.name());
        });
        return handles;
    }

    private void updateFiles(boolean push){
        if(push) stack.push(directory);
        navigation.setText(directory.toString());

        GlyphLayout layout = Pools.obtain(GlyphLayout.class);
        layout.setText(Fonts.def, navigation.getText());

        if(layout.width < navigation.getWidth()){
            navigation.setCursorPosition(0);
        }else{
            navigation.setCursorPosition(navigation.getText().length());
        }

        Pools.free(layout);

        files.clearChildren();
        files.top().left();
        Fi[] names = getFileNames();

        Image upimage = new Image(Icon.folderParent);
        TextButton upbutton = new TextButton(".." + directory.toString(), Styles.clearTogglet);
        upbutton.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                directory = directory.parent();
                lastDirectory = directory;
                updateFiles(true);
            }
        });

        upbutton.left().add(upimage).padRight(Scl.scl(4f)).padLeft(Scl.scl(4f));
        upbutton.getLabel().setAlignment(Align.left);
        upbutton.getCells().reverse();

        files.add(upbutton).align(Align.topLeft).fillX().expandX().height(Scl.scl(50f)).pad(Scl.scl(2f)).colspan(2);
        files.row();

        ButtonGroup<TextButton> group = new ButtonGroup<>();
        group.setMinCheckCount(0);

        for(Fi file : names){
            if(!file.isDirectory() && !filter.get(file)) continue; //skip non-filtered files

            String filename = file.name();

            TextButton button = new TextButton(filename, Styles.clearTogglet);
            button.getLabel().setWrap(false);
            button.getLabel().setEllipsis(true);
            group.add(button);

            button.addListener(new ClickListener(){
                @Override
                public void clicked(InputEvent event, float x, float y){
                    if(!file.isDirectory()){
                        filefield.setText(filename);
                        updateFileFieldStatus();
                    }else{
                        directory = directory.child(filename);
                        lastDirectory = directory;
                        updateFiles(true);
                    }
                }
            });

            //原版是 filefield.changed(...)：libgdx 用 ChangeListener
            filefield.addListener(new ChangeListener(){
                @Override
                public void changed(ChangeEvent event, Actor actor){
                    button.setChecked(filename.equals(filefield.getText()));
                }
            });

            Image image = new Image(file.isDirectory() ? Icon.folder : Icon.fileText);

            button.add(image).padRight(Scl.scl(4f)).padLeft(Scl.scl(4f));
            button.getCells().reverse();
            files.top().left().add(button).align(Align.topLeft).fillX().expandX()
                .height(Scl.scl(50f)).pad(Scl.scl(2f)).padTop(0f).padBottom(0f).colspan(2);
            button.getLabel().setAlignment(Align.left);
            files.row();
        }

        pane.setScrollY(0f);
        updateFileFieldStatus();

        if(open) filefield.setText("");

        //原版用 Boolp 按需求值；libgdx 只有 boolean 版，这里在每次刷新目录后同步一次
        if(backButton != null) backButton.setDisabled(!stack.canBack());
        if(forwardButton != null) forwardButton.setDisabled(!stack.canForward());
    }

    /** 用图标的图片按钮（对应 arc 的 {@code ImageButton(icon)}，本项目没有该构造器）。 */
    private static ImageButton iconButton(Drawable icon){
        ImageButton.ImageButtonStyle style = new ImageButton.ImageButtonStyle(Styles.defaulti);
        style.imageUp = icon;
        return new ImageButton(style);
    }

    public class FileHistory{
        private final com.badlogic.gdx.utils.Array<Fi> history = new com.badlogic.gdx.utils.Array<>();
        private int index;

        public FileHistory(){
        }

        public void push(Fi file){
            if(index != history.size) history.truncate(index);
            history.add(file);
            index++;
        }

        public void back(){
            if(!canBack()) return;
            index--;
            directory = history.get(index - 1);
            lastDirectory = directory;
            updateFiles(false);
        }

        public void forward(){
            if(!canForward()) return;
            directory = history.get(index);
            lastDirectory = directory;
            index++;
            updateFiles(false);
        }

        public boolean canForward(){
            return !(index >= history.size);
        }

        public boolean canBack(){
            return !(index == 1) && index > 0;
        }

        void print(){
            System.out.println("\n\n\n\n\n\n");
            int i = 0;
            for(Fi file : history){
                i++;
                if(index == i){
                    System.out.println("[[" + file.toString() + "]]");
                }else{
                    System.out.println("--" + file.toString() + "--");
                }
            }
        }
    }
}
