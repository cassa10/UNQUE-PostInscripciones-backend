package ar.edu.unq.postinscripciones.service

import ar.edu.unq.postinscripciones.model.Materia
import ar.edu.unq.postinscripciones.model.comision.Comision
import ar.edu.unq.postinscripciones.model.cuatrimestre.Cuatrimestre
import ar.edu.unq.postinscripciones.model.exception.ExcepcionUNQUE
import ar.edu.unq.postinscripciones.model.exception.MateriaNoEncontradaExcepcion
import ar.edu.unq.postinscripciones.persistence.ComisionRespository
import ar.edu.unq.postinscripciones.persistence.CuatrimestreRepository
import ar.edu.unq.postinscripciones.persistence.FormularioRepository
import ar.edu.unq.postinscripciones.persistence.MateriaRepository
import ar.edu.unq.postinscripciones.service.dto.comision.*
import ar.edu.unq.postinscripciones.service.dto.formulario.FormularioComision
import io.swagger.annotations.ApiModelProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import javax.transaction.Transactional

@Service
class ComisionService {

    @Autowired
    private lateinit var formularioRepository: FormularioRepository

    @Autowired
    private lateinit var comisionRespository: ComisionRespository

    @Autowired
    private lateinit var materiaRepository: MateriaRepository

    @Autowired
    private lateinit var cuatrimestreRepository: CuatrimestreRepository

    @Transactional
    fun actualizarOfertaAcademica(
        comisionesACrear: List<ComisionACrear>,
        inicioInscripciones: LocalDateTime? = null,
        finInscripciones: LocalDateTime? = null,
        cuatrimestre: Cuatrimestre? = null
    ): List<ConflictoComision> {
        val miCuatrimestre: Cuatrimestre = cuatrimestre ?: Cuatrimestre.actualConFechas(inicioInscripciones, finInscripciones)
        val existeCuatrimestre =
            cuatrimestreRepository.findByAnioAndSemestre(miCuatrimestre.anio, miCuatrimestre.semestre)

        val cuatrimestreObtenido = if (existeCuatrimestre.isPresent) {
            this.actualizarCuatrimestre(existeCuatrimestre.get(), inicioInscripciones, finInscripciones)
        } else {
            cuatrimestreRepository.save(miCuatrimestre)
        }

        return guardarComisionesBuscandoConflictos(comisionesACrear, cuatrimestreObtenido)
    }

    @Transactional
    fun ofertaDelCuatrimestre(patronNombre: String = "", cuatrimestre: Cuatrimestre = Cuatrimestre.actual()): List<ComisionDTO> {
        val oferta = comisionRespository.findByCuatrimestreAnioAndCuatrimestreSemestreAndMateriaNombreIgnoreCaseContaining(
            cuatrimestre.anio,
            cuatrimestre.semestre,
            patronNombre
        )
        chequearSiHayOferta(oferta, cuatrimestre)
        return oferta.map { ComisionDTO.desdeModelo(it) }
    }

    @Transactional
    fun crear(formularioComision: FormularioComision): Comision {
        return guardarComision(formularioComision)
    }

    @Transactional
    fun obtener(id: Long): ComisionDTO {
        val comision = comisionRespository.findById(id).orElseThrow { ExcepcionUNQUE("No se encuentra la comision") }
        return ComisionDTO.desdeModelo(comision)
    }

    @Transactional
    fun borrarComision(id: Long) {
        formularioRepository.findByComisionesInscriptoId(id).forEach {
            it.quitarInscripcionDe(id)
            formularioRepository.save(it)
        }
        comisionRespository.deleteById(id)
    }

    @Transactional
    fun obtenerComisionesMateria(codigoMateria: String, cuatrimestre: Cuatrimestre = Cuatrimestre.actual()): List<ComisionDTO> {
        val cuatrimestreObtenido = cuatrimestreRepository.findByAnioAndSemestre(cuatrimestre.anio, cuatrimestre.semestre)
            .orElseThrow { ExcepcionUNQUE("No existe el cuatrimestre") }
        val materia = materiaRepository.findById(codigoMateria)
            .orElseThrow { ExcepcionUNQUE("No se encuentra la materia") }
        val comisiones = comisionRespository.findAllByMateriaAndCuatrimestreAnioAndCuatrimestreSemestre(materia, cuatrimestreObtenido.anio, cuatrimestreObtenido.semestre)

        return comisiones.map { ComisionDTO.desdeModelo(it) }
    }

    private fun guardarComisionesBuscandoConflictos(
        comisionesACrear: List<ComisionACrear>,
        cuatrimestre: Cuatrimestre,
    ): List<ConflictoComision> {
        val conflictos: MutableList<ConflictoComision> = mutableListOf()
        comisionesACrear.forEach { comisionACrear ->
            val materia = materiaRepository.findByNombreIgnoringCase(comisionACrear.nombreMateria)
            if(!materia.isPresent) {
                val mensaje = "No existe la materia ${comisionACrear.nombreMateria}"
                conflictos.add(ConflictoComision(comisionACrear.nombreMateria, comisionACrear.numeroComision, mensaje))
            } else {
                val existeComision = comisionRespository
                    .findByNumeroAndMateriaAndCuatrimestre(comisionACrear.numeroComision, materia.get(), cuatrimestre)
                if (existeComision.isPresent) {
                    val mensaje = "Ya existe esta comision"
                    conflictos.add(ConflictoComision(comisionACrear.nombreMateria, comisionACrear.numeroComision, mensaje))
                } else {
                    guardarComision(comisionACrear, materia.get(), cuatrimestre)
                }
            }
        }
        return conflictos
    }

    @Transactional
    fun modificarHorarios(comisionesConHorarios: List<ComisionConHorarios>, cuatrimestre: Cuatrimestre = Cuatrimestre.actual()): MutableList<ConflictoHorarios> {
        val cuatrimestreObtenido = cuatrimestreRepository.findByAnioAndSemestre(cuatrimestre.anio, cuatrimestre.semestre).orElseThrow { ExcepcionUNQUE("Cuatrimestre no encontrado") }
        val conflictoHorarios = mutableListOf<ConflictoHorarios>()

        comisionesConHorarios.forEach { comisionConHorarios ->
            val materia = materiaRepository.findMateriaByCodigo(comisionConHorarios.materia)
            if (materia.isPresent) {
                val comision = comisionRespository
                    .findByNumeroAndMateriaAndCuatrimestre(comisionConHorarios.comision, materia.get(), cuatrimestreObtenido)
                if (comision.isPresent) {
                    val comisionObtenida = comision.get()
                    comisionObtenida.modificarHorarios(comisionConHorarios.horarios.map { HorarioDTO.aModelo(it) })
                    comisionRespository.save(comisionObtenida)
                } else {
                    conflictoHorarios.add(ConflictoHorarios(comisionConHorarios.comision, comisionConHorarios.materia, "No se encontró la comision"))
                }
            } else {
                conflictoHorarios.add(ConflictoHorarios(comisionConHorarios.comision, comisionConHorarios.materia, "No se encontró la materia"))
            }
        }

        return conflictoHorarios
    }

    private fun actualizarCuatrimestre(
        cuatrimestre: Cuatrimestre,
        inicioInscripciones: LocalDateTime?,
        finInscripciones: LocalDateTime?
    ): Cuatrimestre {
        cuatrimestre.actualizarFechas(inicioInscripciones, finInscripciones)
        return cuatrimestreRepository.save(cuatrimestre)
    }

    private fun chequearSiHayOferta(
        oferta: List<Comision>,
        cuatrimestre: Cuatrimestre
    ) {
        if (oferta.isEmpty()) throw ExcepcionUNQUE("No hay oferta registrada para el cuatrimestre ${cuatrimestre.anio}, ${cuatrimestre.semestre}")
    }

    private fun guardarComision(
        comisionACrear: ComisionACrear,
        materia: Materia,
        cuatrimestre: Cuatrimestre
    ): Comision {
        return comisionRespository.save(
            Comision(
                materia,
                comisionACrear.numeroComision,
                cuatrimestre,
                mutableListOf(),
                comisionACrear.cuposTotales,
                comisionACrear.sobrecuposTotales
            )
        )
    }

    private fun guardarComision(formularioComision: FormularioComision): Comision {
        val materia = materiaRepository.findById(formularioComision.codigoMateria)
            .orElseThrow { MateriaNoEncontradaExcepcion() }
        val cuatrimestre =
            cuatrimestreRepository.findByAnioAndSemestre(formularioComision.anio, formularioComision.semestre).get()
        return comisionRespository.save(
            Comision(
                materia,
                formularioComision.numero,
                cuatrimestre,
                formularioComision.horarios.map { HorarioDTO.aModelo(it) }.toMutableList(),
                formularioComision.cuposTotales,
                formularioComision.sobreCuposTotales,
                formularioComision.modalidad
            )
        )
    }
}

data class ConflictoHorarios(
    @ApiModelProperty(example = "1")
    val comision: Int,
    @ApiModelProperty(example = "01035")
    val materia: String,
    @ApiModelProperty(example = "Comision no encontrada")
    val mensaje: String
)