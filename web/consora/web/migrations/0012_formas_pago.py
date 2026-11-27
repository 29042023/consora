from django.db import migrations


def cargar_formas_pago(apps, schema_editor):
    """Catálogo de formas de pago de los contratos: contado y 2, 3, 6 y 12 cuotas."""
    FormaPagoContrato = apps.get_model('web', 'FormaPagoContrato')
    FormaPagoContrato.objects.get_or_create(tipo='contado', cuotas=1)
    for cuotas in (2, 3, 6, 12):
        FormaPagoContrato.objects.get_or_create(tipo='cuotas', cuotas=cuotas)

    TipoDeuda = apps.get_model('web', 'TipoDeuda')
    TipoDeuda.objects.get_or_create(nombre='Contrato con proveedor')


class Migration(migrations.Migration):

    dependencies = [
        ('web', '0011_contratos_historial'),
    ]

    operations = [
        migrations.RunPython(cargar_formas_pago, migrations.RunPython.noop),
    ]
