package com.github.epsilon.modules.impl.render.bettertooltips;

import org.jetbrains.annotations.NotNull;

import java.io.DataOutput;
import java.io.IOException;

/**
 * 只统计写入字节数而不实际写出的 DataOutput，用于计算物品 NBT 的字节大小。
 */
public class ByteCountDataOutput implements DataOutput {

    public static final ByteCountDataOutput INSTANCE = new ByteCountDataOutput();

    private int count;

    public int getCount() {
        return count;
    }

    public void reset() {
        count = 0;
    }

    @Override
    public void write(int b) throws IOException {
        count++;
    }

    @Override
    public void write(byte[] b) throws IOException {
        count += b.length;
    }

    @Override
    public void write(byte @NotNull [] b, int off, int len) throws IOException {
        count += len;
    }

    @Override
    public void writeBoolean(boolean v) {
        count++;
    }

    @Override
    public void writeByte(int v) {
        count++;
    }

    @Override
    public void writeShort(int v) {
        count += 2;
    }

    @Override
    public void writeChar(int v) {
        count += 2;
    }

    @Override
    public void writeInt(int v) {
        count += 4;
    }

    @Override
    public void writeLong(long v) {
        count += 8;
    }

    @Override
    public void writeFloat(float v) {
        count += 4;
    }

    @Override
    public void writeDouble(double v) {
        count += 8;
    }

    @Override
    public void writeBytes(String s) {
        count += s.length();
    }

    @Override
    public void writeChars(String s) {
        count += s.length() * 2;
    }

    @Override
    public void writeUTF(@NotNull String s) {
        count += 2 + getUTFLength(s);
    }

    long getUTFLength(String s) {
        long utflen = 0;
        for (int cpos = 0; cpos < s.length(); cpos++) {
            char c = s.charAt(cpos);
            if (c >= 0x0001 && c <= 0x007F) {
                utflen++;
            } else if (c > 0x07FF) {
                utflen += 3;
            } else {
                utflen += 2;
            }
        }
        return utflen;
    }

}
