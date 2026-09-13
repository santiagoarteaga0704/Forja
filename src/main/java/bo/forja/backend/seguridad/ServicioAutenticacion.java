package bo.forja.backend.seguridad;

import bo.forja.backend.dominio.Usuario;
import bo.forja.backend.repositorio.UsuarioRepositorio;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Registro de cuentas y emision de tokens.
 * <p>
 * El token lleva en el sujeto el identificador del usuario y no su correo.
 * La diferencia importa: el identificador es inmutable y es la clave con
 * la que el resto del sistema comprueba la pertenencia a un proyecto,
 * mientras que un correo puede cambiar y dejaria tokens apuntando a nadie.
 */
@Service
public class ServicioAutenticacion {

    private final UsuarioRepositorio usuarios;
    private final PasswordEncoder contrasenas;
    private final JwtEncoder tokens;
    private final Duration vigencia;

    public ServicioAutenticacion(UsuarioRepositorio usuarios,
                                 PasswordEncoder contrasenas,
                                 JwtEncoder tokens,
                                 @Value("${forja.jwt.vigencia-horas:12}") long vigenciaHoras) {
        this.usuarios = usuarios;
        this.contrasenas = contrasenas;
        this.tokens = tokens;
        this.vigencia = Duration.ofHours(vigenciaHoras);
    }

    @Transactional
    public Credencial registrar(String email, String nombre, String password) {
        String correo = normalizar(email);
        if (usuarios.existsByEmail(correo)) {
            throw new EmailYaRegistrado(correo);
        }

        Usuario usuario = new Usuario();
        usuario.setEmail(correo);
        usuario.setNombre(nombre.trim());
        usuario.setPasswordHash(contrasenas.encode(password));
        usuarios.save(usuario);

        return emitir(usuario);
    }

    @Transactional(readOnly = true)
    public Credencial iniciarSesion(String email, String password) {
        Usuario usuario = usuarios.findByEmail(normalizar(email))
                .orElseThrow(CredencialesInvalidas::new);

        // Se comprueba la contrasena incluso sobre una cuenta inactiva para
        // que el tiempo de respuesta no delate el estado de la cuenta.
        boolean coincide = contrasenas.matches(password, usuario.getPasswordHash());
        if (!coincide || !usuario.isActivo()) {
            throw new CredencialesInvalidas();
        }

        return emitir(usuario);
    }

    private Credencial emitir(Usuario usuario) {
        Instant ahora = Instant.now();
        Instant expira = ahora.plus(vigencia);

        JwtClaimsSet declaraciones = JwtClaimsSet.builder()
                .issuer("forja")
                .subject(usuario.getId().toString())
                .issuedAt(ahora)
                .expiresAt(expira)
                .claim("nombre", usuario.getNombre())
                .claim("email", usuario.getEmail())
                .build();

        // La cabecera declara HS256 explicitamente: sin ella el codificador
        // asume una firma asimetrica y no encuentra clave con la que firmar.
        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = tokens.encode(JwtEncoderParameters.from(cabecera, declaraciones))
                .getTokenValue();
        return new Credencial(token, expira, usuario.getId(), usuario.getNombre(), usuario.getEmail());
    }

    private static String normalizar(String email) {
        return email.trim().toLowerCase();
    }
}
