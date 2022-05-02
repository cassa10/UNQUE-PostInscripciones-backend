package ar.edu.unq.postinscripciones.service

import ar.edu.unq.postinscripciones.model.Alumno
import ar.edu.unq.postinscripciones.model.EstadoSolicitud
import ar.edu.unq.postinscripciones.model.Materia
import ar.edu.unq.postinscripciones.model.comision.Comision
import ar.edu.unq.postinscripciones.model.comision.Dia
import ar.edu.unq.postinscripciones.model.comision.Horario
import ar.edu.unq.postinscripciones.model.cuatrimestre.Cuatrimestre
import ar.edu.unq.postinscripciones.model.cuatrimestre.Semestre
import ar.edu.unq.postinscripciones.model.exception.ExcepcionUNQUE
import ar.edu.unq.postinscripciones.resources.DataService
import ar.edu.unq.postinscripciones.service.dto.FormularioComision
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalTime

@IntegrationTest
class AlumnoServiceTest {

    @Autowired
    private lateinit var alumnoService: AlumnoService

    @Autowired
    private lateinit var cuatrimestreService: CuatrimestreService

    @Autowired
    private lateinit var comisionService: ComisionService

    @Autowired
    private lateinit var materiaService: MateriaService

    @Autowired
    private lateinit var dataService: DataService

    private lateinit var alumno: Alumno
    private lateinit var cuatrimestre: Cuatrimestre
    private lateinit var comision1Algoritmos: Comision
    private lateinit var algo: Materia

    @BeforeEach
    fun setUp() {
        val formularioCrear = FormularioCrearAlumno(
            45328,
            "Nicolas",
            "Martinez",
            "nicolas.martinez@unq.edu.ar",
            42256394,
            "42256395"
        )

        alumno = alumnoService.crear(formularioCrear)
        algo = materiaService.crear("Algoritmos", "ALG-208")
        val formularioCuatrimestre = FormularioCuatrimestre(2022, Semestre.S1)
        cuatrimestre = cuatrimestreService.crear(formularioCuatrimestre)
        val horarios = listOf(
            Horario(Dia.LUNES, LocalTime.of(18, 30, 0), LocalTime.of(21, 30, 0)),
            Horario(Dia.JUEVES, LocalTime.of(18, 30, 0), LocalTime.of(21, 30, 0))
        )

        val formularioComision = FormularioComision(
            1,
            algo.codigo,
            2022,
            Semestre.S1,
            35,
            5,
            horarios
        )
        comision1Algoritmos = comisionService.crear(formularioComision)
    }

    @Test
    fun `Se puede crear un alumno`() {
        assertThat(alumno).isNotNull
    }

    @Test
    fun `Un alumno registra un formulario de solicitud de cupo`() {
        val alumnoDespuesDeGuardarFormulario =
            alumnoService.guardarSolicitudPara(
                alumno.legajo,
                cuatrimestre.id!!,
                listOf(comision1Algoritmos.id!!)
            )

        assertThat(alumnoDespuesDeGuardarFormulario.haSolicitado(comision1Algoritmos)).isTrue
    }

    @Test
    fun `Un alumno no puede registrar dos formularios para el mismo cuatrimestre`() {
        alumnoService.guardarSolicitudPara(
            alumno.legajo,
            cuatrimestre.id!!,
            listOf(comision1Algoritmos.id!!)
        )
        val exception = assertThrows<ExcepcionUNQUE> {
            alumnoService.guardarSolicitudPara(
                alumno.legajo,
                cuatrimestre.id!!,
                listOf()
            )
        }
        assertThat(exception.message).isEqualTo("Ya has solicitado materias para este cuatrimestre")
    }

    @Test
    fun `Se puede obtener el formulario`() {
        val alumnoDespuesDeGuardarFormulario =
                alumnoService.guardarSolicitudPara(
                        alumno.legajo,
                        cuatrimestre.id!!,
                        listOf(comision1Algoritmos.id!!)
                )
        val formulario = alumnoDespuesDeGuardarFormulario.obtenerFormulario(cuatrimestre.anio, cuatrimestre.semestre)
        val formularioPersistido = alumnoService.obtenerFormulario(cuatrimestre.anio, cuatrimestre.semestre, alumno.legajo)

        assertThat(formularioPersistido).usingRecursiveComparison().isEqualTo(formulario)
    }

    @Test
    fun `Se puede aprobar una solicitud de sobrecupo`() {
        val alumnoDespuesDeGuardarFormulario =
                alumnoService.guardarSolicitudPara(
                        alumno.legajo,
                        cuatrimestre.id!!,
                        listOf(comision1Algoritmos.id!!)
                )
        val formulario = alumnoDespuesDeGuardarFormulario.obtenerFormulario(cuatrimestre.anio, cuatrimestre.semestre)
        val solicitudPendiente = formulario.solicitudes.first()
        val solicitudAprobada = alumnoService.cambiarEstado(solicitudPendiente.id!!, EstadoSolicitud.APROBADO)

        assertThat(solicitudAprobada.estado).isEqualTo(EstadoSolicitud.APROBADO)
        assertThat(solicitudAprobada).usingRecursiveComparison().ignoringFields("estado").isEqualTo(solicitudPendiente)
    }

    @Test
    fun `Se puede rechazar una solicitud de sobrecupo`() {
        val alumnoDespuesDeGuardarFormulario =
                alumnoService.guardarSolicitudPara(
                        alumno.legajo,
                        cuatrimestre.id!!,
                        listOf(comision1Algoritmos.id!!)
                )
        val formulario = alumnoDespuesDeGuardarFormulario.obtenerFormulario(cuatrimestre.anio, cuatrimestre.semestre)
        val solicitudPendiente = formulario.solicitudes.first()
        val solicitudRechazada = alumnoService.cambiarEstado(solicitudPendiente.id!!, EstadoSolicitud.RECHAZADO)

        assertThat(solicitudRechazada.estado).isEqualTo(EstadoSolicitud.RECHAZADO)
    }

    @AfterEach
    fun tearDown() {
        dataService.clearDataSet()
    }

}