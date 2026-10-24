"""Copy the Dead Air 3.0 config blocks from the Forge Config.java into the NeoForge one."""
import sys
from pathlib import Path

forge = Path(sys.argv[1]).read_text(encoding="utf-8").splitlines()
neo_path = Path(sys.argv[2])
neo = neo_path.read_text(encoding="utf-8").splitlines()

if any("BROADCASTS_ENABLED" in l for l in neo):
    sys.exit("already ported")


def grab(start_marker, end_marker, inclusive_end=True):
    s = next(i for i, l in enumerate(forge) if start_marker in l)
    e = next(i for i in range(s, len(forge)) if end_marker in forge[i])
    return [l.replace("ForgeConfigSpec", "ModConfigSpec") for l in forge[s:e + (1 if inclusive_end else 0)]]


def after_line(lines, marker, block, occurrence=1):
    idx = [i for i, l in enumerate(lines) if marker in l][occurrence - 1]
    return lines[:idx + 1] + block + lines[idx + 1:]


fields = [""] + grab("// --- Emergency broadcasts / The Listener ---", "SURVIVOR_SUPPLY_DAYS;")
builder = [""] + grab("// ========== EMERGENCY BROADCASTS ==========", 'defineInRange("survivorSupplyDays"') + ["        BUILDER.pop();"]
statics = grab("public static boolean broadcastsEnabled", "public static int survivorSupplyDays")
reload = grab("broadcastsEnabled = BROADCASTS_ENABLED.get();", "survivorSupplyDays = SURVIVOR_SUPPLY_DAYS.get();")
defaults = grab("BROADCASTS_ENABLED.set(true);", "SURVIVOR_SUPPLY_DAYS.set(2);")
saves = grab("BROADCASTS_ENABLED.save();", "SURVIVOR_SUPPLY_DAYS.save();")

neo = after_line(neo, "ModConfigSpec.ConfigValue<String> CUSTOM_MUSIC_PATH;", fields)
pop_idx = next(i for i, l in enumerate(neo) if 'define("customMusicPath"' in l)
pop_idx = next(i for i in range(pop_idx, len(neo)) if "BUILDER.pop();" in neo[i])
neo = neo[:pop_idx + 1] + builder + neo[pop_idx + 1:]
neo = after_line(neo, "public static String customMusicPath;", statics)
neo = after_line(neo, "customMusicPath = CUSTOM_MUSIC_PATH.get()", reload)
neo = after_line(neo, 'CUSTOM_MUSIC_PATH.set("");', defaults, occurrence=2)
neo = after_line(neo, "CUSTOM_MUSIC_PATH.save();", saves)

neo_path.write_text("\n".join(neo) + "\n", encoding="utf-8")
print("ported config blocks")
