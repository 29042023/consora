package com.example.consora_mobile.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Base de datos local (SQLite) de la app. Las tablas siguen la misma estructura
 * que los modelos de Django (web/models.py) para poder sincronizar más adelante.
 */
class ConsoraDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE roles (
                id_rol INTEGER PRIMARY KEY AUTOINCREMENT,
                rol TEXT NOT NULL UNIQUE
            )"""
        )
        db.execSQL(
            """CREATE TABLE estados_cuenta (
                id_estado_cuenta INTEGER PRIMARY KEY AUTOINCREMENT,
                estado TEXT NOT NULL,
                motivo TEXT
            )"""
        )
        db.execSQL(
            """CREATE TABLE user (
                id_user INTEGER PRIMARY KEY AUTOINCREMENT,
                img TEXT,
                name_surname TEXT NOT NULL,
                password TEXT NOT NULL,
                mail TEXT NOT NULL UNIQUE COLLATE NOCASE,
                role INTEGER NOT NULL REFERENCES roles(id_rol),
                dni TEXT UNIQUE,
                phone TEXT,
                date_nacimiento TEXT,
                id_estado_cuenta INTEGER REFERENCES estados_cuenta(id_estado_cuenta),
                created_at TEXT NOT NULL DEFAULT (datetime('now'))
            )"""
        )
        db.execSQL(
            """CREATE TABLE administrador (
                id_administrador INTEGER PRIMARY KEY AUTOINCREMENT,
                id_user INTEGER NOT NULL UNIQUE REFERENCES user(id_user) ON DELETE CASCADE,
                matricula TEXT NOT NULL UNIQUE,
                fecha_alta_matricula TEXT,
                ente_otorgante TEXT
            )"""
        )
        // Asistente y el administrador para el que trabaja (ambos son filas de user)
        db.execSQL(
            """CREATE TABLE asistente (
                id_asistente INTEGER PRIMARY KEY AUTOINCREMENT,
                id_user INTEGER NOT NULL UNIQUE REFERENCES user(id_user) ON DELETE CASCADE,
                id_admin INTEGER NOT NULL REFERENCES user(id_user) ON DELETE CASCADE,
                created_at TEXT NOT NULL DEFAULT (datetime('now')),
                habilitado INTEGER NOT NULL DEFAULT 1
            )"""
        )
        // Edificios en los que el administrador habilitó a cada asistente (id_user = id_asistente de la tabla asistente)
        db.execSQL(
            """CREATE TABLE edificios_asistente (
                edificios_asistente INTEGER PRIMARY KEY AUTOINCREMENT,
                id_consorcio INTEGER NOT NULL REFERENCES consorcio(id_consorcio) ON DELETE CASCADE,
                id_user INTEGER NOT NULL REFERENCES asistente(id_asistente) ON DELETE CASCADE,
                created_at TEXT NOT NULL DEFAULT (datetime('now')),
                habilitado INTEGER NOT NULL DEFAULT 1,
                UNIQUE (id_consorcio, id_user)
            )"""
        )
        // Tareas que el administrador le asigna a un asistente (estado: "en proceso" o "completado")
        db.execSQL(
            """CREATE TABLE tareas_asistente (
                id_tarea INTEGER PRIMARY KEY AUTOINCREMENT,
                id_user INTEGER NOT NULL REFERENCES user(id_user) ON DELETE CASCADE,
                descripcion TEXT NOT NULL,
                estado TEXT NOT NULL DEFAULT 'en proceso' CHECK (estado IN ('en proceso', 'completado')),
                created_at TEXT NOT NULL DEFAULT (datetime('now'))
            )"""
        )
        // Perfil del Marketplace: el dueño puede ser un proveedor o un administrador.
        // link = web propia del proveedor, si tiene
        db.execSQL(
            """CREATE TABLE cuentas_marketplace (
                id_cuentas_marketplace INTEGER PRIMARY KEY AUTOINCREMENT,
                dueno INTEGER NOT NULL UNIQUE REFERENCES user(id_user) ON DELETE CASCADE,
                descripcion TEXT,
                link TEXT
            )"""
        )
        // Fotos de las publicaciones de cada cuenta (archivos en img/publicaciones)
        db.execSQL(
            """CREATE TABLE img_cuentas_marketplace (
                id_img_cuentas_marketplace INTEGER PRIMARY KEY AUTOINCREMENT,
                cuenta INTEGER NOT NULL REFERENCES cuentas_marketplace(id_cuentas_marketplace) ON DELETE CASCADE,
                img TEXT NOT NULL,
                descripcion TEXT,
                creation TEXT NOT NULL DEFAULT (datetime('now'))
            )"""
        )
        // Reseñas de una cuenta del Marketplace; cada usuario deja una sola por cuenta
        db.execSQL(
            """CREATE TABLE resenas (
                id_resenas_cuentas_marketplace INTEGER PRIMARY KEY AUTOINCREMENT,
                cuentas_marketplace INTEGER NOT NULL REFERENCES cuentas_marketplace(id_cuentas_marketplace) ON DELETE CASCADE,
                autor INTEGER NOT NULL REFERENCES user(id_user) ON DELETE CASCADE,
                estrellas INTEGER NOT NULL CHECK (estrellas BETWEEN 1 AND 5),
                descripcion TEXT,
                creation TEXT NOT NULL DEFAULT (datetime('now')),
                UNIQUE (cuentas_marketplace, autor)
            )"""
        )
        db.execSQL(
            """CREATE TABLE consorcio (
                id_consorcio INTEGER PRIMARY KEY AUTOINCREMENT,
                representante INTEGER NOT NULL REFERENCES user(id_user),
                name_consorcio TEXT NOT NULL,
                img_consorcio TEXT,
                descripcion TEXT,
                direccion TEXT NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE tipo_deuda (
                id_tipo_deuda INTEGER PRIMARY KEY AUTOINCREMENT,
                nombre TEXT NOT NULL UNIQUE
            )"""
        )
        // Deuda de un consorcio (servicio, impuesto, trabajo...). Queda pendiente
        // hasta que se le carga su factura.
        db.execSQL(
            """CREATE TABLE deuda (
                id_deuda INTEGER PRIMARY KEY AUTOINCREMENT,
                id_consorcio INTEGER NOT NULL REFERENCES consorcio(id_consorcio) ON DELETE CASCADE,
                id_tipo_deuda INTEGER NOT NULL REFERENCES tipo_deuda(id_tipo_deuda),
                descripcion TEXT NOT NULL,
                proveedor TEXT,
                importe REAL NOT NULL,
                vencimiento TEXT,
                id_contrato_proveedor INTEGER REFERENCES contratos_proveedores(id_contrato_proveedor) ON DELETE SET NULL,
                nro_cuota INTEGER
            )"""
        )
        db.execSQL(
            """CREATE TABLE factura (
                id_factura INTEGER PRIMARY KEY AUTOINCREMENT,
                id_consorcio INTEGER NOT NULL REFERENCES consorcio(id_consorcio) ON DELETE CASCADE,
                id_deuda INTEGER NOT NULL UNIQUE REFERENCES deuda(id_deuda),
                id_user INTEGER NOT NULL REFERENCES user(id_user),
                proveedor TEXT NOT NULL,
                nro_comprobante TEXT NOT NULL,
                fecha TEXT NOT NULL,
                importe REAL NOT NULL,
                archivo TEXT,
                creado_en TEXT NOT NULL DEFAULT (datetime('now'))
            )"""
        )
        db.execSQL(
            """CREATE TABLE agenda_evento (
                id_evento INTEGER PRIMARY KEY AUTOINCREMENT,
                id_user INTEGER NOT NULL REFERENCES user(id_user) ON DELETE CASCADE,
                id_consorcio INTEGER REFERENCES consorcio(id_consorcio) ON DELETE SET NULL,
                titulo TEXT NOT NULL,
                descripcion TEXT,
                fecha TEXT NOT NULL,
                hora TEXT
            )"""
        )
        // Chats (misma estructura que TipoChat, Chat, MiembroChat y MensajeChat de Django)
        db.execSQL(
            """CREATE TABLE tipo_chat (
                id_tipo_chat INTEGER PRIMARY KEY AUTOINCREMENT,
                name_chat TEXT NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE chat (
                id_chat INTEGER PRIMARY KEY AUTOINCREMENT,
                id_tipo_chat INTEGER NOT NULL REFERENCES tipo_chat(id_tipo_chat)
            )"""
        )
        db.execSQL(
            """CREATE TABLE miembros_chat (
                id_miembro INTEGER PRIMARY KEY AUTOINCREMENT,
                id_chat INTEGER NOT NULL REFERENCES chat(id_chat) ON DELETE CASCADE,
                id_user INTEGER NOT NULL REFERENCES user(id_user) ON DELETE CASCADE,
                UNIQUE (id_chat, id_user)
            )"""
        )
        db.execSQL(
            """CREATE TABLE mensaje_chat (
                id_mensaje INTEGER PRIMARY KEY AUTOINCREMENT,
                id_chat INTEGER NOT NULL REFERENCES chat(id_chat) ON DELETE CASCADE,
                id_user INTEGER NOT NULL REFERENCES user(id_user) ON DELETE CASCADE,
                mensaje TEXT NOT NULL DEFAULT '',
                archivo TEXT,
                nombre_archivo TEXT,
                id_votacion_contrato INTEGER REFERENCES votacion_contrato(id_votacion_contrato) ON DELETE SET NULL,
                id_reunion_participante INTEGER REFERENCES reunion_participante(id_reunion_participante) ON DELETE SET NULL,
                enviado_en TEXT NOT NULL
            )"""
        )
        // Propietarios/residentes y la unidad que ocupan en cada consorcio
        // (en la web todavía no existe esta relación; habría que agregarla en Django)
        db.execSQL(
            """CREATE TABLE propietario_consorcio (
                id_propietario_consorcio INTEGER PRIMARY KEY AUTOINCREMENT,
                id_user INTEGER NOT NULL REFERENCES user(id_user) ON DELETE CASCADE,
                id_consorcio INTEGER NOT NULL REFERENCES consorcio(id_consorcio) ON DELETE CASCADE,
                unidad TEXT,
                UNIQUE (id_user, id_consorcio)
            )"""
        )
        // Título (PDF) de cada administrador
        db.execSQL(
            """CREATE TABLE titulo_administrador (
                id_titulo INTEGER PRIMARY KEY AUTOINCREMENT,
                id_user INTEGER NOT NULL REFERENCES user(id_user) ON DELETE CASCADE,
                titulo TEXT NOT NULL
            )"""
        )
        // Votaciones de un consorcio. tipo: 'comun' (Sí / No / Abstención) o 'contratacion'
        db.execSQL(
            """CREATE TABLE votacion (
                id_votacion INTEGER PRIMARY KEY AUTOINCREMENT,
                id_consorcio INTEGER NOT NULL REFERENCES consorcio(id_consorcio) ON DELETE CASCADE,
                tipo TEXT NOT NULL CHECK (tipo IN ('comun', 'contratacion')),
                detalle TEXT NOT NULL,
                fecha TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
                fecha_limite TEXT NOT NULL
            )"""
        )
        // Voto de un propietario o del administrador: 'si' | 'no' | 'abstencion', o en una
        // contratación el id de la propuesta elegida ('ninguno' = no contratar a nadie)
        db.execSQL(
            """CREATE TABLE votacion_user (
                id_votacion_user INTEGER PRIMARY KEY AUTOINCREMENT,
                id_user INTEGER NOT NULL REFERENCES user(id_user) ON DELETE CASCADE,
                id_votacion INTEGER NOT NULL REFERENCES votacion(id_votacion) ON DELETE CASCADE,
                voto TEXT NOT NULL,
                votado_en TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
                UNIQUE (id_votacion, id_user)
            )"""
        )
        // Proveedor invitado a una contratación (la invitación le llega por chat).
        // aceptacion: NULL = no respondió, 1 = aceptó participar, 0 = rechazó
        db.execSQL(
            """CREATE TABLE votacion_contrato (
                id_votacion_contrato INTEGER PRIMARY KEY AUTOINCREMENT,
                id_proveedor INTEGER NOT NULL REFERENCES user(id_user) ON DELETE CASCADE,
                id_votacion INTEGER NOT NULL REFERENCES votacion(id_votacion) ON DELETE CASCADE,
                propuesta_pdf TEXT,
                aceptacion INTEGER,
                contratado INTEGER NOT NULL DEFAULT 0
            )"""
        )
        // Documentos de una votación (presupuestos, planos, acta...)
        db.execSQL(
            """CREATE TABLE doc_votacion (
                id_doc_votacion INTEGER PRIMARY KEY AUTOINCREMENT,
                id_votacion INTEGER NOT NULL REFERENCES votacion(id_votacion) ON DELETE CASCADE,
                doc TEXT NOT NULL
            )"""
        )
        // Reuniones del consorcio (misma estructura que Reunion, ReunionParticipante y DocReunion de Django).
        // modalidad: 'virtual' (Google Meet) o 'presencial'; estado: 'programada' o 'cancelada'
        // (en curso / finalizada salen de la fecha y hora)
        db.execSQL(
            """CREATE TABLE reunion (
                id_reunion INTEGER PRIMARY KEY AUTOINCREMENT,
                id_consorcio INTEGER NOT NULL REFERENCES consorcio(id_consorcio) ON DELETE CASCADE,
                id_organizador INTEGER NOT NULL REFERENCES user(id_user),
                id_evento INTEGER REFERENCES agenda_evento(id_evento) ON DELETE SET NULL,
                titulo TEXT NOT NULL,
                descripcion TEXT,
                modalidad TEXT NOT NULL CHECK (modalidad IN ('virtual', 'presencial')),
                lugar TEXT,
                fecha_inicio TEXT NOT NULL,
                fecha_fin TEXT NOT NULL,
                meet_link TEXT,
                meet_codigo TEXT,
                google_event_id TEXT,
                estado TEXT NOT NULL DEFAULT 'programada',
                created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
            )"""
        )
        // Quién participa y su respuesta. Los proveedores se invitan como opcionales (es_opcional = 1):
        // la invitación les llega por chat y participan solo si la aceptan.
        db.execSQL(
            """CREATE TABLE reunion_participante (
                id_reunion_participante INTEGER PRIMARY KEY AUTOINCREMENT,
                id_reunion INTEGER NOT NULL REFERENCES reunion(id_reunion) ON DELETE CASCADE,
                id_user INTEGER NOT NULL REFERENCES user(id_user) ON DELETE CASCADE,
                invitado_por INTEGER REFERENCES user(id_user) ON DELETE SET NULL,
                rol_participante TEXT NOT NULL CHECK (rol_participante IN ('propietario', 'asistente', 'proveedor', 'administrador')),
                es_opcional INTEGER NOT NULL DEFAULT 0,
                respuesta TEXT NOT NULL DEFAULT 'pendiente' CHECK (respuesta IN ('pendiente', 'acepta', 'rechaza')),
                respondido_en TEXT,
                asistio INTEGER,
                notificado_en TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
                UNIQUE (id_reunion, id_user)
            )"""
        )
        // Documentos de una reunión: orden del día, acta u otros
        db.execSQL(
            """CREATE TABLE doc_reunion (
                id_doc_reunion INTEGER PRIMARY KEY AUTOINCREMENT,
                id_reunion INTEGER NOT NULL REFERENCES reunion(id_reunion) ON DELETE CASCADE,
                doc TEXT NOT NULL,
                tipo TEXT NOT NULL DEFAULT 'otro' CHECK (tipo IN ('orden_del_dia', 'acta', 'otro')),
                subido_en TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
            )"""
        )
        // Contratos con proveedores: al contado o en cuotas; cada cuota es una deuda
        db.execSQL(
            """CREATE TABLE forma_pago_contrato (
                id_forma_pago_contrato INTEGER PRIMARY KEY AUTOINCREMENT,
                tipo TEXT NOT NULL CHECK (tipo IN ('contado', 'cuotas')),
                cuotas INTEGER NOT NULL DEFAULT 1,
                UNIQUE (tipo, cuotas)
            )"""
        )
        db.execSQL(
            """CREATE TABLE contratos_proveedores (
                id_contrato_proveedor INTEGER PRIMARY KEY AUTOINCREMENT,
                id_proveedor INTEGER NOT NULL REFERENCES user(id_user),
                id_consorcio INTEGER NOT NULL REFERENCES consorcio(id_consorcio) ON DELETE CASCADE,
                id_votacion_contrato INTEGER UNIQUE REFERENCES votacion_contrato(id_votacion_contrato) ON DELETE SET NULL,
                descripcion TEXT NOT NULL,
                precio REAL NOT NULL,
                id_forma_pago INTEGER NOT NULL REFERENCES forma_pago_contrato(id_forma_pago_contrato),
                estado TEXT NOT NULL DEFAULT 'vigente' CHECK (estado IN ('vigente', 'finalizado', 'cancelado')),
                fecha TEXT NOT NULL,
                time TEXT NOT NULL DEFAULT (time('now', 'localtime')),
                id_user INTEGER REFERENCES user(id_user) ON DELETE SET NULL,
                -- El trabajo (aparte de los pagos): en proceso hasta que se marca terminado
                trabajo_terminado_en TEXT,
                -- Contrato formal firmado, en PDF (ruta del archivo en la app)
                contrato_pdf TEXT
            )"""
        )
        // Historial de cada edificio: contratos, pagos, votaciones, reuniones y obras
        db.execSQL(
            """CREATE TABLE historial_consorcio (
                id_historial_consorcio INTEGER PRIMARY KEY AUTOINCREMENT,
                id_consorcio INTEGER NOT NULL REFERENCES consorcio(id_consorcio) ON DELETE CASCADE,
                categoria TEXT NOT NULL DEFAULT 'otro' CHECK (categoria IN ('contrato', 'pago', 'votacion', 'reunion', 'obra', 'otro')),
                descripcion TEXT NOT NULL,
                id_user INTEGER REFERENCES user(id_user) ON DELETE SET NULL,
                fecha TEXT NOT NULL,
                time TEXT NOT NULL,
                manual INTEGER NOT NULL DEFAULT 0
            )"""
        )
        SeedData.load(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Mientras sea una base de prueba, se recrea desde cero al cambiar la versión
        listOf("historial_consorcio", "factura", "deuda", "contratos_proveedores", "forma_pago_contrato", "doc_votacion", "votacion_user", "mensaje_chat", "doc_reunion", "reunion_participante", "reunion", "votacion_contrato", "votacion", "titulo_administrador", "propietario_consorcio", "miembros_chat", "chat", "tipo_chat", "agenda_evento", "factura", "deuda", "tipo_deuda", "edificios_asistente", "consorcio", "resenas", "img_cuentas_marketplace", "cuentas_marketplace", "tareas_asistente", "asistente", "administrador", "user", "estados_cuenta", "roles")
            .forEach { db.execSQL("DROP TABLE IF EXISTS $it") }
        onCreate(db)
    }

    companion object {
        private const val DB_NAME = "consora.db"
        private const val DB_VERSION = 21

        @Volatile
        private var instance: ConsoraDatabase? = null

        fun get(context: Context): ConsoraDatabase =
            instance ?: synchronized(this) {
                instance ?: ConsoraDatabase(context).also { instance = it }
            }
    }
}

/** Datos de ejemplo, equivalentes a los del comando seed_data de la web. */
private object SeedData {

    private const val PASSWORD_PRUEBA = "consora123"

    private class Tarea(val asistente: Long, val descripcion: String, val completada: Boolean, val haceDias: Long)

    private class Resena(val cuenta: Long, val autor: Long, val estrellas: Int, val descripcion: String, val haceDias: Long)

    fun load(db: SQLiteDatabase) {
        val roles = listOf(
            "Administrador del sistema", "Administrador/a", "Propietario/Residente", "Proveedor", "Asistente",
        ).associateWith { db.insertOrThrow("roles", null, ContentValues().apply { put("rol", it) }) }

        val activa = insertEstado(db, "Activa", null)
        insertEstado(db, "Suspendida", "Pago pendiente")
        insertEstado(db, "Baja", "Solicitada por el usuario")

        fun usuario(
            mail: String, nombre: String, rol: String, dni: String?, phone: String?, nacimiento: String?,
            img: String? = null,
        ) =
            db.insertOrThrow("user", null, ContentValues().apply {
                put("img", img)
                put("mail", mail)
                put("name_surname", nombre)
                put("password", PasswordHasher.hash(PASSWORD_PRUEBA))
                put("role", roles.getValue(rol))
                put("dni", dni)
                put("phone", phone)
                put("date_nacimiento", nacimiento)
                put("id_estado_cuenta", activa)
            })

        val franco = usuario(
            "tomasfrancorusso@gmail.com", "Franco Russo", "Administrador/a", null, null, null, "asset:img/usuarios/avatar_franco.jpg",
        )
        val maria = usuario("maria.gonzalez@consora.com", "María González", "Administrador/a", "30111222", "+5491111111111", "1985-03-12")
        // id_user del asistente -> id_asistente (para edificios_asistente)
        val idAsistente = mutableMapOf<Long, Long>()
        // Equipo de María (los del seed_data de la web)
        val (lucia, martin) = listOf(
            usuario("lucia.r@consora.com", "Lucía Ramírez", "Asistente", "35222333", "+5491122222222", "1996-07-01"),
            usuario("martin.g@consora.com", "Martín Gómez", "Asistente", "34333444", "+5491133333333", "1994-11-20"),
            usuario("sofia.p@consora.com", "Sofía Pérez", "Asistente", "36444555", "+5491144444444", "1998-02-15"),
        ).onEach { idAsistente[it] = insertAsistente(db, it, maria) }
        // Equipo de Franco
        val (valentina, tomas, camila) = listOf(
            usuario("valentina.lopez@consora.com.ar", "Valentina López", "Asistente", "37111222", "+5491170001001", "1999-04-08", "asset:img/usuarios/avatar_valentina.webp"),
            usuario("tomas.acosta@consora.com.ar", "Tomás Acosta", "Asistente", "36999888", "+5491170002002", "1997-08-23"),
            usuario("camila.diaz@consora.com.ar", "Camila Díaz", "Asistente", "38222111", "+5491170003003", "2000-01-30"),
            usuario("nicolas.herrera@consora.com.ar", "Nicolás Herrera", "Asistente", "35888777", "+5491170004004", "1995-12-11"),
            usuario("julieta.morales@consora.com.ar", "Julieta Morales", "Asistente", "39333444", "+5491170005005", "2001-06-17"),
        ).onEach { idAsistente[it] = insertAsistente(db, it, franco) }

        // Tareas de ejemplo (asistente, descripción, completada, hace cuántos días)
        listOf(
            Tarea(valentina, "Pedir presupuesto de pintura para el palier del Edificio Norte", false, 2),
            Tarea(valentina, "Cargar las facturas de luz de octubre", true, 6),
            Tarea(valentina, "Avisar a los propietarios del corte de agua del jueves", true, 9),
            Tarea(tomas, "Coordinar la revisión mensual del ascensor con AscenSur", false, 1),
            Tarea(tomas, "Reclamar a ElectroHogar el presupuesto de las luces de emergencia", false, 4),
            Tarea(camila, "Armar el orden del día de la próxima asamblea", false, 3),
            Tarea(lucia, "Responder el reclamo de Carlos por la filtración", true, 5),
            Tarea(martin, "Renovar la póliza del seguro del edificio", false, 7),
        ).forEach { t ->
            db.insertOrThrow("tareas_asistente", null, ContentValues().apply {
                put("id_user", t.asistente)
                put("descripcion", t.descripcion)
                put("estado", if (t.completada) TareaAsistente.COMPLETADO else TareaAsistente.EN_PROCESO)
                put("created_at", LocalDateTime.now().minusDays(t.haceDias).format(ChatFormato.FECHA_HORA))
            })
        }
        val carlos = usuario("carlos.perez@mail.com", "Carlos Pérez", "Propietario/Residente", "28444555", "+5491144444444", "1978-05-05")
        val ana = usuario("ana.fernandez@mail.com", "Ana Fernández", "Propietario/Residente", "31555666", "+5491155555555", "1990-09-18")
        // Propietario con correo real: para probar las invitaciones de Google Calendar desde la web
        val francoDev = usuario("franco.russo.dev@gmail.com", "Franco Tomás Russo", "Propietario/Residente", null, null, null)
        // "asset:" = imagen incluida en la app (assets/img/...); si no, es la ruta de un archivo
        val electro = usuario("contacto@electrohogar.com", "ElectroHogar SRL", "Proveedor", null, "+5491166666666", null, "asset:img/usuarios/avatar_electrohogar.png")
        val cuentaElectro = insertCuentaMarketplace(
            db, electro,
            "Instalaciones eléctricas, tableros y luces de emergencia para edificios. Presupuestos sin cargo.",
            "https://www.electrohogar.com",
        )
        // Una publicación de ejemplo (imagen incluida en la app)
        db.insertOrThrow("img_cuentas_marketplace", null, ContentValues().apply {
            put("cuenta", cuentaElectro)
            put("img", "asset:img/publicaciones/publicacion_electrohogar.png")
            put("descripcion", "Llegaron los nuevos electrodomésticos: heladeras, lavarropas, microondas y más. Consultanos por precios para consorcios.")
            put("creation", LocalDateTime.now().minusDays(3).format(ChatFormato.FECHA_HORA))
        })
        // Proveedores del Marketplace (sin foto: se muestran con iniciales), con su descripción y web
        val (ascensur, _, limpieza, plomeria) = listOf(
            Triple(usuario("info@ascensur.com.ar", "AscenSur Ascensores", "Proveedor", null, "+5491167001001", null),
                "Mantenimiento mensual y reparación de ascensores. Guardia técnica 24 hs.", "https://www.ascensur.com.ar"),
            Triple(usuario("contacto@serviciosintegrales.com.ar", "Servicios Integrales SRL", "Proveedor", null, "+5491167002002", null),
                "Mantenimiento general de edificios: albañilería, herrería y pequeñas reparaciones.", null),
            Triple(usuario("ventas@limpiezatotal.com.ar", "Limpieza Total", "Proveedor", null, "+5491167003003", null),
                "Limpieza de espacios comunes, vidrios en altura y final de obra.", "https://www.limpiezatotal.com.ar"),
            Triple(usuario("plomeria.ruiz@mail.com", "Jorge Ruiz Plomería", "Proveedor", null, "+5491167004004", null),
                "Plomero matriculado. Destapaciones, tanques de agua y bombas.", null),
            Triple(usuario("info@pinturasdelsur.com.ar", "Pinturas del Sur", "Proveedor", null, "+5491167005005", null),
                "Pintura de fachadas, palieres y cocheras. Trabajos en altura.", "https://www.pinturasdelsur.com.ar"),
            Triple(usuario("contacto@seguridadplus.com.ar", "Seguridad Plus", "Proveedor", null, "+5491167006006", null),
                "Cámaras, portones automáticos y control de acceso.", "https://www.seguridadplus.com.ar"),
            Triple(usuario("info@jardinesverdes.com.ar", "Jardines Verdes", "Proveedor", null, "+5491167007007", null),
                "Mantenimiento de jardines, terrazas verdes y riego automático.", null),
            Triple(usuario("gasista.medina@mail.com", "Raúl Medina Gasista", "Proveedor", null, "+5491167008008", null),
                "Gasista matriculado. Pruebas de hermeticidad y trámites ante la distribuidora.", null),
            Triple(usuario("info@fumigar.com.ar", "Fumigar Control de Plagas", "Proveedor", null, "+5491167009009", null),
                "Desinsectación, desratización y control de palomas con certificado.", "https://www.fumigar.com.ar"),
            Triple(usuario("cerrajeria24@mail.com", "Cerrajería 24 hs", "Proveedor", null, "+5491167010010", null),
                "Aperturas, cambio de cerraduras y copias de llaves magnéticas.", null),
        ).map { (id, descripcion, link) -> insertCuentaMarketplace(db, id, descripcion, link) }

        // CIMECO: proveedor con logo propio y dos trabajos publicados (igual que en la web)
        val cimeco = usuario(
            "cimeco.adm@gmail.com", "CIMECO Ingeniería & Mantenimiento", "Proveedor", null, "+541167459591", null,
            "asset:img/usuarios/avatar_cimeco.webp",
        )
        val cuentaCimeco = insertCuentaMarketplace(
            db, cimeco,
            "Impermeabilización y mantenimiento de edificios. Más de 10 años protegiendo edificios del agua.\n" +
                "Terrazas, techos, balcones, medianeras, tanques y canaletas. Filtraciones y humedad, " +
                "fisuras en fachadas, pintura y trabajos en altura.\n" +
                "Visita y diagnóstico, presupuesto claro y seguimiento de obra. Atención 24/7.",
            "https://cimeco-impermeabilizaciones.netlify.app",
        )
        listOf(
            Triple("asset:img/publicaciones/publicacion_cimeco_libertador.webp",
                "Impermeabilización y reparación de fachada en un edificio residencial sobre Av. del Libertador, CABA. " +
                    "Montamos andamio con bandeja de protección peatonal para trabajar con seguridad sin interrumpir " +
                    "el ingreso al edificio.", 12L),
            Triple("asset:img/publicaciones/publicacion_cimeco_obra.webp",
                "Ingeniería en impermeabilizaciones: trabajo en altura con andamio y bandeja de protección peatonal. " +
                    "Visita y diagnóstico, presupuesto claro y seguimiento de obra. ¡Consultanos!", 4L),
        ).forEach { (img, texto, dias) ->
            db.insertOrThrow("img_cuentas_marketplace", null, ContentValues().apply {
                put("cuenta", cuentaCimeco)
                put("img", img)
                put("descripcion", texto)
                put("creation", LocalDateTime.now().minusDays(dias).format(ChatFormato.FECHA_HORA))
            })
        }

        // Reseñas de ejemplo (cuenta, autor, estrellas, texto, hace cuántos días)
        listOf(
            Resena(cuentaElectro, franco, 5, "Cambiaron todas las luces de emergencia del Edificio Norte en un día. Muy prolijos.", 20),
            Resena(cuentaElectro, maria, 4, "Buen trabajo con el tablero general, aunque el presupuesto tardó un poco.", 45),
            Resena(cuentaElectro, carlos, 5, "Atentos y puntuales.", 8),
            Resena(ascensur, franco, 4, "Cumplen con el mantenimiento mensual. La guardia responde rápido.", 30),
            Resena(ascensur, ana, 3, "El ascensor quedó funcionando, pero tuvieron que venir dos veces.", 12),
            Resena(limpieza, maria, 5, "Dejaron los vidrios del hall impecables.", 15),
            Resena(plomeria, carlos, 4, "Destapó la cañería del palier sin romper nada.", 25),
            Resena(plomeria, franco, 5, "Excelente con el cambio de bomba del tanque.", 5),
        ).forEach { r ->
            db.insertOrThrow("resenas", null, ContentValues().apply {
                put("cuentas_marketplace", r.cuenta)
                put("autor", r.autor)
                put("estrellas", r.estrellas)
                put("descripcion", r.descripcion)
                put("creation", LocalDateTime.now().minusDays(r.haceDias).format(ChatFormato.FECHA_HORA))
            })
        }

        insertAdministrador(db, franco, "RPA-67890", null)
        // Licencia de ejemplo (PDF de prueba, sin validez) incluida en la app
        db.insertOrThrow("titulo_administrador", null, ContentValues().apply {
            put("id_user", franco)
            put("titulo", "asset:docs/titulos/licencia_franco_russo.pdf")
        })
        insertAdministrador(db, maria, "RPA-12345", "2015-02-10")

        // Fotos incluidas en la app para algunos consorcios
        val fotos = mapOf(
            "Edificio San Martín" to "asset:img/consorcios/consorcio_san_martin.png",
            "Edificio Norte" to "asset:img/consorcios/consorcio_norte.png",
        )
        val consorcioIds = mutableMapOf<String, Long>()
        listOf(
            Triple("Edificio Las Palmas", "Av. Rivadavia 4520, CABA", "Edificio de 42 unidades en Av. Rivadavia"),
            Triple("Torres del Sol", "Bv. Illia 1210, Córdoba", "Complejo de 96 unidades en Córdoba"),
            Triple("Edificio Belgrano", "Av. Cabildo 2140, CABA", "Edificio de 76 unidades en Belgrano"),
            Triple("Edificio San Martín", "San Martín 850, CABA", "Edificio de 30 unidades"),
            Triple("Edificio Norte", "Av. del Libertador 6200, CABA", "Edificio de 54 unidades"),
        ).forEach { (nombre, direccion, descripcion) ->
            consorcioIds[nombre] = db.insertOrThrow("consorcio", null, ContentValues().apply {
                // En la app, Franco administra dos de los consorcios para tener datos de prueba
                put("representante", if (nombre in listOf("Edificio San Martín", "Edificio Norte")) franco else maria)
                put("name_consorcio", nombre)
                put("direccion", direccion)
                put("descripcion", descripcion)
                put("img_consorcio", fotos[nombre])
            })
        }

        // Unidad de cada propietario
        listOf(
            Triple(carlos, "Edificio San Martín", "4° B"),
            Triple(ana, "Edificio Norte", "2° A"),
            Triple(francoDev, "Edificio Norte", "5° C"),
        ).forEach { (usuario, consorcio, unidad) ->
            db.insertOrThrow("propietario_consorcio", null, ContentValues().apply {
                put("id_user", usuario)
                put("id_consorcio", consorcioIds.getValue(consorcio))
                put("unidad", unidad)
            })
        }

        // Edificios habilitados para cada asistente (asistente, edificio, habilitado)
        listOf(
            Triple(valentina, "Edificio San Martín", true),
            Triple(valentina, "Edificio Norte", true),
            Triple(tomas, "Edificio Norte", true),
            Triple(camila, "Edificio San Martín", false),
            Triple(lucia, "Edificio Las Palmas", true),
            Triple(lucia, "Edificio Belgrano", true),
            Triple(martin, "Torres del Sol", true),
        ).forEach { (asistente, consorcio, habilitado) ->
            db.insertOrThrow("edificios_asistente", null, ContentValues().apply {
                put("id_consorcio", consorcioIds.getValue(consorcio))
                put("id_user", idAsistente.getValue(asistente))
                put("habilitado", if (habilitado) 1 else 0)
            })
        }

        loadFacturasYAgenda(db, franco)
        loadChats(db)
        loadVotaciones(db)
        loadReuniones(db)
        loadContratosEHistorial(db)
    }

    /** Contratos con proveedores (cada cuota es una deuda) e historial de los edificios, iguales a los de la web. */
    fun loadContratosEHistorial(db: SQLiteDatabase) {
        fun userId(mail: String) = db.rawQuery("SELECT id_user FROM user WHERE mail = ?", arrayOf(mail))
            .use { it.moveToFirst(); it.getLong(0) }
        fun consorcioId(nombre: String) = db.rawQuery(
            "SELECT id_consorcio FROM consorcio WHERE name_consorcio = ?", arrayOf(nombre),
        ).use { it.moveToFirst(); it.getLong(0) }
        val franco = userId("tomasfrancorusso@gmail.com")
        val sanMartin = consorcioId("Edificio San Martín")
        val norte = consorcioId("Edificio Norte")
        val hoy = LocalDate.now()

        val formas = (listOf("contado" to 1) + listOf(2, 3, 6, 12).map { "cuotas" to it }).associate { (tipo, n) ->
            n to db.insertOrThrow("forma_pago_contrato", null, ContentValues().apply { put("tipo", tipo); put("cuotas", n) })
        }
        val tipoContrato = db.insertOrThrow("tipo_deuda", null, ContentValues().apply { put("nombre", "Contrato con proveedor") })

        fun contrato(consorcio: Long, mail: String, nombreProveedor: String, descripcion: String, precio: Double, cuotas: Int,
                     primerPago: LocalDate, pagas: Int, nroBase: String, propuesta: Long? = null) {
            val id = db.insertOrThrow("contratos_proveedores", null, ContentValues().apply {
                put("id_proveedor", userId(mail))
                put("id_consorcio", consorcio)
                put("id_votacion_contrato", propuesta)
                put("descripcion", descripcion)
                put("precio", precio)
                put("id_forma_pago", formas.getValue(cuotas))
                put("estado", if (pagas == cuotas) "finalizado" else "vigente")
                put("fecha", primerPago.toString())
                put("id_user", franco)
            })
            val importe = Math.round(precio / cuotas * 100) / 100.0
            for (i in 1..cuotas) {
                val vence = primerPago.plusMonths((i - 1).toLong())
                val monto = if (i < cuotas) importe else precio - importe * (cuotas - 1)
                val deuda = db.insertOrThrow("deuda", null, ContentValues().apply {
                    put("id_consorcio", consorcio)
                    put("id_tipo_deuda", tipoContrato)
                    put("descripcion", "$descripcion · " + if (cuotas == 1) "pago único" else "cuota $i/$cuotas")
                    put("proveedor", nombreProveedor)
                    put("importe", monto)
                    put("vencimiento", vence.toString())
                    put("id_contrato_proveedor", id)
                    put("nro_cuota", i)
                })
                if (i <= pagas) db.insertOrThrow("factura", null, ContentValues().apply {
                    put("id_consorcio", consorcio)
                    put("id_deuda", deuda)
                    put("id_user", franco)
                    put("proveedor", nombreProveedor)
                    put("nro_comprobante", "$nroBase-%05d".format(i))
                    put("fecha", vence.minusDays(2).toString())
                    put("importe", monto)
                })
            }
        }

        // Ganador de la votación de ascensores: 12 cuotas, van 2 pagas
        val propuestaAscensur = db.rawQuery(
            "SELECT id_votacion_contrato FROM votacion_contrato WHERE contratado = 1 AND id_proveedor = ?",
            arrayOf(userId("info@ascensur.com.ar").toString()),
        ).use { if (it.moveToFirst()) it.getLong(0) else null }
        contrato(sanMartin, "info@ascensur.com.ar", "AscenSur Ascensores", "Mantenimiento anual de ascensores 2027", 2400000.0, 12,
            hoy.minusDays(40), 2, "0003-0002", propuestaAscensur)
        contrato(sanMartin, "contacto@electrohogar.com", "ElectroHogar SRL", "Cambio de luces de emergencia", 540000.0, 3,
            hoy.minusDays(20), 1, "0002-0007")
        contrato(norte, "plomeria.ruiz@mail.com", "Jorge Ruiz Plomería", "Cambio de bomba del tanque de agua", 385000.0, 1,
            hoy.minusDays(35), 1, "0001-0004")

        // Estado de los trabajos: la bomba y las luces ya se terminaron (las luces se siguen pagando)
        listOf("Cambio de bomba del tanque de agua" to 33L, "Cambio de luces de emergencia" to 10L).forEach { (descripcion, dias) ->
            db.update("contratos_proveedores", ContentValues().apply { put("trabajo_terminado_en", hoy.minusDays(dias).toString()) },
                "descripcion = ?", arrayOf(descripcion))
        }

        class Evento(val consorcio: Long, val categoria: String, val descripcion: String, val dias: Long, val hora: String, val manual: Boolean = false)
        listOf(
            Evento(sanMartin, "obra", "Se impermeabilizó la terraza y se cambiaron las membranas.", 210, "09:00", true),
            Evento(sanMartin, "obra", "Pintura completa de la fachada.", 120, "09:00", true),
            Evento(sanMartin, "votacion", "Se cerró la votación: Mantenimiento anual de los ascensores para 2027.", 45, "18:00"),
            Evento(sanMartin, "votacion", "Por votación se eligió a AscenSur Ascensores: Mantenimiento anual de los ascensores para 2027.", 44, "10:15"),
            Evento(sanMartin, "contrato", "Se contrató a AscenSur Ascensores: Mantenimiento anual de ascensores 2027 (\$ 2.400.000, 12 cuotas).", 44, "10:20"),
            Evento(sanMartin, "pago", "Se pagó \"Mantenimiento anual de ascensores 2027 · cuota 1/12\" a AscenSur Ascensores (\$ 200.000).", 42, "11:00"),
            Evento(sanMartin, "contrato", "Se contrató a ElectroHogar SRL: Cambio de luces de emergencia (\$ 540.000, 3 cuotas).", 22, "16:30"),
            Evento(sanMartin, "pago", "Se pagó \"Cambio de luces de emergencia · cuota 1/3\" a ElectroHogar SRL (\$ 180.000).", 22, "17:00"),
            Evento(sanMartin, "reunion", "Se hizo la reunión \"Revisión del contrato de ascensores\" por Google Meet.", 15, "19:00"),
            Evento(sanMartin, "pago", "Se pagó \"Mantenimiento anual de ascensores 2027 · cuota 2/12\" a AscenSur Ascensores (\$ 200.000).", 12, "11:30"),
            Evento(sanMartin, "votacion", "Se cerró la votación: Cambiar el horario de limpieza de los espacios comunes a la mañana.", 3, "09:00"),
            Evento(norte, "obra", "Se reemplazó el portero eléctrico de la entrada.", 160, "09:00", true),
            Evento(norte, "contrato", "Se contrató a Jorge Ruiz Plomería: Cambio de bomba del tanque de agua (\$ 385.000, contado).", 37, "12:00"),
            Evento(norte, "pago", "Se pagó \"Cambio de bomba del tanque de agua · pago único\" a Jorge Ruiz Plomería (\$ 385.000).", 35, "10:00"),
            Evento(norte, "contrato", "Se terminó de pagar el contrato con Jorge Ruiz Plomería: Cambio de bomba del tanque de agua.", 35, "10:01"),
            Evento(norte, "reunion", "Se programó la reunión \"Asamblea ordinaria\" en el SUM del edificio.", 1, "20:00"),
        ).forEach { e ->
            db.insertOrThrow("historial_consorcio", null, ContentValues().apply {
                put("id_consorcio", e.consorcio)
                put("categoria", e.categoria)
                put("descripcion", e.descripcion)
                put("id_user", franco)
                put("fecha", hoy.minusDays(e.dias).toString())
                put("time", "${e.hora}:00")
                put("manual", if (e.manual) 1 else 0)
            })
        }
    }

    /**
     * Reuniones de los consorcios de Franco (iguales a las de la web): la asamblea presencial que ya
     * estaba en la agenda, una virtual con dos proveedores invitados (uno aceptó, otro no respondió)
     * y una pasada con la asistencia cargada.
     */
    fun loadReuniones(db: SQLiteDatabase) {
        fun userId(mail: String) = db.rawQuery("SELECT id_user FROM user WHERE mail = ?", arrayOf(mail))
            .use { it.moveToFirst(); it.getLong(0) }
        fun consorcioId(nombre: String) = db.rawQuery(
            "SELECT id_consorcio FROM consorcio WHERE name_consorcio = ?", arrayOf(nombre),
        ).use { it.moveToFirst(); it.getLong(0) }
        val f = ChatFormato.FECHA_HORA
        val franco = userId("tomasfrancorusso@gmail.com")
        val hoy = LocalDate.now()
        val individual = db.rawQuery("SELECT id_tipo_chat FROM tipo_chat WHERE name_chat = 'Individual'", null)
            .use { it.moveToFirst(); it.getLong(0) }

        fun reunion(consorcio: Long, titulo: String, descripcion: String, modalidad: String, inicio: LocalDateTime,
                    minutos: Long, lugar: String? = null, link: String? = null, eventoTitulo: String? = null): Pair<Long, LocalDateTime> {
            val creada = inicio.minusDays(5)
            val evento = eventoTitulo?.let { t ->
                db.rawQuery("SELECT id_evento FROM agenda_evento WHERE id_user = ? AND titulo = ?", arrayOf(franco.toString(), t))
                    .use { if (it.moveToFirst()) it.getLong(0) else null }
            } ?: db.insertOrThrow("agenda_evento", null, ContentValues().apply {
                put("id_user", franco)
                put("id_consorcio", consorcio)
                put("titulo", "Reunión: $titulo")
                put("descripcion", descripcion)
                put("fecha", inicio.toLocalDate().toString())
                put("hora", inicio.toLocalTime().toString())
            })
            val id = db.insertOrThrow("reunion", null, ContentValues().apply {
                put("id_consorcio", consorcio)
                put("id_organizador", franco)
                put("id_evento", evento)
                put("titulo", titulo)
                put("descripcion", descripcion)
                put("modalidad", modalidad)
                put("lugar", lugar)
                put("fecha_inicio", inicio.format(f))
                put("fecha_fin", inicio.plusMinutes(minutos).format(f))
                put("meet_link", link)
                put("meet_codigo", link?.substringAfterLast('/'))
                put("created_at", creada.format(f))
            })
            // Organizador (confirmado), propietarios y asistentes habilitados del consorcio
            db.execSQL(
                """INSERT INTO reunion_participante (id_reunion, id_user, rol_participante, respuesta, respondido_en, notificado_en)
                   VALUES (?, ?, 'administrador', 'acepta', ?, ?)""", arrayOf<Any>(id, franco, creada.format(f), creada.format(f)),
            )
            db.execSQL(
                """INSERT INTO reunion_participante (id_reunion, id_user, invitado_por, rol_participante, notificado_en)
                   SELECT ?, id_user, ?, 'propietario', ? FROM propietario_consorcio WHERE id_consorcio = ?""",
                arrayOf<Any>(id, franco, creada.format(f), consorcio),
            )
            db.execSQL(
                """INSERT INTO reunion_participante (id_reunion, id_user, invitado_por, rol_participante, notificado_en)
                   SELECT ?, a.id_user, ?, 'asistente', ? FROM edificios_asistente ea JOIN asistente a ON a.id_asistente = ea.id_user
                   WHERE ea.id_consorcio = ? AND ea.habilitado = 1""",
                arrayOf<Any>(id, franco, creada.format(f), consorcio),
            )
            return id to creada
        }

        fun responde(reunion: Pair<Long, LocalDateTime>, mail: String, respuesta: String, asistio: Boolean? = null) =
            db.execSQL(
                "UPDATE reunion_participante SET respuesta = ?, respondido_en = ?, asistio = ? WHERE id_reunion = ? AND id_user = ?",
                arrayOf<Any?>(respuesta, reunion.second.plusHours(3).format(f), asistio?.let { if (it) 1 else 0 }, reunion.first, userId(mail)),
            )

        /** Chat individual entre Franco y el proveedor (lo crea si no existe). */
        fun chatCon(proveedor: Long): Long =
            db.rawQuery(
                """SELECT a.id_chat FROM miembros_chat a JOIN miembros_chat b ON b.id_chat = a.id_chat
                   WHERE a.id_user = ? AND b.id_user = ? LIMIT 1""",
                arrayOf(franco.toString(), proveedor.toString()),
            ).use { if (it.moveToFirst()) it.getLong(0) else null }
                ?: db.insertOrThrow("chat", null, ContentValues().apply { put("id_tipo_chat", individual) }).also { chat ->
                    for (uid in listOf(franco, proveedor)) {
                        db.insertOrThrow("miembros_chat", null, ContentValues().apply {
                            put("id_chat", chat)
                            put("id_user", uid)
                        })
                    }
                }

        /** El proveedor se invita como opcional: le llega por chat y participa si acepta. */
        fun invitarProveedor(reunion: Pair<Long, LocalDateTime>, titulo: String, consorcio: String, inicio: LocalDateTime,
                             mail: String, respuesta: String = "pendiente", asistio: Boolean? = null) {
            val proveedor = userId(mail)
            val (id, creada) = reunion
            val participante = db.insertOrThrow("reunion_participante", null, ContentValues().apply {
                put("id_reunion", id)
                put("id_user", proveedor)
                put("invitado_por", franco)
                put("rol_participante", "proveedor")
                put("es_opcional", 1)
                put("respuesta", respuesta)
                if (respuesta != "pendiente") put("respondido_en", creada.plusHours(2).format(f))
                asistio?.let { put("asistio", if (it) 1 else 0) }
                put("notificado_en", creada.format(f))
            })
            val chat = chatCon(proveedor)
            db.insertOrThrow("mensaje_chat", null, ContentValues().apply {
                put("id_chat", chat)
                put("id_user", franco)
                put("mensaje", "Te invitamos a la reunión \"$titulo\" de $consorcio, el ${Reunion.diaHora(inicio.format(f))}.")
                put("id_reunion_participante", participante)
                put("enviado_en", creada.format(f))
            })
            if (respuesta == "acepta") db.insertOrThrow("mensaje_chat", null, ContentValues().apply {
                put("id_chat", chat)
                put("id_user", proveedor)
                put("mensaje", "Acepto la invitación, ahí estaré.")
                put("enviado_en", creada.plusHours(2).format(f))
            })
        }

        val norte = consorcioId("Edificio Norte")
        val sanMartin = consorcioId("Edificio San Martín")

        val asamblea = reunion(norte, "Asamblea ordinaria", "Rendición de cuentas del semestre y elección del consejo de propietarios.",
            "presencial", hoy.plusDays(4).atTime(19, 30), 90, lugar = "SUM del edificio, planta baja", eventoTitulo = "Reunión de consorcio")
        responde(asamblea, "ana.fernandez@mail.com", "acepta")

        val tituloTerraza = "Presentación de propuestas: impermeabilización de la terraza"
        val inicioTerraza = hoy.plusDays(2).atTime(20, 0)
        val terraza = reunion(norte, tituloTerraza, "Cada proveedor explica su propuesta antes de que cierre la votación.",
            "virtual", inicioTerraza, 60, link = "https://meet.google.com/cns-terr-aza")
        invitarProveedor(terraza, tituloTerraza, "Edificio Norte", inicioTerraza, "cimeco.adm@gmail.com", "acepta")
        invitarProveedor(terraza, tituloTerraza, "Edificio Norte", inicioTerraza, "contacto@serviciosintegrales.com.ar")
        responde(terraza, "ana.fernandez@mail.com", "acepta")

        val tituloAscensores = "Revisión del contrato de ascensores"
        val inicioAscensores = hoy.minusDays(15).atTime(18, 0)
        val ascensores = reunion(sanMartin, tituloAscensores, "AscenSur presentó el plan de mantenimiento para 2027.",
            "virtual", inicioAscensores, 60, link = "https://meet.google.com/asc-ensu-r27")
        invitarProveedor(ascensores, tituloAscensores, "Edificio San Martín", inicioAscensores, "info@ascensur.com.ar", "acepta", asistio = true)
        responde(ascensores, "carlos.perez@mail.com", "acepta", asistio = true)
        db.execSQL("UPDATE reunion_participante SET asistio = 1 WHERE id_reunion = ? AND id_user = ?", arrayOf<Any>(ascensores.first, franco))
    }

    /**
     * Votaciones de los consorcios de Franco (iguales a las de la web). En las de contratación, cada
     * proveedor recibe la invitación por chat; las propuestas en PDF vienen incluidas en la app.
     */
    fun loadVotaciones(db: SQLiteDatabase) {
        fun userId(mail: String) = db.rawQuery("SELECT id_user FROM user WHERE mail = ?", arrayOf(mail))
            .use { it.moveToFirst(); it.getLong(0) }
        fun consorcioId(nombre: String) = db.rawQuery(
            "SELECT id_consorcio FROM consorcio WHERE name_consorcio = ?", arrayOf(nombre),
        ).use { it.moveToFirst(); it.getLong(0) }

        val ahora = LocalDateTime.now()
        val franco = userId("tomasfrancorusso@gmail.com")
        val norte = consorcioId("Edificio Norte")
        val sanMartin = consorcioId("Edificio San Martín")
        val individual = db.rawQuery("SELECT id_tipo_chat FROM tipo_chat WHERE name_chat = 'Individual'", null)
            .use { it.moveToFirst(); it.getLong(0) }

        fun votacion(consorcio: Long, tipo: String, detalle: String, creadaHace: Long, cierraEnDias: Long) =
            db.insertOrThrow("votacion", null, ContentValues().apply {
                put("id_consorcio", consorcio)
                put("tipo", tipo)
                put("detalle", detalle)
                put("fecha", ahora.minusDays(creadaHace).format(ChatFormato.FECHA_HORA))
                put("fecha_limite", ahora.plusDays(cierraEnDias).format(ChatFormato.FECHA_HORA))
            })

        fun votar(v: Long, mail: String, voto: String) =
            db.insertOrThrow("votacion_user", null, ContentValues().apply {
                put("id_votacion", v)
                put("id_user", userId(mail))
                put("voto", voto)
            })

        /** Chat individual entre Franco y el proveedor (lo crea si no existe). */
        fun chatCon(proveedor: Long): Long =
            db.rawQuery(
                """SELECT a.id_chat FROM miembros_chat a JOIN miembros_chat b ON b.id_chat = a.id_chat
                   WHERE a.id_user = ? AND b.id_user = ? LIMIT 1""",
                arrayOf(franco.toString(), proveedor.toString()),
            ).use { if (it.moveToFirst()) it.getLong(0) else null }
                ?: db.insertOrThrow("chat", null, ContentValues().apply { put("id_tipo_chat", individual) }).also { chat ->
                    for (uid in listOf(franco, proveedor)) {
                        db.insertOrThrow("miembros_chat", null, ContentValues().apply {
                            put("id_chat", chat)
                            put("id_user", uid)
                        })
                    }
                }

        /** Invitación al proveedor (llega por chat) y, si aceptó, su respuesta con la propuesta. */
        fun propuesta(v: Long, detalle: String, consorcio: String, mail: String, pdf: String?, aceptacion: Boolean?, contratado: Boolean = false, creadaHace: Long): Long {
            val proveedor = userId(mail)
            val id = db.insertOrThrow("votacion_contrato", null, ContentValues().apply {
                put("id_votacion", v)
                put("id_proveedor", proveedor)
                put("propuesta_pdf", if (aceptacion == true) pdf?.let { "asset:docs/votaciones/$it" } else null)
                if (aceptacion == null) putNull("aceptacion") else put("aceptacion", if (aceptacion) 1 else 0)
                put("contratado", if (contratado) 1 else 0)
            })
            val chat = chatCon(proveedor)
            val enviada = ahora.minusDays(creadaHace)
            db.insertOrThrow("mensaje_chat", null, ContentValues().apply {
                put("id_chat", chat)
                put("id_user", franco)
                put("mensaje", "Te invitamos a participar en una votación de contratación de $consorcio: $detalle")
                put("id_votacion_contrato", id)
                put("enviado_en", enviada.format(ChatFormato.FECHA_HORA))
            })
            if (aceptacion == true) {
                db.insertOrThrow("mensaje_chat", null, ContentValues().apply {
                    put("id_chat", chat)
                    put("id_user", proveedor)
                    put("mensaje", "Acepto participar en la votación. Te adjunto mi propuesta.")
                    put("archivo", pdf?.let { "asset:docs/votaciones/$it" })
                    put("nombre_archivo", pdf?.let { "Propuesta.pdf" })
                    put("enviado_en", enviada.plusHours(2).format(ChatFormato.FECHA_HORA))
                })
            }
            return id
        }

        // Abiertas
        val pintura = votacion(norte, "comun",
            "Pintura del hall de entrada y palieres. El presupuesto es de $ 1.200.000 y se pagaría en 3 cuotas junto con las expensas.", 2, 5)
        votar(pintura, "ana.fernandez@mail.com", "si")

        val detalleTerraza = "Impermeabilización de la terraza por las filtraciones en el 2° A. Elegí la propuesta que prefieras."
        val terraza = votacion(norte, "contratacion", detalleTerraza, 1, 7)
        val cimeco = propuesta(terraza, detalleTerraza, "Edificio Norte", "cimeco.adm@gmail.com", "propuesta_cimeco_terraza.pdf", true, creadaHace = 1)
        // Servicios Integrales todavía no respondió: la invitación queda pendiente en su chat
        propuesta(terraza, detalleTerraza, "Edificio Norte", "contacto@serviciosintegrales.com.ar", null, null, creadaHace = 1)
        votar(terraza, "ana.fernandez@mail.com", cimeco.toString())

        // Cerradas
        val limpieza = votacion(sanMartin, "comun", "Cambiar el horario de limpieza de los espacios comunes a la mañana (de 8 a 12 h).", 10, -3)
        votar(limpieza, "carlos.perez@mail.com", "si")
        votar(limpieza, "tomasfrancorusso@gmail.com", "si")

        val detalleAscensores = "Mantenimiento anual de los ascensores para 2027."
        val ascensores = votacion(sanMartin, "contratacion", detalleAscensores, 20, -5)
        val ascensur = propuesta(ascensores, detalleAscensores, "Edificio San Martín", "info@ascensur.com.ar", "propuesta_ascensur_ascensores.pdf", true, contratado = true, creadaHace = 20)
        propuesta(ascensores, detalleAscensores, "Edificio San Martín", "contacto@serviciosintegrales.com.ar", "propuesta_servicios_integrales_ascensores.pdf", true, creadaHace = 20)
        votar(ascensores, "carlos.perez@mail.com", ascensur.toString())
        votar(ascensores, "tomasfrancorusso@gmail.com", ascensur.toString())
    }

    /** Facturas y eventos de ejemplo para los consorcios de Franco. */
    fun loadFacturasYAgenda(db: SQLiteDatabase, franco: Long) {
        fun consorcioId(nombre: String) = db.rawQuery(
            "SELECT id_consorcio FROM consorcio WHERE name_consorcio = ?", arrayOf(nombre),
        ).use { it.moveToFirst(); it.getLong(0) }

        // Catálogo de tipos de deuda
        val tipos = listOf(
            "Luz", "Agua", "Gas", "ABL / Impuestos", "Ascensor", "Limpieza",
            "Mantenimiento y reparaciones", "Seguro", "Sueldos y cargas sociales",
            "Honorarios de administración", "Otros",
        ).associateWith { db.insertOrThrow("tipo_deuda", null, ContentValues().apply { put("nombre", it) }) }

        val sanMartin = consorcioId("Edificio San Martín")
        val norte = consorcioId("Edificio Norte")
        val hoy = LocalDate.now()

        fun deuda(consorcio: Long, tipo: String, descripcion: String, proveedor: String, importe: Double, vence: LocalDate) =
            db.insertOrThrow("deuda", null, ContentValues().apply {
                put("id_consorcio", consorcio)
                put("id_tipo_deuda", tipos.getValue(tipo))
                put("descripcion", descripcion)
                put("proveedor", proveedor)
                put("importe", importe)
                put("vencimiento", vence.toString())
            })

        fun factura(consorcio: Long, deuda: Long, proveedor: String, nro: String, fecha: LocalDate, importe: Double) =
            db.insertOrThrow("factura", null, ContentValues().apply {
                put("id_consorcio", consorcio)
                put("id_deuda", deuda)
                put("id_user", franco)
                put("proveedor", proveedor)
                put("nro_comprobante", nro)
                put("fecha", fecha.toString())
                put("importe", importe)
            })

        // Deudas que ya tienen su factura cargada
        val ascensor = deuda(sanMartin, "Ascensor", "Service mensual de ascensor", "AscenSur", 185000.0, hoy.minusDays(2))
        factura(sanMartin, ascensor, "AscenSur", "0003-00012345", hoy.minusDays(6), 185000.0)
        val luz = deuda(sanMartin, "Luz", "Luz espacios comunes", "Edesur", 96350.5, hoy.minusDays(5))
        factura(sanMartin, luz, "Edesur", "B-0045-88812", hoy.minusDays(12), 96350.5)
        val limpieza = deuda(norte, "Limpieza", "Limpieza mensual", "Limpieza Total SRL", 240000.0, hoy.minusDays(1))
        factura(norte, limpieza, "Limpieza Total SRL", "0001-00004521", hoy.minusDays(3), 240000.0)

        // Deudas pendientes (sin factura): son las que aparecen al cargar una factura
        deuda(sanMartin, "Agua", "AySA bimestre 5", "AySA", 54200.0, hoy.plusDays(8))
        deuda(sanMartin, "Seguro", "Póliza integral de consorcio", "La Segunda", 132000.0, hoy.plusDays(15))
        deuda(norte, "Gas", "Gas caldera central", "Metrogas", 38900.0, hoy.plusDays(5))
        deuda(norte, "Mantenimiento y reparaciones", "Reparación portón de cochera", "Portones Sur", 310000.0, hoy.plusDays(20))
        deuda(norte, "ABL / Impuestos", "ABL cuota 10", "AGIP", 67450.0, hoy.plusDays(12))

        listOf(
            arrayOf(sanMartin, "Visita técnica ascensor", "Revisión mensual de AscenSur", hoy.plusDays(1), "10:00"),
            arrayOf(norte, "Reunión de consorcio", "Asamblea ordinaria en el SUM", hoy.plusDays(4), "19:30"),
            arrayOf(null, "Vencimiento seguro edificio", "Renovar póliza de Edificio Norte", hoy.plusDays(10), null),
        ).forEach { (consorcio, titulo, descripcion, fecha, hora) ->
            db.insertOrThrow("agenda_evento", null, ContentValues().apply {
                put("id_user", franco)
                put("id_consorcio", consorcio as Long?)
                put("titulo", titulo as String)
                put("descripcion", descripcion as String)
                put("fecha", fecha.toString())
                put("hora", hora as String?)
            })
        }
    }

    /** Conversaciones de ejemplo: las del seed_data de la web (María) y algunas de Franco. */
    fun loadChats(db: SQLiteDatabase) {
        val individual = db.insertOrThrow("tipo_chat", null, ContentValues().apply { put("name_chat", "Individual") })
        db.insertOrThrow("tipo_chat", null, ContentValues().apply { put("name_chat", "Grupal") })

        fun userId(mail: String) = db.rawQuery("SELECT id_user FROM user WHERE mail = ?", arrayOf(mail))
            .use { it.moveToFirst(); it.getLong(0) }

        val ahora = LocalDateTime.now()
        fun hace(dias: Long, horas: Long = 0, minutos: Long = 0) =
            ahora.minusDays(dias).minusHours(horas).minusMinutes(minutos).format(ChatFormato.FECHA_HORA)

        /** Crea un chat individual entre a y b con los mensajes (autor, texto, cuándo). */
        fun chat(a: String, b: String, vararg mensajes: Triple<String, String, String>) {
            val chat = db.insertOrThrow("chat", null, ContentValues().apply { put("id_tipo_chat", individual) })
            for (mail in listOf(a, b)) {
                db.insertOrThrow("miembros_chat", null, ContentValues().apply {
                    put("id_chat", chat)
                    put("id_user", userId(mail))
                })
            }
            for ((autor, texto, cuando) in mensajes) {
                db.insertOrThrow("mensaje_chat", null, ContentValues().apply {
                    put("id_chat", chat)
                    put("id_user", userId(autor))
                    put("mensaje", texto)
                    put("enviado_en", cuando)
                })
            }
        }

        val maria = "maria.gonzalez@consora.com"
        val franco = "tomasfrancorusso@gmail.com"
        val carlos = "carlos.perez@mail.com"
        val ana = "ana.fernandez@mail.com"
        val electro = "contacto@electrohogar.com"
        val lucia = "lucia.r@consora.com"

        // --- Chats de María (iguales a los de la web) ---
        chat(
            maria, carlos,
            Triple(carlos, "Hola, quería consultar por el estado de mi reclamo de la filtración en la terraza.", hace(0, 3, 10)),
            Triple(maria, "Hola Carlos, ya estamos esperando el presupuesto de Servicios Integrales SRL. Apenas lo tengamos te aviso.", hace(0, 3)),
            Triple(carlos, "Te mando una foto más, se nota mejor la humedad en la esquina.", hace(0, 2, 10)),
            Triple(maria, "Perfecto, la sumo a la solicitud. En cuanto tengamos el presupuesto te contactamos para coordinar.", hace(0, 2)),
            Triple(carlos, "Perfecto, quedo atento a la respuesta", hace(0, 1)),
        )
        chat(
            maria, ana,
            Triple(ana, "¿Ya se coordinó la visita técnica?", hace(1, 0, 10)),
            Triple(maria, "Todavía no, estamos comparando presupuestos. Te confirmo esta semana.", hace(1)),
        )
        chat(
            maria, electro,
            Triple(maria, "Hola, ¿cómo va el presupuesto para la solicitud #1047?", hace(1, 2)),
            Triple(electro, "Envío el presupuesto en breve", hace(1)),
        )
        chat(maria, lucia, Triple(lucia, "Ya avisé a Carlos sobre el reclamo", hace(4)))

        // --- Chats de Franco ---
        chat(
            franco, carlos,
            Triple(carlos, "Buenas Franco, ¿cuándo viene el técnico del ascensor?", hace(0, 1, 30)),
            Triple(franco, "Hola Carlos, mañana a las 10 viene AscenSur a hacer la revisión mensual.", hace(0, 1, 20)),
            Triple(carlos, "Genial, gracias!", hace(0, 1, 15)),
        )
        chat(
            franco, electro,
            Triple(franco, "Hola, necesito un presupuesto para cambiar las luces de emergencia del Edificio Norte.", hace(2, 4)),
            Triple(electro, "Hola Franco, ¿cuántas luces son aproximadamente?", hace(2, 3)),
            Triple(franco, "Son 12, distribuidas en palieres y cocheras.", hace(2, 2)),
            Triple(electro, "Perfecto, te lo mando mañana.", hace(1, 5)),
        )
        chat(
            franco, ana,
            Triple(ana, "Hola, ¿ya está la fecha de la próxima asamblea?", hace(3, 2)),
            Triple(franco, "Sí Ana, va a ser en el SUM. Ya la cargué en la agenda y les llega el aviso.", hace(3, 1)),
        )
    }

    private fun insertEstado(db: SQLiteDatabase, estado: String, motivo: String?) =
        db.insertOrThrow("estados_cuenta", null, ContentValues().apply {
            put("estado", estado)
            put("motivo", motivo)
        })

    private fun insertAdministrador(db: SQLiteDatabase, idUser: Long, matricula: String, fechaAlta: String?) {
        db.insertOrThrow("administrador", null, ContentValues().apply {
            put("id_user", idUser)
            put("matricula", matricula)
            put("fecha_alta_matricula", fechaAlta)
            put("ente_otorgante", "Registro Público de Administradores - GCBA")
        })
    }

    private fun insertCuentaMarketplace(db: SQLiteDatabase, dueno: Long, descripcion: String, link: String?): Long =
        db.insertOrThrow("cuentas_marketplace", null, ContentValues().apply {
            put("dueno", dueno)
            put("descripcion", descripcion)
            put("link", link)
        })

    private fun insertAsistente(db: SQLiteDatabase, idUser: Long, idAdmin: Long): Long =
        db.insertOrThrow("asistente", null, ContentValues().apply {
            put("id_user", idUser)
            put("id_admin", idAdmin)
        })
}
