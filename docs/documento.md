# UNIVERSIDAD GABRIEL RENÉ MORENO
## FACULTAD DE CIENCIAS DE LA COMPUTACIÓN

# PARCIAL - 1

## FORJA: HERRAMIENTA CASE COLABORATIVA PARA LA FASE DE DISEÑO, CON DICTADO POR VOZ, LECTURA DE PIZARRA, GENERACIÓN DE BACKEND SPRING BOOT E INTERCAMBIO XMI CON ENTERPRISE ARCHITECT

**ESTUDIANTE:**

| Nombre | Registro |
|---|---|
| ARTEAGA SILVA GEIMBERT SANTIAGO | 223041688 |

**DOCENTE:** M.Sc. MARTÍNEZ CANEDO ROLANDO ANTONIO

**MATERIA:** INGENIERÍA DE SOFTWARE 1

SANTA CRUZ - BOLIVIA

---

# 1) Perfil

## 1.1 Introducción

El diseño de un sistema rara vez nace en una herramienta de modelado. Nace en una
pizarra, entre varias personas, en lenguaje natural y a mano alzada. Lo que queda
de esa reunión es una fotografía que alguien debe transcribir después a un
diagrama formal, y ese diagrama a código. Cada transcripción es una oportunidad de
perder información y de que se separen tres cosas que deberían coincidir: lo que
el equipo decidió, lo que quedó documentado y lo que finalmente se programó.

Las herramientas CASE actuales resuelven bien el tramo central de ese recorrido
—permiten dibujar el modelo y algunas generan código— pero dejan los dos extremos
afuera. No recogen lo que se dijo ni lo que se dibujó a mano, y su trabajo
colaborativo es limitado: en la práctica una persona modela y las demás miran.

FORJA aborda el recorrido completo. Es una herramienta CASE colaborativa que
permite que varias personas construyan el mismo diagrama de clases UML al mismo
tiempo, por tres vías distintas —dibujándolo, dictándolo en castellano o
fotografiando una pizarra— y que conecta ese modelo con su implementación: genera
un proyecto Spring Boot ejecutable e intercambia el modelo con Enterprise
Architect mediante XMI 2.5.1, en los dos sentidos.

El sistema incorpora además un agente guía embebido que observa el uso de la
herramienta y el estado del modelo para enseñar a usarla y señalar problemas de
diseño antes de que lleguen al código generado.

## 1.2 Objetivo General

Desarrollar una herramienta CASE colaborativa que permita construir diagramas de
clases UML entre varias personas en tiempo real —mediante edición gráfica,
dictado por voz y reconocimiento de fotografías de pizarra—, generar
automáticamente a partir de ellos un backend Spring Boot ejecutable e
intercambiar el modelo con Enterprise Architect a través de XMI 2.5.1.

## 1.3 Objetivos Específicos

- Implementar un sistema de autenticación sin estado que permita registrar
  usuarios, iniciar y cerrar sesión, y trabajar desde el cliente web y el cliente
  móvil con la misma credencial.
- Desarrollar la gestión de proyectos y diagramas, con invitación de
  colaboradores por correo y permisos otorgados a nivel de proyecto.
- Construir un lienzo de edición gráfica de diagramas de clases con la notación
  UML 2.5.1 correcta.
- Garantizar la edición concurrente del mismo diagrama por varias personas
  mediante exclusión mutua sobre cada elemento y sincronización en tiempo real.
- Implementar el dictado por voz mediante una gramática determinista en
  castellano que convierta frases en operaciones del modelo.
- Integrar el reconocimiento de fotografías de pizarra para incorporar al modelo
  un diagrama dibujado a mano.
- Incorporar un modelo de lenguaje local que traduzca pedidos en lenguaje libre a
  operaciones válidas del modelo, con revisión previa antes de aplicarlas.
- Desarrollar un agente guía basado en reglas que observe el modelo y el uso de
  la herramienta para enseñar a usarla y advertir problemas de diseño.
- Generar automáticamente, a partir del modelo, un proyecto Spring Boot de cuatro
  capas que compile.
- Implementar la exportación e importación de modelos en XMI 2.5.1 compatible con
  Enterprise Architect.
- Desarrollar un cliente móvil Android capaz de modelar sin conexión y
  sincronizar los cambios al recuperarla.

## 1.4 Descripción del problema

El trabajo de diseño de software presenta hoy cuatro problemas concretos:

**La transcripción manual pierde información.** Entre la pizarra y la herramienta
de modelado hay una persona copiando, y entre la herramienta y el código hay otra
escribiendo. Cada paso introduce diferencias que nadie vuelve a revisar.

**El modelado es individual aunque el diseño sea colectivo.** Las herramientas
tradicionales operan sobre un archivo. Dos personas no pueden editarlo a la vez
sin que una sobrescriba a la otra, de modo que el diseño lo discute el equipo
pero lo transcribe una sola persona.

**La entrada es exclusivamente gráfica.** Para agregar una clase hay que
dibujarla, aunque decir «Paciente tiene muchas Consultas» sea más rápido y más
cercano a cómo se discute un modelo.

**Los errores de diseño se descubren tarde.** Una clase sin atributos, una
interfaz con atributos o una jerarquía con atributos repetidos son problemas que
la herramienta podría señalar en el momento, pero que habitualmente se descubren
cuando el código ya está escrito.

## 1.5 Alcance

**Módulo de Gestión de Usuarios.** Registro, inicio y cierre de sesión mediante
credenciales propias con contraseña cifrada. La sesión es sin estado, lo que
permite que el cliente móvil trabaje sin conexión sin depender de una sesión
viva en el servidor.

**Módulo de Gestión de Proyectos y Diagramas.** Creación de proyectos, creación
de diagramas dentro de un proyecto, e invitación de colaboradores por correo. Los
permisos se otorgan por proyecto y no por diagrama.

**Módulo de Edición Gráfica.** Lienzo con notación UML 2.5.1: clases con
atributos y operaciones, visibilidades, clases abstractas, interfaces, y los seis
tipos de relación con sus multiplicidades.

**Módulo de Edición Colaborativa.** Varias personas sobre el mismo diagrama en
tiempo real, con exclusión mutua sobre cada elemento arbitrada por la base de
datos y difusión inmediata de los cambios aceptados.

**Módulo de Dictado por Voz.** Gramática determinista en castellano que convierte
frases habladas o escritas en operaciones del modelo, funcionando también sin
conexión en el cliente móvil.

**Módulo de Lectura de Pizarra.** Reconocimiento óptico de una fotografía de un
diagrama dibujado a mano, ejecutado en el propio navegador.

**Módulo de Inteligencia Artificial Generativa.** Modelo de lenguaje local que
traduce un pedido en lenguaje libre a operaciones del modelo, con revisión previa
obligatoria antes de aplicarlas.

**Módulo de Agente Guía.** Sistema experto de reglas que observa el modelo y el
uso de la herramienta, enseña a usarla y advierte problemas de diseño.

**Módulo de Generación de Backend.** Producción automática de un proyecto Spring
Boot con cuatro capas por cada clase concreta del modelo.

**Módulo de Intercambio XMI.** Exportación e importación de modelos en XMI 2.5.1
compatible con Enterprise Architect, preservando la disposición del diagrama.

**Módulo Móvil.** Cliente Android que permite modelar sin conexión, encolando las
operaciones y sincronizándolas al recuperar la red.

### Fuera del alcance

- Otros diagramas UML: solo se modela el diagrama de clases.
- Generación del cliente frontend: se genera el backend, no la interfaz.
- Ingeniería inversa desde código existente hacia el modelo.

---

# Parte I — Fundamentación Teórica

## 1.1.1 Ingeniería de Software Asistida por Computadora (CASE)

*Computer-Aided Software Engineering* designa al conjunto de herramientas que
asisten las actividades del proceso de desarrollo de software. La clasificación
habitual las ordena según la fase que cubren:

- **Upper-CASE**: asisten las fases tempranas —análisis y diseño—. Modelado,
  diagramación, especificación de requisitos.
- **Lower-CASE**: asisten la construcción y el mantenimiento. Generación de
  código, ingeniería inversa, pruebas.
- **I-CASE** *(integrated)*: cubren el ciclo completo sobre un repositorio común.

**FORJA es una herramienta upper-CASE que cruza deliberadamente hacia
lower-CASE.** Su objeto de trabajo es el modelo de diseño, pero no se detiene
ahí: genera la estructura de la implementación. Esa frontera es precisamente
donde ocurre la pérdida de información que el proyecto busca eliminar.

Las capacidades que caracterizan a una herramienta CASE, y cómo las cumple FORJA:

| Capacidad | En FORJA |
|---|---|
| Repositorio central del modelo | PostgreSQL; el modelo no vive en archivos sueltos |
| Edición gráfica del modelo | Lienzo SVG con notación UML 2.5.1 |
| Verificación de consistencia | Agente de reglas que detecta problemas del modelo |
| Generación de código | Proyecto Spring Boot de cuatro capas por clase |
| Intercambio con otras herramientas | XMI 2.5.1, en los dos sentidos |
| Trabajo en equipo | Exclusión mutua y sincronización en tiempo real |
| Trazabilidad | Bitácora de operaciones con su origen |

## 1.1.2 Desarrollo de Software basado en Componentes

El desarrollo basado en componentes propone construir sistemas ensamblando
unidades de software con interfaces bien definidas, reemplazables y
reutilizables, en lugar de escribir cada sistema desde cero. Szyperski define un
componente como una unidad de composición con interfaces especificadas
contractualmente y dependencias explícitas de contexto, desplegable de forma
independiente.

Este proyecto **reutiliza componentes y produce componentes**, y conviene
distinguir las dos cosas.

### Componentes que FORJA reutiliza

| Componente | Función que aporta |
|---|---|
| Spring Boot 4.1.1 | Contenedor, inyección de dependencias, servidor web |
| Spring Security + Nimbus | Firma y validación de credenciales JWT |
| Hibernate / JPA | Mapeo objeto-relacional |
| Flyway | Versionado reproducible del esquema de la base |
| PostgreSQL 17 | Persistencia y arbitraje de la concurrencia |
| React 19 + Vite | Cliente web |
| Tesseract.js | Reconocimiento óptico, ejecutado en el navegador |
| Flutter | Cliente móvil Android |
| Ollama + Gemma 3 | Ejecución local del modelo de lenguaje |

La criptografía, el mapeo objeto-relacional y el reconocimiento óptico no se
implementaron: son problemas resueltos, y reescribirlos habría consumido el
tiempo que el proyecto necesitaba para lo que sí es propio.

### Componentes que FORJA produce

El generador no emite un archivo suelto: produce un **proyecto ensamblado por
componentes**, con cuatro capas por cada clase concreta del modelo.

```
Clase «Paciente» del modelo
        │
        ├── dominio/Paciente.java              entidad JPA
        ├── repositorio/PacienteRepositorio    acceso a datos
        ├── servicio/PacienteServicio          lógica de negocio
        └── web/PacienteControlador            interfaz REST
```

Cada capa depende únicamente de la inmediatamente inferior, y esa dependencia se
declara por constructor. El resultado compila y se ejecuta, y sus piezas pueden
sustituirse individualmente: es un ensamblado de componentes, no un volcado de
texto.

### El mantenimiento del software

El desarrollo basado en componentes se justifica en buena medida por su efecto
sobre el mantenimiento, que es la fase más larga y costosa del ciclo de vida. Se
distinguen cuatro tipos:

**Mantenimiento correctivo.** Corrige defectos detectados después de la entrega.
En este proyecto puede ejemplificarse con los defectos hallados al usar la
herramienta el 20 de septiembre: el paso de revisión del pedido no ataba, el
agente devolvía error al consultarlo, y la importación XMI perdía la disposición
del diagrama. Los tres se corrigieron con una prueba que los reproduce primero.

**Mantenimiento adaptativo.** Ajusta el sistema a cambios de su entorno. El caso
del proyecto es el traductor de lenguaje natural: se diseñó inicialmente para
ejecutarse dentro del cliente móvil y se adaptó a un servicio local invocado
desde el servidor cuando la medición mostró que el modelo pequeño no daba
resultados utilizables en el teléfono.

**Mantenimiento preventivo.** Modifica el sistema para evitar fallos futuros. El
ejemplo del proyecto es la separación de la base de datos de pruebas: la suite
vaciaba las tablas de la base de desarrollo, y aunque eso no era un defecto del
producto, garantizaba una pérdida de datos futura.

**Mantenimiento perfectivo.** Mejora características existentes sin corregir un
fallo. El rediseño de la interfaz y la ampliación del agente guía para que
responda consultando el estado real pertenecen a esta categoría.

## 1.1.3 Ingeniería Humana (Experiencia del Usuario)

La ingeniería humana estudia cómo debe comportarse un sistema para que las
personas puedan usarlo sin esfuerzo innecesario. En una herramienta CASE el
problema es particular: quien la usa está concentrado en un problema de diseño, y
toda atención que la herramienta le exija para sí misma es atención que se resta
del modelo.

Las decisiones de este proyecto en esa dirección:

**Una sola superficie clara, y es el modelo.** Toda la aplicación es oscura salvo
el diagrama, que se presenta como una hoja de papel de ingeniería. La metáfora es
explícita —la aplicación es la mesa de dibujo y el diagrama es la hoja apoyada
encima— y de ella se desprende el resto: los instrumentos no compiten con el
contenido.

**El color solo se usa cuando significa algo.** Hay exactamente dos colores con
significado: uno indica que un elemento está vivo, es propio o está seleccionado;
el otro, que otra persona lo tiene tomado. Un tercer color decorativo destruiría
la utilidad de los dos primeros.

**Los errores no dejan a la persona detenida.** El agente guía no falla ante
ninguna entrada; el pedido al modelo de lenguaje degrada a una respuesta vacía en
lugar de a un error; una credencial que dejó de ser válida devuelve a la pantalla
de ingreso en vez de mostrar un mensaje técnico.

**Lo que el modelo propone se revisa antes de aplicarse.** Un modelo de lenguaje
de tamaño reducido se equivoca, de modo que su propuesta se muestra y una persona
decide. Esta decisión es a la vez de experiencia de usuario y de arquitectura.

**Nada se marca como hecho sin evidencia.** El recorrido de aprendizaje que
presenta el agente no avanza por haber visitado una pantalla, sino por haber
realizado la acción, verificada contra la bitácora.

## 1.1.4 Inteligencia Artificial aplicada al desarrollo de software

La disciplina distingue dos grandes paradigmas:

**IA simbólica.** El conocimiento se representa explícitamente como hechos y
reglas, y un motor de inferencia deriva conclusiones. Es el paradigma de los
sistemas expertos. Su propiedad característica es que las conclusiones son
auditables y reproducibles: puede señalarse qué regla se aplicó.

**IA sub-simbólica o conexionista.** El conocimiento está distribuido en los
parámetros de un modelo entrenado sobre datos. Es el paradigma de las redes
neuronales y de los modelos de lenguaje. Su propiedad característica es la
capacidad de generalizar a entradas no previstas; su contrapartida, que la misma
entrada puede producir salidas distintas y no hay una regla que exhibir.

**FORJA implementa los dos, deliberadamente separados según lo que cada uno hace
bien.**

| Paradigma | Dónde | Para qué |
|---|---|---|
| Simbólico | Agente guía: 24 reglas y 17 respuestas escritas | Razonar sobre el modelo y enseñar la herramienta |
| Generativo | Traducción de lenguaje libre a operaciones | Entender lo que una persona escribe o dice |

El criterio que ordena la separación es que **las reglas mandan, y el modelo
generativo interviene únicamente donde las reglas no llegan.** Para saber que una
interfaz no puede declarar atributos no hace falta inferir nada: hace falta
conocer la regla. Un modelo de lenguaje lo diría correctamente casi siempre, y
ese «casi» no es defendible en una herramienta cuya función es enseñar.

La decisión se sustenta en una medición y no en una preferencia. Consultado el
mismo pedido varias veces con el parámetro de temperatura en cero, Gemma 3 4B
produce propuestas distintas en cada ejecución. Por eso el modelo **propone y una
persona revisa**, y por eso lo que la herramienta debe afirmar con certeza lo
afirman las reglas.

> Conviene precisar dos puntos que suelen confundirse. **El generador de código no
> utiliza inteligencia artificial**: es un traductor determinista con reglas de
> mapeo, y esa es su virtud, porque el proyecto generado compila siempre y es
> reproducible. **El reconocimiento de voz tampoco**: la conversión de voz a texto
> la realiza el navegador o el sistema Android. Lo que FORJA implementa es la
> conversión de texto a operaciones del modelo.

## 1.1.5 El Proceso Unificado de Desarrollo de Software (PUDS)

El Proceso Unificado fue formulado por Jacobson, Booch y Rumbaugh —los mismos
autores que unificaron UML— y se define por tres características:

**Dirigido por casos de uso.** Los requisitos se expresan como interacciones
observables entre actores y el sistema, y esos casos guían el diseño, la
implementación y las pruebas.

**Centrado en la arquitectura.** La arquitectura se establece temprano, en una
versión ejecutable y reducida, atacando primero los riesgos técnicos mayores.

**Iterativo e incremental.** El desarrollo avanza en iteraciones que producen
incrementos ejecutables del producto, no documentos intermedios.

El proceso organiza el trabajo en cuatro fases —Inicio, Elaboración,
Construcción y Transición— y en cinco flujos de trabajo que atraviesan todas las
fases en proporciones distintas: captura de requisitos, análisis, diseño,
implementación y pruebas. La Parte II de este documento sigue esos flujos.

## 1.1.6 UML 2.5.1

UML nació de la unificación de tres métodos de modelado orientado a objetos
desarrollados de forma independiente a finales de los años ochenta: el método de
**Grady Booch**, la *Object Modeling Technique* de **James Rumbaugh**, y
*Objectory* / OOSE de **Ivar Jacobson**. La unificación se formalizó en Rational
Software y el resultado fue adoptado por el *Object Management Group* (OMG) como
estándar en 1997. La versión vigente es **UML 2.5.1** (OMG, diciembre de 2017), y
es la que FORJA implementa.

El **diagrama de clases** es el diagrama estructural central del lenguaje:
describe los tipos del sistema, sus características y las relaciones entre ellos.
Los elementos que FORJA modela:

| Elemento UML | Representación |
|---|---|
| Clase | Rectángulo con tres compartimentos: nombre, atributos, operaciones |
| Clase abstracta | Nombre en cursiva |
| Interfaz | Estereotipo `«interface»` |
| Atributo | `visibilidad nombre: Tipo` |
| Operación | `visibilidad nombre(parámetros): TipoRetorno` |
| Visibilidad | `+` público, `-` privado, `#` protegido, `~` paquete |
| Asociación | Línea con multiplicidades en ambos extremos |
| Agregación | Rombo hueco en el extremo del todo |
| Composición | Rombo lleno en el extremo del todo |
| Generalización | Triángulo hueco apuntando a la superclase |
| Realización | Triángulo hueco con línea discontinua |
| Dependencia | Flecha abierta con línea discontinua |
| Multiplicidad | `1`, `0..1`, `0..*`, `1..*`, `n..m` |

La distinción entre **agregación y composición** merece mención porque es la que
más consecuencias tiene en el código generado: la composición implica que la parte
no existe sin el todo, y el generador la traduce con borrado en cascada
(`cascade = CascadeType.ALL, orphanRemoval = true`), mientras que la agregación no
lo hace.

### XMI: el intercambio entre herramientas

*XML Metadata Interchange* es el estándar del OMG para serializar modelos
conformes a MOF —y por lo tanto modelos UML— en XML. Su propósito es que un
modelo construido en una herramienta pueda abrirse en otra sin pérdida.

El intercambio real presenta dos dificultades que el estándar no resuelve, y que
este proyecto tuvo que atender:

**El estándar admite varias formas de expresar lo mismo.** El tipo de un atributo
puede venir como referencia `href` a la biblioteca de tipos primitivos, como
`xmi:idref` a un tipo declarado en el documento, o como atributo `type` directo.
Las herramientas usan las tres; el importador de FORJA acepta las tres.

**La disposición visual viaja fuera del modelo.** XMI serializa el modelo, no el
dibujo. Enterprise Architect deposita la geometría en un bloque de extensión
propio con la posición de cada elemento. Una herramienta que ignore ese bloque
importa el modelo correcto pero con todas las clases amontonadas.

## 1.1.7 Spring Boot

Spring Boot es una extensión del framework Spring orientada a reducir la
configuración necesaria para poner en marcha una aplicación. Se apoya en tres
mecanismos:

**Inversión de control e inyección de dependencias.** Los objetos no construyen
sus colaboradores: los reciben, y el contenedor resuelve el grafo de
dependencias. La consecuencia práctica, y la que importa para este proyecto, es
que cada pieza puede probarse sustituyendo sus colaboradores por dobles de
prueba.

**Autoconfiguración.** El framework inspecciona el *classpath* y configura lo que
encuentra, permitiendo sobrescribir cualquier decisión.

**Starters.** Agrupaciones de dependencias coherentes entre sí. FORJA utiliza los
de `web`, `data-jpa`, `security`, `oauth2-resource-server`, `websocket`,
`validation` y `flyway`.

La versión utilizada es **Spring Boot 4.1.1 sobre Java 21**, y es además la
tecnología que el generador de código produce.
