package uk.co.extraspecialstudio.dead_air.broadcast;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Scrambles transmissions for players without a Signal Decoder. Digits, spaces and punctuation survive. */
public final class BroadcastText {
    private static final char[] GLYPHS = {'#', '%', '&', '@', '/', '\\', '*', '?', '~', '=', '+'};

    public static List<String> scramble(List<String> lines) {
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) out.add(scramble(line));
        return out;
    }

    public static String scramble(String line) {
        Random random = new Random(line.hashCode());
        StringBuilder sb = new StringBuilder(line.length());
        for (char c : line.toCharArray()) {
            if (Character.isLetter(c) && random.nextFloat() < 0.65f) {
                sb.append(GLYPHS[random.nextInt(GLYPHS.length)]);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private BroadcastText() {}
}
