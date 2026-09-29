package com.phoenix.game.util;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.func.Boolf;
import com.phoenix.game.func.Cons;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 文件句柄。参照 {@code arc.files.Fi} 移植。
 * <p><b>与 arc 的差异（刻意）</b>：arc 的 Fi 是独立实现，libgdx 的 {@code FileHandle} 方法名几乎相同
 * 但返回值是 {@code FileHandle}（原版代码大量写 {@code Fi f = dir.child("x")}、{@code for(Fi f : dir.list())}），
 * 而 {@code list()} 的数组返回类型无法协变覆盖，所以这里**不继承** FileHandle，改成持有它并转发。
 * <p>需要交给 libgdx API 时用 {@link #handle()} 取底层 FileHandle。
 * <p>注意：本类的所有方法都按"本地文件系统路径"处理（原版 Fi 也主要用于 mod/存档/截图目录）。
 */
public class Fi{

    protected final FileHandle handle;

    public Fi(String path){
        this.handle = Gdx.files == null ? new FileHandle(path) : Gdx.files.absolute(path);
    }

    public Fi(File file){
        this(file.getPath());
    }

    public Fi(FileHandle handle){
        this.handle = handle;
    }

    /** @return 底层 libgdx 句柄（与 libgdx API 交互时用）。 */
    public FileHandle handle(){
        return handle;
    }

    /** @return 绝对路径句柄（对应原版 {@code Fi.get(path)}）。 */
    public static Fi get(String path){
        return new Fi(path);
    }

    /** @return 临时文件（对应原版 tempFile）。 */
    public static Fi tempFile(String prefix){
        try{
            File file = File.createTempFile(prefix, ".tmp");
            file.deleteOnExit();
            return new Fi(file);
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    /** @return 临时目录（对应原版 tempDirectory）。 */
    public static Fi tempDirectory(String prefix){
        try{
            File file = Files.createTempDirectory(prefix).toFile();
            file.deleteOnExit();
            return new Fi(file);
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    public String path(){
        return handle.path();
    }

    public String absolutePath(){
        return handle.file().getAbsolutePath();
    }

    public String name(){
        return handle.name();
    }

    public boolean extEquals(String extension){
        return extension().equalsIgnoreCase(extension);
    }

    public String extension(){
        return handle.extension();
    }

    public String nameWithoutExtension(){
        return handle.nameWithoutExtension();
    }

    public String pathWithoutExtension(){
        String path = path();
        int dot = path.lastIndexOf('.');
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return dot > slash ? path.substring(0, dot) : path;
    }

    public File file(){
        return handle.file();
    }

    public InputStream read(){
        return handle.read();
    }

    public BufferedInputStream read(int bufferSize){
        return new BufferedInputStream(handle.read(), bufferSize);
    }

    public Reader reader(){
        return new InputStreamReader(handle.read(), StandardCharsets.UTF_8);
    }

    public Reader reader(String charset){
        return handle.reader(charset);
    }

    public BufferedReader reader(int bufferSize){
        return new BufferedReader(reader(), bufferSize);
    }

    public BufferedReader reader(int bufferSize, String charset){
        return new BufferedReader(handle.reader(charset), bufferSize);
    }

    public String readString(){
        return handle.readString("UTF-8");
    }

    public String readString(String charset){
        return handle.readString(charset);
    }

    public byte[] readBytes(){
        return handle.readBytes();
    }

    public int readBytes(byte[] bytes, int offset, int size){
        try(InputStream input = read()){
            int read = 0;
            while(read < size){
                int result = input.read(bytes, offset + read, size - read);
                if(result < 0) break;
                read += result;
            }
            return read;
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    public OutputStream write(){
        return write(false);
    }

    public OutputStream write(boolean append){
        return handle.write(append);
    }

    public OutputStream write(boolean append, int bufferSize){
        return new java.io.BufferedOutputStream(handle.write(append), bufferSize);
    }

    public void write(InputStream input, boolean append){
        handle.write(input, append);
    }

    public Writer writer(boolean append){
        return new OutputStreamWriter(handle.write(append), StandardCharsets.UTF_8);
    }

    public Writer writer(boolean append, String charset){
        return handle.writer(append, charset);
    }

    /** 把 Pixmap 写成 PNG（对应原版 Fi.writePNG）。 */
    public void writePNG(Pixmap pixmap){
        PixmapIO.writePNG(handle, pixmap);
    }

    public void writeString(String string){
        writeString(string, false);
    }

    public void writeString(String string, boolean append){
        handle.writeString(string, append, "UTF-8");
    }

    public void writeString(String string, boolean append, String charset){
        handle.writeString(string, append, charset);
    }

    public void writeBytes(byte[] bytes){
        writeBytes(bytes, false);
    }

    public void writeBytes(byte[] bytes, boolean append){
        try(OutputStream output = write(append)){
            output.write(bytes);
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    public void writeBytes(byte[] bytes, int offset, int size, boolean append){
        try(OutputStream output = write(append)){
            output.write(bytes, offset, size);
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    /** 深度优先遍历自身与全部子文件（对应原版 Fi.walk）。 */
    public void walk(Cons<Fi> visitor){
        visitor.get(this);
        if(isDirectory()){
            for(Fi child : list()){
                child.walk(visitor);
            }
        }
    }

    /** @return 递归收集所有满足条件的文件（对应原版 Fi.findAll）。 */
    public Array<Fi> findAll(Boolf<Fi> predicate){
        Array<Fi> result = new Array<>();
        walk(file -> {
            if(predicate.get(file)) result.add(file);
        });
        return result;
    }

    /** @return 递归收集所有文件。 */
    public Array<Fi> findAll(){
        return findAll(file -> true);
    }

    public Fi[] list(){
        return list((FileFilter)null);
    }

    public Fi[] list(FileFilter filter){
        File[] files = file().listFiles(filter);
        return toFiArray(files);
    }

    public Fi[] list(FilenameFilter filter){
        File[] files = file().listFiles(filter);
        return toFiArray(files);
    }

    /** @return 按扩展名过滤的子文件（对应原版 Fi.list(String)）。 */
    public Fi[] list(String extension){
        return list((FilenameFilter)(dir, name) -> name.toLowerCase().endsWith("." + extension.toLowerCase()));
    }

    private Fi[] toFiArray(File[] files){
        if(files == null) return new Fi[0];
        Fi[] result = new Fi[files.length];
        for(int i = 0; i < files.length; i++){
            result[i] = new Fi(files[i]);
        }
        return result;
    }

    public boolean isDirectory(){
        return handle.isDirectory();
    }

    public Fi child(String name){
        return new Fi(handle.child(name));
    }

    public Fi sibling(String name){
        return new Fi(handle.sibling(name));
    }

    public Fi parent(){
        FileHandle parent = handle.parent();
        return parent == null ? null : new Fi(parent);
    }

    public boolean mkdirs(){
        // libgdx 的 FileHandle.mkdirs() 返回 void，这里按 arc 的语义返回"目录是否已存在"
        handle.mkdirs();
        return isDirectory();
    }

    public boolean exists(){
        return handle.exists();
    }

    public boolean delete(){
        return handle.delete();
    }

    public boolean deleteDirectory(){
        return handle.deleteDirectory();
    }

    public void emptyDirectory(){
        emptyDirectory(false);
    }

    public void emptyDirectory(boolean preserveTree){
        handle.emptyDirectory(preserveTree);
    }

    public void copyTo(Fi destination){
        handle.copyTo(destination.handle);
    }

    public void moveTo(Fi destination){
        handle.moveTo(destination.handle);
    }

    public long length(){
        return handle.length();
    }

    public long lastModified(){
        return handle.lastModified();
    }

    @Override
    public boolean equals(Object other){
        if(!(other instanceof Fi)) return false;
        return file().equals(((Fi)other).file());
    }

    @Override
    public int hashCode(){
        return file().hashCode();
    }

    @Override
    public String toString(){
        return path();
    }
}
