package com.phoenix.game.net;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * 服务端玩家管理。对应原版 Mindustry 的 mindustry.net.Administration（headless 版）。
 * <p>维护 uuid → PlayerInfo（名字/IP/管理员/封禁次数）、IP 封禁、子网封禁、白名单、人数上限，
 * 并持久化到 {@code admins} 目录（{@code players.dat} 玩家档案、{@code banned.txt} 封禁 IP、
 * {@code whitelist.txt} 白名单、{@code subnets.txt} 子网封禁）。服务端可手工编辑这些文本文件。
 * <p><b>F-LOW 批补齐</b>：子网封禁（subnetBans）与 {@link Config} 配置项子系统（strict/allowCustomClients
 * 等 19 项，走 {@code Core.settings}）——原版 Administration.java:63-79/411-489；消费点
 * {@link #getStrict()}/{@link #allowsCustomClients()}/{@link #isSubnetBanned} 已由
 * {@code core/NetServer.onConnect} 接线。
 * <p>聊天/动作过滤（antiSpam 的 ChatFilter/ActionFilter 机制）未实现，保留接口供后续。
 */
public class Administration {
    /** 数据目录（应用本地目录下）。 */
    private static final String DATA_DIR = "admins";
    /** 玩家档案文件。 */
    private static final String PLAYERS_FILE = "players.dat";
    /** 封禁 IP 文件（每行一个）。 */
    private static final String BANNED_FILE = "banned.txt";
    /** 白名单文件（每行一个 uuid）。 */
    private static final String WHITELIST_FILE = "whitelist.txt";
    /** 子网封禁文件（每行一个 IP 前缀，F-LOW 批照原版 subnetBans 补齐）。 */
    private static final String SUBNET_FILE = "subnets.txt";

    /** uuid → 玩家档案。 */
    public final ObjectMap<String, PlayerInfo> playerInfo = new ObjectMap<>();
    /** 封禁的 IP 列表。 */
    public final Array<String> bannedIPs = new Array<>();
    /** 白名单 uuid 列表。 */
    public final Array<String> whitelist = new Array<>();
    /** 封禁的 IP 前缀列表（对应原版 {@code Administration.subnetBans}，前缀匹配）。 */
    public final Array<String> subnetBans = new Array<>();
    /** 是否启用白名单。 */
    public boolean whitelistEnabled;
    /** 加入上限；<=0 表示不限制。 */
    public int playerLimit;

    /** 应用启动后调用：读入档案/封禁/白名单。 */
    public void load(){
        File dir = dataDir();
        File playersFile = new File(dir, PLAYERS_FILE);
        if(playersFile.exists()){
            try{
                byte[] data = Files.readAllBytes(playersFile.toPath());
                readPlayers(data);
            }catch(IOException e){
                System.err.println("读玩家档案失败: " + e);
            }
        }

        readLines(new File(dir, BANNED_FILE), bannedIPs);
        readLines(new File(dir, WHITELIST_FILE), whitelist);
        readLines(new File(dir, SUBNET_FILE), subnetBans);
    }

    /** 保存档案/封禁/白名单/子网封禁到磁盘。 */
    public void save(){
        try{
            File dir = dataDir();
            if(!dir.exists() && !dir.mkdirs()){
                System.err.println("无法创建管理数据目录: " + dir);
                return;
            }
            Files.write(new File(dir, PLAYERS_FILE).toPath(), writePlayers());
            writeLines(new File(dir, BANNED_FILE), bannedIPs);
            writeLines(new File(dir, WHITELIST_FILE), whitelist);
            writeLines(new File(dir, SUBNET_FILE), subnetBans);
        }catch(IOException e){
            System.err.println("保存管理数据失败: " + e);
        }
    }

    /** 获取或创建玩家档案（按 uuid）。 */
    public PlayerInfo getInfo(String uuid){
        PlayerInfo info = playerInfo.get(uuid);
        if(info == null){
            info = new PlayerInfo(uuid);
            playerInfo.put(uuid, info);
        }
        return info;
    }

    /** @return 该 IP 是否被封禁。 */
    public boolean isIPBanned(String ip){
        for(String banned : bannedIPs){
            if(banned.equals(ip)) return true;
        }
        return false;
    }

    public boolean isIDBanned(String uuid){
        return playerInfo.containsKey(uuid) && playerInfo.get(uuid).banned;
    }

    /** 按 uuid 封禁/解封。 */
    public void ban(String uuid, boolean ban){
        if(uuid == null) return;
        getInfo(uuid).banned = ban;
        save();
    }

    /** 封禁 IP（持久化）。 */
    public void banIP(String ip){
        if(!bannedIPs.contains(ip, true)){
            bannedIPs.add(ip);
            save();
        }
    }

    /** 解封 IP。 */
    public void unbanIP(String ip){
        bannedIPs.removeValue(ip, true);
        save();
    }

    // ---- 子网封禁（对应原版 Administration.java:63-79 的 subnetBans，F-LOW 批补齐）----

    /** @return 全部被封禁的 IP 前缀（对应原版 {@code getSubnetBans}）。 */
    public Array<String> getSubnetBans(){
        return subnetBans;
    }

    /** 解封一个 IP 前缀（对应原版 {@code removeSubnetBan}）。 */
    public void removeSubnetBan(String ip){
        subnetBans.removeValue(ip, true);
        save();
    }

    /** 封禁一个 IP 前缀（对应原版 {@code addSubnetBan}；前缀匹配，如 {@code 192.168.}）。 */
    public void addSubnetBan(String ip){
        subnetBans.add(ip);
        save();
    }

    /** @return 该 IP 是否命中任一封禁前缀（对应原版 {@code isSubnetBanned} 的 {@code subnetBans.contains(ip::startsWith)}）。 */
    public boolean isSubnetBanned(String ip){
        if(ip == null) return false;
        for(int i = 0; i < subnetBans.size; i++){
            if(ip.startsWith(subnetBans.get(i))) return true;
        }
        return false;
    }

    /** @return 严格模式是否开启（对应原版 {@code getStrict}：纠位 + 防重复 UUID，仅 headless 服务端消费）。 */
    public boolean getStrict(){
        return Config.strict.bool();
    }

    /** @return 是否允许非官方客户端连接（对应原版 {@code allowsCustomClients}）。 */
    public boolean allowsCustomClients(){
        return Config.allowCustomClients.bool();
    }

    public boolean isAdmin(String uuid){
        if(uuid == null) return false;
        PlayerInfo info = playerInfo.get(uuid);
        return info != null && info.admin;
    }

    /** 设置/撤销管理员（持久化）。 */
    public void setAdmin(String uuid, boolean admin){
        if(uuid == null) return;
        getInfo(uuid).admin = admin;
        save();
    }

    // ---- 白名单 ----

    public void setWhitelistEnabled(boolean enabled){
        whitelistEnabled = enabled;
        save();
    }

    public boolean isWhitelisted(String uuid){
        return !whitelistEnabled || whitelist.contains(uuid, true);
    }

    public void whitelist(String uuid){
        if(!whitelist.contains(uuid, true)){
            whitelist.add(uuid);
            save();
        }
    }

    public void unwhitelist(String uuid){
        whitelist.removeValue(uuid, true);
        save();
    }

    /** 玩家加入时更新档案。 */
    public void updatePlayerJoined(String uuid, String name, String ip){
        if(uuid == null) return;
        PlayerInfo info = getInfo(uuid);
        info.lastName = name;
        info.lastIP = ip;
        if(!info.ips.contains(ip, true)) info.ips.add(ip);
        if(!info.names.contains(name, true)) info.names.add(name);
        info.timesJoined++;
    }

    /** @return 服务器是否已满。 */
    public boolean isServerFull(int currentPlayers){
        return playerLimit > 0 && currentPlayers >= playerLimit;
    }

    /** 按名字搜索玩家（返回第一个匹配，无则 null）。 */
    public PlayerInfo findByName(String name){
        for(PlayerInfo info : playerInfo.values()){
            if(name.equals(info.lastName)) return info;
        }
        return null;
    }

    /** 全部管理员。 */
    public Array<PlayerInfo> getAdmins(){
        Array<PlayerInfo> out = new Array<>();
        for(PlayerInfo info : playerInfo.values()){
            if(info.admin) out.add(info);
        }
        return out;
    }

    /** 全部封禁玩家。 */
    public Array<PlayerInfo> getBanned(){
        Array<PlayerInfo> out = new Array<>();
        for(PlayerInfo info : playerInfo.values()){
            if(info.banned) out.add(info);
        }
        return out;
    }

    /**
     * 服务器配置项（对应原版 {@code Administration.Config}，原版 Administration.java:411-489）：
     * 每项可以是字符串/布尔/数字，持久化在 {@code Core.settings}（本项目 {@code phoenix_settings}）。
     * <p><b>与原版的差异</b>：socketInput 三项的 {@code changed} 回调原版会 fire {@code Trigger.socketConfigChanged}
     * （本地 TCP 控制台子系统未移植，Trigger 里也无该常量）→ 回调留空。
     * <p>当前消费点：{@link #getStrict()} / {@link #allowsCustomClients()}（本项目自研 NetServer 的
     * 严格模式/自定义客户端校验尚未接，见类注释）；其余项为"配置面先立好"，UI/命令行入口留 TODO。
     */
    public enum Config{
        name("The server name as displayed on clients.", "Server", "servername"),
        desc("The server description, displayed under the name. Max 100 characters.", "off"),
        port("The port to host on.", Vars.port),
        autoUpdate("Whether to auto-update and exit when a new bleeding-edge update arrives.", false),
        showConnectMessages("Whether to display connect/disconnect messages.", true),
        enableVotekick("Whether votekick is enabled.", true),
        startCommands("Commands run at startup. This should be a comma-separated list.", ""),
        crashReport("Whether to send crash reports.", false, "crashreport"),
        logging("Whether to log everything to files.", true),
        strict("Whether strict mode is on - corrects positions and prevents duplicate UUIDs.", true),
        antiSpam("Whether spammers are automatically kicked and rate-limited.", Vars.headless),
        messageRateLimit("Message rate limit in seconds. 0 to disable.", 0),
        messageSpamKick("How many times a player must send a message before the cooldown to get kicked. 0 to disable.", 3),
        socketInput("Allows a local application to control this server through a local TCP socket.", false, "socket"),
        socketInputPort("The port for socket input.", 6859),
        socketInputAddress("The bind address for socket input.", "localhost"),
        allowCustomClients("Whether custom clients are allowed to connect.", !Vars.headless, "allow-custom"),
        whitelist("Whether the whitelist is used.", false),
        motd("The message displayed to people on connection.", "off");

        public static final Config[] all = values();

        public final Object defaultValue;
        public final String key, description;

        Config(String description, Object def){
            this(description, def, null);
        }

        Config(String description, Object def, String key){
            this.description = description;
            this.key = key == null ? name() : key;
            this.defaultValue = def;
        }

        public boolean isNum(){
            return defaultValue instanceof Integer;
        }

        public boolean isBool(){
            return defaultValue instanceof Boolean;
        }

        public boolean isString(){
            return defaultValue instanceof String;
        }

        /** @return 当前值（对应原版 {@code Config.get}；本项目 Settings 没有泛型 get → 按默认值类型分发）。 */
        public Object get(){
            if(isBool()) return bool();
            if(isNum()) return num();
            return string();
        }

        public boolean bool(){
            return Core.settings.getBool(key, (Boolean)defaultValue);
        }

        public int num(){
            return Core.settings.getInt(key, (Integer)defaultValue);
        }

        public String string(){
            return Core.settings.getString(key, (String)defaultValue);
        }

        /** 写入并落盘（对应原版 {@code Config.set}；本项目 Settings 的 put 后必须手动 save）。 */
        public void set(Object value){
            if(value instanceof Boolean){
                Core.settings.put(key, (Boolean)value);
            }else if(value instanceof Integer){
                Core.settings.put(key, (Integer)value);
            }else{
                Core.settings.put(key, String.valueOf(value));
            }
            Core.settings.save();
        }
    }

    // ---- 持久化 ----
    private File dataDir(){
        return Gdx.files.local(DATA_DIR).file();
    }

    /** 玩家档案二进制：数量 + 每条(uuid,lastName,lastIP,admin,banned,timesJoined,timesKicked)。 */
    private byte[] writePlayers() throws IOException {
        java.io.ByteArrayOutputStream raw = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream out = new java.io.DataOutputStream(raw);
        out.writeInt(playerInfo.size);
        for(PlayerInfo info : playerInfo.values()){
            out.writeUTF(info.id == null ? "" : info.id);
            out.writeUTF(info.lastName == null ? "" : info.lastName);
            out.writeUTF(info.lastIP == null ? "" : info.lastIP);
            out.writeBoolean(info.admin);
            out.writeBoolean(info.banned);
            out.writeInt(info.timesJoined);
            out.writeInt(info.timesKicked);
        }
        out.flush();
        return raw.toByteArray();
    }

    private void readPlayers(byte[] data){
        try{
            java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(data));
            int n = in.readInt();
            for(int i = 0; i < n; i++){
                PlayerInfo info = new PlayerInfo(in.readUTF());
                info.lastName = in.readUTF();
                info.lastIP = in.readUTF();
                info.admin = in.readBoolean();
                info.banned = in.readBoolean();
                info.timesJoined = in.readInt();
                info.timesKicked = in.readInt();
                playerInfo.put(info.id, info);
            }
        }catch(IOException e){
            System.err.println("解析玩家档案失败: " + e);
        }
    }

    private void readLines(File file, Array<String> target){
        if(!file.exists()) return;
        try{
            for(String line : Files.readAllLines(file.toPath())){
                String t = line.trim();
                if(!t.isEmpty()) target.add(t);
            }
        }catch(IOException e){
            System.err.println("读文件失败 " + file + ": " + e);
        }
    }

    private void writeLines(File file, Array<String> lines) throws IOException {
        StringBuilder sb = new StringBuilder();
        for(String line : lines){
            sb.append(line).append('\n');
        }
        Files.write(file.toPath(), sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** 玩家档案。 */
    public static class PlayerInfo {
        public String id;
        public String lastName = "";
        public String lastIP = "";
        public final Array<String> ips = new Array<>();
        public final Array<String> names = new Array<>();
        public boolean banned, admin;
        public int timesJoined, timesKicked;

        public PlayerInfo(String id){
            this.id = id;
        }
    }

    /**
     * 玩家追踪信息（对应原版 {@code Administration.TraceInfo}）。
     * <p>管理员对某玩家执行 trace 时，服务端把该玩家的 IP / uuid / 是否改版客户端 / 是否移动端回传给发起者。
     */
    public static class TraceInfo {
        public String ip, uuid;
        public boolean modded, mobile;

        public TraceInfo(String ip, String uuid, boolean modded, boolean mobile){
            this.ip = ip;
            this.uuid = uuid;
            this.modded = modded;
            this.mobile = mobile;
        }
    }
}