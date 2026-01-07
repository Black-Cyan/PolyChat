package core.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 用于将 Model 对象序列化/反序列化为自定义二进制 .mod 格式的工具。
 * 格式（所有多字节整数均为大端序）：
 * - 文件头：ASCII "PolyChat Model" (14 字节)
 * - 版本：1 字节 (0x01)
 * - baseUrl 长度 (2 字节) + 字节
 * - apiKey 长度 (2 字节) + 字节
 * - modelName 长度 (2 字节) + 字节
 * - nickname 长度 (2 字节) + 字节
 * 使用 GZIP 压缩
 */
public final class ModelIOUtil {
    private static final byte[] MAGIC = "PolyChat Model".getBytes(StandardCharsets.US_ASCII);
    private static final byte VERSION = 0x01;

    private ModelIOUtil() {}

    public static void writeModel(File file, ModelPayload model) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file);
             GZIPOutputStream gzos = new GZIPOutputStream(fos);
             DataOutputStream dos = new DataOutputStream(gzos)) {
            dos.write(MAGIC);
            dos.writeByte(VERSION);
            writeString(dos, model.baseUrl());
            writeString(dos, model.apiKey());
            writeString(dos, model.modelName());
            writeString(dos, model.nickname());
            dos.flush();
        }
    }

    public static ModelPayload readModel(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             GZIPInputStream gzis = new GZIPInputStream(fis);
             DataInputStream dis = new DataInputStream(gzis)) {
            byte[] magicBuf = new byte[MAGIC.length];
            dis.readFully(magicBuf);
            if (!java.util.Arrays.equals(magicBuf, MAGIC)) {
                throw new IOException("Invalid file header: not a PolyChat Model file");
            }
            byte version = dis.readByte();
            if (version != VERSION) {
                throw new IOException("Unsupported version: " + version);
            }
            String baseUrl = readString(dis);
            String apiKey = readString(dis);
            String modelName = readString(dis);
            String nickname = readString(dis);
            return new ModelPayload(baseUrl, apiKey, modelName, nickname);
        }
    }

    public record ModelPayload(String baseUrl, String apiKey, String modelName, String nickname) {}

    private static void writeString(DataOutputStream dos, String value) throws IOException {
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        if (data.length > 0xFFFF) {
            throw new IOException("String too long");
        }
        dos.writeShort(data.length);
        dos.write(data);
    }

    private static String readString(DataInputStream dis) throws IOException {
        int len = dis.readUnsignedShort();
        byte[] data = new byte[len];
        dis.readFully(data);
        return new String(data, StandardCharsets.UTF_8);
    }
}
