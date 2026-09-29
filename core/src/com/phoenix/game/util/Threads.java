package com.phoenix.game.util;

/**
 * 线程工具。参照 {@code arc.util.async.Threads} 移植。
 * <p>原版用它起后台 daemon 线程（mod 扫描、存档预览加载等）；本项目的寻路线程也是同一模式。
 */
public class Threads{

    private Threads(){
    }

    public static void yield(){
        Thread.yield();
    }

    public static void sleep(long millis){
        try{
            Thread.sleep(millis);
        }catch(InterruptedException e){
            Thread.currentThread().interrupt();
        }
    }

    public static void sleep(long millis, int nanos){
        try{
            Thread.sleep(millis, nanos);
        }catch(InterruptedException e){
            Thread.currentThread().interrupt();
        }
    }

    /** 把异常打印到标准错误流并终止进程（对应原版 throwAppException）。 */
    public static void throwAppException(Throwable th){
        th.printStackTrace();
        System.exit(1);
    }

    public static Thread thread(Runnable runnable){
        Thread thread = new Thread(runnable);
        thread.start();
        return thread;
    }

    public static Thread daemon(Runnable runnable){
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    public static Thread daemon(String name, Runnable runnable){
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}
