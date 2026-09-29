package com.phoenix.game.util;

import com.phoenix.game.core.Strings;

/**
 * 日志门面。参照 {@code arc.util.Log} 移植（只取原版实际用到的部分）。
 * <p>arc 的 Log 带颜色码与可替换的 LogHandler；本项目没有控制台着色设施，
 * 默认实现直接写 stdout/stderr（debug/info → out，warn/err → err），并剥掉 arc 颜色码。
 */
public class Log{

    /** 日志级别，对应原版 {@code arc.util.Log.LogLevel}（声明顺序必须一致）。 */
    public enum LogLevel{
        debug, info, warn, err, none
    }

    /** 日志输出后端，对应原版 {@code arc.util.Log.LogHandler}。 */
    public interface LogHandler{
        void log(LogLevel level, String text);
    }

    /** 默认后端：写标准输出/错误流。 */
    public static class DefaultLogHandler implements LogHandler{
        @Override
        public void log(LogLevel level, String text){
            if(level == LogLevel.warn || level == LogLevel.err){
                System.err.println(text);
            }else{
                System.out.println(text);
            }
        }
    }

    private static LogHandler logger = new DefaultLogHandler();
    private static LogLevel level = LogLevel.info;

    public static void setLogger(LogHandler newLogger){
        logger = newLogger;
    }

    public static LogHandler getLogger(){
        return logger;
    }

    public static void setLogLevel(LogLevel newLevel){
        level = newLevel;
    }

    /** 兼容原版签名：本项目没有控制台着色，忽略该开关。 */
    public static void setUseColors(boolean useColors){
    }

    public static void log(LogLevel logLevel, String text, Object... args){
        if(logLevel.ordinal() < level.ordinal() || level == LogLevel.none) return;
        logger.log(logLevel, format(text, args));
    }

    public static void debug(String text, Object... args){
        log(LogLevel.debug, text, args);
    }

    public static void info(String text, Object... args){
        log(LogLevel.info, text, args);
    }

    public static void info(Object object){
        log(LogLevel.info, String.valueOf(object));
    }

    /** 逐项打印一组对象（对应原版 infoList）。 */
    public static void infoList(Object... values){
        for(Object value : values){
            log(LogLevel.info, String.valueOf(value));
        }
    }

    /** 带标签的单行信息（对应原版 infoTag）。 */
    public static void infoTag(String tag, String text){
        log(LogLevel.info, "[" + tag + "] " + text);
    }

    public static void warn(String text, Object... args){
        log(LogLevel.warn, text, args);
    }

    /** 带标签的单行警告（对应原版 errTag）。 */
    public static void errTag(String tag, String text){
        log(LogLevel.err, "[" + tag + "] " + text);
    }

    public static void err(String text, Object... args){
        log(LogLevel.err, text, args);
    }

    public static void err(Throwable th){
        log(LogLevel.err, throwableToString(th));
    }

    public static void err(String text, Throwable th){
        log(LogLevel.err, text + "\n" + throwableToString(th));
    }

    /** 把 {@code {0}}、{@code {1}}… 替换成 args（与 {@link Strings#format} 同一套规则）。 */
    public static String format(String text, Object... args){
        if(text == null) return "null";
        if(args == null || args.length == 0) return text;
        return Strings.format(text, args);
    }

    /** 剥掉 arc 的颜色码（形如 {@code &y}/{@code &lb} 与 {@code [red]}）。 */
    public static String removeCodes(String text){
        if(text == null) return "";
        return text.replaceAll("&[a-zA-Z]{1,2}", "").replaceAll("\\[[a-zA-Z_]+\\]", "");
    }

    private static String throwableToString(Throwable th){
        java.io.StringWriter writer = new java.io.StringWriter();
        th.printStackTrace(new java.io.PrintWriter(writer));
        return writer.toString();
    }
}
