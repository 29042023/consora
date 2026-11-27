package com.example.consora_mobile.data

/** Datos de la cuenta (tabla `user` + rol, estado y matrícula si es administrador). */
data class Usuario(
    val id: Long,
    val nameSurname: String,
    val mail: String,
    val img: String?,
    val rol: String,
    val dni: String?,
    val phone: String?,
    val dateNacimiento: String?,
    val estadoCuenta: String?,
    val createdAt: String,
    val cantidadConsorcios: Int,
    val administrador: DatosAdministrador?,
) {
    data class DatosAdministrador(
        val matricula: String,
        val fechaAltaMatricula: String?,
        val enteOtorgante: String?,
    )

    val esAdmin get() = rol == ROL_ADMIN || rol == ROL_ADMIN_SISTEMA
    val esAsistente get() = rol == ROL_ASISTENTE
    val esProveedor get() = rol == ROL_PROVEEDOR
    val esPropietario get() = rol == ROL_PROPIETARIO

    /**
     * Subconsulta SQL con los consorcios que ve el usuario (igual que en la web): los que representa
     * (administrador), los que le habilitaron (asistente) o en los que tiene una unidad (propietario).
     * Usa un único parámetro: el id del usuario.
     */
    val sqlConsorciosVisibles: String
        get() = when {
            esAsistente -> """SELECT ea.id_consorcio FROM edificios_asistente ea
                JOIN asistente a ON a.id_asistente = ea.id_user WHERE a.id_user = ? AND ea.habilitado = 1"""
            esPropietario -> "SELECT id_consorcio FROM propietario_consorcio WHERE id_user = ?"
            else -> "SELECT id_consorcio FROM consorcio WHERE representante = ?"
        }

    companion object {
        const val ROL_ADMIN = "Administrador/a"
        const val ROL_ADMIN_SISTEMA = "Administrador del sistema"
        const val ROL_ASISTENTE = "Asistente"
        const val ROL_PROVEEDOR = "Proveedor"
        const val ROL_PROPIETARIO = "Propietario/Residente"
    }

    /** Igual que la propiedad `iniciales` del modelo de Django. */
    val iniciales: String
        get() = nameSurname.split(" ").filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "?" }
}
