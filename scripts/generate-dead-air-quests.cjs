/**
 * Generate Creatopia Unbound FTB Quests chapter: dead_air.snbt
 * Source of truth for quest progression lives here; copy output to the pack's
 * config/ftbquests/quests/chapters/ after running in Quest WIP quest-tools.
 *
 * In Quest WIP instance:
 *   node quest-tools/generate-dead-air.cjs
 */
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");

function randomId() {
  return crypto.randomBytes(8).toString("hex").toUpperCase();
}

function itemTask(item, count) {
  const id = randomId();
  return count
    ? `{ count: ${count}L, id: "${id}", item: "${item}", type: "item" }`
    : `{ id: "${id}", item: "${item}", type: "item" }`;
}

function checkTask(title) {
  return `{ id: "${randomId()}", title: "${title}", type: "checkmark" }`;
}

function quest({ title, subtitle, deps = [], tasks, x, y, shape, size }) {
  const id = randomId();
  const lines = ["\t\t{"];
  if (deps.length) lines.push(`\t\t\tdependencies: [${deps.map((d) => `"${d}"`).join(", ")}]`);
  if (subtitle) lines.push(`\t\t\tsubtitle: "${subtitle}"`);
  lines.push(`\t\t\ttitle: "${title}"`);
  lines.push(`\t\t\tid: "${id}"`);
  if (shape) lines.push(`\t\t\tshape: "${shape}"`);
  if (size) lines.push(`\t\t\tsize: ${size}d`);
  lines.push("\t\t\ttasks: [" + tasks.join(", ") + "]");
  lines.push(`\t\t\tx: ${x}d`);
  lines.push(`\t\t\ty: ${y}d`);
  lines.push("\t\t}");
  return { id, block: lines.join("\n") };
}

function buildChapter() {
  const chapterId = randomId();
  const q = [];

  const onAir = quest({
    title: "On the Air",
    subtitle: "Find a Radio Tower in the wild. Your Field Guide arrives when you tune in.",
    tasks: [checkTask("Explore a Radio Tower")],
    x: 0,
    y: 0,
  });
  q.push(onAir);

  const guide = quest({
    title: "Dead Air Field Guide",
    subtitle: "Read the in-game guide for stations, walkies, and tower upgrades.",
    deps: [onAir.id],
    tasks: [itemTask("dead_air:dead_air_field_guide")],
    x: 2,
    y: 0,
  });
  q.push(guide);

  const notes = quest({
    title: "Static Notes",
    subtitle: "Listen on your walkie while mobs die nearby — interference drops notes.",
    deps: [guide.id],
    tasks: [itemTask("dead_air:static_note", 9)],
    x: 4,
    y: 0,
  });
  q.push(notes);

  const t1 = quest({
    title: "T1.Radio",
    subtitle: "Craft a tier-1 walkie for local station discovery.",
    deps: [notes.id],
    tasks: [itemTask("dead_air:walkie_t1")],
    x: 6,
    y: 0,
  });
  q.push(t1);

  const chord = quest({
    title: "Resonant Chord",
    subtitle: "Pile nine Static Notes on the ground to resonate them into a chord.",
    deps: [notes.id],
    tasks: [itemTask("dead_air:resonant_chord")],
    x: 8,
    y: 0,
  });
  q.push(chord);

  const panel = quest({
    title: "Radio Panel",
    subtitle: "Bridge Dead Air and RadioTowers — craft a panel for your tower.",
    deps: [t1.id, chord.id],
    tasks: [itemTask("radiotowers:radio_panel")],
    x: 10,
    y: 0,
  });
  q.push(panel);

  const relay = quest({
    title: "Dimensional Relay",
    subtitle: "Combine chords with end-game materials to reach across dimensions.",
    deps: [panel.id],
    tasks: [itemTask("dead_air:dimensional_relay")],
    x: 12,
    y: 0,
  });
  q.push(relay);

  const t2 = quest({
    title: "T2.Radio",
    subtitle: "Walkie Link — link to a boosted tower station from anywhere.",
    deps: [relay.id, t1.id],
    tasks: [itemTask("dead_air:walkie_t2")],
    x: 14,
    y: -2,
  });
  q.push(t2);

  const signal = quest({
    title: "Signal Upgrade",
    subtitle: "Install on a radio panel to enable cross-dimensional linking.",
    deps: [relay.id],
    tasks: [itemTask("dead_air:signal_upgrade")],
    x: 14,
    y: 2,
  });
  q.push(signal);

  const compA = quest({
    title: "Disc Compendium (I)",
    subtitle: "Catalog disc A for jukebox playback at towers.",
    deps: [panel.id],
    tasks: [itemTask("dead_air:disc_compendium_a")],
    x: 10,
    y: 2,
  });
  q.push(compA);

  const compB = quest({
    title: "Disc Compendium (II)",
    deps: [compA.id],
    tasks: [itemTask("dead_air:disc_compendium_b")],
    x: 12,
    y: 2,
  });
  q.push(compB);

  const jukebox = quest({
    title: "Jukebox Upgrade",
    subtitle: "Install on a panel to play music discs through the tower.",
    deps: [compA.id, compB.id],
    tasks: [itemTask("dead_air:jukebox_upgrade")],
    x: 14,
    y: 2,
  });
  q.push(jukebox);

  const milestone = quest({
    title: "Full Spectrum",
    subtitle: "Linked walkie, boosted tower, and jukebox — the airwaves are yours.",
    deps: [t2.id, signal.id, jukebox.id],
    tasks: [checkTask("Dead Air mastery")],
    x: 16,
    y: 0,
    shape: "hexagon",
    size: 2,
  });
  q.push(milestone);

  return `{
	default_hide_dependency_lines: false
	default_quest_shape: ""
	filename: "dead_air"
	group: "63765EECDAA7B99C"
	icon: "dead_air:dead_air_field_guide"
	id: "${chapterId}"
	order_index: 1
	quest_links: [ ]
	quests: [
${q.map((x) => x.block).join("\n")}
	]
	title: "Dead Air"
}
`;
}

if (require.main === module) {
  const out = process.argv[2];
  const snbt = buildChapter();
  if (out) {
    fs.writeFileSync(path.resolve(out), snbt, "utf8");
    console.log("Wrote", out);
  } else {
    process.stdout.write(snbt);
  }
}

module.exports = { buildChapter };
