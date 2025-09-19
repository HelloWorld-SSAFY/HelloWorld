from django.db import migrations

class Migration(migrations.Migration):
    dependencies = [("api", "0002_triggercategorypolicy_max_gw_and_more")]
    operations = [
        migrations.RunPython(migrations.RunPython.noop, migrations.RunPython.noop),
    ]