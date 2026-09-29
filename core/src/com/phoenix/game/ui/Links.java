package com.phoenix.game.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.phoenix.game.graphics.Pal;
import com.phoenix.game.world.meta.I18n;
import io.anuke.mindustry.gen.Icon;

/**
 * 关于页面里的外链列表（名称/标题/描述/图标/颜色）。参照 Mindustry mindustry.ui.Links 移植。
 *
 * <p>与原版的三处差异：
 * <ul>
 *   <li>原版 {@code Core.bundle.getNotNull(...)} / {@code getOrNull(...)} 在 libgdx 的 I18NBundle 里没有，
 *       而且本项目 bundle 是裁剪过的 → 走 {@link I18n} 的双形态判定（缺键回退）。</li>
 *   <li>arc 的 {@code Strings.capitalize} 本项目未提供 → 内联一个最小实现。</li>
 *   <li>本项目 UI 图标集是裁剪过的：原版用的 list / book / redditAlien / itchio / android / githubSquare
 *       在 {@code assets-raw/sprites/ui/icons} 里不存在，改用语义最接近的现有图标（逐条见
 *       {@link #createLinks()} 里的注释），仅 reddit / itch.io 用通用 link 图标兜底。</li>
 * </ul>
 */
public class Links{
    private static LinkEntry[] links;

    private static void createLinks(){
        links = new LinkEntry[]{
            new LinkEntry("discord", "https://discord.gg/mindustry", Icon.discord, Color.valueOf("7289da")),
            //原版 Icon.list → 本项目没有，用语义更贴切的 changelog
            new LinkEntry("changelog", "https://github.com/Anuken/Mindustry/releases", Icon.changelog, Pal.accent.cpy()),
            new LinkEntry("trello", "https://trello.com/b/aE2tcUwF", Icon.trello, Color.valueOf("026aa7")),
            //原版 Icon.book → 本项目没有，用 wiki
            new LinkEntry("wiki", "https://mindustrygame.github.io/wiki/", Icon.wiki, Color.valueOf("0f142f")),
            new LinkEntry("feathub", "https://github.com/Anuken/Mindustry-Suggestions/issues/new/choose/", Icon.add, Color.valueOf("ebebeb")),
            //原版 Icon.redditAlien → 本项目没有，退化用通用 link 图标
            new LinkEntry("reddit", "https://www.reddit.com/r/Mindustry/", Icon.link, Color.valueOf("ee593b")),
            //原版 Icon.itchio → 本项目没有，退化用 donate
            new LinkEntry("itch.io", "https://anuke.itch.io/mindustry", Icon.donate, Color.valueOf("fa5c5c")),
            //原版写 Icon.googleplay（全小写），本项目注解处理器生成的字段是 googlePlay
            new LinkEntry("google-play", "https://play.google.com/store/apps/details?id=io.anuke.mindustry", Icon.googlePlay, Color.valueOf("689f38")),
            //原版 Icon.android → 本项目没有，用 fdroid（这条链接本来就是 f-droid）
            new LinkEntry("f-droid", "https://f-droid.org/packages/io.anuke.mindustry/", Icon.fdroid, Color.valueOf("026aa7")),
            new LinkEntry("github", "https://github.com/Anuken/Mindustry/", Icon.github, Color.valueOf("24292e")),
            //原版 Icon.githubSquare → 本项目没有，用 devBuilds
            new LinkEntry("dev-builds", "https://github.com/Anuken/MindustryBuilds", Icon.devBuilds, Color.valueOf("fafbfc"))
        };
    }

    public static LinkEntry[] getLinks(){
        if(links == null){
            createLinks();
        }

        return links;
    }

    public static class LinkEntry{
        public final String name, title, description, link;
        public final Color color;
        public final Drawable icon;

        public LinkEntry(String name, String link, Drawable icon, Color color){
            this.name = name;
            this.color = color;
            this.description = I18n.get("link." + name + ".description", "");
            this.link = link;
            this.icon = icon;

            String title = I18n.get("link." + name + ".title", null);
            this.title = title != null ? title : capitalize(name.replace("-", " "));
        }
    }

    /** 首字母大写（对应 arc 的 {@code Strings.capitalize}，本项目 core.Strings 未提供）。 */
    private static String capitalize(String text){
        if(text.isEmpty()) return text;
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
