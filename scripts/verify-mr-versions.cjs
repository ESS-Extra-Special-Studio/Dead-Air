const jobs = [
  ["dead-air", "2.1.3"],
  ["apocalypse-structures-radio-towers-and-airdrops", "1.2.2"],
];
(async () => {
  for (const [slug, prefix] of jobs) {
    const r = await fetch(`https://api.modrinth.com/v2/project/${slug}/version?limit=8`, {
      headers: { "User-Agent": "ESS/1.0" },
    });
    const list = await r.json();
    console.log("\n", slug, r.status);
    for (const v of list.filter((x) => String(x.version_number).startsWith(prefix))) {
      console.log(" ", v.version_number, v.id, (v.loaders || []).join(","));
    }
  }
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
