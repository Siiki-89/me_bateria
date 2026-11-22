# ME Bateria

Aplicativo da ME Motos Elétricas para acompanhar baterias JBD pelo Bluetooth. O cliente pode cadastrar a bateria pelo segundo QR da etiqueta ou pelo identificador completo, consultar carga, tensão, corrente, temperaturas e grupos de células e acompanhar as recargas observadas pelo celular. Os dados ficam no aparelho.

O aplicativo Flutter fica em `mobile/`. A raiz conserva o piloto Android em Java e os recursos de marca usados pela versão Android. Clone o repositório inteiro.

## Desenvolvimento

Com Flutter instalado, execute na pasta `mobile/`:

```sh
flutter pub get
flutter analyze
flutter test
flutter devices
flutter run -d ID_DO_APARELHO
```

## Atualização do piloto Android

A versão Flutter usa o identificador `br.com.memotos.bateria`. Para atualizar uma instalação existente, assine o APK com a mesma chave usada pelo piloto. Configure `mobile/android/key.properties` localmente com `storeFile`, `storePassword`, `keyAlias` e `keyPassword`. O caminho de `storeFile` pode ser absoluto ou relativo a `mobile/android/`.

Na pasta `mobile/`, execute:

```sh
flutter build apk --release
adb install -r build/app/outputs/flutter-apk/app-release.apk
```

Na primeira abertura, a versão Flutter importa as baterias salvas, a última leitura disponível e o histórico local do piloto. Desinstalar o aplicativo antes de atualizar pode apagar esses dados.

## Teste no iPhone

No Mac com Flutter e Xcode, abra `mobile/`, execute `flutter pub get` e abra `ios/Runner.xcworkspace` no Xcode. Selecione a equipe Apple em **Runner → Signing & Capabilities**. Conecte o iPhone, selecione-o com `flutter devices` e inicie com `flutter run -d ID_DO_IPHONE`.

Com o iPhone perto da bateria, valide a permissão de Bluetooth, a identificação pelo segundo QR, a primeira leitura e a reconexão. A leitura ao vivo requer a bateria física.
