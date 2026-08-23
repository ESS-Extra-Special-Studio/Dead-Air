package uk.co.extraspecialstudio.dead_air.music;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Reads Vorbis track length from an OGG container (Java AudioSystem does not support OGG).
 * Public so Forge ModuleClassLoader can resolve it when SoundDurationChecker loads.
 */
public final class OggDurationReader {
    private OggDurationReader() {
    }

    public static float readDurationSeconds(InputStream rawIn) throws IOException {
        try (InputStream in = rawIn.markSupported() ? rawIn : new BufferedInputStream(rawIn)) {
            long maxGranule = 0L;
            int sampleRate = 0;
            byte[] capture = new byte[4];

            while (true) {
                if (in.read(capture) != 4) {
                    break;
                }
                if (capture[0] != 'O' || capture[1] != 'g' || capture[2] != 'g' || capture[3] != 'S') {
                    break;
                }

                in.skip(2); // version + header type
                long granule = readLittleEndianLong(in);
                in.skip(12); // serial, page sequence, checksum

                int pageSegments = in.read();
                if (pageSegments < 0) {
                    break;
                }

                byte[] segmentTable = in.readNBytes(pageSegments);
                if (segmentTable.length != pageSegments) {
                    break;
                }

                int pageSize = 0;
                for (byte segment : segmentTable) {
                    pageSize += segment & 0xFF;
                }

                if (granule > maxGranule) {
                    maxGranule = granule;
                }

                byte[] pageData = in.readNBytes(pageSize);
                if (pageData.length != pageSize) {
                    break;
                }

                if (sampleRate == 0) {
                    sampleRate = parseSampleRate(pageData, segmentTable);
                }
            }

            if (sampleRate > 0 && maxGranule > 0) {
                return (float) maxGranule / sampleRate;
            }
            return 0f;
        }
    }

    private static int parseSampleRate(byte[] pageData, byte[] segmentTable) {
        int offset = 0;
        for (byte segment : segmentTable) {
            int len = segment & 0xFF;
            if (len <= 0) {
                continue;
            }
            if (offset + len <= pageData.length && pageData[offset] == 1 && len >= 16) {
                if (pageData[offset + 1] == 'v'
                    && pageData[offset + 2] == 'o'
                    && pageData[offset + 3] == 'r'
                    && pageData[offset + 4] == 'b'
                    && pageData[offset + 5] == 'i'
                    && pageData[offset + 6] == 's') {
                    return (pageData[offset + 12] & 0xFF)
                        | ((pageData[offset + 13] & 0xFF) << 8)
                        | ((pageData[offset + 14] & 0xFF) << 16)
                        | ((pageData[offset + 15] & 0xFF) << 24);
                }
            }
            offset += len;
        }
        return 0;
    }

    private static long readLittleEndianLong(InputStream in) throws IOException {
        byte[] buf = in.readNBytes(8);
        if (buf.length != 8) {
            throw new IOException("Unexpected EOF reading OGG granule");
        }
        return (buf[0] & 0xFFL)
            | ((buf[1] & 0xFFL) << 8)
            | ((buf[2] & 0xFFL) << 16)
            | ((buf[3] & 0xFFL) << 24)
            | ((buf[4] & 0xFFL) << 32)
            | ((buf[5] & 0xFFL) << 40)
            | ((buf[6] & 0xFFL) << 48)
            | ((buf[7] & 0xFFL) << 56);
    }
}
