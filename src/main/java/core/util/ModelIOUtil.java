package core.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Utility to serialize/deserialize Model objects to a custom binary .mod format.
 * Format (all multibyte integers are big-endian):
 * - Magic header: ASCII "PolyChat Model" (14 bytes)
 * - Version: 1 byte (0x01)
 * - Model count: 4 bytes (int)
 * For each model:
 *   - baseUrl length (2 bytes) + bytes
 *   - apiKey length (2 bytes) + bytes
 *   - modelName length (2 bytes) + bytes
 *   - nickname length (2 bytes) + bytes
 * Payload is compressed with GZIP.
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
