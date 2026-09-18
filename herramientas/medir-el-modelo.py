# -*- coding: utf-8 -*-
"""Mide cuanto tarda el traductor local, con el prompt real de TraductorOllama.

Existe porque el tiempo del pedido NO es una propiedad del codigo: depende de si
Gemma 3 4B entra entero en la VRAM. En la maquina donde se desarrollo -RTX 3050
de portatil, 4 GB- el modelo ocupa 3,5 GB, asi que con un navegador abierto no
entra y Ollama lo reparte con la CPU. Eso es la diferencia entre 15 y 45 segundos
delante de quien esta mirando.

ANTES DE UNA DEMOSTRACION: cerrar el navegador y todo lo que use GPU, correr esto,
y confirmar que `ollama ps` diga 100% GPU en la columna PROCESSOR.

    python herramientas/medir-el-modelo.py
    python herramientas/medir-el-modelo.py "arma un diagrama de una biblioteca"

No necesita el backend ni la base de datos: habla con Ollama directo. Si el
prompt cambia en TraductorOllama.promptPara, hay que copiarlo aca; esta duplicado
a proposito, para que medir no obligue a levantar la aplicacion entera.
"""
import json
import sys
import time
import urllib.error
import urllib.request

URL = "http://localhost:11434/api/generate"
MODELO = "gemma3:4b"

PROMPT = """Convertis un pedido en instrucciones para una herramienta de diagramas de clases UML.

Respondes SOLO con instrucciones, una por linea. Sin numerar, sin vinetas, sin
explicar y sin saludar. Copias las formas EXACTAMENTE como estan escritas abajo,
cambiando solo los NOMBRE. Si el pedido no se puede expresar con esas formas, no
respondes nada.

Para crear una clase y darle atributos (estas empiezan con "crea" o con "a "):
crea la clase NOMBRE
a NOMBRE agregale el atributo UNO de tipo texto
a NOMBRE agregale el atributo UNO de tipo entero obligatorio
a NOMBRE agregale el metodo HACER que devuelve entero

Para unir dos clases (estas NUNCA empiezan con "a ", empiezan con el nombre de la clase):
NOMBRE hereda de OTRO
NOMBRE tiene muchas OTROS
NOMBRE se compone de muchas OTROS
marca NOMBRE como abstracta

Reglas que no se rompen:
- Los unicos tipos que existen son: texto, entero, decimal, booleano, fecha, fechayhora.
  Nunca uses el nombre de una clase como tipo de un atributo.
- Si una clase se relaciona con otra, lo decis con "tiene muchas" o "se compone de
  muchas", NUNCA con un atributo.
- Creas TODAS las clases primero, y recien despues sus atributos y sus relaciones.
- Los nombres de clase van en singular y con la primera letra en mayuscula.

Clases que ya estan en el diagrama, usalas en vez de crearlas de nuevo: {existentes}

Pedido: {pedido}
"""

RELACIONES = (" tiene muchas ", " hereda de ", " se compone de ")


def llamar(cuerpo, espera):
    peticion = urllib.request.Request(
        URL, data=json.dumps(cuerpo).encode("utf-8"),
        headers={"Content-Type": "application/json"})
    arranque = time.time()
    with urllib.request.urlopen(peticion, timeout=espera) as respuesta:
        datos = json.loads(respuesta.read())
    return datos.get("response", ""), time.time() - arranque


def precalentar():
    """Lo mismo que hace CalentadorDeModelo al arrancar la aplicacion."""
    return llamar({"model": MODELO, "prompt": "", "stream": False,
                   "keep_alive": "30m"}, 400)[1]


def traducir(pedido):
    return llamar({
        "model": MODELO,
        "prompt": PROMPT.format(existentes="(el diagrama esta vacio)", pedido=pedido),
        "stream": False,
        "keep_alive": "30m",
        "options": {"temperature": 0},
    }, 300)


def main():
    pedido = sys.argv[1] if len(sys.argv) > 1 else \
        "arma un diagrama de una veterinaria con duenos, mascotas y consultas"

    try:
        print(f"precalentando {MODELO}...")
        print(f"  cargo en {precalentar():.1f} s\n")
    except urllib.error.URLError as e:
        print(f"Ollama no contesta en {URL}: {e}")
        print("Arrancalo con 'ollama serve' y bajate el modelo con "
              f"'ollama pull {MODELO}'.")
        return 1

    print(f"pedido: {pedido}\n")
    for vuelta in (1, 2, 3):
        texto, segundos = traducir(pedido)
        lineas = [l.strip() for l in texto.splitlines() if l.strip()]
        relaciones = [l for l in lineas if any(r in l for r in RELACIONES)]

        print(f"--- vuelta {vuelta}: {segundos:.1f} s, "
              f"{len(lineas)} lineas, {len(relaciones)} relaciones ---")
        if vuelta == 1:
            for linea in lineas:
                print("   " + linea)

    print("\nSi tardo mas de 35 s, el modelo no esta entero en la GPU.")
    print("Comproba con 'ollama ps': la columna PROCESSOR tiene que decir 100% GPU.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
