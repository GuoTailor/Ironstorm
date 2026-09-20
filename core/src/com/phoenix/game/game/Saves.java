package com.phoenix.game.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Time;
import com.phoenix.game.io.SaveIO;
import com.phoenix.game.io.SaveMeta;
import com.phoenix.game.io.SavePreview;

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
 * </ul>
 * 未移植：导入导出、异步存档。
 */
public class Saves{
    /** 自动存档间隔（帧）。对应原版设置 saveinterval（默认 60s）。 */
    public static final int autosaveInterval = 60 * 60;
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
        if(current == null || Vars.state.isMenu() || Vars.state.gameOver || !autosave){
            timer = 0f;
            return;
        }

        timer += Time.delta();
        if(timer >= autosaveInterval){
            timer = 0f;
            try{
                saveCurrent();
            }catch(Exception e){
                System.err.println("自动存档失败: " + e);
            }
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

        @Override
        public String toString(){
            return name + " (第" + wave() + "波 " + playtimeText() + " " + dateText() + ")";
        }
    }
}
