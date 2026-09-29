package com.phoenix.game.struct;

import com.phoenix.game.func.Boolf;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * 通用环形队列。参照 {@code arc.struct.Queue} 移植。
 * <p>索引语义与 arc 一致：{@code addFirst} 从头部插入、{@code removeLast} 从尾部取出，{@code size} 为公开字段。
 * <p>（与同包的 {@link IntQueue} 是同一套实现，只是元素类型泛型化、避免装箱。）
 */
public class Queue<T> implements Iterable<T>{

    public int size = 0;
    protected T[] values;
    protected int head = 0;
    protected int tail = 0;

    @SuppressWarnings("unchecked")
    public Queue(){
        this(16, null);
    }

    @SuppressWarnings("unchecked")
    public Queue(int initialSize){
        this(initialSize, null);
    }

    @SuppressWarnings("unchecked")
    public Queue(int initialSize, Class<T> type){
        values = (T[])new Object[Math.max(initialSize, 1)];
    }

    public void addLast(T value){
        if(size == values.length) resize(Math.max(8, (int)(size * 1.75f)));
        values[tail++] = value;
        if(tail == values.length) tail = 0;
        size++;
    }

    public void addFirst(T value){
        if(size == values.length) resize(Math.max(8, (int)(size * 1.75f)));
        if(--head < 0) head = values.length - 1;
        values[head] = value;
        size++;
    }

    public T removeFirst(){
        if(size == 0) throw new NoSuchElementException("Queue is empty.");
        T value = values[head];
        values[head] = null;
        if(++head == values.length) head = 0;
        size--;
        return value;
    }

    public T removeLast(){
        if(size == 0) throw new NoSuchElementException("Queue is empty.");
        if(--tail < 0) tail = values.length - 1;
        T value = values[tail];
        values[tail] = null;
        size--;
        return value;
    }

    public int indexOf(T value, boolean identity){
        for(int i = 0; i < size; i++){
            T other = get(i);
            if(identity ? other == value : other != null && other.equals(value)) return i;
        }
        return -1;
    }

    public int indexOf(Boolf<T> predicate){
        for(int i = 0; i < size; i++){
            if(predicate.get(get(i))) return i;
        }
        return -1;
    }

    public boolean remove(T value){
        return remove(value, false);
    }

    public boolean remove(T value, boolean identity){
        int index = indexOf(value, identity);
        if(index < 0) return false;
        removeIndex(index);
        return true;
    }

    public T removeIndex(int index){
        if(index < 0 || index >= size) throw new IndexOutOfBoundsException(String.valueOf(index));
        T value = get(index);
        // 逻辑下标整体前移一位（环形缓冲下用 get/set 逐位搬，size 通常很小）
        for(int i = index; i < size - 1; i++){
            set(i, get(i + 1));
        }
        int lastIndex = head + size - 1;
        if(lastIndex >= values.length) lastIndex -= values.length;
        values[lastIndex] = null;
        if(--tail < 0) tail = values.length - 1;
        size--;
        return value;
    }

    private void set(int index, T value){
        int i = head + index;
        if(i >= values.length) i -= values.length;
        values[i] = value;
    }

    public boolean isEmpty(){
        return size == 0;
    }

    public T first(){
        if(size == 0) throw new NoSuchElementException("Queue is empty.");
        return values[head];
    }

    public T last(){
        if(size == 0) throw new NoSuchElementException("Queue is empty.");
        int index = tail - 1;
        if(index < 0) index = values.length - 1;
        return values[index];
    }

    public T get(int index){
        if(index < 0 || index >= size) throw new IndexOutOfBoundsException(String.valueOf(index));
        int i = head + index;
        if(i >= values.length) i -= values.length;
        return values[i];
    }

    public void clear(){
        for(int i = 0; i < size; i++){
            int index = head + i;
            if(index >= values.length) index -= values.length;
            values[index] = null;
        }
        head = tail = size = 0;
    }

    /** 确保还能再放入 additionalCapacity 个元素。 */
    public void ensureCapacity(int additionalCapacity){
        int needed = size + additionalCapacity;
        if(values.length < needed) resize(needed);
    }

    protected void resize(int newSize){
        T[] newValues = newArray(newSize);

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

    @SuppressWarnings("unchecked")
    private T[] newArray(int length){
        return (T[])new Object[length];
    }

    @Override
    public Iterator<T> iterator(){
        return new Iterator<T>(){
            private int index = 0;

            @Override
            public boolean hasNext(){
                return index < size;
            }

            @Override
            public T next(){
                if(index >= size) throw new NoSuchElementException();
                return get(index++);
            }

            @Override
            public void remove(){
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public String toString(){
        return "Queue[size=" + size + "]";
    }
}
