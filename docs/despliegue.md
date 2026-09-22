# Desplegar FORJA en AWS

Una instancia, un contenedor con la aplicación y otro con la base. La web viaja
dentro de la misma imagen que la API, así que hay **una sola dirección** y no
hay CORS que configurar.

La imagen se construye en la máquina de desarrollo y la instancia solo la baja.
No al revés: `npm` y Maven no entran cómodos en el gigabyte de una instancia
gratuita, y cuando entran tardan veinte minutos.

---

## Antes de empezar

- Cuenta de AWS.
- Una cuenta de Docker Hub (gratis) para publicar la imagen.
- Docker corriendo en tu máquina.

> **La imagen es pública y eso está bien:** no lleva ningún secreto adentro. La
> contraseña de la base, el secreto del JWT y la clave de Gemini se pasan como
> variables de entorno **al arrancar**, no se hornean. El repositorio ya es
> público, así que no se expone nada nuevo.

---

## 1. Publicar la imagen (en tu máquina)

```powershell
cd C:\dev\forja
docker login                                  # usuario de Docker Hub
docker build -t TU-USUARIO/forja:1 .
docker push TU-USUARIO/forja:1
```

Tarda unos minutos la primera vez. Cada vez que cambies el código, subís una
etiqueta nueva (`:2`, `:3`…) — no reuses la misma, o la instancia se queda con
la vieja en caché.

## 2. Crear la instancia (consola de AWS)

`EC2` → `Lanzar instancia`:

| Campo | Valor |
|---|---|
| Nombre | `forja` |
| Imagen (AMI) | **Amazon Linux 2023** |
| Tipo | **t3.micro** (mirá que diga *elegible para capa gratuita*) |
| Par de claves | `Continuar sin par de claves` — se entra por el navegador |
| Almacenamiento | 20 GB |

En **Configuración de red** → `Editar` → `Agregar regla de grupo de seguridad`:

| Tipo | Puerto | Origen |
|---|---|---|
| SSH | 22 | Mi IP |
| **HTTP** | **80** | **Cualquier lugar (0.0.0.0/0)** |

> Sin la regla del 80 la instancia arranca, el contenedor arranca, y la página
> no abre desde ningún lado. Es el error más común y no avisa: el navegador
> queda cargando.

Lanzá la instancia y anotá su **IPv4 pública**.

## 3. Entrar

Seleccionás la instancia → botón `Conectar` → pestaña **EC2 Instance Connect**
→ `Conectar`. Se abre una terminal en el navegador. No hace falta SSH ni claves.

## 4. Preparar la instancia

```bash
sudo dnf update -y
sudo dnf install -y docker
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user

# El plugin de compose no viene con el paquete de docker.
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -sSL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# Memoria de intercambio: la instancia tiene 1 GB y corren la JVM y Postgres.
# Sin esto, el sistema mata una de las dos en cualquier pico y el corte llega
# sin aviso.
sudo dd if=/dev/zero of=/swapfile bs=1M count=2048
sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

**Cerrá la terminal y volvé a entrar**, o el grupo `docker` no toma efecto y
todo pide `sudo`.

## 5. Desplegar

```bash
mkdir ~/forja && cd ~/forja

curl -sSLO https://raw.githubusercontent.com/santiagoarteaga0704/Forja/master/compose.nube.yml

cat > .env <<'FIN'
FORJA_IMAGEN=TU-USUARIO/forja:1
FORJA_DB_PASSWORD=poner-una-contrasena-larga
FORJA_JWT_SECRETO=poner-un-secreto-de-32-caracteres-o-mas
FORJA_VISION_HABILITADA=true
FORJA_VISION_CLAVE=la-clave-de-gemini
FIN
chmod 600 .env

docker compose -f compose.nube.yml up -d
docker compose -f compose.nube.yml logs -f app
```

**Está listo cuando** el log dice `Started ForjaBackendApplication`. `Ctrl+C`
corta el seguimiento del log, no la aplicación.

## 6. Abrir

```
http://LA-IP-PUBLICA
```

Registrás una cuenta y ya está.

---

## Qué funciona allá y qué no

| | En la nube | Local |
|---|---|---|
| Modelar, colaborar en vivo, XMI, generar backend | sí | sí |
| **Leer una foto** (modelo de visión remoto) | **sí** | sí |
| Pedido por IA y agente (Gemma con Ollama) | **no** | sí |
| Dictado por micrófono | **no** (necesita HTTPS) | sí, en Edge |

Las dos ausencias son deliberadas y se explican solas:

- **Ollama** ocupa más que la instancia entera. En la nube el botón `Pedir` no
  aparece, y la aplicación es la de siempre sin él.
- **El micrófono** necesita un contexto seguro: los navegadores no dan acceso al
  audio sobre `http://`. Con un dominio y un certificado funcionaría; sin eso,
  el dictado **escrito** anda igual, porque es el mismo analizador.

Por eso conviene llevar las dos versiones a la defensa: la desplegada para
mostrar que está en la nube, y la local para las dos funciones que necesitan la
máquina.

---

## Actualizar después de un cambio

En tu máquina:

```powershell
docker build -t TU-USUARIO/forja:2 .
docker push TU-USUARIO/forja:2
```

En la instancia:

```bash
cd ~/forja
sed -i 's|forja:1|forja:2|' .env
docker compose -f compose.nube.yml up -d
```

## Si algo falla

| Síntoma | Causa casi segura |
|---|---|
| La página no abre y el navegador queda cargando | Falta la regla del puerto 80 en el grupo de seguridad. |
| `permission denied` al usar docker | No volviste a entrar después del `usermod`. |
| El contenedor se reinicia solo | Falta memoria. Comprobá el swap con `free -h`. |
| `falta FORJA_IMAGEN en el .env` | El `.env` tiene que estar en el mismo directorio que el compose. |
| Sale la versión vieja tras actualizar | Reusaste la etiqueta. Subí una nueva y cambiá el `.env`. |

Ver qué está pasando:

```bash
docker compose -f compose.nube.yml ps
docker compose -f compose.nube.yml logs --tail 50 app
free -h
```
