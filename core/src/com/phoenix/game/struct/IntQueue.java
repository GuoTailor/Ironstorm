package com.phoenix.game.struct;

import java.util.NoSuchElementException;

/**
 * 最小实现：整型环形队列。参照 arc.struct.IntQueue 移植（libgdx 没有对应类型，Queue 会装箱）。
 * 索引语义与 arc 保持一致：addFirst 从头部插入、removeLast 从尾部取出，size 为公开字段。
 */
public class IntQueue{
    public int size = 0;
    protected int[] values;
    protected int head = 0;
    protected int tail = 0;

    public IntQueue(){
        this(16);
    }

    public IntQueue(int initialSize){
        values = new int[Math.max(initialSize, 1)];
    }

    public void addLast(int value){
        if(size == values.length) resize(Math.max(8, (int)(size * 1.75f)));
        values[tail++] = value;
        if(tail == values.length) tail = 0;
        size++;
    }

    public void addFirst(int value){
        if(size == values.length) resize(Math.max(8, (int)(size * 1.75f)));
        if(--head < 0) head = values.length - 1;
        values[head] = value;
        size++;
    }

    /** 同 {@link #addLast(int)}。 */
    public void add(int value){
        addLast(value);
    }

    public int removeFirst(){
        if(size == 0) throw new NoSuchElementException("Queue is empty.");
        int value = values[head++];
        if(head == values.length) head = 0;
        size--;
        return value;
    }

    public int removeLast(){
        if(size == 0) throw new NoSuchElementException("Queue is empty.");
        if(--tail < 0) tail = values.length - 1;
        int value = values[tail];
        size--;
        return value;
    }

    public int first(){
        if(size == 0) throw new NoSuchElementException("Queue is empty.");
        return values[head];
    }

    public int last(){
        if(size == 0) throw new NoSuchElementException("Queue is empty.");
        int index = tail - 1;
        if(index < 0) index = values.length - 1;
        return values[index];
    }

    public boolean isEmpty(){
        return size == 0;
    }

    public void clear(){
        head = tail = size = 0;
    }

    /** 确保还能再放入 additionalCapacity 个元素。 */
    public void ensureCapacity(int additionalCapacity){
        int needed = size + additionalCapacity;
        if(values.length < needed) resize(needed);
    }

    protected void resize(int newSize){
        int[] newValues = new int[newSize];

        if(tail < head){
            System.arraycopy(values, head, newValues, 0, values.length - head);
            System.arraycopy(values, 0, newValues, values.length - head, tail);
        }else{
            System.arraycopy(values, head, newValues, 0, size);
        }

        head = 0;
        tail = size;
        values = newValues;
    }

    @Override
    public String toString(){
        return "IntQueue[size=" + size + "]";
    }
}
