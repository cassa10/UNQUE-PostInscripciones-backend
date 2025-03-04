package ar.edu.unq.postinscripciones.service.dto.alumno

import ar.edu.unq.postinscripciones.model.EstadoFormulario
import javax.persistence.Tuple

data class AlumnoFormulario(
    val alumno: AlumnoDTO,
    val formularioId: Long,
    val estadoFormulario: EstadoFormulario,
    val cantComisionesInscripto : Int,
    val cantSolicitudesPendientes : Int,
    val cantSolicitudesAprobadas : Int,
) {
    companion object {
        fun fromTuple(tupla: Tuple): AlumnoFormulario {
            return AlumnoFormulario(
                AlumnoDTO(
                    tupla.get(0) as Int,
                    tupla.get(1) as String,
                    tupla.get(2) as String,
                    tupla.get(3) as String,
                    tupla.get(4) as Int,
                    tupla.get(5) as Double,
                ),
                tupla.get(6) as Long,
                tupla.get(7) as EstadoFormulario,
                tupla.get(8) as Int,
                (tupla.get(9) as Long).toInt(),
                (tupla.get(10) as Long).toInt(),
            )
        }
    }
}