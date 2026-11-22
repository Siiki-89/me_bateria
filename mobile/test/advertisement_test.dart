import 'package:flutter_test/flutter_test.dart';
import 'package:me_bateria/transport.dart';

void main() {
  const address = [0xA5, 0xC2, 0x11, 0x22, 0x33, 0x44];
  test('recognizes the full label at the start of a JBD advertisement', () {
    expect(
      matchesBatteryManufacturerData([...address, 0, 4, 100], address),
      isTrue,
    );
  });
  test('also recognizes the label following a company identifier', () {
    expect(matchesBatteryManufacturerData([1, 2, ...address], address), isTrue);
  });
  test('ignores incomplete labels and unrelated advertisements', () {
    for (final data in <List<int>>[
      [],
      address.sublist(0, 5),
      [...address.take(5), 1],
      [1, ...address, 0],
    ]) {
      expect(matchesBatteryManufacturerData(data, address), isFalse);
    }
  });
}
