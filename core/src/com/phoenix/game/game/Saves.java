package com.phoenix.game.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Time;
import com.phoenix.game.io.SaveIO;
import com.phoenix.game.io.SaveMeta;
import com.phoenix.game.io.SavePreview;
import com.phoenix.game.type.Zone;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 存档槽位管理器。参照 Mindustry mindustry.game.Saves 移植。
 * <p>职责：
 * <ul>
 *   <li>扫描/新建/删除存档槽（{@code saves/<n>.msav}）</li>
 *   <li>自动存档计时（仅玩家局、非菜单、非终局时触发）</li>
 *   <li>槽位元数据（波次/时长/存档时间/缩略图）—— 只读存档的 meta 区，不解析整张地图</li>
 *   <li><b>区域存档槽</b>（{@code saves/-1.msav}）：战役进度自动落盘，见 {@link #zoneSave()} /
 *       {@link #getZoneSlot()}；该槽在存档列表里隐藏（{@link SaveSlot#isHidden()}）</li>
 * </ul>
 * 未移植：导入导出、异步存档。
 */
public class Saves{
    /** 存档目录（应用本地目录下）。 */
    private static final String SAVE_DIR = "saves";
    /** 存档文件扩展名。 */
    private static final String EXT = Vars.saveExtension;
    /** 元数据里存显示名用的 tag key。 */
    private static final String NAME_TAG = "name";

    /** 全部槽位，按文件名顺序。 */
    private final Array<SaveSlot> slots = new Array<>();
    /** 当前正在游玩的槽位；null 表示本局尚未绑定槽（新战役）。 */
    private SaveSlot current;
    /** 是否启用自动存档。 */
    public boolean autosave = true;
    /** 自动存档计时器（帧）。 */
    private float timer;
    /** 存档进行中标记（同步写，量小，仅防重入）。 */
    private boolean saving;

    /** 扫描存档目录，恢复槽位列表。应用启动后调用一次。 */
    public void load(){
        slots.clear();
        File dir = Gdx.files.local(SAVE_DIR).file();
        if(!dir.exists() || dir.listFiles() == null) return;

        File[] files = dir.listFiles((d, name) -> name.endsWith("." + EXT));
        if(files == null) return;

        java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName));
        for(File file : files){
            SaveSlot slot = new SaveSlot(file);
            slot.refreshMeta();
            slots.add(slot);
        }
    }

    /** 每帧驱动自动存档计时。由 Logic.update 调用。 */
    public void update(){
        //自动存档判据（T19 起）：① 全局总开关 {@link #autosave}（本项目特有，HUD 上的勾选框）；
        //  ② 逐槽开关 {@link SaveSlot#isAutosave()}（原版语义，见 SaveDialog 卡片上的 Icon.save 按钮）。
        //  原版只有②；本项目多保留①，两者都开才会自动存档。
        //  ③ 教程局不自动存档（对应原版判据尾部的 `!state.rules.tutorial`）。
        if(current == null || Vars.state.isMenu() || Vars.state.gameOver || !autosave || !current.isAutosave()
            || (Vars.state.rules != null && Vars.state.rules.tutorial)){
            timer = 0f;
            return;
        }

        timer += Time.delta();
        //间隔取自设置 saveinterval（秒）：对应原版 Saves.update 的
        //`time > Core.settings.getInt("saveinterval") * 60`。原版 Time.delta() 以秒计，
        //而本项目它 ≈ 1.0/帧（timer 累加的是帧数）→ 同样 * 60 换算成帧数，口径一致。
        if(timer >= Core.settings.getInt("saveinterval", 60) * 60){
            timer = 0f;

            //对应原版：先置"存档中"标记（HUD 的 $saveload 指示器据此显示），再延后 2 tick 执行保存 ——
            //本项目存档是同步阻塞的，若在这里直接存完，指示器一帧都看不到。
            saving = true;
            Time.run(2f, () -> {
                try{
                    saveCurrent();
                }catch(Exception e){
                    System.err.println("自动存档失败: " + e);
                }
                saving = false;
            });
        }
    }

    /** 新建一个槽位并立即保存当前局。
     * @param name 显示名（可空，空则用文件序号） */
    public SaveSlot addSave(String name) throws IOException{
        File file = nextSlotFile();
        SaveSlot slot = new SaveSlot(file);
        if(name != null && !name.isEmpty()) slot.name = name;
        slots.add(slot);
        current = slot;
        slot.save();
        return slot;
    }

    /** 把当前局保存到当前槽（无槽则自动新建）。 */
    public void saveCurrent() throws IOException{
        if(current == null){
            addSave(null);
            return;
        }
        current.save();
    }

    /** 读档到指定槽。 */
    public void load(SaveSlot slot) throws IOException{
        slot.load();
        current = slot;
    }

    /** @return 区域存档槽文件：**固定** {@code saves/-1.msav}（对应原版 {@code zoneFile}）。 */
    private File zoneFile(){
        return new File(Gdx.files.local(SAVE_DIR).file(), "-1." + EXT);
    }

    /**
     * 把当前局写进**区域存档槽**（对应原版 {@code Saves.zoneSave()}）。
     * <p>原版用的是**固定的单个文件** {@code saves/-1.msav}（不是每个区域一个）—— 战役一次只玩一个区域，
     * 换区域时覆盖即可；槽位归属靠 meta 里的 {@code rules.zone}（见 {@link SaveSlot#getZone()}）。
     * <p>⚠ 调用点必须在 {@code state.rules.zone} 已赋值**之后**：{@code SaveSlot.save()} 内部会
     * {@code refreshMeta()}，而 meta 里的 {@code zoneName} 是写盘时由 {@code rules.zone} 生成的 ——
     * 顺序反了 {@link #getZoneSlot()} 会恒为 null。{@code Control.playZone} 在 {@code enterWorld()} 之后调它。
     * <p>同时把该槽设为"当前槽"：这样自动存档会持续刷新区域进度（与原版 {@code SaveSlot.save()}
     * 在非菜单态设 {@code current = this} 的行为一致）。
     */
    public void zoneSave(){
        try{
            File file = zoneFile();
            //同一个文件只保留一个槽对象
            for(int i = slots.size - 1; i >= 0; i--){
                if(slots.get(i).file.equals(file)){
                    slots.removeIndex(i);
                }
            }
            SaveSlot slot = new SaveSlot(file);
            slot.name = "zone";
            slots.add(slot);
            slot.save();
            current = slot;
        }catch(Exception e){
            System.err.println("区域存档失败: " + e);
        }
    }

    /**
     * @return 区域存档槽；没有（或该档的 {@code rules.zone} 解析不出来）时返回 null。
     * <p>对应原版 {@code Saves.getZoneSlot()}：既要求文件是 {@code -1.msav}，也要求 meta 里能解出区域。
     * 解析不出区域的 {@code -1.msav} 不算区域槽 → 它也不会被 {@link SaveSlot#isHidden()} 隐藏，
     * 于是仍能在普通存档列表里看到并手动载入（比"文件在但找不到"要好）。
     */
    public SaveSlot getZoneSlot(){
        File file = zoneFile();
        for(int i = 0; i < slots.size; i++){
            SaveSlot slot = slots.get(i);
            if(slot.file.equals(file) && slot.getZone() != null){
                return slot;
            }
        }
        return null;
    }

    /**
     * 把某个槽设为"当前槽"。
     * <p>原版是在 {@code SaveSlot.save()} 里直接写 {@code current = this}；本项目 {@link SaveSlot}
     * 是 static 嵌套类、拿不到外层实例，所以由调用方（{@code SaveDialog} 的覆盖保存）显式调这里。
     */
    public void setCurrent(SaveSlot slot){
        current = slot;
    }

    /** 删除槽位文件（含缩略图）并从列表移除。 */
    public void delete(SaveSlot slot){
        slots.removeValue(slot, true);
        SavePreview.deletePreview(slot.file);
        if(slot.file.exists() && !slot.file.delete()){
            System.err.println("删除存档失败: " + slot.file);
        }
        File backup = SaveIO.backupFileFor(slot.file);
        if(backup.exists()) backup.delete();
        if(current == slot) current = null;
    }

    /** @return 全部槽位。 */
    public Array<SaveSlot> getSlots(){
        return slots;
    }

    /** @return 当前槽位（可 null）。 */
    public SaveSlot getCurrent(){
        return current;
    }

    /** @return 是否在存档写入中。 */
    public boolean isSaving(){
        return saving;
    }

    /** 本局不绑定槽（新战役开荒时调用）。 */
    public void resetCurrent(){
        current = null;
        timer = 0f;
        //兜底：延后保存任务（Time.run）可能被 Time.clear() 取消，避免 $saveload 指示器卡在"保存中"
        saving = false;
    }

    /** @return 当前槽位是否存在（供 UI 判断"保存"还是"另存为"）。 */
    public boolean hasCurrent(){
        return current != null;
    }

    /** 生成下一个不存在的槽位文件（saves/0.msav, 1.msav...）。 */
    private File nextSlotFile(){
        File dir = Gdx.files.local(SAVE_DIR).file();
        if(!dir.exists() && !dir.mkdirs()){
            throw new IllegalStateException("无法创建存档目录: " + dir);
        }
        int i = 0;
        File file;
        do{
            file = new File(dir, i + "." + EXT);
            i++;
        }while(file.exists());
        return file;
    }

    /** 一个存档槽位：对应一个 .msav 文件 + 缩略图 + 内存元数据。 */
    public static class SaveSlot{
        /** 槽位文件。 */
        public final File file;
        /** 显示名（新建时可指定；会写进存档 meta 的 tags 里持久化）。 */
        public String name;
        /** 元数据（读自存档 meta 区；失败时可能为 null）。 */
        public SaveMeta meta;

        SaveSlot(File file){
            this.file = file;
            this.name = file.getName();
        }

        /** @return 缩略图文件（可能不存在）。 */
        public File previewFile(){
            return SavePreview.previewFileFor(file);
        }

        /** @return 是否有缩略图。 */
        public boolean hasPreview(){
            return SavePreview.hasPreview(file);
        }

        /**
         * @return 本槽所属的战役区域（由 meta 里的 {@code rules.zoneName} 反查）；非区域槽返回 null。
         * <p>对应原版 {@code SaveSlot.getZone()}（原版直接读 {@code meta.rules.zone} 对象，本项目存名字）。
         * 区域在当前版本里被删掉时返回 null（读档时 {@code SaveIO.applyMeta} 也会 err 并退化成非区域局）。
         */
        public Zone getZone(){
            if(meta == null || meta.rules == null
                || meta.rules.zoneName == null || meta.rules.zoneName.isEmpty()){
                return null;
            }
            return Vars.content.getByName(com.phoenix.game.ctype.ContentType.zone, meta.rules.zoneName);
        }

        /**
         * @return 是否在存档列表里隐藏（区域槽隐藏，对应原版 {@code SaveSlot.isHidden()}）。
         * <p>区域槽是自动维护的"继续战役"进度，不该混进玩家的手动存档列表里。
         */
        public boolean isHidden(){
            return getZone() != null;
        }

        /** 从存档文件重新读元数据（只解压 meta 区，很便宜）。 */
        public void refreshMeta(){
            try{
                meta = SaveIO.readMeta(file);
                if(meta != null && meta.tags != null){
                    String stored = meta.tags.get(NAME_TAG);
                    if(stored != null && !stored.isEmpty()) name = stored;
                }
            }catch(Exception e){
                System.err.println("读取存档元数据失败 " + file.getName() + ": " + e);
            }
        }

        /** 写入当前局到本槽（含缩略图）。 */
        public void save() throws IOException{
            //显示名随存档一起持久化
            SaveIO.save(file, name == null ? null : java.util.Collections.singletonMap(NAME_TAG, name));
            refreshMeta();
            //缩略图：GL 未就绪（headless）时静默跳过
            SavePreview.savePreview(file);
        }

        /** 从本槽读档（替换世界与状态，含玩家重建）。 */
        public void load() throws IOException{
            SaveIO.load(file);
            Vars.state.set(com.phoenix.game.core.GameState.State.playing);
        }

        /** @return 当前波次（无元数据时为 0）。 */
        public int wave(){
            return meta == null ? 0 : meta.wave;
        }

        /** @return 本局累计游玩时长（秒）。 */
        public long playtime(){
            return meta == null ? 0 : meta.playtime;
        }

        /** @return 存档写入时间（毫秒时间戳）。 */
        public long dateMillis(){
            return meta == null ? 0L : meta.saved;
        }

        /** @return 存档时间的显示文本。 */
        public String dateText(){
            return dateMillis() <= 0L ? "未知" : new SimpleDateFormat("MM-dd HH:mm").format(new Date(dateMillis()));
        }

        /** @return 游玩时长的显示文本（hh:mm:ss）。 */
        public String playtimeText(){
            long seconds = playtime();
            return String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
        }

        /** @return 本槽索引（文件名去扩展名，如 {@code 0.msav} → {@code "0"}）；用于拼 per-slot 设置键。 */
        private String index(){
            String n = file.getName();
            int dot = n.lastIndexOf('.');
            return dot > 0 ? n.substring(0, dot) : n;
        }

        /**
         * @return 本槽是否启用自动存档。对应原版 {@code SaveSlot.isAutosave()}。
         * <p>⚠ per-slot 开关存在 {@code Core.settings} 的 {@code save-<index>-autosave} 键里（默认开），
         * <b>不是</b>存档 meta 字段 —— 所以它既不需要改 {@code SaveMeta} 白名单，也不会随存档文件迁移。
         * <p>消费点：{@code Saves.update()}（自动存档判据）、{@code SaveDialog} 的卡片开关、
         * {@code PausedDialog} 的退出文案与退出保存判据。
         */
        public boolean isAutosave(){
            return Core.settings.getBool("save-" + index() + "-autosave", true);
        }

        /** 设置本槽的自动存档开关并立即落盘（对应原版 {@code SaveSlot.setAutosave}）。 */
        public void setAutosave(boolean save){
            Core.settings.put("save-" + index() + "-autosave", save);
            Core.settings.save();
        }

        @Override
        public String toString(){
            return name + " (第" + wave() + "波 " + playtimeText() + " " + dateText() + ")";
        }
    }
}
