---
description: Review SmartHome code quality — run all three analysers, report violations grouped by tool
---

Run the three static analyzers in report mode (they write full reports without failing the build,
so one pass gives you everything). Compile first so SpotBugs has bytecode to read:

    ./mvnw compile checkstyle:checkstyle pmd:pmd spotbugs:spotbugs

- CheckStyle → Google Java Style / formatting
- PMD → bad patterns, dead code, smells
- SpotBugs → bytecode-level real bugs

Then read the XML reports the plugins write under `target/` (by default `checkstyle-result.xml`,
`pmd.xml`, `spotbugsXml.xml`) and:

- Group every violation by tool.
- For each: file, line, rule name, and one line on *why* the rule exists — the real-world risk it
  guards against.
- Propose a concrete fix for each, but **do not apply any** unless I confirm.
- Finish with a summary: total per tool, and which are quick formatting fixes vs. genuine
  logic/design issues.