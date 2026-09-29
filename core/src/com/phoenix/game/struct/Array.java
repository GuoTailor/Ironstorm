package com.phoenix.game.struct;

import com.badlogic.gdx.utils.ObjectSet;
import com.phoenix.game.func.Boolf;
import com.phoenix.game.func.Cons;
import com.phoenix.game.func.Floatf;
import com.phoenix.game.func.Func;
import com.phoenix.game.func.Func2;
import com.phoenix.game.func.Intf;
import com.phoenix.game.util.Eachable;

import java.util.ArrayList;
import java.util.Comparator;

/**
 * 动态数组。参照 {@code arc.struct.Array} 移植。
 * <p><b>为什么继承 libgdx 的 Array 而不是重写</b>：libgdx 的 {@code Array} 已经覆盖了 arc 的绝大部分 API
 * （add/addAll/get/set/insert/remove/contains/indexOf/sort/reverse/shuffle/pop/peek/first/random/truncate…），
 * 只有 arc 独有的函数式方法（{@code each}/{@code find}/{@code map}/{@code select}/{@code remove(Boolf)}…）缺失。
 * 继承后原版代码的 {@code arr.find(...)}、{@code arr.each(...)} 可以**零改动**直接用，
 * 不必去改 82 个调用点。
 * <p>注意：本类只加方法、不改 libgdx 的既有语义；{@code size}/{@code items}/{@code ordered} 沿用父类的公开字段。
 */
public class Array<T> extends com.badlogic.gdx.utils.Array<T> implements Eachable<T>{

    public Array(){
        super();
    }

    public Array(int capacity){
        super(capacity);
    }

    public Array(boolean ordered, int capacity){
        super(ordered, capacity);
    }

    public Array(boolean ordered, int capacity, Class<?> arrayType){
        super(ordered, capacity, arrayType);
    }

    public Array(Class<?> arrayType){
        super(arrayType);
    }

    public Array(com.badlogic.gdx.utils.Array<? extends T> array){
        super(array);
    }

    public Array(T[] array){
        super(array);
    }

    public Array(boolean ordered, T[] array, int start, int count){
        super(ordered, array, start, count);
    }

    // ---- arc 独有的静态工厂（用协变返回类型隐藏父类同名静态方法）----

    /** @return 由给定元素构造的数组（对应 arc {@code Array.with}）。 */
    @SafeVarargs
    public static <T> Array<T> with(T... array){
        return new Array<>(array);
    }

    public static <T> Array<T> with(Iterable<T> array){
        Array<T> result = new Array<>();
        for(T value : array){
            result.add(value);
        }
        return result;
    }

    /** 把一组数组/集合拍平成一个数组（对应 arc {@code Array.withArrays}）。 */
    public static <T> Array<T> withArrays(Object... arrays){
        Array<T> result = new Array<>();
        for(Object object : arrays){
            if(object instanceof Iterable){
                for(Object value : (Iterable<?>)object){
                    result.add((T)value);
                }
            }else if(object != null && object.getClass().isArray()){
                int length = java.lang.reflect.Array.getLength(object);
                for(int i = 0; i < length; i++){
                    result.add((T)java.lang.reflect.Array.get(object, i));
                }
            }
        }
        return result;
    }

    /** @return 数组里所有满足条件的元素（对应 arc {@code Array.select(T[], Boolf)}）。 */
    public static <T> Array<T> select(T[] array, Boolf<T> predicate){
        Array<T> result = new Array<>();
        for(T value : array){
            if(predicate.get(value)) result.add(value);
        }
        return result;
    }

    // ---- arc 独有的实例方法 ----

    @Override
    public void each(Cons<? super T> consumer){
        for(int i = 0; i < size; i++){
            consumer.get(items[i]);
        }
    }

    /** 只对满足 filter 的元素执行 consumer（对应 arc {@code each(Boolf, Cons)}）。 */
    @SuppressWarnings("unchecked")
    public <E extends T> void each(Boolf<? super T> filter, Cons<E> consumer){
        for(int i = 0; i < size; i++){
            T item = items[i];
            if(filter.get(item)) consumer.get((E)item);
        }
    }

    /** 就地移除不满足条件的元素（对应 arc {@code eachFilter}）。 */
    public void eachFilter(Boolf<? super T> predicate){
        for(int i = 0; i < size; i++){
            if(!predicate.get(items[i])){
                removeIndex(i);
                i--;
            }
        }
    }

    /** 就地替换每个元素（对应 arc {@code replace}）。 */
    public void replace(Func<T, T> function){
        for(int i = 0; i < size; i++){
            items[i] = function.get(items[i]);
        }
    }

    /** 把每个元素（本身是可迭代对象）拍平（对应 arc {@code flatten}）。 */
    @SuppressWarnings("unchecked")
    public <R> Array<R> flatten(){
        Array<R> result = new Array<>();
        for(int i = 0; i < size; i++){
            Object item = items[i];
            if(item instanceof Iterable){
                for(Object value : (Iterable<?>)item){
                    result.add((R)value);
                }
            }else if(item != null && item.getClass().isArray()){
                int length = java.lang.reflect.Array.getLength(item);
                for(int j = 0; j < length; j++){
                    result.add((R)java.lang.reflect.Array.get(item, j));
                }
            }
        }
        return result;
    }

    /** 映射后再拍平（对应 arc {@code flatMap}）。 */
    public <R> Array<R> flatMap(Func<T, Iterable<R>> mapper){
        Array<R> result = new Array<>();
        for(int i = 0; i < size; i++){
            Iterable<R> mapped = mapper.get(items[i]);
            if(mapped != null){
                for(R value : mapped){
                    result.add(value);
                }
            }
        }
        return result;
    }

    public <R> Array<R> map(Func<T, R> mapper){
        Array<R> result = new Array<>(size);
        for(int i = 0; i < size; i++){
            result.add(mapper.get(items[i]));
        }
        return result;
    }

    /** @return 每个元素映射成 int 后的结果（对应 arc {@code mapInt}）。 */
    public com.badlogic.gdx.utils.IntArray mapInt(Intf<T> mapper){
        com.badlogic.gdx.utils.IntArray result = new com.badlogic.gdx.utils.IntArray(size);
        for(int i = 0; i < size; i++){
            result.add(mapper.get(items[i]));
        }
        return result;
    }

    /** 从左到右归约（对应 arc {@code reduce}）。 */
    public <R> R reduce(R initial, Func2<T, R, R> reducer){
        R result = initial;
        for(int i = 0; i < size; i++){
            result = reducer.get(items[i], result);
        }
        return result;
    }

    public boolean contains(Boolf<T> predicate){
        return find(predicate) != null;
    }

    /** @return 第一个满足条件的元素，没有则 null（对应 arc {@code find}）。 */
    public T find(Boolf<T> predicate){
        for(int i = 0; i < size; i++){
            if(predicate.get(items[i])) return items[i];
        }
        return null;
    }

    public int count(Boolf<T> predicate){
        int count = 0;
        for(int i = 0; i < size; i++){
            if(predicate.get(items[i])) count++;
        }
        return count;
    }

    public T min(Comparator<T> comparator){
        if(size == 0) return null;
        T result = items[0];
        for(int i = 1; i < size; i++){
            if(comparator.compare(items[i], result) < 0) result = items[i];
        }
        return result;
    }

    public T max(Comparator<T> comparator){
        if(size == 0) return null;
        T result = items[0];
        for(int i = 1; i < size; i++){
            if(comparator.compare(items[i], result) > 0) result = items[i];
        }
        return result;
    }

    public T min(Floatf<T> value){
        if(size == 0) return null;
        T result = items[0];
        for(int i = 1; i < size; i++){
            if(value.get(items[i]) < value.get(result)) result = items[i];
        }
        return result;
    }

    public T max(Floatf<T> value){
        if(size == 0) return null;
        T result = items[0];
        for(int i = 1; i < size; i++){
            if(value.get(items[i]) > value.get(result)) result = items[i];
        }
        return result;
    }

    public float sumf(Floatf<T> value){
        float sum = 0f;
        for(int i = 0; i < size; i++){
            sum += value.get(items[i]);
        }
        return sum;
    }

    public int sum(Intf<T> value){
        int sum = 0;
        for(int i = 0; i < size; i++){
            sum += value.get(items[i]);
        }
        return sum;
    }

    /** 把本数组交给 consumer 处理后再返回自身（对应 arc {@code with(Cons)}，用于链式构建）。 */
    public Array<T> with(Cons<Array<T>> consumer){
        consumer.get(this);
        return this;
    }

    /** 追加一个元素并返回自身（对应 arc {@code and}）。 */
    public Array<T> and(T value){
        add(value);
        return this;
    }

    /** 移除第一个满足条件的元素（对应 arc {@code remove(Boolf)}）。 */
    public boolean remove(Boolf<T> predicate){
        for(int i = 0; i < size; i++){
            if(predicate.get(items[i])){
                removeIndex(i);
                return true;
            }
        }
        return false;
    }

    /** 移除全部满足条件的元素（对应 arc {@code removeAll(Boolf)}）。 */
    public Array<T> removeAll(Boolf<T> predicate){
        for(int i = 0; i < size; i++){
            if(predicate.get(items[i])){
                removeIndex(i);
                i--;
            }
        }
        return this;
    }

    /** @return 满足条件的元素组成的新数组（对应 arc {@code select(Boolf)}）。 */
    public Array<T> select(Boolf<T> predicate){
        Array<T> result = new Array<>();
        for(int i = 0; i < size; i++){
            if(predicate.get(items[i])) result.add(items[i]);
        }
        return result;
    }

    /** 把满足条件的元素收进 out（对应 arc {@code selectFrom}）。 */
    public Array<T> selectFrom(Array<T> out, Boolf<T> predicate){
        for(int i = 0; i < size; i++){
            if(predicate.get(items[i])) out.add(items[i]);
        }
        return out;
    }

    /** 去重（按 equals，对应 arc {@code distinct}）。 */
    public Array<T> distinct(){
        ObjectSet<T> seen = new ObjectSet<>();
        Array<T> result = new Array<>();
        for(int i = 0; i < size; i++){
            if(seen.add(items[i])) result.add(items[i]);
        }
        return result;
    }

    /** 按浮点键排序并返回自身（对应 arc {@code sort(Floatf)}）。 */
    public Array<T> sort(Floatf<? super T> value){
        sort((a, b) -> Float.compare(value.get(a), value.get(b)));
        return this;
    }

    /** 按可比较键排序并返回自身（对应 arc {@code sortComparing}）。 */
    @SuppressWarnings("unchecked")
    public <U extends Comparable<? super U>> Array<T> sortComparing(Func<? super T, ? extends U> keyExtractor){
        sort((a, b) -> ((Comparable<U>)keyExtractor.get(a)).compareTo(keyExtractor.get(b)));
        return this;
    }

    /** 随机取一个元素，数组为空时返回 fallback（对应 arc {@code random(T)}）。 */
    public T random(T fallback){
        return size == 0 ? fallback : items[(int)(Math.random() * size)];
    }

    public Array<T> copy(){
        return new Array<>(this);
    }

    public ArrayList<T> list(){
        ArrayList<T> result = new ArrayList<>(size);
        for(int i = 0; i < size; i++){
            result.add(items[i]);
        }
        return result;
    }

    public ObjectSet<T> asSet(){
        ObjectSet<T> result = new ObjectSet<>(size);
        for(int i = 0; i < size; i++){
            result.add(items[i]);
        }
        return result;
    }

    /** 泛型转换（对应 arc {@code as(Class)}）。 */
    @SuppressWarnings("unchecked")
    public <R> Array<R> as(Class<R> type){
        return (Array<R>)this;
    }

    public String toString(String separator, Func<T, String> stringifier){
        StringBuilder builder = new StringBuilder();
        for(int i = 0; i < size; i++){
            if(i > 0) builder.append(separator);
            builder.append(stringifier.get(items[i]));
        }
        return builder.toString();
    }

    @Override
    public String toString(String separator){
        StringBuilder builder = new StringBuilder();
        for(int i = 0; i < size; i++){
            if(i > 0) builder.append(separator);
            builder.append(items[i]);
        }
        return builder.toString();
    }
}
