# Auditoria tecnica do AAB de release

Data da auditoria: 2026-08-20

## Artefato analisado

| Item | Resultado |
| --- | --- |
| Status | PASS |
| Artefato | `app/build/outputs/bundle/release/app-release.aab` |
| Caminho absoluto | `C:\Users\dvmrn\Repositórios\ApenasDance-AiMotionTrackingDanceGame\app\build\outputs\bundle\release\app-release.aab` |
| Tamanho | 42.418.437 bytes |
| Data de modificacao | 2026-08-20 00:23:44 |
| SHA-256 | `A8924C518CE7B35A320C172FBB8DCCBFAE959AD1DEB778B5FF298C1A1DAD55F1` |
| Confirmacao | O arquivo existe e foi tratado como o artefato de release analisado. |

Tambem foram executados os builds `assembleRelease` e `bundleRelease` antes da auditoria. O APK de release gerado foi `app/build/outputs/apk/release/app-release-unsigned.apk`; a auditoria tecnica abaixo considera o AAB como artefato principal.

## Metodo de verificacao

| Verificacao | Fonte utilizada | Status |
| --- | --- | --- |
| Existencia, tamanho, data e hash do AAB | Sistema de arquivos e SHA-256 | PASS |
| Manifest final | `bundletool dump manifest` diretamente sobre o AAB | PASS |
| Configuracao do bundle | `bundletool dump config` diretamente sobre o AAB | PASS |
| Estrutura de arquivos | Listagem ZIP do AAB | PASS |
| Bibliotecas nativas | Listagem ZIP do AAB | PASS |
| Busca de strings em entradas do AAB | Leitura byte a byte das entradas ZIP | PASS |
| Dependencias de release | Gradle `dependencyInsight` | PASS |
| Comparacao com `docs/license_audit.md` | Arquivo nao localizado | PENDING |
| Testes funcionais em dispositivo | Nao executados no ambiente de auditoria | PENDING |

## 1. Identidade do aplicativo

| Item | Valor confirmado | Fonte | Status |
| --- | --- | --- | --- |
| Package/applicationId | `com.sintonia.sincronia` | Manifest do AAB | PASS |
| versionCode | `1` | Manifest do AAB | PASS |
| versionName | `0.1.0` | Manifest do AAB | PASS |
| minSdk | `26` | Manifest do AAB | PASS |
| targetSdk | `35` | Manifest do AAB | PASS |
| compileSdkVersion | `35` | Manifest do AAB | PASS |
| compileSdkVersionCodename | `15` | Manifest do AAB | PASS |
| Android Gradle Plugin | `8.7.3` | `BUNDLE-METADATA/com.android.tools.build.gradle/app-metadata.properties` | PASS |
| `android:allowBackup` | `false` | Manifest do AAB | PASS |
| `android:extractNativeLibs` | `false` | Manifest do AAB | PASS |
| `android:debuggable` | Atributo nao presente | Manifest do AAB | PASS |

O Manifest final do AAB contem a activity principal `com.sintonia.sincronia.MainActivity`, exportada para o launcher, com orientacao portrait.

## 2. Configuracao de release e debug

### Resultado geral

| Item | Existe no codigo | Presente no AAB | Executado no release | Uso funcional normal | Exclusivo debug | Consumo potencial | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `DebugReportButton` | Sim, arquivo preservado comentado | Nao localizado em entradas nem strings do AAB | Nao | Nao | Sim | Nao identificado no AAB | PASS |
| `realtime_comparison_debug_report.json` | Sim, como constante/string em codigo | String localizada em `base/dex/classes3.dex`; arquivo JSON nao localizado | Nao identificado como fluxo ativo | Nao | Sim | Nao gera arquivo no estado atual | PASS |
| `RealtimeComparisonDebugConfig` | Sim | String localizada em `base/dex/classes3.dex` | Configurado com `WRITE_REALTIME_COMPARISON_REPORT = false` | Nao | Sim | Classe/string permanece no DEX por `minify=false` | PASS |
| `WRITE_REALTIME_COMPARISON_REPORT` | Sim | String localizada em `base/dex/classes3.dex` | Valor de codigo-fonte: `false` | Nao | Sim | Sem coleta ativa identificada | PASS |
| `RealtimeComparisonDebugCollector` | Sim | String localizada em `base/dex/classes3.dex` | Collector ativo configurado como `null` no codigo | Nao | Sim | Codigo permanece no DEX por `minify=false` | PASS |
| `debugReportPath` | Apenas em comentarios no codigo | Nao localizado no AAB | Nao | Nao | Sim | Nao identificado | PASS |
| `DebugVideoEncoder` | Sim | String localizada em `base/dex/classes3.dex` | Sim, durante importacao | Sim, gera video com esqueleto para opcao de visualizacao | Nao exclusivamente | Gera `dance_debug.mp4` | PASS COM RESSALVA |
| `dance_debug.mp4` | Sim, nome de arquivo runtime | String localizada em `base/dex/classes3.dex`; arquivo nao empacotado no AAB | Sim, durante importacao | Sim, usado quando `showSkeleton` esta ativo | Nao exclusivamente | Armazenamento e processamento adicionais | PASS COM RESSALVA |
| `ProcessingPerformanceReport` | Sim | String localizada em `base/dex/classes3.dex` | Desabilitado por `SHOW_PROCESSING_REPORT = false` | Nao no estado atual | Diagnostico/performance | Codigo permanece no DEX por `minify=false` | PASS |
| `SHOW_PROCESSING_REPORT` | Sim | String localizada em `base/dex/classes3.dex` | Valor de codigo-fonte: `false` | Nao no estado atual | Diagnostico/performance | Sem UI ativa identificada | PASS |
| `realtime_comparison_debug` | Sim como trecho de string | String localizada em `base/dex/classes3.dex` | Nao identificado como fluxo ativo | Nao | Sim | Sem arquivo gerado identificado | PASS |
| `debugVideo` | Sim | String localizada em `base/dex/classes3.dex` | Sim, metadata e selecao de video | Sim, quando opcao de esqueleto esta ativa | Nao exclusivamente | Armazenamento adicional | PASS COM RESSALVA |

### Observacoes sobre artefatos de debug

| Item encontrado no AAB | Avaliacao | Status |
| --- | --- | --- |
| `BUNDLE-METADATA/com.android.tools.build.debugsymbols/*/*.sym` | Simbolos nativos de `libsincronia_yuv.so` e `libyuv.so`. Ficam em `BUNDLE-METADATA` do bundle e nao sao arquivos runtime instalados como recurso do app. Representam metadados de simbolizacao nativa. | PASS |
| `base/root/DebugProbesKt.bin` | Recurso pequeno associado a coroutines/debug probes. Nao foi identificado como funcionalidade de debug acessivel pela interface do aplicativo. | PASS COM RESSALVA |
| `base/res/drawable/abc_vector_test.xml` e layouts `ime_*_test_activity.xml` | Recursos padrao de bibliotecas AndroidX/AppCompat. Nao foram identificados como testes proprios ou relatorios do aplicativo. | PASS |

### Achado de release

O AAB final contem classes e strings de componentes de debug/diagnostico no DEX porque a build de release esta com `isMinifyEnabled = false`. Nao foi localizada chamada ativa para gerar o relatorio `realtime_comparison_debug_report.json`, nem arquivo de relatorio empacotado no AAB. Esse ponto nao foi classificado como falha funcional, mas registra que o codigo de suporte ao relatorio ainda permanece dentro do artefato compilado.

## 3. Permissoes

Permissoes efetivamente presentes no Manifest do AAB:

| Permissao | Origem/indicio | Motivo funcional identificado | Necessidade aparente | Status |
| --- | --- | --- | --- | --- |
| `android.permission.CAMERA` | Manifest do AAB e codigo de camera em `DancingOverlay` | Captura em tempo real para comparacao de movimentos | Necessaria para a funcionalidade principal de danca/camera | PASS |
| `android.permission.ACCESS_NETWORK_STATE` | Manifest do AAB | Incluida por dependencia/transitivo; relacionada ao stack de transporte | Nao foi identificado uso direto pelo codigo do app | FAIL |
| `android.permission.INTERNET` | Manifest do AAB; merge blame aponta `com.google.android.datatransport:transport-backend-cct:3.1.0` | Suporte de rede para DataTransport, transitivo de MediaPipe Tasks Core | Nao foi identificado uso direto pelo codigo do app; contradiz expectativa tecnica de artefato estritamente offline | FAIL |
| `com.sintonia.sincronia.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Manifest do AAB | Permissao signature gerada por AndroidX/Core para receiver dinamico nao exportado | Compatibilidade de biblioteca | PASS |

O Manifest tambem contem componentes de `com.google.android.datatransport.runtime`, incluindo `TransportBackendDiscovery`, `JobInfoSchedulerService` e `AlarmManagerSchedulerBroadcastReceiver`. A origem confirmada por Gradle `dependencyInsight` foi:

```text
com.google.android.datatransport:transport-runtime:3.1.0
+--- com.google.android.datatransport:transport-backend-cct:3.1.0
|    \--- com.google.mediapipe:tasks-core:0.10.14
|         \--- com.google.mediapipe:tasks-vision:0.10.14
```

Esse achado nao altera automaticamente a conclusao sobre comportamento em runtime, mas confirma que o artefato final possui permissao de rede e componentes de transporte.

## 4. Bibliotecas nativas

ABIs presentes:

- `arm64-v8a`
- `armeabi-v7a`
- `x86`
- `x86_64`

Arquivos `.so` encontrados:

| ABI | Biblioteca | Tamanho |
| --- | --- | ---: |
| `arm64-v8a` | `libandroidx.graphics.path.so` | 10.096 |
| `arm64-v8a` | `libimage_processing_util_jni.so` | 29.008 |
| `arm64-v8a` | `libmediapipe_tasks_vision_jni.so` | 14.027.656 |
| `arm64-v8a` | `libsincronia_yuv.so` | 561.072 |
| `arm64-v8a` | `libsurface_util_jni.so` | 4.832 |
| `arm64-v8a` | `libyuv.so` | 590.152 |
| `armeabi-v7a` | `libandroidx.graphics.path.so` | 7.252 |
| `armeabi-v7a` | `libimage_processing_util_jni.so` | 20.380 |
| `armeabi-v7a` | `libmediapipe_tasks_vision_jni.so` | 8.465.324 |
| `armeabi-v7a` | `libsincronia_yuv.so` | 373.320 |
| `armeabi-v7a` | `libsurface_util_jni.so` | 3.440 |
| `armeabi-v7a` | `libyuv.so` | 391.980 |
| `x86` | `libandroidx.graphics.path.so` | 9.284 |
| `x86` | `libimage_processing_util_jni.so` | 38.292 |
| `x86` | `libmediapipe_tasks_vision_jni.so` | 21.416.860 |
| `x86` | `libsincronia_yuv.so` | 664.220 |
| `x86` | `libsurface_util_jni.so` | 3.712 |
| `x86` | `libyuv.so` | 697.324 |
| `x86_64` | `libandroidx.graphics.path.so` | 10.760 |
| `x86_64` | `libimage_processing_util_jni.so` | 48.104 |
| `x86_64` | `libsincronia_yuv.so` | 748.632 |
| `x86_64` | `libsurface_util_jni.so` | 4.928 |
| `x86_64` | `libyuv.so` | 781.288 |

Observacoes:

- `libmediapipe_tasks_vision_jni.so` esta presente para `arm64-v8a`, `armeabi-v7a` e `x86`.
- Nao foi localizada entrada `base/lib/x86_64/libmediapipe_tasks_vision_jni.so` no AAB analisado.
- `libsincronia_yuv.so` e `libyuv.so` estao presentes nas quatro ABIs.
- Nao foram identificadas bibliotecas nativas inesperadas fora de MediaPipe/AndroidX/libyuv/JNI proprio.

Status: PASS COM RESSALVA para a ausencia de `libmediapipe_tasks_vision_jni.so` em `x86_64`, que nao foi validada em dispositivo/emulador x86_64 nesta auditoria.

## 5. Dependencias e licencas

### Dependencias relevantes confirmadas

| Dependencia | Confirmacao | Status |
| --- | --- | --- |
| `com.google.mediapipe:tasks-vision:0.10.14` | Gradle `dependencyInsight`; bibliotecas nativas e asset `pose_landmarker_full.task` no AAB | PASS |
| `com.google.mediapipe:tasks-core:0.10.14` | Dependencia transitiva de Tasks Vision | PASS |
| `com.google.protobuf:protobuf-javalite:4.26.1` | Gradle `dependencyInsight`; arquivos `.proto` em `base/root/google/protobuf` | PASS |
| `com.google.android.datatransport:*` | Gradle `dependencyInsight`; Manifest final e arquivos `transport-*.properties` | PASS COM RESSALVA |
| `libyuv` | `libyuv.so` empacotada por ABI e `libsincronia_yuv.so` linkada pelo CMake | PASS |

### Arquivos de licenca

| Arquivo | Presenca no AAB | Tamanho | Status |
| --- | --- | ---: | --- |
| `base/assets/third_party_licenses.txt` | Presente | 12.443 bytes | PASS |
| `docs/license_audit.md` | Nao localizado no repositorio durante a auditoria | N/A | PENDING |

O arquivo `third_party_licenses.txt` inclui referencias a MediaPipe Tasks Vision/Core, Pose Landmarker, AndroidX, Media3, Kotlin/Kotlinx, Guava, AutoValue, Flogger e Google Android DataTransport. O arquivo tambem contem aviso de privacidade do MediaPipe informando processamento local e mencionando metricas de performance/utilizacao das Tasks APIs.

Nao foi possivel comparar formalmente com `docs/license_audit.md`, pois esse arquivo nao foi localizado. A comparacao com a auditoria de licencas permanece PENDING.

## 6. Assets e arquivos empacotados

### Assets principais

| Arquivo | Tamanho | Avaliacao | Status |
| --- | ---: | --- | --- |
| `base/assets/pose_landmarker_full.task` | 9.398.198 | Modelo Pose Landmarker utilizado pelo MediaPipe | PASS |
| `base/assets/third_party_licenses.txt` | 12.443 | Licencas de terceiros | PASS |
| `base/assets/shaders/*.glsl` | Variavel | Shaders de Media3/transformacao de video | PASS |

### Assets `SS_*`/`ss_*`

Arquivos encontrados:

- `base/res/drawable/ss_icon.png`
- `base/res/drawable/ss_logo.png`
- `base/res/drawable-nodpi-v4/ss_bom.png`
- `base/res/drawable-nodpi-v4/ss_ok.png`
- `base/res/drawable-nodpi-v4/ss_otimo.png`
- `base/res/drawable-nodpi-v4/ss_ss.png`
- `base/res/drawable-nodpi-v4/ss_x.png`

Esses assets sao considerados autoria propria do projeto e nao foram classificados como assets de terceiros. Status: PASS.

### Busca por arquivos suspeitos

| Padrao | Resultado no AAB | Status |
| --- | --- | --- |
| `realtime` | Nenhuma entrada ZIP localizada | PASS |
| `comparison` | Nenhuma entrada ZIP localizada | PASS |
| `report` | Nenhuma entrada ZIP localizada | PASS |
| `dump` | Nenhuma entrada ZIP localizada | PASS |
| `debug` | Simbolos nativos em `BUNDLE-METADATA` e `DebugProbesKt.bin` | PASS COM RESSALVA |
| `test` | Recursos padrao de bibliotecas AndroidX/AppCompat | PASS |
| `license` | `base/assets/third_party_licenses.txt` | PASS |

Nao foram localizados videos de teste, JSONs de teste, relatorios persistidos, dumps ou arquivos temporarios proprios empacotados no AAB.

## 7. Estrutura e tamanho

Composicao aproximada por diretorio interno, considerando tamanho nao comprimido:

| Area | Tamanho aproximado |
| --- | ---: |
| `base/lib` | 48.907.916 bytes |
| `base/dex` | 31.274.220 bytes |
| `base/assets` | 9.487.579 bytes |
| `BUNDLE-METADATA/com.android.tools.build.debugsymbols` | 5.400.408 bytes |
| `base/res` | 1.095.043 bytes |
| `base/resources.pb` | 892.611 bytes |
| `base/root` | 126.386 bytes |

Maiores entradas:

| Arquivo | Tamanho |
| --- | ---: |
| `base/lib/x86/libmediapipe_tasks_vision_jni.so` | 21.416.860 |
| `base/lib/arm64-v8a/libmediapipe_tasks_vision_jni.so` | 14.027.656 |
| `base/dex/classes.dex` | 12.822.964 |
| `base/dex/classes2.dex` | 9.985.508 |
| `base/assets/pose_landmarker_full.task` | 9.398.198 |
| `base/lib/armeabi-v7a/libmediapipe_tasks_vision_jni.so` | 8.465.324 |
| `base/dex/classes3.dex` | 7.436.700 |
| `base/dex/classes4.dex` | 1.029.048 |

O tamanho do AAB e dominado por bibliotecas nativas do MediaPipe, DEX, recurso Pose Landmarker e bibliotecas nativas libyuv/JNI. Nao foi identificado arquivo grande inesperado fora desses grupos.

## 8. Armazenamento e geracao de arquivos em runtime

O armazenamento funcional identificado usa `context.applicationContext.filesDir`, com base em:

```text
<filesDir>/dances/
```

Em um dispositivo Android, esse local corresponde normalmente a armazenamento interno privado do app, por exemplo:

```text
/data/user/0/com.sintonia.sincronia/files/dances/
```

### Arquivos gerados

| Arquivo | Local | Momento de criacao | Finalidade | Necessario | Exclusivo debug | Status |
| --- | --- | --- | --- | --- | --- | --- |
| `dance.mp4` | `<filesDir>/dances/dance_NNN/` | Importacao de danca | Video importado, recortado/redimensionado | Sim | Nao | PASS |
| `dance_debug.mp4` | `<filesDir>/dances/dance_NNN/` | Importacao de danca | Video derivado com esqueleto desenhado | Necessario apenas para a opcao de visualizacao de esqueleto | Nao exclusivamente | PASS COM RESSALVA |
| `moveset.json` | `<filesDir>/dances/dance_NNN/` | Processamento de pose na importacao | Landmarks, landmarks normalizados e angulos por frame | Sim, para comparacao/scoring | Nao | PASS |
| `metadata.json` | `<filesDir>/dances/dance_NNN/` | Final da importacao e atualizacao de melhor rank | Metadados da danca e melhor rank | Sim | Nao | PASS |
| `.importing` | `<filesDir>/dances/dance_NNN/` | Inicio da importacao | Marcador de importacao incompleta | Sim, para limpeza de falhas | Nao | PASS |
| `realtime_comparison_debug_report.json` | Diretorio do moveset, se habilitado | Nao gerado no estado atual | Relatorio de comparacao em tempo real | Nao | Sim | PASS |

### Limpeza

| Cenario | Comportamento identificado | Status |
| --- | --- | --- |
| Inicio de importacao | Cria pasta da danca, cria `.importing` e remove `dance.mp4`, `dance_debug.mp4` e `moveset.json` preexistentes no mesmo slot | PASS |
| Importacao concluida | Grava `metadata.json` e remove `.importing` | PASS |
| Falha de importacao | Remove `moveset.json`, `dance_debug.mp4` e executa `danceFolder.deleteRecursively()` | PASS |
| Falha sem pose detectada | Remove `moveset.json`, remove `dance_debug.mp4` e lança excecao | PASS |
| Cancelamento de transformacao | Cancela `Transformer`; remove `outputFile` quando o cancelamento nao e `CancellationException` | PASS COM RESSALVA |
| Inicializacao/refresh | Remove pastas com marcador `.importing` via `cleanupInterruptedImports()` | PASS |
| Exclusao pelo usuario | `deleteDance(id)` remove a pasta da danca via `deleteRecursively()` | PASS |

### Observacao sobre `dance_debug.mp4`

`dance_debug.mp4` e gerado durante toda importacao, independentemente de a preferencia `showSkeleton` estar ativada no momento. O arquivo e usado por uma funcionalidade normal do aplicativo quando a visualizacao de esqueleto e habilitada. Portanto, nao foi classificado como exclusivo de debug. Ainda assim, representa consumo adicional de processamento e armazenamento que poderia ser evitado se a funcionalidade fosse gerada sob demanda ou condicionada a uma configuracao. Status: PASS COM RESSALVA.

## 9. Integridade funcional

| Teste | Resultado | Status |
| --- | --- | --- |
| Build `assembleRelease` | Concluido com sucesso | PASS |
| Build `bundleRelease` | Concluido com sucesso | PASS |
| Abertura do aplicativo | Nao testado; nenhum dispositivo/emulador foi usado nesta auditoria | PENDING |
| Importacao de danca | Nao testado em runtime | PENDING |
| Processamento | Nao testado em runtime | PENDING |
| Camera | Nao testado em runtime | PENDING |
| Comparacao de movimentos | Nao testado em runtime | PENDING |
| Pontuacao | Nao testado em runtime | PENDING |
| Feedback | Nao testado em runtime | PENDING |
| Resultado | Nao testado em runtime | PENDING |
| Armazenamento runtime | Verificado por codigo; nao testado em dispositivo | PENDING |
| Carregamento | Nao testado em runtime | PENDING |
| Navegacao principal | Nao testado em runtime | PENDING |

Nao foi presumido funcionamento de recursos de runtime apenas pelo sucesso do build.

## Resumo de resultados

| Area | Status |
| --- | --- |
| Existencia e integridade do AAB | PASS |
| Identidade do aplicativo | PASS |
| Manifest final | PASS COM RESSALVA |
| Debug report JSON | PASS |
| Botao/modal de relatorio debug | PASS |
| Permissoes | FAIL |
| Bibliotecas nativas | PASS COM RESSALVA |
| Licencas no AAB | PASS |
| Comparacao com `docs/license_audit.md` | PENDING |
| Assets empacotados | PASS |
| Estrutura/tamanho | PASS |
| Armazenamento runtime | PASS COM RESSALVA |
| Testes funcionais em dispositivo | PENDING |

## Itens FAIL

1. O AAB final contem `android.permission.INTERNET`.
   - Origem confirmada: `com.google.android.datatransport:transport-backend-cct:3.1.0`, transitivo de `com.google.mediapipe:tasks-core:0.10.14`.
   - Impacto tecnico: o artefato final nao pode ser descrito tecnicamente como estritamente sem permissao de rede.

2. O AAB final contem `android.permission.ACCESS_NETWORK_STATE`.
   - Origem provavel: stack transitivo de transporte/rede.
   - Impacto tecnico: nao foi identificado uso direto pelo codigo do aplicativo.

## Itens PENDING

1. `docs/license_audit.md` nao foi localizado; a comparacao formal com esse documento nao pode ser concluida.
2. Testes funcionais em dispositivo/emulador nao foram executados.
3. A ausencia de `libmediapipe_tasks_vision_jni.so` em `x86_64` nao foi validada em runtime.

# Conclusao

Estado geral do artefato: **APROVADO COM RESSALVAS**.

O AAB de release foi gerado, localizado e auditado como artefato tecnico. Nao foram encontrados relatorios JSON de debug, botao de relatorio debug ativo, arquivos temporarios proprios empacotados ou videos de teste no AAB. As licencas de terceiros estao presentes no asset `third_party_licenses.txt`, e os assets `SS_*`/`ss_*` foram tratados como autoria propria.

As principais ressalvas tecnicas sao a presenca de permissoes e componentes de rede/DataTransport no Manifest final, a permanencia de codigo/strings de diagnostico no DEX por `minify=false`, a geracao runtime de `dance_debug.mp4` como video derivado e a ausencia de testes funcionais em dispositivo nesta auditoria.
