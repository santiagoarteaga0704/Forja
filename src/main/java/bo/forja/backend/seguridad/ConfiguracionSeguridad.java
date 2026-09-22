package bo.forja.backend.seguridad;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Seguridad de la API.
 * <p>
 * La autenticacion es <b>sin estado</b>, con un JWT firmado que el cliente
 * presenta en cada peticion. La decision viene impuesta por el propio
 * enunciado: la aplicacion movil debe operar sin conexion y sincronizar
 * despues, de modo que no puede depender de una sesion viva en el
 * servidor. Un token que el cliente guarda y reutiliza sobrevive a la
 * perdida de red; una sesion en memoria del servidor, no.
 * <p>
 * La firma es simetrica (HMAC con SHA-256) porque un unico servicio emite
 * y valida los tokens. Un par de claves asimetricas solo tendria sentido
 * si un tercero tuviera que validarlos sin poder emitirlos.
 */
@Configuration
@EnableWebSecurity
public class ConfiguracionSeguridad {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracionSeguridad.class);

    @Bean
    SecurityFilterChain cadenaDeFiltros(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                // No hay cookies de sesion que proteger: el token viaja en la
                // cabecera Authorization, donde un sitio ajeno no puede ponerlo.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rutas -> rutas
                        .requestMatchers("/api/auth/registro", "/api/auth/sesion").permitAll()
                        // El canal colaborativo valida el token durante su propio
                        // apreton de manos: el navegador no permite enviar
                        // cabeceras al abrir un WebSocket.
                        .requestMatchers("/ws/**").permitAll()
                        // El cliente web viaja dentro de esta misma imagen en el
                        // despliegue, y se sirve desde este mismo origen: un solo
                        // dominio, sin CORS que configurar y con el canal
                        // colaborativo por el mismo puerto. Quien abre la
                        // aplicacion por primera vez todavia no tiene token, asi
                        // que la pantalla de entrada no puede exigirlo.
                        .requestMatchers(HttpMethod.GET,
                                "/", "/index.html", "/assets/**", "/marca/**", "/favicon.ico",
                                "/*.svg", "/*.png", "/*.ico", "/*.webmanifest").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(recurso -> recurso.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /**
     * Permite que el cliente React, servido desde otro origen durante el
     * desarrollo, llame a la API.
     * <p>
     * Los origenes se declaran uno por uno en lugar de permitir cualquiera:
     * como la autenticacion viaja en una cabecera y no en una cookie, un
     * origen abierto no expondria la sesion del usuario, pero si permitiria
     * que cualquier pagina consumiera la API con un token filtrado.
     */
    // El nombre del bean no es decorativo: Spring Security busca la fuente de
    // configuracion de CORS por el nombre "corsConfigurationSource", y con
    // cualquier otro la registra en el contexto pero no la usa. El sintoma es
    // silencioso -el vuelo previo responde 200 sin las cabeceras de permiso- y
    // solo se manifiesta en el navegador, que bloquea la llamada.
    @Bean("corsConfigurationSource")
    CorsConfigurationSource origenesPermitidos(
            @Value("${forja.web.origenes:http://localhost:5173}") List<String> origenes) {

        log.info("CORS habilitado para los origenes: {}", origenes);
        CorsConfiguration regla = new CorsConfiguration();
        regla.setAllowedOrigins(origenes);
        regla.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        regla.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/api/**", regla);
        return fuente;
    }

    /**
     * Clave de firma. En desarrollo vale el valor por omision; en el
     * despliegue se inyecta por variable de entorno. HS256 exige al menos
     * 256 bits, asi que el secreto no puede tener menos de 32 caracteres.
     */
    @Bean
    SecretKey claveDeFirma(@Value("${forja.jwt.secreto}") String secreto) {
        byte[] material = secreto.getBytes(StandardCharsets.UTF_8);
        if (material.length < 32) {
            throw new IllegalStateException(
                    "forja.jwt.secreto debe tener al menos 32 caracteres para firmar con HS256");
        }
        return new SecretKeySpec(material, "HmacSHA256");
    }

    @Bean
    JwtEncoder codificadorDeTokens(SecretKey claveDeFirma) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(claveDeFirma));
    }

    @Bean
    JwtDecoder decodificadorDeTokens(SecretKey claveDeFirma) {
        return NimbusJwtDecoder.withSecretKey(claveDeFirma)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * BCrypt incorpora la sal en el propio hash y su costo es ajustable, de
     * modo que el almacenamiento de contrasenas no queda atado a la
     * capacidad de computo del ano en que se escribio el codigo.
     */
    @Bean
    PasswordEncoder codificadorDeContrasenas() {
        return new BCryptPasswordEncoder();
    }
}
