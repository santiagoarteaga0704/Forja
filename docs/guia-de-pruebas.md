# Guía de pruebas de FORJA

Cómo verificar el proyecto entero, de lo más barato a lo más caro: primero lo
que corre solo, después lo que hay que mirar con los ojos.

Todo se ejecuta desde `C:\dev\forja` salvo donde se diga otra cosa.

---

## 0. Antes de empezar

### Lo que tiene que estar

| | Cómo se comprueba |
|---|---|
| Java 21 | `java -version` |
| Docker Desktop **corriendo** | `docker info` |
| Node 22 | `node -v` |
| Flutter (solo para el móvil) | `flutter --version` |
| Ollama (solo para la IA) | `ollama list` → tiene que aparecer `gemma3:4b` |

### Dos trampas que ya mordieron

**El puerto 5433 lo pelea otro proyecto.** El contenedor `erp_postgres` del ERP
arranca solo con Docker Desktop y se queda con el mismo puerto que FORJA.

```powershell
docker ps                      # ¿hay algo más en 5433?
docker stop erp_postgres       # si lo hay
```

Si el `up` ya falló una vez, el contenedor puede quedar corriendo **sin publicar
el puerto**. Se arregla recreándolo; el volumen con los datos no se toca:

```powershell
docker compose up -d --force-recreate
```

**El JVM viejo no muere solo.** `spring-boot:run` bifurca un proceso que
sobrevive a `Ctrl+C`. Si no lo matás, el backend nuevo no arranca y **los
endpoints nuevos dan 404 sin ninguna explicación**.

```powershell
Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
  Where-Object { $_.CommandLine -like '*ForjaBackendApplication*' } |
  Stop-Process -Force
```

---

## 1. Levantar todo

```powershell
docker compose up -d                                  # Postgres 17 en el 5433
$env:FORJA_JWT_SECRETO = "una-clave-de-32-caracteres-o-mas"
$env:FORJA_IA_HABILITADA = "true"                     # solo si vas a probar el pedido por IA
./mvnw spring-boot:run                                # backend en 8080
```

En otra terminal:

```powershell
cd web
npm run dev                                           # cliente en 5173
```

**Está listo cuando** en el log aparece `Started ForjaBackendApplication` y
`docker compose ps` muestra `forja-db` en `healthy`.

---

## 2. Las pruebas automáticas

### Backend — 284 pruebas

```powershell
./mvnw test
```

Necesita **Docker arriba**: son pruebas contra un Postgres de verdad, no contra
una base en memoria. Eso es deliberado: la exclusión mutua y la serialización de
la bitácora las arbitra Postgres, y contra H2 no se probaría lo que importa.

**Vacían las tablas al terminar.** Si tenías datos cargados a mano, se van.

Lo que cubren, por si te lo preguntan:

| Grupo | Qué verifica |
|---|---|
| `ServicioBloqueoTest` | Exclusión mutua, incluida una carrera de 8 hilos por el mismo elemento |
| `ServicioOperacionesTest` | Bitácora, reenvío idempotente, rechazo por bloqueo ajeno |
| `GeneradorTest` | Que del diagrama salga un proyecto Spring Boot de 4 capas |
| `XmiTest` | Ida y vuelta de XMI 2.5.1 |
| `ParserVozCorpusTest` | El corpus de frases del dictado |
| `PropuestasEnRevisionTest`, `ServicioPedidoRevisionTest` | Que se aplique lo revisado y no una segunda opinión del modelo |
| `PreguntasTest`, `PreguntasResistenciaTest` | Que el agente responda, y que ninguna entrada lo rompa |
| `AgenteNoSeCaeTest` | Que conteste aunque no se pueda anotar el uso |

### Cliente web

No hay arnés de pruebas en `web/`. Lo que hay:

```powershell
cd web
npx tsc -b          # tipos
npx oxlint          # lint
npm run build       # que compile de verdad
```

### Móvil

```powershell
cd movil
flutter test                 # el núcleo, sin necesidad de un teléfono
```

---

## 3. El recorrido a mano

Esto es lo que no cubre ninguna prueba automática y **es el paso 2 de la
evaluación**: el software funcionando como producto.

Abrí **http://localhost:5173**.

### 3.1 Entrar

1. La pantalla muestra la hoja construyéndose sola en tres etapas. Dejala correr
   un ciclo completo (~14 s) y fijate que la lista de la derecha se encienda en
   sincronía.
2. Apuntá con el mouse a una de las tres vías: la demostración se detiene ahí.
3. Creá una cuenta y entrá.

### 3.2 Proyecto y diagrama

4. Creá un proyecto. Aparece en la lista con «recién» y la etiqueta «Tuyo».
5. Elegilo: a la derecha aparecen sus diagramas.
6. Creá un diagrama. Se abre el lienzo solo.

### 3.3 Las tres vías de entrada al modelo

**Dibujar.** Botón `Clase` → nombre → aparece en la hoja. Arrastrala. Con
`Relación` uní dos clases y elegí multiplicidades.

**Dictar.** Botón `Dictar`. Escribí (o decí, si el micrófono anda):

```
crea la clase Paciente
a Paciente agregale el atributo nombre de tipo texto
Paciente tiene muchas Consultas
```

Cada frase se aplica al instante, sin modelo de lenguaje: es la gramática
determinista.

**Fotografiar.** Botón `Pizarra`. Cargá una foto de un diagrama en una pizarra.
El OCR corre **en el navegador**, sin mandar la imagen a ningún lado.

### 3.4 El pedido por IA (necesita Ollama)

7. Botón `Pedir` en la barra de dictado. Escribí:
   `arma un diagrama de una veterinaria con dueños, mascotas y consultas`
8. **Tarda entre 15 y 45 segundos.** Revisá la propuesta y aplicala.
9. Comprobá que **lo aplicado sea exactamente lo propuesto** y que las clases no
   se pisen.

> Si el botón `Pedir` no aparece, la bandera `FORJA_IA_HABILITADA` no llegó al
> proceso. Con la bandera prendida pero Ollama apagado el botón **sí aparece** y
> el pedido devuelve vacío sin error: es el comportamiento previsto.

### 3.5 Las dos salidas

10. `Generar backend` → mirá el código de las cuatro capas y descargá el zip.
11. `XMI` → exportar. El archivo abre en Enterprise Architect.
12. `XMI` → importar, con un XMI exportado de EA.

### 3.6 El agente guía

13. Botón `Guía`. Tiene que haber consejos basados en lo que hiciste.
14. Preguntale **«cómo uso la aplicación»**. Tiene que contestar el camino
    proyecto → diagrama → modelo, no «esa no la sé contestar».
15. Probá `¿por dónde empiezo?`, `¿qué genera el backend?`, `¿cómo lo abro en
    Enterprise Architect?`.

---

## 4. Trabajo simultáneo

Lo que distingue a esta herramienta de un editor de diagramas cualquiera.

1. Abrí el mismo diagrama en **dos navegadores distintos** (o uno normal y uno
   de incógnito), cada uno con su cuenta. Invitá a la segunda desde
   `Invitar a colaborar`.
2. En el primero, empezá a editar una clase. En el segundo, **esa clase aparece
   tomada** y no se puede editar.
3. Movés una clase en uno → se mueve en el otro **al instante**, sin recargar.
4. El indicador `En vivo` de la barra tiene que estar en verde en los dos.

---

## 5. El móvil

```powershell
cd movil
flutter run              # con un teléfono conectado o un emulador abierto
```

**La dirección del servidor se escribe en la pantalla de entrada**, no está en
el código:

| Dónde corre | Dirección |
|---|---|
| Emulador de Android | `http://10.0.2.2:8080` |
| Teléfono real en la misma red | `http://<IP-de-tu-PC>:8080` (sale de `ipconfig`) |

Para el teléfono real el backend tiene que escuchar en todas las interfaces, no
solo en `localhost`.

**La prueba que importa es la de sin conexión:**

1. Abrí un diagrama con el teléfono conectado.
2. Activá el **modo avión**.
3. Seguí modelando: las operaciones se encolan.
4. Sacá el modo avión. La cola se reproduce y el diagrama se sincroniza.

---

## 6. Antes de la defensa

Checklist del día, en orden.

- [ ] **Cerrar los servidores de desarrollo de los OTROS proyectos.** Es el
      gemelo en RAM de la regla de la GPU, y muerde igual: el 20 de septiembre
      el sistema mató el backend y Vite por falta de memoria, con la máquina al
      14% libre. FORJA usaba 23 MB; **TDS y sus pruebas se llevaban ~2,7 GB** y
      Gemma otros 1,9. Se revisa así:

      ```powershell
      Get-Process node,java | Sort-Object WorkingSet64 -Descending |
        Select-Object -First 8 @{n='MB';e={[math]::Round($_.WorkingSet64/1MB)}}, ProcessName, Id
      ```

- [ ] `docker ps` — que `erp_postgres` **no** esté ocupando el 5433.
- [ ] **Cerrar el navegador y Wallpaper Engine** antes de arrancar el backend.
      La GPU tiene 4 GB y `gemma3:4b` ocupa 3,5: si no entra entero, Ollama lo
      reparte con la CPU y el pedido pasa de **15 s a 45 s**.
- [ ] Arrancar el backend y esperar a que el modelo cargue.
- [ ] `ollama ps` — la columna `PROCESSOR` tiene que decir **100% GPU**.
      Recién ahí abrir el navegador: el modelo ya está residente y no lo echa.
- [ ] Llevar el `.task`/modelo en un USB por si hay que rehacer algo.
- [ ] Bajar el **paquete de español** del reconocimiento de voz de Android y
      ensayar el dictado del móvil **en modo avión**.
- [ ] Tener las dos versiones a mano: la desplegada y la local.

> **La demo de IA se hace en la local, no en la desplegada.** En la EC2 la
> bandera de IA va apagada —`gemma3:4b` no entra en una instancia razonable— así
> que ahí el botón `Pedir` no aparece. Está previsto y es coherente con el plan
> de llevar dos versiones.

### Volver a medir el modelo

```powershell
python herramientas\medir-el-modelo.py
```

No necesita el backend ni la base: habla con Ollama directo, precalienta y da
tres vueltas con tiempos y cuántas relaciones salieron.

---

## 7. Si algo falla

| Síntoma | Causa casi segura |
|---|---|
| `Port 8080 was already in use` | El JVM viejo. Matalo (sección 0). |
| Los endpoints nuevos dan **404** | Lo mismo: está respondiendo el backend viejo. |
| `Bind for 0.0.0.0:5433 failed` | `erp_postgres`. Paralo. |
| `Could not resolve placeholder` | Quedó un `application.yml` en `target/test-classes/`. Borralo o corré `mvnw clean`. |
| El botón `Pedir` no aparece | `FORJA_IA_HABILITADA` no llegó al proceso. |
| El backend o Vite mueren solos | Falta de RAM. Cerrá los otros proyectos (sección 6). |
| El pedido tarda 45 s | El modelo no entró entero en la GPU. `ollama ps`. |
| El agente responde «no la sé contestar» | Reformulá; si es una pregunta razonable que no cubre, es una clave que falta en `Preguntas.java`. |
