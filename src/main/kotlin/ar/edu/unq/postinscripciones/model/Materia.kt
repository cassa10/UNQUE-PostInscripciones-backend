package ar.edu.unq.postinscripciones.model

import javax.persistence.*

@Entity
class Materia(
    @Id
    val codigo: String = "",
    @Column(unique=true)
    val nombre: String = "",
    @ManyToMany(fetch = FetchType.LAZY)
    val correlativas: MutableList<Materia> = mutableListOf(),
    @Enumerated(EnumType.STRING)
    val carrera: Carrera = Carrera.SIMULTANEIDAD
) {
    fun esLaMateria(materia: Materia) = this.codigo == materia.codigo
}
