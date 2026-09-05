-- =====================================================================
-- FORJA :: Herramienta CASE colaborativa
-- V1 - Esquema inicial: usuarios, proyectos, modelo UML, colaboracion
-- =====================================================================

-- ---------- Usuarios y proyectos -------------------------------------

CREATE TABLE usuario (
    id            UUID PRIMARY KEY,
    email         VARCHAR(180) NOT NULL UNIQUE,
    password_hash VARCHAR(120) NOT NULL,
    nombre        VARCHAR(120) NOT NULL,
    activo        BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE proyecto (
    id             UUID PRIMARY KEY,
    nombre         VARCHAR(150) NOT NULL,
    descripcion    TEXT,
    propietario_id UUID NOT NULL REFERENCES usuario (id) ON DELETE RESTRICT,
    creado_en      TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_proyecto_propietario ON proyecto (propietario_id);

-- Membresia: habilita el trabajo colaborativo sobre un mismo proyecto
CREATE TABLE proyecto_miembro (
    proyecto_id UUID NOT NULL REFERENCES proyecto (id) ON DELETE CASCADE,
    usuario_id  UUID NOT NULL REFERENCES usuario (id)  ON DELETE CASCADE,
    rol         VARCHAR(20) NOT NULL,
    invitado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (proyecto_id, usuario_id),
    CONSTRAINT ck_miembro_rol CHECK (rol IN ('PROPIETARIO', 'EDITOR', 'LECTOR'))
);
CREATE INDEX idx_miembro_usuario ON proyecto_miembro (usuario_id);

-- ---------- Modelo UML ------------------------------------------------

CREATE TABLE diagrama (
    id             UUID PRIMARY KEY,
    proyecto_id    UUID NOT NULL REFERENCES proyecto (id) ON DELETE CASCADE,
    nombre         VARCHAR(150) NOT NULL,
    tipo           VARCHAR(20)  NOT NULL,
    -- Version monotona del diagrama: base del control de concurrencia
    -- optimista y del ordenamiento de operaciones en la sincronizacion.
    version        BIGINT       NOT NULL DEFAULT 0,
    creado_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_diagrama_tipo CHECK (tipo IN ('CLASES', 'SECUENCIA'))
);
CREATE INDEX idx_diagrama_proyecto ON diagrama (proyecto_id);

CREATE TABLE clase_uml (
    id           UUID PRIMARY KEY,
    diagrama_id  UUID NOT NULL REFERENCES diagrama (id) ON DELETE CASCADE,
    nombre       VARCHAR(120) NOT NULL,
    estereotipo  VARCHAR(60),
    es_abstracta BOOLEAN NOT NULL DEFAULT FALSE,
    -- Geometria en el lienzo colaborativo
    pos_x        DOUBLE PRECISION NOT NULL DEFAULT 0,
    pos_y        DOUBLE PRECISION NOT NULL DEFAULT 0,
    ancho        DOUBLE PRECISION NOT NULL DEFAULT 200,
    alto         DOUBLE PRECISION NOT NULL DEFAULT 120,
    creado_en    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_clase_nombre_diagrama UNIQUE (diagrama_id, nombre)
);
CREATE INDEX idx_clase_diagrama ON clase_uml (diagrama_id);

CREATE TABLE atributo_uml (
    id               UUID PRIMARY KEY,
    clase_id         UUID NOT NULL REFERENCES clase_uml (id) ON DELETE CASCADE,
    nombre           VARCHAR(120) NOT NULL,
    tipo             VARCHAR(80)  NOT NULL,
    visibilidad      VARCHAR(12)  NOT NULL DEFAULT 'PRIVADO',
    -- Metadatos que consume el generador de codigo Spring Boot
    es_identificador BOOLEAN NOT NULL DEFAULT FALSE,
    es_requerido     BOOLEAN NOT NULL DEFAULT FALSE,
    es_unico         BOOLEAN NOT NULL DEFAULT FALSE,
    longitud         INTEGER,
    valor_defecto    VARCHAR(120),
    orden            INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_atributo_nombre_clase UNIQUE (clase_id, nombre),
    CONSTRAINT ck_atributo_visibilidad CHECK (visibilidad IN ('PUBLICO', 'PRIVADO', 'PROTEGIDO', 'PAQUETE'))
);
CREATE INDEX idx_atributo_clase ON atributo_uml (clase_id);

CREATE TABLE metodo_uml (
    id           UUID PRIMARY KEY,
    clase_id     UUID NOT NULL REFERENCES clase_uml (id) ON DELETE CASCADE,
    nombre       VARCHAR(120) NOT NULL,
    tipo_retorno VARCHAR(80) NOT NULL DEFAULT 'void',
    visibilidad  VARCHAR(12) NOT NULL DEFAULT 'PUBLICO',
    es_abstracto BOOLEAN NOT NULL DEFAULT FALSE,
    es_estatico  BOOLEAN NOT NULL DEFAULT FALSE,
    orden        INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_metodo_visibilidad CHECK (visibilidad IN ('PUBLICO', 'PRIVADO', 'PROTEGIDO', 'PAQUETE'))
);
CREATE INDEX idx_metodo_clase ON metodo_uml (clase_id);

CREATE TABLE parametro_uml (
    id        UUID PRIMARY KEY,
    metodo_id UUID NOT NULL REFERENCES metodo_uml (id) ON DELETE CASCADE,
    nombre    VARCHAR(120) NOT NULL,
    tipo      VARCHAR(80)  NOT NULL,
    orden     INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_parametro_nombre_metodo UNIQUE (metodo_id, nombre)
);
CREATE INDEX idx_parametro_metodo ON parametro_uml (metodo_id);

CREATE TABLE relacion_uml (
    id                    UUID PRIMARY KEY,
    diagrama_id           UUID NOT NULL REFERENCES diagrama (id)  ON DELETE CASCADE,
    origen_id             UUID NOT NULL REFERENCES clase_uml (id) ON DELETE CASCADE,
    destino_id            UUID NOT NULL REFERENCES clase_uml (id) ON DELETE CASCADE,
    tipo                  VARCHAR(20) NOT NULL,
    -- Multiplicidad en notacion UML: 1, 0..1, 1..*, *
    multiplicidad_origen  VARCHAR(10) NOT NULL DEFAULT '1',
    multiplicidad_destino VARCHAR(10) NOT NULL DEFAULT '1',
    rol_origen            VARCHAR(120),
    rol_destino           VARCHAR(120),
    etiqueta              VARCHAR(150),
    CONSTRAINT ck_relacion_tipo CHECK (tipo IN
        ('ASOCIACION', 'AGREGACION', 'COMPOSICION', 'HERENCIA', 'DEPENDENCIA', 'REALIZACION')),
    -- Una clase no puede heredar ni realizarse a si misma; la
    -- autoasociacion, en cambio, es legitima en UML.
    CONSTRAINT ck_relacion_no_refleja CHECK (
        origen_id <> destino_id OR tipo NOT IN ('HERENCIA', 'REALIZACION'))
);
CREATE INDEX idx_relacion_diagrama ON relacion_uml (diagrama_id);
CREATE INDEX idx_relacion_origen   ON relacion_uml (origen_id);
CREATE INDEX idx_relacion_destino  ON relacion_uml (destino_id);

-- ---------- Colaboracion: exclusion mutua ----------------------------
-- Un elemento del diagrama solo puede estar bloqueado por un usuario a
-- la vez. La unicidad de (elemento_tipo, elemento_id) delega la
-- exclusion mutua al motor de base de datos, evitando condiciones de
-- carrera entre instancias del backend.

CREATE TABLE bloqueo_elemento (
    id            UUID PRIMARY KEY,
    diagrama_id   UUID NOT NULL REFERENCES diagrama (id) ON DELETE CASCADE,
    elemento_tipo VARCHAR(20) NOT NULL,
    elemento_id   UUID NOT NULL,
    usuario_id    UUID NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    sesion_id     VARCHAR(80) NOT NULL,
    adquirido_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Vencimiento: libera bloqueos huerfanos si el cliente se desconecta
    -- sin liberar explicitamente (caida de red, cierre abrupto).
    expira_en     TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_bloqueo_elemento UNIQUE (elemento_tipo, elemento_id),
    CONSTRAINT ck_bloqueo_tipo CHECK (elemento_tipo IN ('CLASE', 'RELACION', 'DIAGRAMA'))
);
CREATE INDEX idx_bloqueo_diagrama ON bloqueo_elemento (diagrama_id);
CREATE INDEX idx_bloqueo_expira   ON bloqueo_elemento (expira_en);

-- ---------- Bitacora de operaciones: sincronizacion offline ----------
-- Cada cambio sobre el modelo se registra como una operacion con numero
-- de secuencia por diagrama. El cliente movil que estuvo sin conexion
-- pide las operaciones posteriores a su ultima secuencia conocida y
-- reproduce unicamente el delta.

CREATE TABLE operacion (
    id            UUID PRIMARY KEY,
    diagrama_id   UUID NOT NULL REFERENCES diagrama (id) ON DELETE CASCADE,
    secuencia     BIGINT NOT NULL,
    usuario_id    UUID NOT NULL REFERENCES usuario (id) ON DELETE RESTRICT,
    tipo          VARCHAR(40) NOT NULL,
    -- Carga util del cambio; JSONB permite consultar el historial
    carga         JSONB NOT NULL,
    -- Identificador generado por el cliente: garantiza idempotencia al
    -- reintentar el envio de una operacion encolada sin conexion.
    token_cliente VARCHAR(80) NOT NULL,
    -- Trazabilidad del canal de entrada, exigida por la fundamentacion:
    -- permite medir cuanto del modelo se construyo por voz o por foto.
    origen        VARCHAR(20) NOT NULL DEFAULT 'LIENZO',
    creada_en     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_operacion_secuencia UNIQUE (diagrama_id, secuencia),
    CONSTRAINT uq_operacion_token     UNIQUE (diagrama_id, token_cliente),
    CONSTRAINT ck_operacion_origen CHECK (origen IN ('LIENZO', 'VOZ', 'FOTO', 'IMPORTACION', 'AGENTE'))
);
CREATE INDEX idx_operacion_diagrama_sec ON operacion (diagrama_id, secuencia);
