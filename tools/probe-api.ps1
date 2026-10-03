# Muestra las firmas públicas de clases de Minecraft en cada versión (para confirmar nombres de la API 26.x).
# Uso: powershell -File tools/probe-api.ps1 -Classes net.minecraft.server.permissions.PermissionLevel [-Filter regex]
param(
    [string[]]$Classes,
    [string]$Filter = '.',
    [string[]]$Versions = @('26.1.2', '26.2', '26.3')
)
$Classes = $Classes | ForEach-Object { $_ -split ',' } | Where-Object { $_ }
$Versions = $Versions | ForEach-Object { $_ -split ',' } | Where-Object { $_ }
$maven = "$env:USERPROFILE\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft"
foreach ($v in $Versions) {
    "################ MC $v ################"
    $cp = "$maven\minecraft-common-deobf\$v\minecraft-common-deobf-$v.jar;$maven\minecraft-clientonly-deobf\$v\minecraft-clientonly-deobf-$v.jar"
    foreach ($c in $Classes) {
        "---- $c"
        & javap -cp $cp -public $c 2>&1 | Where-Object { $_ -match $Filter -or $_ -match '^(public|Compiled|\})' }
    }
}


