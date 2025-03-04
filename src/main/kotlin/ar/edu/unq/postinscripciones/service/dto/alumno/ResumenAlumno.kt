package ar.edu.unq.postinscripciones.service.dto.alumno

import ar.edu.unq.postinscripciones.model.Carrera
import ar.edu.unq.postinscripciones.service.dto.formulario.FormularioDirectorDTO
import ar.edu.unq.postinscripciones.service.dto.materia.MateriaCursadaResumenDTO
import io.swagger.annotations.ApiModelProperty

data class ResumenAlumno(
    @ApiModelProperty(example = "Hilda")
    val nombre: String,
    @ApiModelProperty(example = "12345677")
    val dni: Int,
    val legajo: Int,
    val carrera: Carrera,
    @ApiModelProperty(example = "7.31")
    val coeficiente: Double,
    val formulario: FormularioDirectorDTO,
    val resumenCursadas: List<MateriaCursadaResumenDTO>
)
