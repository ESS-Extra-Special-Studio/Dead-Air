# Port all Dead Air soundtrack expansions to NeoForge 1.21.1 siblings.
$ErrorActionPreference = "Stop"
$Idea = "C:\Users\Ksivi\IdeaProjects"
$Logos = "C:\Users\Ksivi\Desktop\Extra Special Studio\Minecraft\logos"
$TplRoot = "$Idea\Dead Letters - 1.21.1 NeoForge"
$TestMods = "$env:USERPROFILE\curseforge\minecraft\Instances\NEOFORGE 1.21.1 TESTS\mods"

$Packs = @(
  @{
    ForgeName = "Dead Air After Hours FM"
    NeoName   = "Dead Air After Hours FM - 1.21.1 NeoForge"
    ModId     = "dead_air_after_hours_fm"
    ModName   = "Dead Air - After Hours FM"
    Version   = "1.1.1"
    Authors   = "Extra_Special_K, TWGMike, Manialate"
    Desc      = "A Dead Air radio expansion with 25 Lo-Fi and chillout tracks for After Hours FM. Works standalone; Dead Air is recommended for in-game radio playback."
    LogoSrc   = "dead_air_after_hours_fm.png"
    RootProj  = "dead-air-after-hours-fm-1.21.1-neoforge"
    LogLabel  = "After Hours FM"
  },
  @{
    ForgeName = "Dead Air - Beat Blocks"
    NeoName   = "Dead Air - Beat Blocks - 1.21.1 NeoForge"
    ModId     = "dead_air_block_beats_fm"
    ModName   = "Dead Air - Block Beats FM"
    Version   = "1.1.1"
    Authors   = "Extra_Special_K, TWGMike, Manialate"
    Desc      = "A Dead Air radio expansion with Block Beats FM soundtrack tracks. Works standalone; Dead Air is recommended for in-game radio playback."
    LogoSrc   = "dead_air_beat_blocks.png"
    RootProj  = "dead-air-block-beats-fm-1.21.1-neoforge"
    LogLabel  = "Block Beats FM"
  },
  @{
    ForgeName = "Dead Air - Broken Youth Radio"
    NeoName   = "Dead Air - Broken Youth Radio - 1.21.1 NeoForge"
    ModId     = "dead_air_broken_youth_radio"
    ModName   = "Dead Air - Broken Youth Radio"
    Version   = "1.1.1"
    Authors   = "Extra_Special_K, TWGMike, Manialate"
    Desc      = "A Dead Air radio expansion with Broken Youth Radio soundtrack tracks. Works standalone; Dead Air is recommended for in-game radio playback."
    LogoSrc   = "dead_air_broken_youth_radio.png"
    RootProj  = "dead-air-broken-youth-radio-1.21.1-neoforge"
    LogLabel  = "Broken Youth Radio"
  },
  @{
    ForgeName = "Dead Air - Frontline FM"
    NeoName   = "Dead Air - Frontline FM - 1.21.1 NeoForge"
    ModId     = "dead_air_frontline_fm"
    ModName   = "Dead Air - Frontline FM"
    Version   = "1.1.1"
    Authors   = "Extra_Special_K, TWGMike, Manialate"
    Desc      = "A Dead Air radio expansion with Frontline FM soundtrack tracks. Works standalone; Dead Air is recommended for in-game radio playback."
    LogoSrc   = "dead_air.png"
    RootProj  = "dead-air-frontline-fm-1.21.1-neoforge"
    LogLabel  = "Frontline FM"
  },
  @{
    ForgeName = "Dead Air - Iron Rain FM"
    NeoName   = "Dead Air - Iron Rain FM - 1.21.1 NeoForge"
    ModId     = "dead_air_iron_rain_fm"
    ModName   = "Dead Air - Iron Rain FM"
    Version   = "1.1.1"
    Authors   = "Extra_Special_K, TWGMike, Manialate"
    Desc      = "A Dead Air radio expansion with Iron Rain FM metal soundtrack tracks. Works standalone; Dead Air is recommended for in-game radio playback."
    LogoSrc   = "dead_air_iron_rain_fm.png"
    RootProj  = "dead-air-iron-rain-fm-1.21.1-neoforge"
    LogLabel  = "Iron Rain FM"
  },
  @{
    ForgeName = "Dead Air - Wayfarer Radio"
    NeoName   = "Dead Air - Wayfarer Radio - 1.21.1 NeoForge"
    ModId     = "dead_air_wayfarer_radio"
    ModName   = "Dead Air - Wayfarer Radio"
    Version   = "1.1.1"
    Authors   = "Extra_Special_K, TWGMike, Manialate"
    Desc      = "A Dead Air radio expansion with Wayfarer Radio soundtrack tracks. Works standalone; Dead Air is recommended for in-game radio playback."
    LogoSrc   = "dead_air.png"
    RootProj  = "dead-air-wayfarer-radio-1.21.1-neoforge"
    LogLabel  = "Wayfarer Radio"
  },
  @{
    ForgeName = "Dead Air Zero Gravity"
    NeoName   = "Dead Air Zero Gravity - 1.21.1 NeoForge"
    ModId     = "dead_air_zero_gravity"
    ModName   = "Dead Air - Zero Gravity"
    Version   = "1.0.1"
    Authors   = "Extra_Special_K, TWGMike, Manialate"
    Desc      = "A Dead Air radio expansion with Zero Gravity soundtrack tracks. Works standalone; Dead Air is recommended for in-game radio playback."
    LogoSrc   = "dead_air_zero_gravity.png"
    RootProj  = "dead-air-zero-gravity-1.21.1-neoforge"
    LogLabel  = "Zero Gravity"
  }
)

function Write-Utf8NoBom([string]$path, [string]$content) {
  $utf8 = New-Object System.Text.UTF8Encoding $false
  [System.IO.File]::WriteAllText($path, $content, $utf8)
}

function Convert-ToNeoJava([string]$forgeJava, [string]$logLabel) {
  $src = Get-Content -Raw $forgeJava
  # Extract TRACK_SLUGS list block (keep as-is)
  if ($src -notmatch '(?s)(private static final List<String> TRACK_SLUGS = List\.of\([^;]+;)') {
    throw "TRACK_SLUGS not found in $forgeJava"
  }
  $trackBlock = $Matches[1]
  if ($src -notmatch 'public class (\w+)') { throw "class name missing" }
  $className = $Matches[1]
  if ($src -notmatch 'MODID = "([^"]+)"') { throw "MODID missing" }
  $modId = $Matches[1]
  $pkg = "uk.co.extraspecialstudio.$modId"

  @"
package $pkg;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;

import java.util.List;

@Mod($className.MODID)
public class $className {
    public static final String MODID = "$modId";
    private static final Logger LOGGER = LogUtils.getLogger();
    $trackBlock

    public $className(IEventBus modEventBus) {
        modEventBus.addListener(this::onRegister);
    }

    private void onRegister(RegisterEvent event) {
        event.register(Registries.SOUND_EVENT, registry -> {
            for (String slug : TRACK_SLUGS) {
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MODID, "music." + slug);
                registry.register(id, SoundEvent.createVariableRangeEvent(id));
            }
        });
        LOGGER.info("[$logLabel] Registered {} tracks.", TRACK_SLUGS.size());
    }
}
"@
}

function New-FmNeoProject($p) {
  $forge = Join-Path $Idea $p.ForgeName
  $neo = Join-Path $Idea $p.NeoName
  Write-Host "`n=== $($p.NeoName) ===" -ForegroundColor Cyan
  if (-not (Test-Path $forge)) { throw "Missing forge project: $forge" }

  New-Item -ItemType Directory -Force -Path $neo | Out-Null

  # Gradle wrapper from template
  New-Item -ItemType Directory -Force -Path "$neo\gradle\wrapper" | Out-Null
  Copy-Item "$TplRoot\gradlew.bat" "$neo\gradlew.bat" -Force
  if (Test-Path "$TplRoot\gradlew") { Copy-Item "$TplRoot\gradlew" "$neo\gradlew" -Force }
  Copy-Item "$TplRoot\gradle\wrapper\*" "$neo\gradle\wrapper\" -Force

  @"
pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    id 'org.gradle.toolchains.foojay-resolver-convention' version '1.0.0'
}

rootProject.name = '$($p.RootProj)'
"@ | Set-Content -Encoding UTF8 "$neo\settings.gradle"

  @"
org.gradle.jvmargs=-Xmx3G
org.gradle.daemon=false
org.gradle.parallel=true
org.gradle.caching=true

parchment_minecraft_version=1.21.1
parchment_mappings_version=2024.11.17

minecraft_version=1.21.1
minecraft_version_range=[1.21.1]
neo_version=21.1.222
neo_version_range=[21.1.0,)
loader_version_range=[1,)

mod_id=$($p.ModId)
mod_name=$($p.ModName)
mod_license=All Rights Reserved
mod_version=$($p.Version)
mod_group_id=uk.co.extraspecialstudio
mod_authors=$($p.Authors)
mod_description=$($p.Desc)
"@ | Set-Content -Encoding UTF8 "$neo\gradle.properties"

  @"
plugins {
    id 'java-library'
    id 'maven-publish'
    id 'net.neoforged.moddev' version '2.0.141'
    id 'idea'
}

tasks.named('wrapper', Wrapper).configure {
    distributionType = Wrapper.DistributionType.BIN
}

version = mod_version
group = mod_group_id

base {
    archivesName = mod_id
}

tasks.named('jar', Jar).configure {
    archiveFileName = "`${mod_id}-`${version}+`${minecraft_version}-neoforge.jar"
    from('CHANGELOG.md') {
        into '.'
    }
}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

neoForge {
    version = project.neo_version

    parchment {
        mappingsVersion = project.parchment_mappings_version
        minecraftVersion = project.parchment_minecraft_version
    }

    runs {
        client {
            client()
            systemProperty 'neoforge.enabledGameTestNamespaces', project.mod_id
        }
        server {
            server()
            programArgument '--nogui'
            systemProperty 'neoforge.enabledGameTestNamespaces', project.mod_id
        }
        configureEach {
            systemProperty 'forge.logging.markers', 'REGISTRIES'
            logLevel = org.slf4j.event.Level.DEBUG
        }
    }

    mods {
        "`${mod_id}" {
            sourceSet(sourceSets.main)
        }
    }
}

var generateModMetadata = tasks.register('generateModMetadata', ProcessResources) {
    var replaceProperties = [
            minecraft_version      : minecraft_version,
            minecraft_version_range: minecraft_version_range,
            neo_version            : neo_version,
            neo_version_range      : neo_version_range,
            loader_version_range   : loader_version_range,
            mod_id                 : mod_id,
            mod_name               : mod_name,
            mod_license            : mod_license,
            mod_version            : mod_version,
            mod_authors            : mod_authors,
            mod_description        : mod_description,
    ]
    inputs.properties replaceProperties
    expand replaceProperties
    filteringCharset = 'UTF-8'
    from 'src/main/templates'
    into 'build/generated/sources/modMetadata'
}

sourceSets.main.resources.srcDir generateModMetadata
neoForge.ideSyncTask generateModMetadata

tasks.register('copyToNeoForgeTestPack') {
    dependsOn 'jar'
    doLast {
        def jar = tasks.named('jar', Jar).get().archiveFile.get().asFile
        def dest = file("`${System.getProperty('user.home')}/curseforge/minecraft/Instances/NEOFORGE 1.21.1 TESTS/mods")
        if (dest.exists()) {
            dest.listFiles({ f -> f.name.startsWith('$($p.ModId)-') && f.name.endsWith('.jar') } as FileFilter)?.each { it.delete() }
            copy { from jar; into dest }
            println "Copied `${jar.name} to `${dest}"
        } else {
            println "NeoForge test pack mods folder not found: `${dest}"
        }
    }
}

tasks.named('build').configure { finalizedBy 'copyToNeoForgeTestPack' }

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
}

idea {
    module {
        downloadSources = true
        downloadJavadoc = true
    }
}
"@ | Set-Content -Encoding UTF8 "$neo\build.gradle"

  New-Item -ItemType Directory -Force -Path "$neo\src\main\templates\META-INF" | Out-Null
  @"
modLoader="javafml"
loaderVersion="`${loader_version_range}"
license="`${mod_license}"

[[mods]]
modId="`${mod_id}"
version="`${mod_version}"
displayName="`${mod_name}"
displayURL="https://extraspecialstudio.co.uk"
authors="`${mod_authors}"
displayTest="IGNORE_ALL_VERSION"
logoFile="`${mod_id}.png"
description='''`${mod_description}'''

[[dependencies.`${mod_id}]]
modId="neoforge"
type="required"
versionRange="`${neo_version_range}"
ordering="NONE"
side="BOTH"

[[dependencies.`${mod_id}]]
modId="minecraft"
type="required"
versionRange="`${minecraft_version_range}"
ordering="NONE"
side="BOTH"
"@ | Set-Content -Encoding UTF8 "$neo\src\main\templates\META-INF\neoforge.mods.toml"

  # Java
  $forgeJava = Get-ChildItem "$forge\src\main\java" -Recurse -Filter "Dead_air_*.java" | Select-Object -First 1
  if (-not $forgeJava) { throw "No Dead_air_*.java in $($p.ForgeName)" }
  $pkgDir = "$neo\src\main\java\uk\co\extraspecialstudio\$($p.ModId)"
  New-Item -ItemType Directory -Force -Path $pkgDir | Out-Null
  Convert-ToNeoJava $forgeJava.FullName $p.LogLabel | Set-Content -Encoding UTF8 "$pkgDir\$($forgeJava.BaseName).java"

  # Assets + pack.mcmeta
  New-Item -ItemType Directory -Force -Path "$neo\src\main\resources" | Out-Null
  if (Test-Path "$forge\src\main\resources\assets") {
    robocopy "$forge\src\main\resources\assets" "$neo\src\main\resources\assets" /E /NFL /NDL /NJH /NJS /nc /ns /np | Out-Null
  }
  @"
{
  "pack": {
    "description": "$($p.ModId) resources",
    "pack_format": 34
  }
}
"@ | Set-Content -Encoding UTF8 "$neo\src\main\resources\pack.mcmeta"

  # Logo
  $logoSrc = Join-Path $Logos $p.LogoSrc
  if (-not (Test-Path $logoSrc)) { $logoSrc = Join-Path $Logos "dead_air.png" }
  Copy-Item $logoSrc "$neo\src\main\resources\$($p.ModId).png" -Force
  Copy-Item $logoSrc "$Logos\$($p.ModId).png" -Force -ErrorAction SilentlyContinue

  # Changelog
  $cl = "$forge\CHANGELOG.md"
  if (Test-Path $cl) {
    Copy-Item $cl "$neo\CHANGELOG.md" -Force
    $text = Get-Content -Raw "$neo\CHANGELOG.md"
    $note = "- NeoForge 1.21.1 port (same soundtrack / client-only expansion as Forge)."
    if ($text -notmatch [regex]::Escape($note)) {
      if ($text -match "(## $($p.Version)\r?\n)") {
        $text = $text -replace "(## $($p.Version)\r?\n)", "`$1`n$note`n"
      } else {
        $text = "# Changelog`n`n## $($p.Version)`n`n$note`n`n" + $text
      }
      Set-Content -Encoding UTF8 "$neo\CHANGELOG.md" -Value $text
    }
  } else {
    @"
# Changelog

## $($p.Version)

- NeoForge 1.21.1 port (same soundtrack / client-only expansion as Forge).
"@ | Set-Content -Encoding UTF8 "$neo\CHANGELOG.md"
  }

  Write-Host "Created $($p.NeoName)"

  # PowerShell Set-Content -Encoding UTF8 writes a BOM; Gradle rejects it.
  Get-ChildItem $neo -Include *.gradle,*.properties,*.toml,*.java,*.md,*.mcmeta -Recurse -File -ErrorAction SilentlyContinue | ForEach-Object {
    $bytes = [System.IO.File]::ReadAllBytes($_.FullName)
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
      Write-Utf8NoBom $_.FullName ([System.IO.File]::ReadAllText($_.FullName))
    }
  }
}

# Create all projects (or only first if -FirstOnly)
$only = $args[0]
if ($only -eq "first") {
  New-FmNeoProject $Packs[0]
} elseif ($only) {
  $hit = $Packs | Where-Object { $_.ModId -eq $only -or $_.NeoName -like "*$only*" } | Select-Object -First 1
  if (-not $hit) { throw "Unknown pack: $only" }
  New-FmNeoProject $hit
} else {
  foreach ($p in $Packs) { New-FmNeoProject $p }
}

Write-Host "`nDone creating projects." -ForegroundColor Green
