# OFFICE iQ content templates (ADR 0027)

Dummy/rules-only templates — no real content, just placeholders and inline
documentation of the rules — for another repo/team to fill in and upload.

Three files, three admin buttons:

| File | Task | Upload target |
| --- | --- | --- |
| `company-tickets-template.json` | Stand up a brand-new catalog entry — `company` + `tickets` already merged into the one file this button expects, since [`POST /api/admin/catalog/import`](../docs/API.md) takes them together (ADR 0009) | Admin Catalog toolbar → **Add workspace** → "Company & tickets file (.json) *" |
| `dsa-template.json` | Add/replace DSA problems (optional, on the same "Add workspace" import, or on an existing entry) | **Add workspace** → "DSA problems file (.json, optional)", or existing-entry Content step → **Upload DSA problems JSON** |
| `quiz-template.json` | Add/replace the quiz pool (optional, on the same "Add workspace" import, or on an existing entry) | **Add workspace** → "Quiz file (.json, optional)", or existing-entry Content step → **Upload Quiz JSON** |

**Editing an existing entry's tickets** (Content step → "Upload Tickets JSON",
`POST /catalog/:id/content/tickets`) has no dedicated template file — that
button takes just the `tickets` array with no `company` block. Copy the
`tickets` array out of `company-tickets-template.json` into its own
`{ "tickets": [...] }` file (drop the `company` key and the `_meta` block, or
leave `_meta` in — it's stripped automatically either way) and upload that.

## Why three files instead of one

Before ADR 0027, a single JSON file carried `company` + `tickets` +
`dsaProblems` + `quizQuestions` together, and DSA problems were paired onto
each ticket positionally or by a matching `day` at *upload* time. Tickets,
DSA and quiz are now uploaded independently, each with its own admin button
and endpoint:

- **Tickets** (`POST /catalog/:id/content/tickets`) creates a new content-pack
  version. It's the backbone — DSA/quiz already on the pack are carried
  forward untouched.
- **DSA** (`POST /catalog/:id/content/dsa`) and **quiz**
  (`POST /catalog/:id/content/quiz`) each *patch* the current active pack in
  place — no new version, tickets and the other type stay exactly as they
  were. Both require tickets to already exist for that catalog entry.
- Every DSA item now carries its own `day` (defaulting to its position in the
  array if omitted, same as tickets) instead of being denormalised onto a
  ticket at ingest time — the two are matched back together at *read* time.

See `docs/adr/0027-split-catalog-content-upload-by-type.md` for the full
design rationale, and `docs/API.md` for the exact request/response shapes.

## Legacy single-file reference

`../content-template.json` at the repo root is a full 30-day *real* example
(not a rules-only dummy) for `officeiq-codebase` itself, still valid as-is
for the single-file import endpoint (`POST /api/admin/catalog/import`) —
its `dsaProblems` already carry explicit `day` values, so it needs no changes
under ADR 0027.
