package uk.co.extraspecialstudio.dead_air.audio;

import net.minecraft.resources.ResourceLocation;
import uk.co.extraspecialstudio.dead_air.Config;
import uk.co.extraspecialstudio.dead_air.Dead_air;
import uk.co.extraspecialstudio.dead_air.radio.RadioStation;
import uk.co.extraspecialstudio.dead_air.radio.SignalStrength;
import uk.co.extraspecialstudio.dead_air.repack.javazoom.jl.decoder.Bitstream;
import uk.co.extraspecialstudio.dead_air.repack.javazoom.jl.decoder.Decoder;
import uk.co.extraspecialstudio.dead_air.repack.javazoom.jl.decoder.Header;
import uk.co.extraspecialstudio.dead_air.repack.javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Client-only MP3 stream playback for internet radio stations (Icecast/Shoutcast-style URLs).
 * Uses JLayer on a daemon thread; volume follows the same signal-bar curve as {@link RadioSoundInstance}.
 */
@SuppressWarnings("null")
public final class InternetStreamManager {

    private static final Object LOCK = new Object();
    private static final AtomicReference<Float> SIGNAL = new AtomicReference<>(1.0f);

    private static volatile Thread worker;
    private static volatile boolean runFlag;
    private static volatile ResourceLocation activeStationId;
    private static volatile String activeUrl;
    private static volatile SourceDataLine outputLine;

    private InternetStreamManager() {}

    /** True while our worker is decoding and writing to the line for this station id. */
    public static boolean isPlaying(ResourceLocation stationId) {
        if (stationId == null || activeStationId == null) return false;
        if (!stationId.equals(activeStationId)) return false;
        Thread w = worker;
        return w != null && w.isAlive();
    }

    /** @return true if a new decode thread was started (for broadcast-started events). */
    public static boolean ensurePlaying(RadioStation station, float signalStrength) {
        if (station == null || !station.isInternetStream()) return false;
        SIGNAL.set(signalStrength);
        String url = station.getInternetStreamUrl();
        if (url == null || url.isEmpty()) return false;

        synchronized (LOCK) {
            if (station.getId().equals(activeStationId) && url.equals(activeUrl) && worker != null && worker.isAlive()) {
                return false;
            }
            stopInternalLocked();
            activeStationId = station.getId();
            activeUrl = url;
            runFlag = true;
            Thread t = new Thread(() -> decodeLoop(url), "dead_air-internet-stream");
            t.setDaemon(true);
            worker = t;
            t.start();
            return true;
        }
    }

    public static void stopIfStation(ResourceLocation stationId) {
        if (stationId == null) return;
        synchronized (LOCK) {
            if (stationId.equals(activeStationId)) {
                stopInternalLocked();
            }
        }
    }

    public static void stopAll() {
        synchronized (LOCK) {
            stopInternalLocked();
        }
    }

    public static void updateSignal(float signalStrength) {
        SIGNAL.set(signalStrength);
    }

    /** Same bar curve as {@link RadioSoundInstance#updateVolume()} (via reflection-free duplicate). */
    public static float volumeFromSignal(float signalStrength) {
        int bars = SignalStrength.getSignalBars(signalStrength);
        if (bars == 0) return 0f;
        float minVol = (float) Config.minVolume;
        float maxVol = (float) (Config.maxVolume >= 0.01 ? Config.maxVolume : 0.7f);
        float barMultiplier = switch (bars) {
            case 5 -> 1.0f;
            case 4 -> 0.7f;
            case 3 -> 0.45f;
            case 2 -> 0.25f;
            case 1 -> 0.12f;
            default -> 0f;
        };
        return Math.min(maxVol, minVol + barMultiplier * (maxVol - minVol));
    }

    private static void stopInternalLocked() {
        runFlag = false;
        SourceDataLine line = outputLine;
        outputLine = null;
        if (line != null) {
            try {
                line.stop();
                line.flush();
                line.close();
            } catch (Exception ignored) {}
        }
        Thread w = worker;
        worker = null;
        activeStationId = null;
        activeUrl = null;
        if (w != null) {
            w.interrupt();
            try {
                w.join(1500L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void decodeLoop(String streamUrl) {
        while (runFlag && !Thread.currentThread().isInterrupted()) {
            InputStream raw = null;
            BufferedInputStream buffered = null;
            Bitstream bitstream = null;
            SourceDataLine line = null;
            try {
                raw = openStream(streamUrl);
                buffered = new BufferedInputStream(raw, 65536);
                bitstream = new Bitstream(buffered);
                Decoder decoder = new Decoder();
                Header header = bitstream.readFrame();
                if (header == null) {
                    sleepQuiet(1500L);
                    continue;
                }
                SampleBuffer first = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                bitstream.closeFrame();
                int sampleRate = decoder.getOutputFrequency();
                int channels = decoder.getOutputChannels();
                if (sampleRate <= 0) sampleRate = 44100;
                if (channels <= 0) channels = 2;

                AudioFormat format = new AudioFormat(sampleRate, 16, channels, true, false);
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                if (!AudioSystem.isLineSupported(info)) {
                    Dead_air.LOGGER.error("Internet stream: audio line not supported for {}", format);
                    sleepQuiet(3000L);
                    continue;
                }
                line = (SourceDataLine) AudioSystem.getLine(info);
                line.open(format, Math.min(line.getBufferSize(), 16384));
                line.start();
                outputLine = line;

                writePcmFrame(line, first);

                while (runFlag && !Thread.currentThread().isInterrupted()) {
                    header = bitstream.readFrame();
                    if (header == null) break;
                    SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                    bitstream.closeFrame();
                    writePcmFrame(line, output);
                }
            } catch (Throwable t) {
                if (runFlag) {
                    Dead_air.LOGGER.warn("Internet stream interrupted or error, will retry: {}", t.toString());
                }
            } finally {
                if (bitstream != null) {
                    try {
                        bitstream.close();
                    } catch (Exception ignored) {}
                }
                try {
                    if (buffered != null) buffered.close();
                } catch (Exception ignored) {}
                try {
                    if (raw != null) raw.close();
                } catch (Exception ignored) {}
                if (line != null) {
                    try {
                        line.stop();
                        line.flush();
                        line.close();
                    } catch (Exception ignored) {}
                    if (outputLine == line) {
                        outputLine = null;
                    }
                }
            }
            if (runFlag) {
                sleepQuiet(2000L);
            }
        }
    }

    private static void writePcmFrame(SourceDataLine line, SampleBuffer output) {
        if (line == null || output == null) return;
        short[] pcm = output.getBuffer();
        int len = output.getBufferLength();
        if (len <= 0) return;
        float vol = volumeFromSignal(SIGNAL.get());
        byte[] bytes = shortsToLittleEndianPcm16(pcm, len, vol);
        line.write(bytes, 0, bytes.length);
    }

    private static InputStream openStream(String streamUrl) throws Exception {
        URL url = new URL(streamUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "DeadAir-Minecraft/1.0 (+https://creatopia.uk)");
        conn.setRequestProperty("Icy-MetaData", "0");
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(45000);
        conn.setInstanceFollowRedirects(true);
        conn.connect();
        int code = conn.getResponseCode();
        if (code >= 400) {
            conn.disconnect();
            throw new java.io.IOException("HTTP " + code);
        }
        return conn.getInputStream();
    }

    private static byte[] shortsToLittleEndianPcm16(short[] pcm, int length, float volume) {
        int n = Math.min(length, pcm.length);
        byte[] out = new byte[n * 2];
        float v = Math.max(0f, Math.min(1f, volume));
        for (int i = 0; i < n; i++) {
            int s = Math.round(pcm[i] * v);
            s = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, s));
            out[i * 2] = (byte) (s & 0xff);
            out[i * 2 + 1] = (byte) ((s >> 8) & 0xff);
        }
        return out;
    }

    private static void sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
