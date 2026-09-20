package com.phoenix.game.ai;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 线程安全的任务队列。参照 arc.util.async.TaskQueue 移植（去并发集合，改 lightweight 实现）。
 * <p>后台线程轮询 {@link #run()} 消费回调；主线程 {@link #post(Runnable)} 投递。
 */
final class TaskQueue{
    private final Deque<Runnable> queue = new ArrayDeque<>();

    TaskQueue(){
    }

    /** 投递一个任务，供后台线程消费。调用方随时可调用。 */
    synchronized void post(Runnable runnable){
        queue.addLast(runnable);
    }

    /** 消费并执行当前已投递的所有任务。 */
    synchronized void run(){
        while(!queue.isEmpty()){
            Runnable task = queue.pollFirst();
            if(task != null) task.run();
        }
    }

    /** 清空所有待处理任务。 */
    synchronized void clear(){
        queue.clear();
    }
}