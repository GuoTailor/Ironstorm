package com.phoenix.game.ui;


import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Fonts;
import com.phoenix.game.core.Scl;
import io.anuke.annotations.Annotations;
import io.anuke.mindustry.gen.Tex;

import static io.anuke.mindustry.gen.Tex.*;

@Annotations.StyleDefaults
public class Styles {
    public static Drawable black, black9, black8, black6, black3, none, flatDown, flatOver;
    public static Button.ButtonStyle defaultb, waveb;
    public static TextButton.TextButtonStyle defaultt, squaret, nodet, cleart, discordt, infot, clearPartialt, clearTogglet, clearToggleMenut, togglet;
    public static ImageButton.ImageButtonStyle defaulti, nodei, righti, emptyi, emptytogglei, selecti, cleari, clearFulli, clearPartiali, clearPartial2i, clearTogglei, clearTransi, clearToggleTransi, clearTogglePartiali;
    public static ScrollPane.ScrollPaneStyle defaultPane, horizontalPane;
    public static KeybindDialogStyle defaultKeybindDialog;
    public static Slider.SliderStyle defaultSlider, vSlider;
    public static Label.LabelStyle defaultLabel, outlineLabel;
    public static TextField.TextFieldStyle defaultField, areaField;
    public static CheckBox.CheckBoxStyle defaultCheck;
    public static DialogStyle defaultDialog, fullDialog;

    public static void load(){
        black = whiteui.tint( new Color(0f, 0f, 0f, 1f));
        black9 = whiteui.tint( new Color(0f, 0f, 0f, 0.9f));
        black8 = whiteui.tint( new Color(0f, 0f, 0f, 0.8f));
        black6 = whiteui.tint( new Color(0f, 0f, 0f, 0.6f));
        black3 = whiteui.tint( new Color(0f, 0f, 0f, 0.3f));
        none = whiteui.tint( new Color(0f, 0f, 0f, 0f));
        flatDown = createFlatDown();
        flatOver = whiteui.tint(Color.valueOf("454545"));

        defaultb = new Button.ButtonStyle(){{
            down = buttonDown;
            up = button;
            over = buttonOver;
            disabled = buttonDisabled;
        }};

        waveb = new Button.ButtonStyle(){{
            up = buttonEdge4;
            over = buttonEdgeOver4;
            disabled = buttonEdge4;
        }};

        defaultt = new TextButton.TextButtonStyle(){{
            over = buttonOver;
            disabled = buttonDisabled;
            font = Fonts.def;
            fontColor = Color.WHITE;
            disabledFontColor = Color.GRAY;
            down = buttonDown;
            up = button;
        }};
        squaret = new TextButton.TextButtonStyle(){{
            font = Fonts.def;
            fontColor = Color.WHITE;
            disabledFontColor = Color.GRAY;
            over = buttonSquareOver;
            disabled = buttonDisabled;
            down = buttonSquareDown;
            up = buttonSquare;
        }};
        nodet = new TextButton.TextButtonStyle(){{
            disabled = button;
            font = Fonts.def;
            fontColor = Color.WHITE;
            disabledFontColor = Color.GRAY;
            up = buttonOver;
            over = buttonDown;
        }};
        cleart = new TextButton.TextButtonStyle(){{
            over = flatOver;
            font = Fonts.def;
            fontColor = Color.WHITE;
            disabledFontColor = Color.GRAY;
            down = flatOver;
            up = black;
        }};
        discordt = new TextButton.TextButtonStyle(){{
            font = Fonts.def;
            fontColor = Color.WHITE;
            up = discordBanner;
        }};
        infot = new TextButton.TextButtonStyle(){{
            font = Fonts.def;
            fontColor = Color.WHITE;
            up = infoBanner;
        }};
        clearPartialt = new TextButton.TextButtonStyle(){{
            down = whiteui;
            up = pane;
            over = flatDown;
            font = Fonts.def;
            fontColor = Color.WHITE;
            disabledFontColor = Color.GRAY;
        }};
        clearTogglet = new TextButton.TextButtonStyle(){{
            font = Fonts.def;
            fontColor = Color.WHITE;
            checked = flatDown;
            down = flatDown;
            up = black;
            over = flatOver;
            disabled = black;
            disabledFontColor = Color.GRAY;
        }};
        clearToggleMenut = new TextButton.TextButtonStyle(){{
            font = Fonts.def;
            fontColor = Color.WHITE;
            checked = flatDown;
            down = flatDown;
            up = clear;
            over = flatOver;
            disabled = black;
            disabledFontColor = Color.GRAY;
        }};
        togglet = new TextButton.TextButtonStyle(){{
            font = Fonts.def;
            fontColor = Color.WHITE;
            checked = buttonDown;
            down = buttonDown;
            up = button;
            over = buttonOver;
            disabled = buttonDisabled;
            disabledFontColor = Color.GRAY;
        }};

        defaulti = new ImageButton.ImageButtonStyle(){{
            down = buttonDown;
            up = button;
            over = buttonOver;
            disabled = buttonDisabled;
        }};
        nodei = new ImageButton.ImageButtonStyle(){{
            up = buttonOver;
            over = buttonDown;
        }};
        righti = new ImageButton.ImageButtonStyle(){{
            over = buttonRightOver;
            down = buttonRightDown;
            up = buttonRight;
        }};
        emptyi = new ImageButton.ImageButtonStyle();
        emptytogglei = new ImageButton.ImageButtonStyle();
        selecti = new ImageButton.ImageButtonStyle(){{
            checked = buttonSelect;
            up = none;
        }};
        cleari = new ImageButton.ImageButtonStyle(){{
            down = flatOver;
            up = black;
            over = flatOver;
        }};
        clearFulli = new ImageButton.ImageButtonStyle(){{
            down = whiteui;
            up = pane;
            over = flatDown;
        }};
        clearPartiali = new ImageButton.ImageButtonStyle(){{
            down = flatDown;
            up = none;
            over = flatOver;
        }};
        clearPartial2i = new ImageButton.ImageButtonStyle(){{
            down = whiteui;
            up = pane;
            over = flatDown;
        }};
        clearTogglei = new ImageButton.ImageButtonStyle(){{
            down = flatDown;
            checked = flatDown;
            up = black;
            over = flatOver;
        }};
        clearTransi = new ImageButton.ImageButtonStyle(){{
            down = flatDown;
            up = black6;
            over = flatOver;
        }};
        clearToggleTransi = new ImageButton.ImageButtonStyle(){{
            down = flatDown;
            checked = flatDown;
            up = black6;
            over = flatOver;
        }};
        clearTogglePartiali = new ImageButton.ImageButtonStyle(){{
            down = flatDown;
            checked = flatDown;
            up = none;
            over = flatOver;
        }};

        defaultPane = new ScrollPane.ScrollPaneStyle(){{
            vScroll = scroll;
            vScrollKnob = scrollKnobVerticalBlack;
        }};
        horizontalPane = new ScrollPane.ScrollPaneStyle(){{
            vScroll = scroll;
            vScrollKnob = scrollKnobVerticalBlack;
            hScroll = scrollHorizontal;
            hScrollKnob = scrollKnobHorizontalBlack;
        }};

        defaultKeybindDialog = new KeybindDialogStyle(){{
            keyColor = Pal.accent;
            keyNameColor = Color.WHITE;
            controllerColor = Color.LIGHT_GRAY;
        }};

        defaultSlider = new Slider.SliderStyle(){{
            background = slider;
            knob = sliderKnob;
            knobOver = sliderKnobOver;
            knobDown = sliderKnobDown;
        }};
        vSlider = new Slider.SliderStyle(){{
            background = sliderVertical;
            knob = sliderKnob;
            knobOver = sliderKnobOver;
            knobDown = sliderKnobDown;
        }};

        defaultLabel = new Label.LabelStyle(){{
            font = Fonts.def;
            fontColor = Color.WHITE;
        }};
        outlineLabel = new Label.LabelStyle(){{
            font = Fonts.outline;
            fontColor = Color.WHITE;
        }};

        defaultField = new TextField.TextFieldStyle(){{
            font = Fonts.chat;
            fontColor = Color.WHITE;
            disabledFontColor = Color.GRAY;
            disabledBackground = underlineDisabled;
            selection = Tex.selection;
            background = underline;
            cursor = Tex.cursor;
            messageFont = Fonts.def;
            messageFontColor = Color.GRAY;
        }};
        areaField = new TextField.TextFieldStyle(){{
            font = Fonts.chat;
            fontColor = Color.WHITE;
            disabledFontColor = Color.GRAY;
            selection = Tex.selection;
            background = underline;
            cursor = Tex.cursor;
            messageFont = Fonts.def;
            messageFontColor = Color.GRAY;
        }};

        defaultCheck = new CheckBox.CheckBoxStyle(){{
            checkboxOn = checkOn;
            checkboxOff = checkOff;
            checkboxOnOver = checkOnOver;
            checkboxOver = checkOver;
            checkboxOnDisabled = checkOnDisabled;
            checkboxOffDisabled = checkDisabled;
            font = Fonts.def;
            fontColor = Color.WHITE;
            disabledFontColor = Color.GRAY;
        }};

        defaultDialog = new DialogStyle(){{
            stageBackground = black9;
            titleFont = Fonts.def;
            background = windowEmpty;
            titleFontColor = Pal.accent;
        }};
        fullDialog = new DialogStyle(){{
            stageBackground = black;
            titleFont = Fonts.def;
            background = windowEmpty;
            titleFontColor = Pal.accent;
        }};
    }

    private static Drawable createFlatDown(){
        TextureAtlas.AtlasRegion region = (TextureAtlas.AtlasRegion) Core.skin.get("flat-down-base", TextureRegion.class);
        int[] splits = region.findValue("split");

        NinePatchDrawable copy = new NinePatchDrawable(new NinePatch(region, splits[0], splits[1], splits[2], splits[3])){
            private float scale = Scl.scl(1.0F);
            @Override
            public void draw(Batch batch, float x, float y, float width, float height) {
                this.getPatch().draw(batch, x, y, 0.0F, 0.0F, width / this.scale, height / this.scale, this.scale, this.scale, 0.0F);
            }
        };
        copy.setLeftWidth(0);
        copy.setRightWidth(0);
        copy.setTopHeight(0);
        copy.setBottomHeight(0);
        copy.setMinWidth(0);
        copy.setMinHeight(0);
        copy.setTopHeight(0);
        copy.setRightWidth(0);
        copy.setBottomHeight(0);
        copy.setLeftWidth(0);
        return copy;
    }

    public static class DialogStyle {
        public Drawable background;
        public BitmapFont titleFont;
        public Color titleFontColor = new Color(1.0F, 1.0F, 1.0F, 1.0F);
        public Drawable stageBackground;

        public DialogStyle() {
        }
    }

    public static class KeybindDialogStyle {
        public Color keyColor;
        public Color keyNameColor;
        public Color controllerColor;

        public KeybindDialogStyle() {
            this.keyColor = Color.WHITE;
            this.keyNameColor = Color.WHITE;
            this.controllerColor = Color.WHITE;
        }
    }
}
