package br.com.memotos.bateria;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

final class Jbd {
    static byte[] request(int command) {
        if (command != 3 && command != 4) throw new IllegalArgumentException("Consulta desconhecida");
        return new byte[]{(byte) 0xdd, (byte) 0xa5, (byte) command, 0, (byte) 0xff, (byte) -command, 0x77};
    }

    record Basic(double voltage, double current, double remaining, double nominal, int cycles,
                 int protections, int firmware, int percent, int switches, int groups, double[] temperatures) {}

    static Basic basic(byte[] frame) {
        int length = u(frame[3]);
        if (frame[1] != 3 || length < 23) throw new IllegalArgumentException("Resposta de estado incompleta");
        int sensors = u(frame[26]);
        if (23 + 2 * sensors > length) throw new IllegalArgumentException("Temperaturas incompletas");
        double[] temperatures = new double[sensors];
        for (int i = 0; i < sensors; i++) temperatures[i] = (word(frame, 27 + 2 * i) - 2731) / 10.0;
        return new Basic(word(frame, 4) / 100.0, (short) word(frame, 6) / 100.0,
                word(frame, 8) / 100.0, word(frame, 10) / 100.0, word(frame, 12), word(frame, 20),
                u(frame[22]), u(frame[23]), u(frame[24]), u(frame[25]), temperatures);
    }

    static int[] cells(byte[] frame) {
        int length = u(frame[3]);
        if (frame[1] != 4 || length == 0 || length % 2 != 0) throw new IllegalArgumentException("Resposta de células inválida");
        int[] values = new int[length / 2];
        for (int i = 0; i < values.length; i++) values[i] = word(frame, 4 + 2 * i);
        return values;
    }

    private static int u(byte value) { return value & 255; }
    private static int word(byte[] bytes, int at) { return (u(bytes[at]) << 8) | u(bytes[at + 1]); }

    static final class Reader {
        private final ByteArrayOutputStream pending = new ByteArrayOutputStream();

        byte[] accept(byte[] bytes) {
            pending.write(bytes, 0, bytes.length);
            byte[] frame = pending.toByteArray();
            if (frame.length > 262 || (frame.length > 0 && u(frame[0]) != 0xdd)) throw badFrame();
            if (frame.length < 4) return null;
            int end = 4 + u(frame[3]);
            if (frame.length < end + 3) return null;
            if (frame.length != end + 3 || frame[end + 2] != 0x77) throw badFrame();
            int sum = 0;
            for (int i = 2; i < end; i++) sum += u(frame[i]);
            if (((-sum) & 0xffff) != word(frame, end) || frame[2] != 0) throw badFrame();
            pending.reset();
            return frame;
        }

        private IllegalArgumentException badFrame() {
            pending.reset();
            return new IllegalArgumentException("O BMS enviou uma resposta inválida ou recusou a consulta");
        }
    }
}
