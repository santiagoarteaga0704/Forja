-- =====================================================================
-- FORJA :: Herramienta CASE colaborativa
-- V2 - Registro de uso, para que el agente guia sepa que se probo
-- =====================================================================

-- La bitacora `operacion` ya cuenta por que via entro cada cambio del modelo
-- (LIENZO, VOZ, FOTO, IMPORTACION), asi que el agente puede notar por si solo
-- que alguien nunca dicto ni fotografio una pizarra.
--
-- Lo que la bitacora NO puede ver son las acciones que no modifican el modelo:
-- exportar un XMI, mirar el codigo generado, descargar el proyecto, preguntarle
-- algo al propio agente. Son justamente las que hay que ensenar, porque nadie
-- las descubre solo, y sin este registro el agente tendria que adivinar.
--
-- Se guarda por usuario y no por diagrama a proposito: aprender a usar la
-- herramienta se aprende una vez, no una vez por diagrama.

CREATE TABLE uso_herramienta (
    usuario_id  UUID        NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    herramienta VARCHAR(40) NOT NULL,
    veces       INTEGER     NOT NULL DEFAULT 1,
    primera_vez TIMESTAMPTZ NOT NULL DEFAULT now(),
    ultima_vez  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (usuario_id, herramienta)
);
