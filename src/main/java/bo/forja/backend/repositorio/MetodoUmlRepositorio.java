package bo.forja.backend.repositorio;

import bo.forja.backend.dominio.MetodoUml;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MetodoUmlRepositorio extends JpaRepository<MetodoUml, UUID> {

    /**
     * Metodos del diagrama con sus parametros ya cargados.
     * <p>
     * Se consulta desde el metodo y no desde la clase porque {@code metodos}
     * y {@code parametros} son dos colecciones y no pueden traerse en el mismo
     * fetch. Partiendo del metodo, la unica coleccion en juego es la de sus
     * parametros y la consulta es legitima.
     * <p>
     * El {@code LEFT JOIN} importa: con un join interno, los metodos sin
     * parametros quedarian fuera del resultado y su coleccion sin inicializar,
     * que es precisamente el caso que se quiere cubrir.
     */
    @Query("""
            SELECT m FROM MetodoUml m
            LEFT JOIN FETCH m.parametros
            WHERE m.clase.diagrama.id = :diagramaId
            """)
    List<MetodoUml> buscarConParametros(@Param("diagramaId") UUID diagramaId);
}
