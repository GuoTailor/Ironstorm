package com.phoenix.game.util;

import com.phoenix.game.func.Boolf;
import com.phoenix.game.func.Cons;
import com.phoenix.game.func.Floatf;
import com.phoenix.game.func.Func;
import com.phoenix.game.func.Intf;

import java.lang.reflect.Array;
import java.util.Comparator;
import java.util.Iterator;

/**
 * 数组/集合工具。参照 {@code arc.util.Structs} 移植（只取原版实际用到的部分）。
 * <p>libgdx 的 {@code Array} 没有 {@code find/each/random} 这些方法，原版源码又大量使用，
 * 所以统一在这里提供，避免去改 libgdx 的类。
 */
public class Structs{

    private Structs(){
    }

    @SafeVarargs
    public static <T> T[] arr(T... array){
        return array;
    }

    /** 就地移除不满足条件的元素（对应原版 filter(Iterable, Boolf)）。 */
    public static <T> void filter(Iterable<T> array, Boolf<T> predicate){
        Iterator<T> iterator = array.iterator();
        while(iterator.hasNext()){
            if(!predicate.get(iterator.next())) iterator.remove();
        }
    }

    public static <T> void filter(Iterator<T> iterator, Boolf<T> predicate){
        while(iterator.hasNext()){
            if(!predicate.get(iterator.next())) iterator.remove();
        }
    }

    /** @return 过滤后的新数组（元素类型由 type 决定，对应原版 filter(Class, T[], Boolf)）。 */
    @SuppressWarnings("unchecked")
    public static <T> T[] filter(Class<T> type, T[] array, Boolf<T> predicate){
        int count = 0;
        for(T value : array){
            if(predicate.get(value)) count++;
        }
        T[] result = (T[])Array.newInstance(type, count);
        int index = 0;
        for(T value : array){
            if(predicate.get(value)) result[index++] = value;
        }
        return result;
    }

    public static <T> T random(T[] array){
        if(array.length == 0) return null;
        return array[(int)(Math.random() * array.length)];
    }

    /** @return 从给定元素里随机挑一个（对应原版 select(T...)）。 */
    @SafeVarargs
    public static <T> T select(T... array){
        if(array.length == 0) return null;
        return array[(int)(Math.random() * array.length)];
    }

    public static <T> boolean contains(T[] array, T value){
        for(T other : array){
            if(other == value || (other != null && other.equals(value))) return true;
        }
        return false;
    }

    public static <T> boolean contains(T[] array, Boolf<T> predicate){
        return find(array, predicate) != null;
    }

    public static <T> T find(T[] array, Boolf<T> predicate){
        for(T value : array){
            if(predicate.get(value)) return value;
        }
        return null;
    }

    public static <T> int indexOf(T[] array, T value){
        for(int i = 0; i < array.length; i++){
            if(array[i] == value || (array[i] != null && array[i].equals(value))) return i;
        }
        return -1;
    }

    public static <T> int indexOf(T[] array, Boolf<T> predicate){
        for(int i = 0; i < array.length; i++){
            if(predicate.get(array[i])) return i;
        }
        return -1;
    }

    /** 依次比较两个比较器（对应原版 comps）。 */
    public static <T> Comparator<T> comps(Comparator<T> first, Comparator<T> second){
        return (a, b) -> {
            int result = first.compare(a, b);
            return result != 0 ? result : second.compare(a, b);
        };
    }

    public static <T, U> Comparator<T> comparing(Func<? super T, ? extends U> keyExtractor, Comparator<? super U> comparator){
        return (a, b) -> comparator.compare(keyExtractor.get(a), keyExtractor.get(b));
    }

    @SuppressWarnings("unchecked")
    public static <T, U extends Comparable<? super U>> Comparator<T> comparing(Func<? super T, ? extends U> keyExtractor){
        return (a, b) -> ((Comparable<U>)keyExtractor.get(a)).compareTo(keyExtractor.get(b));
    }

    public static <T> Comparator<T> comparingFloat(Floatf<? super T> keyExtractor){
        return (a, b) -> Float.compare(keyExtractor.get(a), keyExtractor.get(b));
    }

    public static <T> Comparator<T> comparingInt(Intf<? super T> keyExtractor){
        return (a, b) -> Integer.compare(keyExtractor.get(a), keyExtractor.get(b));
    }

    @SafeVarargs
    public static <T> void each(Cons<T> consumer, T... array){
        for(T value : array){
            consumer.get(value);
        }
    }

    public static <T> void forEach(Iterable<T> array, Cons<T> consumer){
        for(T value : array){
            consumer.get(value);
        }
    }

    public static <T> T findMin(T[] array, Comparator<T> comparator){
        if(array.length == 0) return null;
        T result = array[0];
        for(int i = 1; i < array.length; i++){
            if(comparator.compare(array[i], result) < 0) result = array[i];
        }
        return result;
    }

    public static <T> T findMin(T[] array, Floatf<T> value){
        if(array.length == 0) return null;
        T result = array[0];
        for(int i = 1; i < array.length; i++){
            if(value.get(array[i]) < value.get(result)) result = array[i];
        }
        return result;
    }

    public static <T> T findMin(Iterable<T> array, Comparator<T> comparator){
        T result = null;
        for(T value : array){
            if(result == null || comparator.compare(value, result) < 0) result = value;
        }
        return result;
    }

    /** 只在满足 filter 的元素里找最小值（对应原版 findMin(Iterable, Boolf, Comparator)）。 */
    public static <T> T findMin(Iterable<T> array, Boolf<T> filter, Comparator<T> comparator){
        T result = null;
        for(T value : array){
            if(!filter.get(value)) continue;
            if(result == null || comparator.compare(value, result) < 0) result = value;
        }
        return result;
    }

    public static <T> boolean inBounds(int x, int y, T[][] array){
        return x >= 0 && y >= 0 && x < array.length && y < array[x].length;
    }

    public static boolean inBounds(int x, int y, int[][] array){
        return x >= 0 && y >= 0 && x < array.length && y < array[x].length;
    }

    public static boolean inBounds(int x, int y, float[][] array){
        return x >= 0 && y >= 0 && x < array.length && y < array[x].length;
    }

    public static boolean inBounds(int x, int y, boolean[][] array){
        return x >= 0 && y >= 0 && x < array.length && y < array[x].length;
    }

    public static <T> boolean inBounds(int x, int y, int z, T[][][] array){
        return x >= 0 && y >= 0 && z >= 0 && x < array.length && y < array[x].length && z < array[x][y].length;
    }

    public static <T> boolean inBounds(int x, int y, int z, int[][][] array){
        return x >= 0 && y >= 0 && z >= 0 && x < array.length && y < array[x].length && z < array[x][y].length;
    }
}
