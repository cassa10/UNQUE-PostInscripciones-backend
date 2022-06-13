package ar.edu.unq.postinscripciones.service

import ar.edu.unq.postinscripciones.model.*
import ar.edu.unq.postinscripciones.model.comision.Comision
import ar.edu.unq.postinscripciones.model.comision.Dia
import ar.edu.unq.postinscripciones.model.comision.Modalidad
import ar.edu.unq.postinscripciones.model.cuatrimestre.Cuatrimestre
import ar.edu.unq.postinscripciones.model.cuatrimestre.Semestre
import ar.edu.unq.postinscripciones.model.exception.ExcepcionUNQUE
import ar.edu.unq.postinscripciones.persistence.FormularioRepository
import ar.edu.unq.postinscripciones.persistence.SolicitudSobrecupoRepository
import ar.edu.unq.postinscripciones.service.dto.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.LocalDateTime

@IntegrationTest
internal class AlumnoServiceTest {

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

    @Autowired
    private lateinit var autenticacionService: AutenticacionService

    private lateinit var alumno: Alumno
    private lateinit var fede: Alumno
    private lateinit var cuatrimestre: Cuatrimestre
    private lateinit var comision1Algoritmos: Comision
    private lateinit var algo: MateriaDTO
    private lateinit var funcional: MateriaDTO

    @BeforeEach
    fun setUp() {
        val nicoFormularioCrear = FormularioCrearAlumno(
                45328,
                "Nicolas",
                "Martinez",
                "nicolas.martinez@unq.edu.ar",
                42256394,
                Carrera.TPI,
                5.0
        )

        val fedeFormularioCrear = FormularioCrearAlumno(
                45329,
                "Fede",
                "Sandoval",
                "fede.sando@unq.edu.ar",
                11223344,
                Carrera.TPI,
                5.0
        )

        alumno = alumnoService.crear(nicoFormularioCrear)
        fede = alumnoService.crear(fedeFormularioCrear)
        algo = materiaService.crear("Algoritmos", "ALG-208", mutableListOf(), Carrera.SIMULTANEIDAD)
        funcional = materiaService.crear("Funcional", "FUN-205", mutableListOf(), Carrera.SIMULTANEIDAD)
        val formularioCuatrimestre = FormularioCuatrimestre(2022, Semestre.S1)
        cuatrimestre = cuatrimestreService.crear(formularioCuatrimestre)
        val horarios = listOf(
            HorarioDTO(Dia.LUNES, "18:30", "21:30"),
            HorarioDTO(Dia.JUEVES, "18:30", "21:30")
        )

        val formularioComision = FormularioComision(
            1,
            algo.codigo,
            2022,
            Semestre.S1,
            35,
            5,
            horarios,
            Modalidad.PRESENCIAL
        )
        comision1Algoritmos = comisionService.crear(formularioComision)

        val codigo = autenticacionService.crearCuenta(alumno.dni, "contrasenia", "contrasenia")
        autenticacionService.confirmarCuenta(alumno.dni, codigo)

    }

    @Test
    fun `Se puede crear un alumno`() {
        assertThat(alumno).isNotNull
    }

    @Test
    fun `Un alumno registra un formulario de solicitud de cupo`() {
        val formulario =
            alumnoService.guardarSolicitudPara(
                alumno.dni,
                listOf(comision1Algoritmos.id!!),
                cuatrimestre
            )
        val comisionesDeSolicitudes = formulario.solicitudes.map { it.comision.id }

        assertThat(comisionesDeSolicitudes).contains(ComisionDTO.desdeModelo(comision1Algoritmos).id)
    }

    @Test
    fun `Un alumno no puede registrar dos formularios para el mismo cuatrimestre`() {
        alumnoService.guardarSolicitudPara(
            alumno.dni,
            listOf(comision1Algoritmos.id!!),
            cuatrimestre
        )
        val exception = assertThrows<ExcepcionUNQUE> {
            alumnoService.guardarSolicitudPara(
                alumno.dni,
                listOf(),
                cuatrimestre
            )
        }
        assertThat(exception.message).isEqualTo("Ya has guardado un formulario para este cuatrimestre")
    }

    @Test
    fun `Un alumno no puede registrar un formulario para una materia que no tiene disponible`() {
        val logica = materiaService.crear("Lógica y Programacion", "LOG-209", mutableListOf(algo.codigo), Carrera.TPI)
        val formularioComision = FormularioComision(
                1,
                logica.codigo,
                cuatrimestre.anio,
                cuatrimestre.semestre,
                35,
                5,
                listOf(
                        HorarioDTO(Dia.LUNES, "18:00", "20:00"),
                        HorarioDTO(Dia.JUEVES, "09:00", "11:00")
                ),
                Modalidad.PRESENCIAL
        )
        val comisionLogica = comisionService.crear(formularioComision)
        val exception = assertThrows<ExcepcionUNQUE> {
            alumnoService.guardarSolicitudPara(
                    alumno.dni,
                    listOf(comisionLogica.id!!),
                    cuatrimestre
            )
        }
        assertThat(exception.message).isEqualTo("El alumno no puede cursar las materias solicitadas")
    }

    @Test
    fun `Se puede obtener el formulario`() {
        val jwt = autenticacionService.loguearse(alumno.dni, "contrasenia")
        val formularioDTO =
            alumnoService.guardarSolicitudPara(
                alumno.dni,
                listOf(comision1Algoritmos.id!!),
                cuatrimestre
            )
        val formularioPersistido = alumnoService.obtenerFormulario(jwt, cuatrimestre)

        assertThat(formularioDTO).usingRecursiveComparison().isEqualTo(formularioPersistido)
    }

    @Test
    fun `Se puede cerrar un formulario`() {
        val formularioDTO =
                alumnoService.guardarSolicitudPara(
                        alumno.dni,
                        listOf(comision1Algoritmos.id!!),
                        cuatrimestre
                )
        val formulario = alumnoService.cerrarFormulario(formularioDTO.id, alumno.dni)

        assertThat(formulario.estado).isEqualTo(EstadoFormulario.CERRADO)
    }

    @Test
    fun `Se puede aprobar una solicitud de sobrecupo`() {
        val fechaDeModificacion = cuatrimestre.finInscripciones.plusDays(5)
        val formulario =
            alumnoService.guardarSolicitudPara(
                alumno.dni,
                listOf(comision1Algoritmos.id!!),
                cuatrimestre
            )

        val solicitudPendiente = formulario.solicitudes.first()
        val solicitudAprobada = alumnoService.cambiarEstadoSolicitud(
                solicitudPendiente.id,
                EstadoSolicitud.APROBADO,
                formulario.id,
                fechaDeModificacion
        )

        assertThat(solicitudAprobada.estado).isEqualTo(EstadoSolicitud.APROBADO)
        assertThat(solicitudAprobada).usingRecursiveComparison().ignoringFields("estado").isEqualTo(solicitudPendiente)
    }

    @Test
    fun `Se puede rechazar una solicitud de sobrecupo`() {
        val fechaDeModificacion = cuatrimestre.finInscripciones.plusDays(5)
        val formulario =
            alumnoService.guardarSolicitudPara(
                alumno.dni,
                listOf(comision1Algoritmos.id!!),
                cuatrimestre
            )

        val solicitudPendiente = formulario.solicitudes.first()
        val solicitudRechazada = alumnoService.cambiarEstadoSolicitud(
                solicitudPendiente.id,
                EstadoSolicitud.RECHAZADO,
                formulario.id,
                fechaDeModificacion
        )

        assertThat(solicitudRechazada.estado).isEqualTo(EstadoSolicitud.RECHAZADO)
    }

    @Test
    fun `Al aprobar una solicitud, el numero de sobrecupos disponibles de una comision baja`() {
        val fechaDeModificacion = cuatrimestre.finInscripciones.plusDays(5)
        val formulario =
                alumnoService.guardarSolicitudPara(
                        alumno.dni,
                        listOf(comision1Algoritmos.id!!),
                        cuatrimestre
                )

        val solicitudPendiente = formulario.solicitudes.first()
        alumnoService.cambiarEstadoSolicitud(
                solicitudPendiente.id,
                EstadoSolicitud.APROBADO,
                formulario.id,
                fechaDeModificacion
        )

        val comisionDespuesDeAprobarSolicitud = comisionService.obtener(comision1Algoritmos.id!!)

        assertThat(comisionDespuesDeAprobarSolicitud.cuposDisponibles).isEqualTo(comision1Algoritmos.sobrecuposDisponibles() - 1)
    }

    @Test
    fun `Al rechazar una solicitud aprobada, el numero de sobrecupos disponibles de una comision aumenta`() {
        val fechaDeModificacion = cuatrimestre.finInscripciones.plusDays(5)
        val formulario =
                alumnoService.guardarSolicitudPara(
                        alumno.dni,
                        listOf(comision1Algoritmos.id!!),
                        cuatrimestre
                )

        val solicitudPendiente = formulario.solicitudes.first()
        alumnoService.cambiarEstadoSolicitud(
                solicitudPendiente.id,
                EstadoSolicitud.APROBADO,
                formulario.id,
                fechaDeModificacion
        )
        val comisionDespuesDeAprobarSolicitud = comisionService.obtener(comision1Algoritmos.id!!)

        alumnoService.cambiarEstadoSolicitud(
                solicitudPendiente.id,
                EstadoSolicitud.RECHAZADO,
                formulario.id,
                fechaDeModificacion
        )

        val comisionDespuesDeRechazarSolicitud = comisionService.obtener(comision1Algoritmos.id!!)

        assertThat(comisionDespuesDeRechazarSolicitud.cuposDisponibles).isEqualTo(comisionDespuesDeAprobarSolicitud.cuposDisponibles + 1)
    }

    @Test
    fun `No se puede cambiar el estado de una solitud si la etapa de inscripciones aun no ha finalizado`() {
        val formularioAbierto =
                alumnoService.guardarSolicitudPara(
                        alumno.dni,
                        listOf(comision1Algoritmos.id!!),
                        cuatrimestre
                )

        val solicitudPendiente = formularioAbierto.solicitudes.first()
        val exception = assertThrows<ExcepcionUNQUE> {
            alumnoService.cambiarEstadoSolicitud(
                    solicitudPendiente.id,
                    EstadoSolicitud.APROBADO,
                    formularioAbierto.id,
                    LocalDateTime.now().plusDays(2))
        }
        assertThat(exception.message).isEqualTo("No se puede cambiar el estado de esta solicitud, la fecha de inscripciones no ha concluido")
    }

    @Test
    fun `No se puede cambiar el estado de una solitud si el formulario se encuentra cerrado`() {
        val fechaDeModificacion = cuatrimestre.finInscripciones.plusDays(5)
        val formularioAbierto =
                alumnoService.guardarSolicitudPara(
                        alumno.dni,
                        listOf(comision1Algoritmos.id!!),
                        cuatrimestre
                )
        alumnoService.cerrarFormulario(formularioId = formularioAbierto.id, alumnoDni = alumno.dni)

        val solicitudPendiente = formularioAbierto.solicitudes.first()
        val exception = assertThrows<ExcepcionUNQUE> {
            alumnoService.cambiarEstadoSolicitud(
                    solicitudPendiente.id,
                    EstadoSolicitud.APROBADO,
                    formularioAbierto.id,
                    fechaDeModificacion
            )
        }
        assertThat(exception.message).isEqualTo("No se puede cambiar el estado de esta solicitud, el formulario al que pertenece se encuentra cerrado")
    }

    @Test
    fun `Se pueden cerrar todos los formularios del cuatrimestre corriente`() {
        val jwt = autenticacionService.loguearse(alumno.dni, "contrasenia")
        val formularioAntesDeCerrar =
            alumnoService.guardarSolicitudPara(
                alumno.dni,
                listOf(comision1Algoritmos.id!!),
                cuatrimestre
            )
        val formulario2AntesDeCerrar =
            alumnoService.guardarSolicitudPara(
                fede.dni,
                listOf(comision1Algoritmos.id!!),
                cuatrimestre
            )

        alumnoService.cambiarEstadoFormularios()
        val formularioDespuesDeCerrar =
            alumnoService.obtenerFormulario(jwt, cuatrimestre)
        val formulario2DespuesDeCerrar =
            alumnoService.obtenerFormulario(jwt, cuatrimestre)

        assertThat(listOf(formularioAntesDeCerrar, formulario2AntesDeCerrar).map { it.estado })
            .usingRecursiveComparison()
            .isEqualTo(listOf(EstadoFormulario.ABIERTO, EstadoFormulario.ABIERTO))

        assertThat(listOf(formularioDespuesDeCerrar, formulario2DespuesDeCerrar).map { it.estado })
            .usingRecursiveComparison()
            .isEqualTo(listOf(EstadoFormulario.CERRADO, EstadoFormulario.CERRADO))
    }

    @Test
    fun `Se puede cargar la historia academica de un alumno`() {
        val materiaCursada = MateriaCursadaDTO(funcional.codigo, EstadoMateria.APROBADO, LocalDate.of(2021, 12, 20))
        val formularioAlumno = FormularioCrearAlumno(
                1234567,
                "Pepe",
                "Sanchez",
                "pepe.sanchez@unq.edu.ar",
                44556,
                Carrera.TPI,
                5.0
        )
        val otroAlumno = alumnoService.crear(formularioAlumno)

        alumnoService.actualizarHistoriaAcademica(listOf(AlumnoConHistoriaAcademica(otroAlumno.dni,listOf(materiaCursada))))

        val alumnoLuegoDeActualizar = alumnoService.buscarAlumno(otroAlumno.dni)
        assertThat(alumnoLuegoDeActualizar.historiaAcademica).isNotEmpty
        assertThat(alumnoLuegoDeActualizar.historiaAcademica.first().materia.codigo).isEqualTo(materiaCursada.codigoMateria)
    }

    @Test
    fun `Se puede actualizar la historia academica de un alumno`() {
        val materiaCursada = MateriaCursadaDTO(funcional.codigo, EstadoMateria.APROBADO, LocalDate.of(2021, 12, 20))
        val formularioAlumno = FormularioCrearAlumno(
                1234567,
                "Pepe",
                "Sanchez",
                "pepe.sanchez@unq.edu.ar",
                44556,
                Carrera.TPI,
                5.0
        )
        val otroAlumno = alumnoService.crear(formularioAlumno)
        val primerActualizarHistoria = listOf(AlumnoConHistoriaAcademica(otroAlumno.dni, listOf(materiaCursada)))
        alumnoService.actualizarHistoriaAcademica(primerActualizarHistoria)
        val materiaCursada2 = MateriaCursadaDTO(algo.codigo, EstadoMateria.APROBADO, LocalDate.of(2021, 12, 20))
        val segundoActualizarHistoria = listOf(AlumnoConHistoriaAcademica(otroAlumno.dni, listOf(materiaCursada, materiaCursada2)))
        val dto = alumnoService.actualizarHistoriaAcademica(segundoActualizarHistoria)
        val alumnoDespuesDeActualizar = alumnoService.buscarAlumno(dto.first().dni)

        assertThat(alumnoDespuesDeActualizar.historiaAcademica).isNotEmpty
        assertThat(alumnoDespuesDeActualizar.historiaAcademica.map { it.materia.codigo })
                .usingRecursiveComparison()
                .isEqualTo(listOf(materiaCursada.codigoMateria, materiaCursada2.codigoMateria))
    }

    @Test
    fun `Se puede obtener las materias disponibles de un alumno`() {
        val materiasdisponibles =
            alumnoService.materiasDisponibles(alumno.dni, cuatrimestre)
        assertThat(materiasdisponibles).isNotEmpty
        assertThat(materiasdisponibles.first().codigo).isEqualTo(algo.codigo)
    }

    @Test
    fun `un alumno tiene disponible materias solo de su carrera`() {
        val logica = materiaService.crear("Lógica y Programacion", "LOG-209", mutableListOf(), Carrera.LICENCIATURA)
        val materiasdisponibles =
            alumnoService.materiasDisponibles(alumno.dni, cuatrimestre)
        assertThat(materiasdisponibles.map { it.codigo }).doesNotContain(logica.codigo)
    }

    @Test
    fun `un alumno no tiene disponible materias de las cuales no cumple los requisitos`() {
        val logica = materiaService.crear("Lógica y Programacion", "LOG-209", mutableListOf("ALG-208"), Carrera.TPI)
        val materiasdisponibles =
            alumnoService.materiasDisponibles(alumno.dni, cuatrimestre)
        assertThat(materiasdisponibles).hasSize(1)
        assertThat(materiasdisponibles.map { it.codigo }).contains(algo.codigo).doesNotContain(logica.codigo)
    }

    @Test
    fun `un alumno tiene disponible materias de las cuales cumple los requisitos`() {
        val materiaCursada = MateriaCursadaDTO(algo.codigo, EstadoMateria.APROBADO, LocalDate.of(2021, 12, 20))
        val formularioAlumno = FormularioCrearAlumno(
                123456712,
                "Pepe",
                "Sanchez",
                "pepe.sanchez@unq.edu.ar",
                4455611,
                Carrera.TPI,
                5.0
        )
        val nacho = alumnoService.crear(formularioAlumno)
        val actualizarHistoria = listOf(AlumnoConHistoriaAcademica(nacho.dni, listOf(materiaCursada)))
        alumnoService.actualizarHistoriaAcademica(actualizarHistoria)
        val logica = materiaService.crear("Lógica y Programacion", "LOG-209", mutableListOf(algo.codigo), Carrera.TPI)
        val formularioComision = FormularioComision(
            1,
            logica.codigo,
            cuatrimestre.anio,
            cuatrimestre.semestre,
            35,
            5,
            listOf(
                HorarioDTO(Dia.LUNES, "18:00", "20:00"),
                HorarioDTO(Dia.JUEVES, "09:00", "11:00")
            ),
            Modalidad.PRESENCIAL
        )
        val comisionLogica = comisionService.crear(formularioComision)
        val materiasdisponibles = alumnoService.materiasDisponibles(nacho.dni, cuatrimestre)
        assertThat(materiasdisponibles).hasSize(1)
        assertThat(materiasdisponibles.map { it.codigo }).contains(logica.codigo)
        assertThat(materiasdisponibles.first().comisiones).allMatch {
            it == ComisionParaAlumno.desdeModelo(
                comisionLogica
            )
        }
    }

    @Test
    fun `un alumno tiene disponible materias de las cuales cumple los requisitos con materias pendiente de aprobacion`() {
        val materiaCursada = MateriaCursadaDTO(algo.codigo, EstadoMateria.PA, LocalDate.of(2021, 12, 20))
        val formularioAlumno = FormularioCrearAlumno(
            123456712,
            "Pepe",
            "Sanchez",
            "pepe.sanchez@unq.edu.ar",
            4455611,
            Carrera.TPI,
            5.0
        )
        val nacho = alumnoService.crear(formularioAlumno)
        val actualizarHistoria = listOf(AlumnoConHistoriaAcademica(nacho.dni, listOf(materiaCursada)))
        alumnoService.actualizarHistoriaAcademica(actualizarHistoria)
        val logica = materiaService.crear("Lógica y Programacion", "LOG-209", mutableListOf(algo.codigo), Carrera.TPI)
        val formularioComision = FormularioComision(
            1,
            logica.codigo,
            cuatrimestre.anio,
            cuatrimestre.semestre,
            35,
            5,
            listOf(
                HorarioDTO(Dia.LUNES, "18:00", "20:00"),
                HorarioDTO(Dia.JUEVES, "09:00", "11:00")
            ),
            Modalidad.PRESENCIAL
        )
        val comisionLogica = comisionService.crear(formularioComision)
        val materiasdisponibles = alumnoService.materiasDisponibles(nacho.dni, cuatrimestre)
        assertThat(materiasdisponibles).hasSize(1)
        assertThat(materiasdisponibles.map { it.codigo }).contains(logica.codigo)
        assertThat(materiasdisponibles.first().comisiones).allMatch {
            it == ComisionParaAlumno.desdeModelo(
                comisionLogica
            )
        }
    }

    @Test
    fun `un alumno no tiene disponible materias que ya aprobo`() {
        val materiaCursada = MateriaCursadaDTO(algo.codigo, EstadoMateria.APROBADO, LocalDate.of(2021, 12, 20))
        val formularioAlumno = FormularioCrearAlumno(
                123456712,
                "Pepe",
                "Sanchez",
                "pepe.sanchez@unq.edu.ar",
                4455611,
                Carrera.TPI,
                5.0
        )
        val nacho = alumnoService.crear(formularioAlumno)
        val actualizarHistoria = listOf(AlumnoConHistoriaAcademica(nacho.dni, listOf(materiaCursada)))
        alumnoService.actualizarHistoriaAcademica(actualizarHistoria)

        val materiasdisponibles = alumnoService.materiasDisponibles(nacho.dni, cuatrimestre)

        assertThat(materiasdisponibles.map { it.codigo }).doesNotContain(algo.codigo)
    }

    @Test
    fun `se levanta una excepcion al enviar un formulario pasada la fecha de fin aceptada por el cuatrimestre`() {
        comisionService.actualizarOfertaAcademica(listOf(), LocalDateTime.now(), LocalDateTime.now().plusDays(3))

        val excepcion = assertThrows<ExcepcionUNQUE> {
            alumnoService.guardarSolicitudPara(
                alumno.dni,
                listOf(comision1Algoritmos.id!!),
                cuatrimestre,
                LocalDateTime.now().plusDays(5)
            )
        }
        assertThat(excepcion.message).isEqualTo("El periodo para enviar solicitudes de sobrecupos ya ha pasado.")
    }

    @Test
    fun `se levanta una excepcion al enviar un formulario antes de la fecha de inicio aceptada por el cuatrimestre`() {
        comisionService.actualizarOfertaAcademica(listOf(), LocalDateTime.now(), LocalDateTime.now().plusDays(3))

        val excepcion = assertThrows<ExcepcionUNQUE> {
            alumnoService.guardarSolicitudPara(
                alumno.dni,
                listOf(comision1Algoritmos.id!!),
                cuatrimestre,
                LocalDateTime.now().minusDays(5)
            )
        }
        assertThat(excepcion.message).isEqualTo("El periodo para enviar solicitudes de sobrecupos no ha empezado.")
    }

    @Test
    fun `se puede pedir el resumen de estado de un alumno`() {
        val materiaCursada = MateriaCursadaDTO(algo.codigo, EstadoMateria.APROBADO, LocalDate.of(2021, 12, 20))
        val materiaCursada2 = MateriaCursadaDTO(funcional.codigo, EstadoMateria.DESAPROBADO, LocalDate.of(2020, 12, 20))
        val materiaCursada3 = MateriaCursadaDTO(funcional.codigo, EstadoMateria.APROBADO, LocalDate.of(2021, 7, 20))
        val intro = materiaService.crear("Intro", "INT-205", mutableListOf(), Carrera.SIMULTANEIDAD)

        val formularioComision2 = FormularioComision(
                1,
                intro.codigo,
                2022,
                Semestre.S1,
                35,
                5,
                listOf(),
                Modalidad.PRESENCIAL
        )
        val comisionIntro = comisionService.crear(formularioComision2)
        val formularioAlumno = FormularioCrearAlumno(
                123456712,
                "Pepe",
                "Sanchez",
                "pepe.sanchez@unq.edu.ar",
                44556,
                Carrera.SIMULTANEIDAD,
                5.0
        )
        val nacho = alumnoService.crear(formularioAlumno)
        val actualizarHistoria = listOf(AlumnoConHistoriaAcademica(nacho.dni, listOf(materiaCursada, materiaCursada2, materiaCursada3)))
        alumnoService.actualizarHistoriaAcademica(actualizarHistoria)

        val formulario = alumnoService.guardarSolicitudPara(
            nacho.dni,
            listOf(comisionIntro.id!!)
        )

        val resumen = alumnoService.obtenerResumenAlumno(nacho.dni)

        assertThat(resumen.nombre).isEqualTo(nacho.nombre)
        assertThat(resumen.dni).isEqualTo(nacho.dni)
        assertThat(resumen.formulario).usingRecursiveComparison().isEqualTo(formulario)
        assertThat(resumen.resumenCursadas.map { it.nombreMateria }).usingRecursiveComparison()
            .isEqualTo(listOf(algo.nombre, funcional.nombre))
    }

    @Test
    fun `se pueden obtener los alumnos que pidieron una comision`() {
        val formularioAlumno = alumnoService.guardarSolicitudPara(alumno.dni, listOf(comision1Algoritmos.id!!))
        val formularioFede = alumnoService.guardarSolicitudPara(fede.dni, listOf(comision1Algoritmos.id!!))

        val alumnos = alumnoService.alumnosQueSolicitaron(comision1Algoritmos.id!!)

        val alumnosEsperados: List<AlumnoSolicitaComision> =
            listOf(
                AlumnoSolicitaComision(alumno.dni, alumno.cantidadAprobadas(), formularioAlumno.id, formularioAlumno.solicitudes.first().id),
                AlumnoSolicitaComision(fede.dni,  fede.cantidadAprobadas(), formularioFede.id, formularioFede.solicitudes.first().id)
            )
        assertThat(alumnos).hasSize(2)
        assertThat(alumnos).usingRecursiveComparison().isEqualTo(alumnosEsperados)
    }

    @Test
    fun `se pueden obtener los alumnos que pidieron una comision con el id del formulario`() {
        val formularioAlumno = alumnoService.guardarSolicitudPara(alumno.dni, listOf(comision1Algoritmos.id!!))
        val formularioFede = alumnoService.guardarSolicitudPara(fede.dni, listOf(comision1Algoritmos.id!!))

        val alumnos = alumnoService.alumnosQueSolicitaron(comision1Algoritmos.id!!)

        assertThat(alumnos.first().idFormulario).isEqualTo(formularioAlumno.id)
        assertThat(alumnos.last().idFormulario).isEqualTo(formularioFede.id)
    }

    @Test
    fun `se pueden obtener los alumnos que pidieron una comision ordenados descendentemente por cantidad de materias aprobadas`() {
        val materiaCursada = MateriaCursadaDTO(funcional.codigo, EstadoMateria.APROBADO, LocalDate.of(2021, 12, 20))
        val formularioAlumno = FormularioCrearAlumno(
                123456712,
                "Pepe",
                "Sanchez",
                "pepe.sanchez@unq.edu.ar",
                4455611,
                Carrera.TPI,
                5.0
        )
        val nacho = alumnoService.crear(formularioAlumno)
        val actualizarHistoria = listOf(AlumnoConHistoriaAcademica(nacho.dni, listOf(materiaCursada)))
        alumnoService.actualizarHistoriaAcademica(actualizarHistoria)

        alumnoService.guardarSolicitudPara(alumno.dni, listOf(comision1Algoritmos.id!!))
        alumnoService.guardarSolicitudPara(nacho.dni, listOf(comision1Algoritmos.id!!))

        val alumnos = alumnoService.alumnosQueSolicitaron(comision1Algoritmos.id!!)
        assertThat(alumnos.first().cantidadDeAprobadas).isEqualTo(alumnos.maxOf { it.cantidadDeAprobadas })
        assertThat(alumnos.last().cantidadDeAprobadas).isEqualTo(alumnos.minOf { it.cantidadDeAprobadas })
    }

    @Test
    fun `un alumno puede editar su formulario dentro del periodo de inscripcion`() {
        val jwt = autenticacionService.loguearse(alumno.dni, "contrasenia")
        alumnoService.guardarSolicitudPara(alumno.dni, listOf(comision1Algoritmos.id!!))
        val formularioComision = FormularioComision(
            2,
            algo.codigo,
            2022,
            Semestre.S1,
            35,
            5,
            listOf(),
            Modalidad.PRESENCIAL
        )
        val comisionDosAlgoritmos = comisionService.crear(formularioComision)

        val formularioActualizado = alumnoService.actualizarFormulario(alumno.dni, listOf(comisionDosAlgoritmos.id!!))

        val formularioLuegoDeActualizar = alumnoService.obtenerFormulario(jwt)
        assertThat(formularioLuegoDeActualizar.solicitudes).containsExactly(formularioActualizado.solicitudes.first())
    }

    @Test
    fun `un alumno no puede editar su formulario fuera del periodo de inscripcion`() {
        val jwt = autenticacionService.loguearse(alumno.dni, "contrasenia")
        val formularioAntesDeActualizar = alumnoService.guardarSolicitudPara(alumno.dni, listOf(comision1Algoritmos.id!!))
        val formularioComision = FormularioComision(
            2,
            algo.codigo,
            2022,
            Semestre.S1,
            35,
            5,
            listOf(),
            Modalidad.PRESENCIAL
        )
        val comisionDosAlgoritmos = comisionService.crear(formularioComision)

        val excepcion = assertThrows<ExcepcionUNQUE> { alumnoService.actualizarFormulario(alumno.dni, listOf(comisionDosAlgoritmos.id!!), fechaCarga = cuatrimestre.finInscripciones.plusDays(1)) }

        val formularioLuegoDeIntentarActualizar = alumnoService.obtenerFormulario(jwt)
        assertThat(formularioLuegoDeIntentarActualizar).usingRecursiveComparison().isEqualTo(formularioAntesDeActualizar)
        assertThat(excepcion.message).isEqualTo("El periodo para enviar solicitudes de sobrecupos ya ha pasado.")
    }

    @Autowired
    lateinit var formularioRepository: FormularioRepository

    @Autowired
    lateinit var solicitudRepository: SolicitudSobrecupoRepository

    @Test
    fun `al desvincular un alumno de un formulario se borra el formulario y las solicitudes pero no la comision de las solicitudes`() {
        val formularioAntesDeActualizar = alumnoService.guardarSolicitudPara(alumno.dni, listOf(comision1Algoritmos.id!!))
        val formularioComision = FormularioComision(
            2,
            algo.codigo,
            2022,
            Semestre.S1,
            35,
            5,
            listOf(),
            Modalidad.PRESENCIAL
        )
        val comisionDosAlgoritmos = comisionService.crear(formularioComision)

        alumnoService.actualizarFormulario(alumno.dni, listOf(comisionDosAlgoritmos.id!!))

        assertThat(formularioRepository.findById(formularioAntesDeActualizar.id).isPresent).isFalse
        assertThat(solicitudRepository.findById(formularioAntesDeActualizar.solicitudes.first().id).isPresent).isFalse
        assertThat(comisionService.obtener(comision1Algoritmos.id!!)).usingRecursiveComparison().isEqualTo(ComisionDTO.desdeModelo(comision1Algoritmos))
        assertThat(cuatrimestreService.obtener(cuatrimestre)).usingRecursiveComparison().isEqualTo(cuatrimestre)
    }

    @Test
    fun `se pueden obtener los alumnos que pidieron una materia sin una comision especifica`() {
        val formularioAlumno = alumnoService.guardarSolicitudPara(alumno.dni, listOf(comision1Algoritmos.id!!))
        val formularioFede = alumnoService.guardarSolicitudPara(fede.dni, listOf(comision1Algoritmos.id!!))

        val alumnos = alumnoService.alumnosQueSolicitaron(algo.codigo, null, cuatrimestre)

        val alumnosEsperados: List<AlumnoSolicitaMateria> =
            listOf(
                AlumnoSolicitaMateria(alumno.dni, formularioAlumno.id, formularioAlumno.solicitudes.first().id, comision1Algoritmos.numero, algo.codigo, alumno.cantidadAprobadas(), alumno.coeficiente ),
                AlumnoSolicitaMateria(fede.dni, formularioFede.id, formularioFede.solicitudes.first().id, comision1Algoritmos.numero, algo.codigo, fede.cantidadAprobadas(), fede.coeficiente ),
            )
        assertThat(alumnos).hasSize(2)
        assertThat(alumnos).usingRecursiveComparison().isEqualTo(alumnosEsperados)
    }

    @Test
    fun `se pueden obtener los alumnos que pidieron una materia con una comision especifica`() {
        val formularioAlumno = alumnoService.guardarSolicitudPara(alumno.dni, listOf(comision1Algoritmos.id!!))
        val horarios = listOf(
            HorarioDTO(Dia.LUNES, "18:30", "21:30"),
            HorarioDTO(Dia.JUEVES, "18:30", "21:30")
        )

        val formularioComision = FormularioComision(
            1,
            algo.codigo,
            2022,
            Semestre.S1,
            35,
            5,
            horarios,
            Modalidad.PRESENCIAL
        )
        val comision2Algoritmos = comisionService.crear(formularioComision)
        alumnoService.guardarSolicitudPara(fede.dni, listOf(comision2Algoritmos.id!!))

        val alumnos = alumnoService.alumnosQueSolicitaron(algo.codigo, comision1Algoritmos.id!!, cuatrimestre)

        val alumnosEsperados: List<AlumnoSolicitaMateria> =
            listOf(
                AlumnoSolicitaMateria(alumno.dni, formularioAlumno.id, formularioAlumno.solicitudes.first().id, comision1Algoritmos.numero, algo.codigo, alumno.cantidadAprobadas(), alumno.coeficiente ),
            )
        assertThat(alumnos).hasSize(1)
        assertThat(alumnos).usingRecursiveComparison().isEqualTo(alumnosEsperados)
    }

    @Test
    fun `se pueden obtener los alumnos que pidieron una materia ordenados por mayor coeficiente`() {
        val materiaCursada = MateriaCursadaDTO(funcional.codigo, EstadoMateria.APROBADO, LocalDate.of(2021, 12, 20))
        val formularioNuevoAlumno = FormularioCrearAlumno(
                123456712,
                "Pepe",
                "Sanchez",
                "pepe.sanchez@unq.edu.ar",
                4455611,
                Carrera.TPI,
                8.21
        )
        var nacho = alumnoService.crear(formularioNuevoAlumno)
        val actualizarHistoria = listOf(AlumnoConHistoriaAcademica(nacho.dni, listOf(materiaCursada)))
        alumnoService.actualizarHistoriaAcademica(actualizarHistoria)
        val formularioFede = alumnoService.guardarSolicitudPara(fede.dni, listOf(comision1Algoritmos.id!!))
        val formularioAlumno = alumnoService.guardarSolicitudPara(nacho.dni, listOf(comision1Algoritmos.id!!))
        nacho = alumnoService.buscarAlumno(nacho.dni)
        fede = alumnoService.buscarAlumno(fede.dni)

        val alumnos = alumnoService.alumnosQueSolicitaron(algo.codigo, null, cuatrimestre)
        val alumnosEsperados: List<AlumnoSolicitaMateria> =
            listOf(
                AlumnoSolicitaMateria(nacho.dni, formularioAlumno.id, formularioAlumno.solicitudes.first().id, comision1Algoritmos.numero, algo.codigo, nacho.cantidadAprobadas(), nacho.coeficiente ),
                AlumnoSolicitaMateria(fede.dni, formularioFede.id, formularioFede.solicitudes.first().id, comision1Algoritmos.numero, algo.codigo, fede.cantidadAprobadas(), fede.coeficiente ),
            )

        assertThat(alumnos).usingRecursiveComparison().isEqualTo(alumnosEsperados)
        assertThat(alumnos.first().cantidadDeAprobadas).isEqualTo(alumnos.maxOf { it.cantidadDeAprobadas })
        assertThat(alumnos.last().cantidadDeAprobadas).isEqualTo(alumnos.minOf { it.cantidadDeAprobadas })
    }

    @Test
    fun `Se puede obtener el formulario de un alumno registrado en el sistema`() {
        val formularioAlumno = alumnoService.guardarSolicitudPara(alumno.dni, listOf(comision1Algoritmos.id!!))
        val jwt = autenticacionService.loguearse(alumno.dni, "contrasenia")

        val formularioObtenido = alumnoService.obtenerFormulario(jwt)

        assertThat(formularioAlumno).usingRecursiveComparison().isEqualTo(formularioObtenido)
    }

    @Test
    fun `Al obtener un formulario cuando aun esta abierto todas sus solicitudes se muestran como pendientes aunque su estado sea otro`() {
        val fechaDeModificacion = cuatrimestre.finInscripciones.plusDays(5)

        val formulario =
                alumnoService.guardarSolicitudPara(
                        alumno.dni,
                        listOf(comision1Algoritmos.id!!),
                        cuatrimestre
                )

        val solicitudPendiente = formulario.solicitudes.first()
        val solicitudAprobada = alumnoService.cambiarEstadoSolicitud(
                solicitudPendiente.id,
                EstadoSolicitud.APROBADO,
                formulario.id,
                fechaDeModificacion
        )


        val jwt = autenticacionService.loguearse(alumno.dni, "contrasenia")

        val formularioObtenido = alumnoService.obtenerFormulario(jwt)

        assertThat(solicitudAprobada.id).isEqualTo(formularioObtenido.solicitudes.first().id)
        assertThat(formularioObtenido.solicitudes.first().estado).isEqualTo(EstadoSolicitud.PENDIENTE)
        assertThat(solicitudAprobada.estado).isEqualTo(EstadoSolicitud.APROBADO)
    }

    @Test
    fun `Al obtener un formulario cuando se encuentra cerrado todas sus solicitudes se muestran como se encuentran`() {
        val fechaDeModificacion = cuatrimestre.finInscripciones.plusDays(5)
        val formulario =
                alumnoService.guardarSolicitudPara(
                        alumno.dni,
                        listOf(comision1Algoritmos.id!!),
                        cuatrimestre
                )

        val solicitudPendiente = formulario.solicitudes.first()
        val solicitudAprobada = alumnoService.cambiarEstadoSolicitud(
                solicitudPendiente.id,
                EstadoSolicitud.APROBADO,
                formulario.id,
                fechaDeModificacion
        )

        alumnoService.cerrarFormulario(formulario.id, alumno.dni)

        val jwt = autenticacionService.loguearse(alumno.dni, "contrasenia")

        val formularioObtenido = alumnoService.obtenerFormulario(jwt)

        assertThat(formularioObtenido.solicitudes.first()).usingRecursiveComparison().isEqualTo(solicitudAprobada)
    }

    @Test
    fun `obtener listado de alumnos sin filtro por nombre o apellido`(){
        val formulario =
            alumnoService.guardarSolicitudPara(
                alumno.dni,
                listOf(comision1Algoritmos.id!!),
                cuatrimestre
            )
        val formulario2 =
            alumnoService.guardarSolicitudPara(
                fede.dni,
                listOf(comision1Algoritmos.id!!),
                cuatrimestre
            )
        val alumnos = alumnoService.alumnosPorNombreOApellido(null)
        assertThat(alumnos.map{ it.formularioId }).isEqualTo(listOf(formulario.id, formulario2.id))
    }

    @Test
    fun `obtener listado de alumnos con filtrado por nombre o apellido`(){
        val formulario =
            alumnoService.guardarSolicitudPara(
                alumno.dni,
                listOf(comision1Algoritmos.id!!),
                cuatrimestre
            )
        val formulario2 =
            alumnoService.guardarSolicitudPara(
                fede.dni,
                listOf(comision1Algoritmos.id!!),
                cuatrimestre
            )
        val alumnos = alumnoService.alumnosPorNombreOApellido("edE")
        assertThat(alumnos.map{ it.formularioId }).isEqualTo(listOf(formulario2.id))
        assertThat(alumnos.first()).usingRecursiveComparison().isEqualTo(AlumnoFormulario(AlumnoDTO.desdeModelo(fede), formulario2.id, EstadoFormulario.ABIERTO, 0, 1))
        assertThat(alumnos.map{ it.formularioId }).doesNotContain(formulario.id)
    }

    @AfterEach
    fun tearDown() {
        dataService.clearDataSet()
    }

}