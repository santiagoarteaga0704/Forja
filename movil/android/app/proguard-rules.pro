# Reglas de R8 para la compilacion de entrega.
#
# De donde salen: `flutter build apk --release` fallaba en
# `:app:minifyReleaseWithR8` con "Missing classes detected while running R8", y
# el propio plugin de Android dejo escritas las tres lineas exactas en
# `build/app/outputs/mapping/release/missing_rules.txt`. Son literalmente esas.
#
# Por que hacen falta: el motor de inferencia (MediaPipe, el que corre el modelo
# en el aparato) nombra en su bytecode tres clases que no vienen en el aar -dos
# protos de diagnostico del grafo de calculo y una anotacion de AutoValue-.
# Ninguna de las tres se usa en tiempo de ejecucion: son referencias muertas que
# R8 encuentra al recorrer el grafo y, por prudencia, convierte en error. Se le
# dice que no avise por ellas, no que las conserve: `-dontwarn` no agrega nada
# al APK.
#
# Cuidado: no es lo mismo que `-keep`. Si alguna vez una de estas tres apareciera
# de verdad en una traza de ejecucion, la respuesta NO es ampliar esta lista,
# sino mirar por que el motor la esta pidiendo.
-dontwarn com.google.auto.value.extension.memoized.Memoized
-dontwarn com.google.mediapipe.proto.CalculatorProfileProto$CalculatorProfile
-dontwarn com.google.mediapipe.proto.GraphTemplateProto$CalculatorGraphTemplate
