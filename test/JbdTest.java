package br.com.memotos.bateria;

import java.util.Arrays;
import java.util.HexFormat;

public class JbdTest {
    public static void main(String[] args) {
        var hex = HexFormat.of();
        var basic = hex.parseHex("DD03001B1700000002D003E8000020780000000000001048030F020B760B82FBFF77");
        var cells = hex.parseHex("DD04001E0F660F630F630F640F3E0F630F370F5B0F650F3B0F630F630F3C0F660F3DF9F977");
        check(Arrays.equals(Jbd.request(3), hex.parseHex("DDA50300FFFD77")));
        check(Arrays.equals(Jbd.request(4), hex.parseHex("DDA50400FFFC77")));
        var reader = new Jbd.Reader();
        check(reader.accept(Arrays.copyOf(basic, 20)) == null);
        var info = Jbd.basic(reader.accept(Arrays.copyOfRange(basic, 20, basic.length)));
        check(info.voltage() == 58.88 && info.percent() == 72 && info.groups() == 15);
        check(info.temperatures().length == 2 && info.temperatures()[0] == 20.3);
        check(Jbd.cells(reader.accept(cells))[4] == 3902);
        cells[8] ^= 1;
        try {
            reader.accept(cells);
            throw new AssertionError("Checksum inválido aceito");
        } catch (IllegalArgumentException expected) { }
        try {
            Jbd.request(0xe1);
            throw new AssertionError("Comando fora da lista aceito");
        } catch (IllegalArgumentException expected) { }
        System.out.println("Testes do protocolo passaram");
    }

    static void check(boolean result) {
        if (!result) throw new AssertionError();
    }
}
