from datetime import date, datetime, time, timedelta
from decimal import Decimal

from django.core.management.base import BaseCommand
from django.utils import timezone

from web.models import (
    FormaPagoContrato, ContratoProveedor, HistorialConsorcio,
    Rol, EstadoCuenta, Usuario, Administrador, Asistente,
    Consorcio, AsistenteConsorcio, MotivoHistorial, Historial, HistorialDoc,
    TipoChat, Chat, MiembroChat, MensajeChat,
    PropietarioConsorcio, TareaAsistente, CuentaMarketplace, ImgCuentaMarketplace, Resena,
    TipoDeuda, Deuda, Factura, AgendaEvento, TituloAdministrador,
    Votacion, VotacionUser, VotacionContrato,
    Reunion, ReunionParticipante,
)


class Command(BaseCommand):
    help = "Carga los datos de ejemplo de CONSORA (roles, estados, usuarios, asistentes, consorcios, historial)."

    def handle(self, *args, **options):

        # --- Roles ---
        roles = {}
        for nombre in [
            'Administrador del sistema',
            'Administrador/a',
            'Propietario/Residente',
            'Proveedor',
            'Asistente',
        ]:
            rol, created = Rol.objects.get_or_create(rol=nombre)
            roles[nombre] = rol
            self.stdout.write(self.style.SUCCESS(f"Rol: {nombre}") if created else f"Rol ya existía: {nombre}")

        # --- Estados de cuenta ---
        estado_activa, _ = EstadoCuenta.objects.get_or_create(estado='Activa', defaults={'motivo': None})
        EstadoCuenta.objects.get_or_create(estado='Suspendida', defaults={'motivo': 'Pago pendiente'})
        EstadoCuenta.objects.get_or_create(estado='Baja', defaults={'motivo': 'Solicitada por el usuario'})

        # --- Usuarios de ejemplo (todos con la misma contraseña de prueba) ---
        PASSWORD_PRUEBA = 'consora123'

        usuarios_data = [
            # Administrador de prueba de la app mobile (tiene foto, 5 asistentes y los consorcios con facturas)
            dict(mail='tomasfrancorusso@gmail.com', name_surname='Franco Russo',
                 rol=roles['Administrador/a'], dni=None, phone=None, date_nacimiento=None,
                 img='img/usuarios/avatar_franco.jpg'),
            dict(mail='valentina.lopez@consora.com.ar', name_surname='Valentina López',
                 rol=roles['Asistente'], dni='37111222', phone='+5491170001001', date_nacimiento='1999-04-08',
                 img='img/usuarios/avatar_valentina.webp'),
            dict(mail='tomas.acosta@consora.com.ar', name_surname='Tomás Acosta',
                 rol=roles['Asistente'], dni='36999888', phone='+5491170002002', date_nacimiento='1997-08-23',
                 img='img/usuarios/avatar_tomas.webp'),
            dict(mail='camila.diaz@consora.com.ar', name_surname='Camila Díaz',
                 rol=roles['Asistente'], dni='38222111', phone='+5491170003003', date_nacimiento='2000-01-30',
                 img='img/usuarios/avatar_camila.webp'),
            dict(mail='nicolas.herrera@consora.com.ar', name_surname='Nicolás Herrera',
                 rol=roles['Asistente'], dni='35888777', phone='+5491170004004', date_nacimiento='1995-12-11',
                 img='img/usuarios/avatar_nicolas.webp'),
            dict(mail='julieta.morales@consora.com.ar', name_surname='Julieta Morales',
                 rol=roles['Asistente'], dni='39333444', phone='+5491170005005', date_nacimiento='2001-06-17',
                 img='img/usuarios/avatar_julieta.webp'),
            dict(mail='maria.gonzalez@consora.com', name_surname='María González',
                 rol=roles['Administrador/a'], dni='30111222', phone='+5491111111111',
                 date_nacimiento='1985-03-12'),
            dict(mail='lucia.r@consora.com', name_surname='Lucía Ramírez',
                 rol=roles['Asistente'], dni='35222333', phone='+5491122222222',
                 date_nacimiento='1996-07-01',
                 img='img/usuarios/avatar_lucia.webp'),
            dict(mail='martin.g@consora.com', name_surname='Martín Gómez',
                 rol=roles['Asistente'], dni='34333444', phone='+5491133333333',
                 date_nacimiento='1994-11-20',
                 img='img/usuarios/avatar_martin.webp'),
            dict(mail='sofia.p@consora.com', name_surname='Sofía Pérez',
                 rol=roles['Asistente'], dni='36444555', phone='+5491144444444',
                 date_nacimiento='1998-02-15',
                 img='img/usuarios/avatar_sofia.webp'),
            dict(mail='carlos.perez@mail.com', name_surname='Carlos Pérez',
                 rol=roles['Propietario/Residente'], dni='28444555', phone='+5491144444444',
                 date_nacimiento='1978-05-05'),
            dict(mail='ana.fernandez@mail.com', name_surname='Ana Fernández',
                 rol=roles['Propietario/Residente'], dni='31555666', phone='+5491155555555',
                 date_nacimiento='1990-09-18'),
            # Propietario con correo real: para probar las invitaciones de Google Calendar
            dict(mail='franco.russo.dev@gmail.com', name_surname='Franco Tomás Russo',
                 rol=roles['Propietario/Residente'], dni=None, phone=None, date_nacimiento=None),
            dict(mail='contacto@electrohogar.com', name_surname='ElectroHogar SRL',
                 rol=roles['Proveedor'], dni=None, phone='+5491166666666',
                 date_nacimiento=None, img='img/usuarios/avatar_electrohogar.png'),
        ]
        # Proveedores del Marketplace (sin foto: se muestran con iniciales), con su descripción y web
        proveedores_data = [
            ('info@ascensur.com.ar', 'AscenSur Ascensores', '+5491167001001',
             'Mantenimiento mensual y reparación de ascensores. Guardia técnica 24 hs.', 'https://www.ascensur.com.ar'),
            ('contacto@serviciosintegrales.com.ar', 'Servicios Integrales SRL', '+5491167002002',
             'Mantenimiento general de edificios: albañilería, herrería y pequeñas reparaciones.', None),
            ('ventas@limpiezatotal.com.ar', 'Limpieza Total', '+5491167003003',
             'Limpieza de espacios comunes, vidrios en altura y final de obra.', 'https://www.limpiezatotal.com.ar'),
            ('plomeria.ruiz@mail.com', 'Jorge Ruiz Plomería', '+5491167004004',
             'Plomero matriculado. Destapaciones, tanques de agua y bombas.', None),
            ('info@pinturasdelsur.com.ar', 'Pinturas del Sur', '+5491167005005',
             'Pintura de fachadas, palieres y cocheras. Trabajos en altura.', 'https://www.pinturasdelsur.com.ar'),
            ('contacto@seguridadplus.com.ar', 'Seguridad Plus', '+5491167006006',
             'Cámaras, portones automáticos y control de acceso.', 'https://www.seguridadplus.com.ar'),
            ('info@jardinesverdes.com.ar', 'Jardines Verdes', '+5491167007007',
             'Mantenimiento de jardines, terrazas verdes y riego automático.', None),
            ('gasista.medina@mail.com', 'Raúl Medina Gasista', '+5491167008008',
             'Gasista matriculado. Pruebas de hermeticidad y trámites ante la distribuidora.', None),
            ('info@fumigar.com.ar', 'Fumigar Control de Plagas', '+5491167009009',
             'Desinsectación, desratización y control de palomas con certificado.', 'https://www.fumigar.com.ar'),
            ('cerrajeria24@mail.com', 'Cerrajería 24 hs', '+5491167010010',
             'Aperturas, cambio de cerraduras y copias de llaves magnéticas.', None),
            ('cimeco.adm@gmail.com', 'CIMECO Ingeniería & Mantenimiento', '+541167459591',
             'Impermeabilización y mantenimiento de edificios. Más de 10 años protegiendo edificios del agua.\n'
             'Terrazas, techos, balcones, medianeras, tanques y canaletas. Filtraciones y humedad, '
             'fisuras en fachadas, pintura y trabajos en altura.\n'
             'Visita y diagnóstico, presupuesto claro y seguimiento de obra. Atención 24/7.',
             'https://cimeco-impermeabilizaciones.netlify.app'),
        ]
        # Proveedores con logo propio (en media/img/usuarios); el resto se muestra con iniciales
        logos_proveedores = {'cimeco.adm@gmail.com': 'img/usuarios/avatar_cimeco.webp'}
        for mail, nombre, phone, _, _ in proveedores_data:
            usuarios_data.append(dict(mail=mail, name_surname=nombre, rol=roles['Proveedor'], dni=None,
                                      phone=phone, date_nacimiento=None, img=logos_proveedores.get(mail)))

        usuarios = {}
        for data in usuarios_data:
            usuario, created = Usuario.objects.get_or_create(
                mail=data['mail'],
                defaults={
                    'name_surname': data['name_surname'],
                    'rol': data['rol'],
                    'dni': data['dni'],
                    'phone': data['phone'],
                    'date_nacimiento': data['date_nacimiento'],
                    'img': data.get('img'),
                    'estado_cuenta': estado_activa,
                },
            )
            if created:
                usuario.set_password(PASSWORD_PRUEBA)
                usuario.save()
                self.stdout.write(self.style.SUCCESS(f"Usuario creado: {data['mail']}"))
            else:
                if data.get('img') and not usuario.img:
                    usuario.img = data['img']
                    usuario.save(update_fields=['img'])
                self.stdout.write(f"Usuario ya existía: {data['mail']}")

            usuarios[data['mail']] = usuario

        # --- Administrador (María) ---
        Administrador.objects.get_or_create(
            usuario=usuarios['maria.gonzalez@consora.com'],
            defaults={
                'matricula': 'RPA-12345',
                'fecha_alta_matricula': '2015-02-10',
                'ente_otorgante': 'Registro Público de Administradores - GCBA',
            },
        )

        # --- Administrador (Franco) ---
        Administrador.objects.get_or_create(
            usuario=usuarios['tomasfrancorusso@gmail.com'],
            defaults={
                'matricula': 'RPA-67890',
                'ente_otorgante': 'Registro Público de Administradores - GCBA',
            },
        )

        # Licencia de ejemplo (PDF de prueba, sin validez) en media/docs/titulos
        TituloAdministrador.objects.get_or_create(
            usuario=usuarios['tomasfrancorusso@gmail.com'], titulo='docs/titulos/licencia_franco_russo.pdf',
        )

        # --- Asistentes (Lucía y Martín activos, Sofía suspendida) ---
        asistentes = {}

        asistente_lucia, _ = Asistente.objects.get_or_create(
            usuario=usuarios['lucia.r@consora.com'],
            defaults={'admin': usuarios['maria.gonzalez@consora.com'], 'habilitado': True},
        )
        asistentes['lucia'] = asistente_lucia

        asistente_martin, _ = Asistente.objects.get_or_create(
            usuario=usuarios['martin.g@consora.com'],
            defaults={'admin': usuarios['maria.gonzalez@consora.com'], 'habilitado': True},
        )
        asistentes['martin'] = asistente_martin

        asistente_sofia, _ = Asistente.objects.get_or_create(
            usuario=usuarios['sofia.p@consora.com'],
            defaults={'admin': usuarios['maria.gonzalez@consora.com'], 'habilitado': False},
        )
        asistentes['sofia'] = asistente_sofia

        # Equipo de Franco
        for clave, mail in [
            ('valentina', 'valentina.lopez@consora.com.ar'),
            ('tomas', 'tomas.acosta@consora.com.ar'),
            ('camila', 'camila.diaz@consora.com.ar'),
            ('nicolas', 'nicolas.herrera@consora.com.ar'),
            ('julieta', 'julieta.morales@consora.com.ar'),
        ]:
            asistentes[clave], _ = Asistente.objects.get_or_create(
                usuario=usuarios[mail],
                defaults={'admin': usuarios['tomasfrancorusso@gmail.com'], 'habilitado': True},
            )

        # --- Consorcios (todos administrados por María) ---
        consorcios_data = [
            dict(nombre='Edificio Las Palmas', direccion='Av. Rivadavia 4520, CABA',
                 descripcion='Edificio de 42 unidades en Av. Rivadavia'),
            dict(nombre='Torres del Sol', direccion='Bv. Illia 1210, Córdoba',
                 descripcion='Complejo de 96 unidades en Córdoba'),
            dict(nombre='Edificio Belgrano', direccion='Av. Cabildo 2140, CABA',
                 descripcion='Edificio de 76 unidades en Belgrano'),
            dict(nombre='Edificio San Martín', direccion='San Martín 850, CABA',
                 descripcion='Edificio de 30 unidades'),
            dict(nombre='Edificio Norte', direccion='Av. del Libertador 6200, CABA',
                 descripcion='Edificio de 54 unidades'),
        ]

        # Como en la app mobile: Franco administra San Martín y Norte (con foto), María el resto
        de_franco = {'Edificio San Martín': 'img/consorcios/consorcio_san_martin.png',
                     'Edificio Norte': 'img/consorcios/consorcio_norte.png'}

        consorcios = {}
        for data in consorcios_data:
            es_de_franco = data['nombre'] in de_franco
            consorcio, created = Consorcio.objects.update_or_create(
                name_consorcio=data['nombre'],
                defaults={
                    'representante': usuarios['tomasfrancorusso@gmail.com' if es_de_franco else 'maria.gonzalez@consora.com'],
                    'direccion': data['direccion'],
                    'descripcion': data['descripcion'],
                    'img_consorcio': de_franco.get(data['nombre']),
                },
            )
            consorcios[data['nombre']] = consorcio
            if created:
                self.stdout.write(self.style.SUCCESS(f"Consorcio creado: {data['nombre']}"))

        # --- Asignaciones asistente ↔ consorcio ---
        asignaciones = [
            ('lucia', 'Edificio Las Palmas', True),
            ('lucia', 'Torres del Sol', True),
            ('lucia', 'Edificio Belgrano', True),

            ('martin', 'Edificio Las Palmas', True),
            ('martin', 'Torres del Sol', True),
            ('martin', 'Edificio Belgrano', False),

            ('sofia', 'Torres del Sol', False),
            ('sofia', 'Edificio Belgrano', False),

            # Equipo de Franco
            ('valentina', 'Edificio San Martín', True),
            ('valentina', 'Edificio Norte', True),
            ('tomas', 'Edificio Norte', True),
            ('camila', 'Edificio San Martín', False),
        ]

        for clave_asistente, nombre_consorcio, habilitado in asignaciones:
            AsistenteConsorcio.objects.get_or_create(
                asistente=asistentes[clave_asistente],
                consorcio=consorcios[nombre_consorcio],
                defaults={'habilitado': habilitado},
            )

        # --- Motivos de historial (agrupados por categoría) ---
        motivos_data = [
            ('mensaje', 'Mensaje con cliente'),
            ('mensaje', 'Archivo enviado a cliente'),
            ('mensaje', 'Archivo recibido'),
            ('presupuesto', 'Presupuesto cargado'),
            ('factura', 'Factura cargada'),
            ('solicitud', 'Solicitud creada'),
            ('solicitud', 'Solicitud actualizada'),
            ('solicitud', 'Solicitud cerrada'),
            ('acceso', 'Consorcio asignado'),
            ('acceso', 'Consorcio deshabilitado'),
            ('acceso', 'Cuenta suspendida'),
            ('acceso', 'Cuenta reactivada'),
        ]

        motivos = {}
        for categoria, motivo in motivos_data:
            obj, created = MotivoHistorial.objects.get_or_create(categoria=categoria, motivo=motivo)
            motivos[motivo] = obj
            if created:
                self.stdout.write(self.style.SUCCESS(f"Motivo creado: [{categoria}] {motivo}"))

        # --- Eventos de historial de ejemplo (con fechas pasadas, para tener datos realistas) ---
        ahora = timezone.now()

        def hace(dias, horas=0):
            return ahora - timedelta(days=dias, hours=horas)

        eventos = [
            # Lucía
            dict(usuario='lucia.r@consora.com', motivo='Mensaje con cliente',
                 descripcion='Respondió al mensaje de ElectroHogar (solicitud #1047)',
                 involucrado='contacto@electrohogar.com', cuando=hace(0, 2)),
            dict(usuario='lucia.r@consora.com', motivo='Solicitud actualizada',
                 descripcion='Cambió la solicitud #1046 a "Presupuestos recibidos"',
                 involucrado=None, cuando=hace(1)),
            dict(usuario='lucia.r@consora.com', motivo='Archivo enviado a cliente',
                 descripcion='Envió el reglamento de copropiedad a Carlos Pérez',
                 involucrado='carlos.perez@mail.com', cuando=hace(2),
                 docs=['reglamento_copropiedad.pdf']),
            dict(usuario='lucia.r@consora.com', motivo='Presupuesto cargado',
                 descripcion='Cargó un presupuesto de Servicios Integrales SRL',
                 involucrado=None, cuando=hace(5),
                 docs=['presupuesto_servicios_integrales.pdf']),
            dict(usuario='lucia.r@consora.com', motivo='Solicitud creada',
                 descripcion='Creó la solicitud #1041 (filtración en cochera)',
                 involucrado='carlos.perez@mail.com', cuando=hace(9),
                 docs=['foto_filtracion_cochera.jpg', 'foto_filtracion_cochera_2.jpg']),
            dict(usuario='lucia.r@consora.com', motivo='Mensaje con cliente',
                 descripcion='Envió un mensaje a un propietario de Las Palmas',
                 involucrado='carlos.perez@mail.com', cuando=hace(14)),
            dict(usuario='lucia.r@consora.com', motivo='Factura cargada',
                 descripcion='Cargó la factura de expensas de septiembre de Las Palmas',
                 involucrado=None, cuando=hace(4),
                 docs=['factura_expensas_las_palmas_09_2026.pdf']),
            dict(usuario='lucia.r@consora.com', motivo='Consorcio asignado',
                 descripcion='Se le asignó el consorcio Torres del Sol',
                 involucrado=None, cuando=hace(90)),

            # Martín
            dict(usuario='martin.g@consora.com', motivo='Factura cargada',
                 descripcion='Cargó la factura de AscenSur por el service del mes',
                 involucrado=None, cuando=hace(6),
                 docs=['factura_ascensur_09_2026.pdf']),
            dict(usuario='martin.g@consora.com', motivo='Presupuesto cargado',
                 descripcion='Cargó un presupuesto de Pinturería Centro',
                 involucrado=None, cuando=hace(3),
                 docs=['presupuesto_pintureria_centro.pdf']),
            dict(usuario='martin.g@consora.com', motivo='Solicitud cerrada',
                 descripcion='Cerró la solicitud #1044 (service de ascensor)',
                 involucrado=None, cuando=hace(7)),
            dict(usuario='martin.g@consora.com', motivo='Archivo recibido',
                 descripcion='Recibió el certificado de service de AscenSur',
                 involucrado='ana.fernandez@mail.com', cuando=hace(8),
                 docs=['certificado_service_ascensor.pdf']),
            dict(usuario='martin.g@consora.com', motivo='Consorcio deshabilitado',
                 descripcion='Se le deshabilitó el acceso al consorcio Belgrano',
                 involucrado=None, cuando=hace(20)),
            dict(usuario='martin.g@consora.com', motivo='Solicitud creada',
                 descripcion='Creó la solicitud #1038 (luces de emergencia)',
                 involucrado=None, cuando=hace(35)),
            dict(usuario='martin.g@consora.com', motivo='Consorcio asignado',
                 descripcion='Se le asignó el consorcio Edificio Norte',
                 involucrado=None, cuando=hace(135)),

            # Sofía
            dict(usuario='sofia.p@consora.com', motivo='Cuenta suspendida',
                 descripcion='Cuenta suspendida por María González',
                 involucrado='maria.gonzalez@consora.com', cuando=hace(45)),
            dict(usuario='sofia.p@consora.com', motivo='Mensaje con cliente',
                 descripcion='Respondió una consulta de un propietario',
                 involucrado='ana.fernandez@mail.com', cuando=hace(49)),
            dict(usuario='sofia.p@consora.com', motivo='Solicitud actualizada',
                 descripcion='Cambió la solicitud #1030 a "En revisión"',
                 involucrado=None, cuando=hace(60)),
            dict(usuario='sofia.p@consora.com', motivo='Consorcio asignado',
                 descripcion='Se le asignó el consorcio Torres del Sol',
                 involucrado=None, cuando=hace(238)),
        ]

        creados = 0
        docs_creados = 0
        for ev in eventos:
            evento, created = Historial.objects.get_or_create(
                usuario=usuarios[ev['usuario']],
                descripcion=ev['descripcion'],
                defaults={
                    'motivo': motivos[ev['motivo']],
                    'usuario_involucrado': usuarios[ev['involucrado']] if ev['involucrado'] else None,
                    'creado_en': ev['cuando'],
                },
            )
            if created:
                creados += 1

            # Los documentos se aseguran siempre, exista o no el evento de antes
            # (si ya corriste seed_data una vez sin esta parte, esto la completa).
            for nombre_doc in ev.get('docs', []):
                _, doc_creado = HistorialDoc.objects.get_or_create(historial=evento, doc=nombre_doc)
                if doc_creado:
                    docs_creados += 1

        self.stdout.write(self.style.SUCCESS(f"Eventos de historial cargados: {creados}"))
        self.stdout.write(self.style.SUCCESS(f"Documentos adjuntos cargados: {docs_creados}"))

        # --- Tipos de chat ---
        tipo_individual, _ = TipoChat.objects.get_or_create(name_chat='Individual')
        TipoChat.objects.get_or_create(name_chat='Grupal')

        def crear_chat_individual(usuario_a, usuario_b, mensajes):
            """Busca un chat individual ya existente entre estos dos usuarios
            por email, o crea uno nuevo."""

            usuario_a = usuarios[usuario_a]
            usuario_b = usuarios[usuario_b]

            chat_existente = (
                Chat.objects
                .filter(tipo_chat=tipo_individual, miembros__usuario=usuario_a)
                .filter(miembros__usuario=usuario_b)
                .first()
            )

            if chat_existente:
                chat = chat_existente
            else:
                chat = Chat.objects.create(tipo_chat=tipo_individual)
                MiembroChat.objects.create(chat=chat, usuario=usuario_a)
                MiembroChat.objects.create(chat=chat, usuario=usuario_b)

            creados_msj = 0

            for autor, texto, cuando in mensajes:
                _, creado = MensajeChat.objects.get_or_create(
                    chat=chat,
                    usuario=usuarios[autor],
                    mensaje=texto,
                    defaults={'enviado_en': cuando},
                )

                if creado:
                    creados_msj += 1

            return chat, creados_msj

            if chat_existente:
                chat = chat_existente
            else:
                chat = Chat.objects.create(tipo_chat=tipo_individual)
                MiembroChat.objects.create(chat=chat, usuario=usuario_a)
                MiembroChat.objects.create(chat=chat, usuario=usuario_b)

            creados_msj = 0
            for autor, texto, cuando in mensajes:
                _, creado = MensajeChat.objects.get_or_create(
                    chat=chat, usuario=usuarios[autor], mensaje=texto,
                    defaults={'enviado_en': cuando},
                )
                if creado:
                    creados_msj += 1

            return chat, creados_msj

        # Chat María <-> Carlos (mismo contenido que el mockup de la página de Chat)
        _, n1 = crear_chat_individual(
            'maria.gonzalez@consora.com', 'carlos.perez@mail.com',
            [
                ('carlos.perez@mail.com', 'Hola, quería consultar por el estado de mi reclamo de la filtración en la terraza.', hace(0, 3)),
                ('maria.gonzalez@consora.com', 'Hola Carlos, ya estamos esperando el presupuesto de Servicios Integrales SRL. Apenas lo tengamos te aviso.', hace(0, 3) ),
                ('carlos.perez@mail.com', 'Te mando una foto más, se nota mejor la humedad en la esquina.', hace(0, 2)),
                ('maria.gonzalez@consora.com', 'Perfecto, la sumo a la solicitud. En cuanto tengamos el presupuesto te contactamos para coordinar.', hace(0, 2)),
                ('carlos.perez@mail.com', 'Perfecto, quedo atento a la respuesta', hace(0, 1)),
            ],
        )

        # Chat María <-> Ana
        _, n2 = crear_chat_individual(
            'maria.gonzalez@consora.com', 'ana.fernandez@mail.com',
            [
                ('ana.fernandez@mail.com', '¿Ya se coordinó la visita técnica?', hace(1)),
                ('maria.gonzalez@consora.com', 'Todavía no, estamos comparando presupuestos. Te confirmo esta semana.', hace(1)),
            ],
        )

        # Chat María <-> ElectroHogar
        _, n3 = crear_chat_individual(
            'maria.gonzalez@consora.com', 'contacto@electrohogar.com',
            [
                ('maria.gonzalez@consora.com', 'Hola, ¿cómo va el presupuesto para la solicitud #1047?', hace(1, 2)),
                ('contacto@electrohogar.com', 'Envío el presupuesto en breve', hace(1)),
            ],
        )

        # Chat María <-> Lucía (asistente)
        _, n4 = crear_chat_individual(
            'maria.gonzalez@consora.com', 'lucia.r@consora.com',
            [
                ('lucia.r@consora.com', 'Ya avisé a Carlos sobre el reclamo', hace(4)),
            ],
        )

        self.stdout.write(self.style.SUCCESS(f"Mensajes de chat cargados: {n1 + n2 + n3 + n4}"))

        # --- Propietarios: unidad de cada uno ---
        for mail, nombre_consorcio, unidad in [
            ('carlos.perez@mail.com', 'Edificio San Martín', '4° B'),
            ('ana.fernandez@mail.com', 'Edificio Norte', '2° A'),
            ('franco.russo.dev@gmail.com', 'Edificio Norte', '5° C'),
        ]:
            PropietarioConsorcio.objects.get_or_create(
                usuario=usuarios[mail], consorcio=consorcios[nombre_consorcio], defaults={'unidad': unidad},
            )

        # --- Tareas de los asistentes (asistente, descripción, completada, hace cuántos días) ---
        for mail, descripcion, completada, dias in [
            ('valentina.lopez@consora.com.ar', 'Pedir presupuesto de pintura para el palier del Edificio Norte', False, 2),
            ('valentina.lopez@consora.com.ar', 'Cargar las facturas de luz de octubre', True, 6),
            ('valentina.lopez@consora.com.ar', 'Avisar a los propietarios del corte de agua del jueves', True, 9),
            ('tomas.acosta@consora.com.ar', 'Coordinar la revisión mensual del ascensor con AscenSur', False, 1),
            ('tomas.acosta@consora.com.ar', 'Reclamar a ElectroHogar el presupuesto de las luces de emergencia', False, 4),
            ('camila.diaz@consora.com.ar', 'Armar el orden del día de la próxima asamblea', False, 3),
            ('lucia.r@consora.com', 'Responder el reclamo de Carlos por la filtración', True, 5),
            ('martin.g@consora.com', 'Renovar la póliza del seguro del edificio', False, 7),
        ]:
            TareaAsistente.objects.get_or_create(
                usuario=usuarios[mail], descripcion=descripcion,
                defaults={
                    'estado': TareaAsistente.COMPLETADO if completada else TareaAsistente.EN_PROCESO,
                    'created_at': hace(dias),
                },
            )

        # --- Marketplace: cuentas, publicaciones y reseñas ---
        cuentas = {}
        cuentas['contacto@electrohogar.com'], _ = CuentaMarketplace.objects.get_or_create(
            dueno=usuarios['contacto@electrohogar.com'],
            defaults={
                'descripcion': 'Instalaciones eléctricas, tableros y luces de emergencia para edificios. Presupuestos sin cargo.',
                'link': 'https://www.electrohogar.com',
            },
        )
        for mail, _, _, descripcion, link in proveedores_data:
            cuentas[mail], _ = CuentaMarketplace.objects.get_or_create(
                dueno=usuarios[mail], defaults={'descripcion': descripcion, 'link': link},
            )

        ImgCuentaMarketplace.objects.get_or_create(
            cuenta=cuentas['contacto@electrohogar.com'],
            img='img/publicaciones/publicacion_electrohogar.png',
            defaults={
                'descripcion': 'Llegaron los nuevos electrodomésticos: heladeras, lavarropas, microondas y más. '
                               'Consultanos por precios para consorcios.',
                'creation': hace(3),
            },
        )

        # Trabajos de CIMECO (textos tomados de su web)
        for img, texto, dias in [
            ('img/publicaciones/publicacion_cimeco_libertador.webp',
             'Impermeabilización y reparación de fachada en un edificio residencial sobre Av. del Libertador, CABA. '
             'Montamos andamio con bandeja de protección peatonal para trabajar con seguridad sin interrumpir '
             'el ingreso al edificio.', 12),
            ('img/publicaciones/publicacion_cimeco_obra.webp',
             'Ingeniería en impermeabilizaciones: trabajo en altura con andamio y bandeja de protección peatonal. '
             'Visita y diagnóstico, presupuesto claro y seguimiento de obra. ¡Consultanos!', 4),
        ]:
            ImgCuentaMarketplace.objects.get_or_create(
                cuenta=cuentas['cimeco.adm@gmail.com'], img=img,
                defaults={'descripcion': texto, 'creation': hace(dias)},
            )

        for cuenta, autor, estrellas, texto, dias in [
            ('contacto@electrohogar.com', 'tomasfrancorusso@gmail.com', 5, 'Cambiaron todas las luces de emergencia del Edificio Norte en un día. Muy prolijos.', 20),
            ('contacto@electrohogar.com', 'maria.gonzalez@consora.com', 4, 'Buen trabajo con el tablero general, aunque el presupuesto tardó un poco.', 45),
            ('contacto@electrohogar.com', 'carlos.perez@mail.com', 5, 'Atentos y puntuales.', 8),
            ('info@ascensur.com.ar', 'tomasfrancorusso@gmail.com', 4, 'Cumplen con el mantenimiento mensual. La guardia responde rápido.', 30),
            ('info@ascensur.com.ar', 'ana.fernandez@mail.com', 3, 'El ascensor quedó funcionando, pero tuvieron que venir dos veces.', 12),
            ('ventas@limpiezatotal.com.ar', 'maria.gonzalez@consora.com', 5, 'Dejaron los vidrios del hall impecables.', 15),
            ('plomeria.ruiz@mail.com', 'carlos.perez@mail.com', 4, 'Destapó la cañería del palier sin romper nada.', 25),
            ('plomeria.ruiz@mail.com', 'tomasfrancorusso@gmail.com', 5, 'Excelente con el cambio de bomba del tanque.', 5),
        ]:
            Resena.objects.get_or_create(
                cuenta=cuentas[cuenta], autor=usuarios[autor],
                defaults={'estrellas': estrellas, 'descripcion': texto, 'creation': hace(dias)},
            )

        # --- Deudas y facturas de los consorcios de Franco ---
        tipos = {}
        for nombre in ['Luz', 'Agua', 'Gas', 'ABL / Impuestos', 'Ascensor', 'Limpieza',
                       'Mantenimiento y reparaciones', 'Seguro', 'Sueldos y cargas sociales',
                       'Honorarios de administración', 'Otros']:
            tipos[nombre], _ = TipoDeuda.objects.get_or_create(nombre=nombre)

        hoy = timezone.localdate()
        san_martin = consorcios['Edificio San Martín']
        norte = consorcios['Edificio Norte']

        def deuda(consorcio, tipo, descripcion, proveedor, importe, vence):
            obj, _ = Deuda.objects.get_or_create(
                consorcio=consorcio, descripcion=descripcion,
                defaults={'tipo_deuda': tipos[tipo], 'proveedor': proveedor,
                          'importe': Decimal(str(importe)), 'vencimiento': vence},
            )
            return obj

        # Deudas que ya tienen su factura cargada
        for consorcio, tipo, descripcion, proveedor, importe, vence, nro, fecha in [
            (san_martin, 'Ascensor', 'Service mensual de ascensor', 'AscenSur', 185000, hoy - timedelta(days=2), '0003-00012345', hoy - timedelta(days=6)),
            (san_martin, 'Luz', 'Luz espacios comunes', 'Edesur', 96350.5, hoy - timedelta(days=5), 'B-0045-88812', hoy - timedelta(days=12)),
            (norte, 'Limpieza', 'Limpieza mensual', 'Limpieza Total SRL', 240000, hoy - timedelta(days=1), '0001-00004521', hoy - timedelta(days=3)),
        ]:
            d = deuda(consorcio, tipo, descripcion, proveedor, importe, vence)
            Factura.objects.get_or_create(
                deuda=d,
                defaults={'consorcio': consorcio, 'usuario': usuarios['tomasfrancorusso@gmail.com'],
                          'proveedor': proveedor, 'nro_comprobante': nro, 'fecha': fecha,
                          'importe': Decimal(str(importe))},
            )

        # Deudas pendientes (sin factura): son las que aparecen al cargar una factura
        deuda(san_martin, 'Agua', 'AySA bimestre 5', 'AySA', 54200, hoy + timedelta(days=8))
        deuda(san_martin, 'Seguro', 'Póliza integral de consorcio', 'La Segunda', 132000, hoy + timedelta(days=15))
        deuda(norte, 'Gas', 'Gas caldera central', 'Metrogas', 38900, hoy + timedelta(days=5))
        deuda(norte, 'Mantenimiento y reparaciones', 'Reparación portón de cochera', 'Portones Sur', 310000, hoy + timedelta(days=20))
        deuda(norte, 'ABL / Impuestos', 'ABL cuota 10', 'AGIP', 67450, hoy + timedelta(days=12))

        # --- Votaciones de los consorcios de Franco (propuestas de ejemplo en docs/votaciones/ejemplos) ---
        def votacion(consorcio, tipo, detalle, creada_hace, cierra_en_dias):
            obj, _ = Votacion.objects.get_or_create(
                consorcio=consorcio, detalle=detalle,
                defaults={'tipo': tipo, 'fecha': hace(creada_hace),
                          'fecha_limite': ahora + timedelta(days=cierra_en_dias)},
            )
            return obj

        def propuesta(v, mail_proveedor, pdf, aceptacion=True, contratado=False):
            """Proveedor invitado a la votación. La invitación le llega por chat desde el administrador;
            aceptacion: None = no respondió, True = aceptó (con su PDF), False = rechazó."""
            obj, _ = VotacionContrato.objects.get_or_create(
                votacion=v, proveedor=usuarios[mail_proveedor],
                defaults={'propuesta_pdf': f'docs/votaciones/ejemplos/{pdf}' if aceptacion and pdf else None,
                          'aceptacion': aceptacion, 'contratado': contratado},
            )
            admin = usuarios['tomasfrancorusso@gmail.com']
            chat_inv, _ = crear_chat_individual(admin.mail, mail_proveedor, [])
            MensajeChat.objects.get_or_create(
                chat=chat_inv, usuario=admin, invitacion=obj,
                defaults={'mensaje': f'Te invitamos a participar en una votación de contratación de '
                                     f'{v.consorcio.name_consorcio}: {v.detalle}',
                          'enviado_en': v.fecha},
            )
            if aceptacion:
                MensajeChat.objects.get_or_create(
                    chat=chat_inv, usuario=usuarios[mail_proveedor], mensaje='Acepto participar en la votación. Te adjunto mi propuesta.',
                    defaults={'archivo': obj.propuesta_pdf, 'nombre_archivo': 'Propuesta.pdf',
                              'enviado_en': v.fecha + timedelta(hours=2)},
                )
            return obj

        def votar(v, mail, voto):
            VotacionUser.objects.get_or_create(votacion=v, usuario=usuarios[mail], defaults={'voto': voto})

        # Abiertas
        pintura = votacion(norte, Votacion.COMUN,
                           'Pintura del hall de entrada y palieres. El presupuesto es de $ 1.200.000 '
                           'y se pagaría en 3 cuotas junto con las expensas.', 2, 5)
        votar(pintura, 'ana.fernandez@mail.com', VotacionUser.SI)

        terraza = votacion(norte, Votacion.CONTRATACION,
                           'Impermeabilización de la terraza por las filtraciones en el 2° A. '
                           'Elegí la propuesta que prefieras.', 1, 7)
        cimeco = propuesta(terraza, 'cimeco.adm@gmail.com', 'propuesta_cimeco_terraza.pdf')
        # Servicios Integrales todavía no respondió: la invitación queda pendiente en su chat
        propuesta(terraza, 'contacto@serviciosintegrales.com.ar', None, aceptacion=None)
        votar(terraza, 'ana.fernandez@mail.com', str(cimeco.pk))

        # Cerradas
        limpieza = votacion(san_martin, Votacion.COMUN,
                            'Cambiar el horario de limpieza de los espacios comunes a la mañana (de 8 a 12 h).', 10, -3)
        votar(limpieza, 'carlos.perez@mail.com', VotacionUser.SI)
        votar(limpieza, 'tomasfrancorusso@gmail.com', VotacionUser.SI)

        ascensores = votacion(san_martin, Votacion.CONTRATACION,
                              'Mantenimiento anual de los ascensores para 2027.', 20, -5)
        ascensur = propuesta(ascensores, 'info@ascensur.com.ar', 'propuesta_ascensur_ascensores.pdf', contratado=True)
        propuesta(ascensores, 'contacto@serviciosintegrales.com.ar', 'propuesta_servicios_integrales_ascensores.pdf')
        votar(ascensores, 'carlos.perez@mail.com', str(ascensur.pk))
        votar(ascensores, 'tomasfrancorusso@gmail.com', str(ascensur.pk))

        # --- Agenda de Franco ---
        for consorcio, titulo, descripcion, dias, hora in [
            (san_martin, 'Visita técnica ascensor', 'Revisión mensual de AscenSur', 1, time(10, 0)),
            (norte, 'Reunión de consorcio', 'Asamblea ordinaria en el SUM', 4, time(19, 30)),
            (None, 'Vencimiento seguro edificio', 'Renovar póliza de Edificio Norte', 10, None),
        ]:
            AgendaEvento.objects.get_or_create(
                usuario=usuarios['tomasfrancorusso@gmail.com'], titulo=titulo,
                defaults={'consorcio': consorcio, 'descripcion': descripcion,
                          'fecha': hoy + timedelta(days=dias), 'hora': hora},
            )

        # --- Reuniones de los consorcios de Franco ---
        franco = usuarios['tomasfrancorusso@gmail.com']

        def reunion(consorcio, titulo, descripcion, modalidad, inicio, minutos, lugar=None, link=None, evento=None):
            if evento is None:
                local = timezone.localtime(inicio)
                evento, _ = AgendaEvento.objects.get_or_create(
                    usuario=franco, titulo=f'Reunión: {titulo}',
                    defaults={'consorcio': consorcio, 'descripcion': descripcion, 'fecha': local.date(), 'hora': local.time()},
                )
            obj, creada = Reunion.objects.get_or_create(
                consorcio=consorcio, titulo=titulo,
                defaults={'organizador': franco, 'evento': evento, 'descripcion': descripcion, 'modalidad': modalidad,
                          'lugar': lugar, 'fecha_inicio': inicio, 'fecha_fin': inicio + timedelta(minutes=minutos),
                          'meet_link': link, 'meet_codigo': link.rsplit('/', 1)[-1] if link else None,
                          'created_at': inicio - timedelta(days=5)},
            )
            if creada:
                ReunionParticipante.objects.create(reunion=obj, usuario=franco, rol_participante=ReunionParticipante.ADMINISTRADOR,
                                                   respuesta=ReunionParticipante.ACEPTA, respondido_en=obj.created_at)
                for pc in consorcio.propietarios.select_related('usuario'):
                    ReunionParticipante.objects.create(reunion=obj, usuario=pc.usuario, invitado_por=franco,
                                                       rol_participante=ReunionParticipante.PROPIETARIO)
                for a in consorcio.asistentes_asignados.filter(habilitado=True).select_related('asistente__usuario'):
                    ReunionParticipante.objects.create(reunion=obj, usuario=a.asistente.usuario, invitado_por=franco,
                                                       rol_participante=ReunionParticipante.ASISTENTE)
            return obj

        def responde(r, mail, respuesta, asistio=None):
            ReunionParticipante.objects.filter(reunion=r, usuario=usuarios[mail]).update(
                respuesta=respuesta, respondido_en=r.created_at + timedelta(hours=3), asistio=asistio,
            )

        def invitar_proveedor(r, mail, respuesta=ReunionParticipante.PENDIENTE, asistio=None):
            """El proveedor se invita como opcional: le llega por chat y participa si acepta."""
            p, _ = ReunionParticipante.objects.get_or_create(
                reunion=r, usuario=usuarios[mail],
                defaults={'rol_participante': ReunionParticipante.PROVEEDOR, 'es_opcional': True, 'invitado_por': franco,
                          'respuesta': respuesta, 'asistio': asistio, 'notificado_en': r.created_at,
                          'respondido_en': None if respuesta == ReunionParticipante.PENDIENTE else r.created_at + timedelta(hours=2)},
            )
            chat_inv, _ = crear_chat_individual(franco.mail, mail, [])
            inicio = timezone.localtime(r.fecha_inicio)
            MensajeChat.objects.get_or_create(
                chat=chat_inv, usuario=franco, invitacion_reunion=p,
                defaults={'mensaje': f'Te invitamos a la reunión "{r.titulo}" de {r.consorcio.name_consorcio}, '
                                     f'el {inicio:%d/%m} a las {inicio:%H:%M}.',
                          'enviado_en': r.created_at},
            )
            if respuesta == ReunionParticipante.ACEPTA:
                # Se busca también por la fecha: en el mismo chat puede haber respuestas reales iguales
                MensajeChat.objects.get_or_create(
                    chat=chat_inv, usuario=usuarios[mail], mensaje='Acepto la invitación, ahí estaré.',
                    enviado_en=r.created_at + timedelta(hours=2),
                )
            return p

        # La asamblea que ya estaba en la agenda, ahora como reunión presencial
        evento_asamblea = AgendaEvento.objects.filter(usuario=franco, titulo='Reunión de consorcio').first()
        asamblea = reunion(norte, 'Asamblea ordinaria', 'Rendición de cuentas del semestre y elección del consejo de propietarios.',
                           Reunion.PRESENCIAL, timezone.make_aware(datetime.combine(hoy + timedelta(days=4), time(19, 30))), 90,
                           lugar='SUM del edificio, planta baja', evento=evento_asamblea)
        responde(asamblea, 'ana.fernandez@mail.com', ReunionParticipante.ACEPTA)

        # Virtual: se invitó a los proveedores de la impermeabilización para que presenten sus propuestas
        terraza_meet = reunion(norte, 'Presentación de propuestas: impermeabilización de la terraza',
                               'Cada proveedor explica su propuesta antes de que cierre la votación.',
                               Reunion.VIRTUAL, timezone.make_aware(datetime.combine(hoy + timedelta(days=2), time(20, 0))), 60,
                               link='https://meet.google.com/cns-terr-aza')
        invitar_proveedor(terraza_meet, 'cimeco.adm@gmail.com', ReunionParticipante.ACEPTA)
        invitar_proveedor(terraza_meet, 'contacto@serviciosintegrales.com.ar')
        responde(terraza_meet, 'ana.fernandez@mail.com', ReunionParticipante.ACEPTA)

        # Pasada: con asistencia cargada
        ascensores_meet = reunion(san_martin, 'Revisión del contrato de ascensores', 'AscenSur presentó el plan de mantenimiento para 2027.',
                                  Reunion.VIRTUAL, timezone.make_aware(datetime.combine(hoy - timedelta(days=15), time(18, 0))), 60,
                                  link='https://meet.google.com/asc-ensu-r27')
        invitar_proveedor(ascensores_meet, 'info@ascensur.com.ar', ReunionParticipante.ACEPTA, asistio=True)
        responde(ascensores_meet, 'carlos.perez@mail.com', ReunionParticipante.ACEPTA, asistio=True)
        ReunionParticipante.objects.filter(reunion=ascensores_meet, usuario=franco).update(asistio=True)

        # --- Contratos con proveedores: cada cuota es una deuda que se paga en Facturación ---
        tipo_contrato, _ = TipoDeuda.objects.get_or_create(nombre='Contrato con proveedor')

        def forma(cuotas):
            obj, _ = FormaPagoContrato.objects.get_or_create(
                tipo=FormaPagoContrato.CONTADO if cuotas == 1 else FormaPagoContrato.CUOTAS, cuotas=cuotas)
            return obj

        def sumar_meses(fecha, meses):
            mes = fecha.month - 1 + meses
            return fecha.replace(year=fecha.year + mes // 12, month=mes % 12 + 1, day=min(fecha.day, 28))

        def contrato(consorcio, mail, descripcion, precio, cuotas, primer_pago, pagas, nro_base, propuesta=None):
            proveedor = usuarios[mail]
            c, creado = ContratoProveedor.objects.get_or_create(
                consorcio=consorcio, descripcion=descripcion,
                defaults={'proveedor': proveedor, 'precio': Decimal(precio), 'forma_pago': forma(cuotas),
                          'fecha': primer_pago, 'propuesta': propuesta, 'creado_por': franco,
                          'estado': ContratoProveedor.FINALIZADO if pagas == cuotas else ContratoProveedor.VIGENTE},
            )
            if creado:
                importe = (Decimal(precio) / cuotas).quantize(Decimal('0.01'))
                for i in range(1, cuotas + 1):
                    vence = sumar_meses(primer_pago, i - 1)
                    d = Deuda.objects.create(
                        consorcio=consorcio, tipo_deuda=tipo_contrato, proveedor=proveedor.name_surname,
                        importe=importe if i < cuotas else Decimal(precio) - importe * (cuotas - 1),
                        descripcion=f'{descripcion} · ' + ('pago único' if cuotas == 1 else f'cuota {i}/{cuotas}'),
                        vencimiento=vence, contrato=c, nro_cuota=i,
                    )
                    if i <= pagas:
                        Factura.objects.create(
                            consorcio=consorcio, deuda=d, usuario=franco, proveedor=proveedor.name_surname,
                            nro_comprobante=f'{nro_base}-{i:05d}', fecha=vence - timedelta(days=2), importe=d.importe,
                        )
            return c

        # Ganador de la votación de ascensores: 12 cuotas, van 2 pagas
        contrato(san_martin, 'info@ascensur.com.ar', 'Mantenimiento anual de ascensores 2027', '2400000', 12,
                 hoy - timedelta(days=40), 2, '0003-0002', propuesta=ascensur)
        # Luces de emergencia: 3 cuotas, va 1 paga
        contrato(san_martin, 'contacto@electrohogar.com', 'Cambio de luces de emergencia', '540000', 3,
                 hoy - timedelta(days=20), 1, '0002-0007')
        # Bomba de agua: al contado y pagado (finalizado)
        contrato(norte, 'plomeria.ruiz@mail.com', 'Cambio de bomba del tanque de agua', '385000', 1,
                 hoy - timedelta(days=35), 1, '0001-0004')

        # Estado de los trabajos: la bomba y las luces ya se terminaron (las luces se siguen pagando)
        for descripcion, dias in [('Cambio de bomba del tanque de agua', 33), ('Cambio de luces de emergencia', 10)]:
            ContratoProveedor.objects.filter(descripcion=descripcion, trabajo_terminado_en__isnull=True).update(
                trabajo_terminado_en=hoy - timedelta(days=dias))

        # --- Historial de los edificios ---
        def historia(consorcio, categoria, descripcion, dias, hora, manual=False):
            HistorialConsorcio.objects.get_or_create(
                consorcio=consorcio, descripcion=descripcion,
                defaults={'categoria': categoria, 'usuario': franco, 'fecha': hoy - timedelta(days=dias),
                          'time': hora, 'manual': manual},
            )

        H = HistorialConsorcio
        for consorcio, categoria, descripcion, dias, hora, manual in [
            (san_martin, H.OBRA, 'Se impermeabilizó la terraza y se cambiaron las membranas.', 210, time(9, 0), True),
            (san_martin, H.OBRA, 'Pintura completa de la fachada.', 120, time(9, 0), True),
            (san_martin, H.VOTACION, 'Se cerró la votación: Mantenimiento anual de los ascensores para 2027.', 45, time(18, 0), False),
            (san_martin, H.VOTACION, 'Por votación se eligió a AscenSur Ascensores: Mantenimiento anual de los ascensores para 2027.', 44, time(10, 15), False),
            (san_martin, H.CONTRATO, 'Se contrató a AscenSur Ascensores: Mantenimiento anual de ascensores 2027 ($ 2.400.000, 12 cuotas).', 44, time(10, 20), False),
            (san_martin, H.PAGO, 'Se pagó "Mantenimiento anual de ascensores 2027 · cuota 1/12" a AscenSur Ascensores ($ 200.000).', 42, time(11, 0), False),
            (san_martin, H.CONTRATO, 'Se contrató a ElectroHogar SRL: Cambio de luces de emergencia ($ 540.000, 3 cuotas).', 22, time(16, 30), False),
            (san_martin, H.PAGO, 'Se pagó "Cambio de luces de emergencia · cuota 1/3" a ElectroHogar SRL ($ 180.000).', 22, time(17, 0), False),
            (san_martin, H.REUNION, 'Se hizo la reunión "Revisión del contrato de ascensores" por Google Meet.', 15, time(19, 0), False),
            (san_martin, H.PAGO, 'Se pagó "Mantenimiento anual de ascensores 2027 · cuota 2/12" a AscenSur Ascensores ($ 200.000).', 12, time(11, 30), False),
            (san_martin, H.VOTACION, 'Se cerró la votación: Cambiar el horario de limpieza de los espacios comunes a la mañana.', 3, time(9, 0), False),
            (norte, H.OBRA, 'Se reemplazó el portero eléctrico de la entrada.', 160, time(9, 0), True),
            (norte, H.CONTRATO, 'Se contrató a Jorge Ruiz Plomería: Cambio de bomba del tanque de agua ($ 385.000, contado).', 37, time(12, 0), False),
            (norte, H.PAGO, 'Se pagó "Cambio de bomba del tanque de agua · pago único" a Jorge Ruiz Plomería ($ 385.000).', 35, time(10, 0), False),
            (norte, H.CONTRATO, 'Se terminó de pagar el contrato con Jorge Ruiz Plomería: Cambio de bomba del tanque de agua.', 35, time(10, 1), False),
            (norte, H.REUNION, 'Se programó la reunión "Asamblea ordinaria" en el SUM del edificio.', 1, time(20, 0), False),
        ]:
            historia(consorcio, categoria, descripcion, dias, hora, manual)

        # --- Chats de Franco ---
        _, n5 = crear_chat_individual(
            'tomasfrancorusso@gmail.com', 'carlos.perez@mail.com',
            [
                ('carlos.perez@mail.com', 'Buenas Franco, ¿cuándo viene el técnico del ascensor?', ahora - timedelta(hours=1, minutes=30)),
                ('tomasfrancorusso@gmail.com', 'Hola Carlos, mañana a las 10 viene AscenSur a hacer la revisión mensual.', ahora - timedelta(hours=1, minutes=20)),
                ('carlos.perez@mail.com', 'Genial, gracias!', ahora - timedelta(hours=1, minutes=15)),
            ],
        )
        _, n6 = crear_chat_individual(
            'tomasfrancorusso@gmail.com', 'contacto@electrohogar.com',
            [
                ('tomasfrancorusso@gmail.com', 'Hola, necesito un presupuesto para cambiar las luces de emergencia del Edificio Norte.', hace(2, 4)),
                ('contacto@electrohogar.com', 'Hola Franco, ¿cuántas luces son aproximadamente?', hace(2, 3)),
                ('tomasfrancorusso@gmail.com', 'Son 12, distribuidas en palieres y cocheras.', hace(2, 2)),
                ('contacto@electrohogar.com', 'Perfecto, te lo mando mañana.', hace(1, 5)),
            ],
        )
        _, n7 = crear_chat_individual(
            'tomasfrancorusso@gmail.com', 'ana.fernandez@mail.com',
            [
                ('ana.fernandez@mail.com', 'Hola, ¿ya está la fecha de la próxima asamblea?', hace(3, 2)),
                ('tomasfrancorusso@gmail.com', 'Sí Ana, va a ser en el SUM. Ya la cargué en la agenda y les llega el aviso.', hace(3, 1)),
            ],
        )
        _, n8 = crear_chat_individual(
            'tomasfrancorusso@gmail.com', 'valentina.lopez@consora.com.ar',
            [
                ('valentina.lopez@consora.com.ar', 'Franco, ya cargué las facturas de luz de octubre.', hace(0, 5)),
                ('tomasfrancorusso@gmail.com', 'Genial, gracias Vale. ¿Pudiste pedir el presupuesto de pintura para el palier del Norte?', hace(0, 4)),
                ('valentina.lopez@consora.com.ar', 'Sí, mañana me pasan dos presupuestos y te los subo.', hace(0, 3)),
            ],
        )
        self.stdout.write(self.style.SUCCESS(f"Mensajes de chat de Franco cargados: {n5 + n6 + n7 + n8}"))

        self.stdout.write(self.style.SUCCESS(
            f"\nListo. Todos los usuarios de ejemplo tienen la contraseña: {PASSWORD_PRUEBA}"
        ))