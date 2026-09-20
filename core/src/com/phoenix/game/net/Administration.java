package com.phoenix.game.net;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * 服务端玩家管理。对应原版 Mindustry 的 mindustry.net.Administration（headless 版）。
 * <p>维护 uuid → PlayerInfo（名字/IP/管理员/封禁次数）、IP 封禁、白名单、人数上限，
 * 并持久化到 {@code admins} 目录（{@code players.dat} 玩家档案、{@code banned.txt} 封禁 IP、
 * {@code whitelist.txt} 白名单）。服务端可手工编辑这些文本文件。
 * <p>聊天/动作过滤（antiSpam）未实现，保留接口供后续。
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

    /** uuid → 玩家档案。 */
    public final ObjectMap<String, PlayerInfo> playerInfo = new ObjectMap<>();
    /** 封禁的 IP 列表。 */
    public final Array<String> bannedIPs = new Array<>();
    /** 白名单 uuid 列表。 */
    public final Array<String> whitelist = new Array<>();
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
    }

    /** 保存档案/封禁/白名单到磁盘。 */
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
}