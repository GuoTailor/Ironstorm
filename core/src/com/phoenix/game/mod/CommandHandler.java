package com.phoenix.game.mod;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;

import java.util.function.Consumer;

/**
 * 命令处理器。对应原版 arc 的 CommandHandler（精简版）。
 * <p>用前缀（如空串=控制台、"/"=游戏内聊天命令）注册命令；每个命令有名字、参数说明与执行回调。
 * phoenix 服务端控制台与 Mod/Plugin 共用此抽象。
 */
public class CommandHandler {
    /** 命令前缀（控制台为空串，游戏内为 "/"）。 */
    public final String prefix;
    private final ObjectMap<String, Command> commands = new ObjectMap<>();

    public CommandHandler(String prefix){
        this.prefix = prefix;
    }

    /** 注册一条命令。 */
    public void register(String name, String params, String description, CommandRunner runner){
        commands.put(name, new Command(name, params, description, runner));
    }

    /** 按名字取命令。 */
    public Command getCommand(String name){
        return commands.get(name);
    }

    /** 全部命令。 */
    public Array<Command> getCommands(){
        Array<Command> result = new Array<>();
        for(ObjectMap.Entry<String, Command> entry : commands.entries()){
            result.add(entry.value);
        }
        return result;
    }

    /** 执行一条命令行。返回 false 表示命令不存在。 */
    public boolean handleMessage(String message, Consumer<String> responder){
        String trimmed = message;
        if(prefix != null && !prefix.isEmpty()){
            if(!trimmed.startsWith(prefix)) return false;
            trimmed = trimmed.substring(prefix.length());
        }
        String[] parts = trimmed.trim().split("\\s+");
        if(parts.length == 0) return false;
        Command cmd = commands.get(parts[0]);
        if(cmd == null) return false;

        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        try{
            cmd.runner.run(args, responder);
        }catch(Throwable t){
            responder.accept("执行命令出错: " + t.getMessage());
        }
        return true;
    }

    /** 一条命令。 */
    public static class Command {
        public final String name, params, description;
        public final CommandRunner runner;

        Command(String name, String params, String description, CommandRunner runner){
            this.name = name;
            this.params = params;
            this.description = description;
            this.runner = runner;
        }

        /** @return 命令使用说明（含参数）。 */
        public String text(){
            return (params == null || params.isEmpty()) ? name : name + " " + params;
        }
    }

    /** 命令执行器。args 为去掉命令名的参数，responder 用于回显输出。 */
    @FunctionalInterface
    public interface CommandRunner {
        void run(String[] args, Consumer<String> responder);
    }
}