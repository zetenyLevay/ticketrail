---
type: reference
spec_version: 5
---

# Ticket Spec, general edition (Markdown vault ↔ ticket service contract)

**Abstract.** This document specifies a contract between a Markdown vault, edited in Obsidian with the Tasks plugin, and an external ticket service and its apps. It defines how tickets are encoded in the vault, how the service reads them, which writes the service is permitted to make, and how the service behaves under concurrent editing and file sync. The vault is the source of truth.

This edition is vault-independent. Every vault-specific value (paths, folder names, queue names, time zone, note types) is a **profile parameter** (1.2), set by the deployment in a **profile**.

---

## 0. Conventions and versioning

### 0.1 Requirements language

The key words "MUST", "MUST NOT", "REQUIRED", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "NOT RECOMMENDED", "MAY", and "OPTIONAL" in this document are to be interpreted as described in BCP 14 (RFC 2119, RFC 8174) when, and only when, they appear in all capitals, as shown here.

### 0.2 Conformance

Requirements in this document apply to one of three parties:

- the **service**: the ticket service that indexes and writes the vault;
- the **app**: any client that talks to the service;
- the **deployment**: the devices, sync setup, editor configuration and profile around the vault.

The service MAY run embedded in an app or as a standalone server. An app with an embedded service MUST meet both the app requirements and the service requirements.

A conforming service satisfies every MUST and MUST NOT addressed to the service, for every valid profile. Requirements on vault content describe what well-formed content looks like; where content is not well-formed, this document specifies how the service MUST behave.

Sections and text marked *(informative)*, and every example, contain no requirements.

### 0.3 Versioning

- The profile MUST declare `spec_version`: the version of this specification it targets, as an integer.
- On start, the service MUST read `spec_version` from the profile.
- If `spec_version` is higher than the highest version the service implements, the service MUST continue to read and index the vault, and MUST refuse every write with `unsupported_spec`.

---

## 1. Terminology and profile

### 1.1 Terminology

| Term | Definition |
|---|---|
| **Vault** | The folder tree of Markdown files, possibly synced between devices. |
| **Editor** | Obsidian with the Tasks plugin, as used by people on any device. |
| **Profile** | The set of profile parameter values (1.2) for one vault, stored as described in 1.3. |
| **Server** | A service running standalone, which apps use over a network. |
| **Embedded service** | A service running inside an app, on the app's own host. |
| **Service configuration** | The service's own settings, stored outside the vault (1.3). |
| **Ticket** | One unit of work with a stable id. Every ticket is either a ticket line or a ticket note. |
| **Ticket line** | A single Markdown checkbox line in Tasks format that qualifies under 3.3. |
| **Ticket note** | A Markdown file whose frontmatter `type` is a ticket note type (4.1). |
| **Queue** | The area a ticket belongs to: a tag on a ticket line, the `queue` field on a ticket note. |
| **Ticket id** | The string `t` followed by a positive integer without leading zeros, e.g. `t142` (6.1). |
| **Today** | The current date in the profile's `timezone` (5.1). |
| **State file** | The note that holds the id counter (6.2). |

Paths:

- The service MUST express every path relative to the vault root, with `/` as the separator.
- The service MUST read files as UTF-8.
- When the service rewrites an existing file, it MUST preserve that file's line-ending convention (`\n` or `\r\n`).

### 1.2 Profile parameters

The profile MUST set every parameter marked *required*. The service MUST use the stated default for an optional parameter the profile leaves unset.

| Parameter             | Type                                          | Required | Default             | Meaning                                                                           |
| --------------------- | --------------------------------------------- | -------- | ------------------- | --------------------------------------------------------------------------------- |
| `spec_version`        | integer                                       | yes      |                     | 0.3                                                                               |
| `timezone`            | IANA zone name                                | yes      |                     | the zone that defines "today" (5.1)                                               |
| `queues`              | set of names matching `[a-z][a-z0-9_-]*`      | yes      |                     | the closed queue set (2.3)                                                        |
| `daily_note_path`     | path pattern containing `{date}`              | yes      |                     | where daily notes live; `{date}` is the ISO date `YYYY-MM-DD`                     |
| `daily_note_template` | path                                          | yes      |                     | the template for a new daily note (5.2)                                           |
| `tickets_heading`     | heading text                                  | no       | `Tickets`           | the level-2 heading in daily notes that collects new tickets (3.3, 5.3)           |
| `task_note_folder`    | folder path                                   | yes      |                     | where the service creates task notes (5.4)                                        |
| `task_note_template`  | path                                          | yes      |                     | gives the frontmatter field order of new task notes (5.4)                         |
| `extra_note_types`    | map from type name to a note type entry (1.4) | no       | empty               | further ticket note types besides `task` (4.1)                                    |
| `state_file`          | path                                          | yes      |                     | the state file (6.2)                                                              |
| `excluded_paths`      | list of folder prefixes                       | no       | empty               | folders never indexed, in addition to 3.4; template folders SHOULD be listed here |
| `conflict_pattern`    | glob                                          | no       | `*.sync-conflict-*` | names of the file synchroniser's conflict copies (8.6)                            |
| `quiet_period`        | seconds                                       | no       | `10`                | 6.4, 8.4                                                                          |
| `debounce`            | milliseconds                                  | no       | `500`               | 8.1                                                                               |
| `poll_interval`       | seconds                                       | no       | `30`                | 8.1, on hosts that cannot watch the vault                                         |
| `full_scan_interval`  | minutes                                       | no       | `10`                | 9.4                                                                               |
| `wip_warn_per_queue`  | integer ≥ 1                                   | no       | `1`                 | 2.4                                                                               |

The service MUST refuse to start with a profile that is missing a required parameter or has an invalid value, and MUST say which parameter is wrong.

### 1.3 Service configuration and profile location

- The service configuration is stored outside the vault, in a location chosen by the implementation. It MUST contain `vault_root`: the absolute path of the vault folder on the service's host. Each host that runs a service has its own service configuration and its own `vault_root`.
- The service configuration MUST contain exactly one of:
  - `profile`: the profile parameters themselves; or
  - `profile_path`: the vault-relative path of a profile stored inside the vault.
- A profile stored inside the vault MUST be a Markdown file containing exactly one fenced code block whose info string is `ticket-profile`. That block holds the profile parameters as YAML. The service MUST ignore the rest of the file.
- A profile stored inside the vault MUST be in a path excluded from indexing (3.4). If it is not, or if the file has no `ticket-profile` block or more than one, the service MUST refuse to start.
- *(Informative: a profile outside the vault keeps the vault free of service files and suits a "connect a vault" flow in an app. A profile inside the vault syncs to every device with the vault and can be edited in the editor.)*

### 1.4 Note type entries

Each entry of `extra_note_types` maps a type name, which MUST NOT be `task`, to an entry with these keys:

| Key | Type | Required | Default | Meaning |
|---|---|---|---|---|
| `queue` | a queue name from `queues` | no | none | fixes the queue of every note of this type (4.2) |
| `due_date` | `writable` or `read_only` | no | `read_only` | whether the service may write `due_date` on this type (7.2) |
| `fields` | list of field names | no | empty | further fields of this type. All are read-only. They MUST NOT repeat a core field (4.2). |

An entry with any other key, or a value outside the table, is invalid (1.2).

### 1.5 Profile changes

- Profile changes take effect only when the service restarts. The service MUST NOT apply a changed profile while running.
- After loading a changed profile, the service MUST run a full scan (9.4).
- The service SHOULD report each line that qualified as a ticket under the previous profile but no longer does, under the `no_longer_ticket` diagnostic. *(Informative: removing a queue makes id-less lines that qualified only through that tag stop being tickets. Lines with an id still qualify through 3.3 item 2.)*

---

## 2. Enumerations

### 2.1 Status

| Status | Line checkbox char | Tasks status type | Meaning |
|---|---|---|---|
| `inbox` | ` ` (space) **and** no queue tag | TODO | captured, not yet triaged |
| `ready` | ` ` (space) with a queue tag | TODO | to do |
| `in_progress` | `/` | IN_PROGRESS | being worked on |
| `blocked` | `b` | ON_HOLD | waiting on something |
| `done` | `x` | DONE | finished |
| `dropped` | `-` | CANCELLED | won't do |

- The service MUST read `X` as `done`. When writing `done`, the service MUST write `x`.
- A checkbox line whose character is not listed above is not a ticket. The service MUST NOT index it. If such a line carries a ticket id (6.1), the service MUST report `id_on_non_ticket`.
- The `status` field of a ticket note MUST hold one of the six status words. If it is missing, empty or unknown, the service MUST report `unknown_status` and MUST index the note as `ready`.
- **Deployment:** the editor's Tasks settings MUST define the custom status `b` with type ON_HOLD, and MUST keep `/` (IN_PROGRESS) and `-` (CANCELLED), on every device.
- App-only data, such as blocked reasons and time estimates, MUST be stored by the service keyed by ticket id outside the vault. The service MUST NOT write app-only data to the vault.

### 2.2 Priority

| Number | Line emoji (written) | Also accepted on read |
|---|---|---|
| `1` | `⏫` | `🔺` |
| `2` | `🔼` | |
| `3` | *(none)* | |
| `4` | `🔽` | `⏬` |

- The service MUST read `🔺` as `1` and `⏬` as `4`.
- The service MUST write only `⏫`, `🔼`, no emoji, or `🔽`. When the service rewrites a line that carries `🔺` or `⏬`, it MUST replace it with `⏫` or `🔽` respectively.
- Vault content SHOULD use only the four written forms.
- The `priority` field of a ticket note MUST be an unquoted integer from 1 to 4. The service MUST treat an empty or missing value as `3`. For any other value outside 1 to 4, the service MUST treat it as `3` and MUST report `bad_priority`.

### 2.3 Queues

The queue set is the closed set given by the profile parameter `queues`.

- On a ticket line, the queue is given by the tag `#<queue>`. A tag matches only if it is case-sensitively equal to `#` plus a queue name, preceded by the start of the description or whitespace, and followed by whitespace or the end of the description.
- If a line carries more than one queue tag, the service MUST use the first and MUST report `multiple_queue_tags`.
- Other tags are permitted. The service MUST return them in `tags` and MUST NOT interpret them.
- On a ticket note, `queue` MUST hold a bare queue name without `#`. If it holds any other value, the service MUST report `unknown_queue` and MUST treat the note as having no queue.
- Changing the queue set is a profile change. The deployment SHOULD update any editor queries that list queue names at the same time.

### 2.4 In-progress limit

This specification imposes no hard limit on `in_progress` tickets.

- The service MUST report each queue that holds more `in_progress` tickets than `wip_warn_per_queue` under the `wip_over` diagnostic.
- The app SHOULD read `wip_warn_per_queue` from the service, and SHOULD warn when a queue exceeds it.
- The service MUST NOT modify the vault to enforce the limit.

---

## 3. Ticket lines

### 3.1 Grammar

The service MUST treat a line as a Tasks task if and only if it matches:

```regex
^(?<indent>[ \t>]*)(?<bullet>[-*+]|\d+[.)]) \[(?<char>.)\] (?<rest>.*)$
```

The service MUST then read `rest` from the right, the same way the Tasks plugin does: it MUST repeatedly strip one trailing component that matches the table below, until none matches.

| Component | Pattern at end of `rest` | Field |
|---|---|---|
| block id | ` \^(?<id>[A-Za-z0-9-]+)$` | `block_id` (a ticket id if it matches `^t[1-9]\d*$`, 6.1) |
| done date | ` ✅ (?<d>\d{4}-\d{2}-\d{2})$` | `done_date` |
| cancelled date | ` ❌ (?<d>\d{4}-\d{2}-\d{2})$` | `cancelled_date` |
| due date | ` 📅 (?<d>\d{4}-\d{2}-\d{2})$` | `due_date` |
| scheduled (do) date | ` ⏳ (?<d>\d{4}-\d{2}-\d{2})$` | `do_date` |
| start date | ` 🛫 (?<d>\d{4}-\d{2}-\d{2})$` | `start_date` |
| created date | ` ➕ (?<d>\d{4}-\d{2}-\d{2})$` | `created_date` |
| recurrence | ` 🔁 (?<r>[a-zA-Z0-9, !]+)$` | `recurrence` (raw rule text) |
| priority | ` (?<p>[🔺⏫🔼🔽⏬])$` | `priority` |
| trailing tag | ` (?<t>#[^\s#]+)$` | moved to the end of the description |

- The service MUST recognise a block id only as the very last component of the line.
- The service MUST ignore variation selectors (`U+FE0F`) after any emoji when reading.
- What remains after stripping is the **description**. The description MUST keep all tags, including the queue tag.
- Lines that contain `🆔` (Tasks id), `⛔` (depends on) or `🏁` (on completion) MUST be indexed, with those parts left in the2026-10-23 description. The service MUST refuse every write to such a line with `unsupported_field`.

### 3.2 Canonical write order

When the service writes or rewrites a line, it MUST emit the components in the order below, which is the order the Tasks plugin serialises in:

```
<indent><bullet> [<char>] <description incl. tags> <priority> 🔁 <rule> ➕ <date> 🛫 <date> ⏳ <date> 📅 <date> ❌ <date> ✅ <date> ^<id>
```

- The service MUST separate components with a single space, and MUST NOT emit absent components or extra spaces.
- When the service adds a queue tag, it MUST append it to the end of the description.

*Examples (informative), with queues `home` and `work`:*

```
- [ ] Clean the kitchen #home 🔼 ⏳ 2026-10-22 ^t142
- [/] Fix the build #work ⏫ 📅 2026-10-24 ^t143
- [x] Read chapter 4 #work ✅ 2026-10-21 ^t139
- [ ] Gym #home 🔁 every week on Monday ⏳ 2026-10-26 ^t144
- [ ] call the dentist ^t145
```

### 3.3 Which lines are tickets

Subject to 3.4, the service MUST treat a Tasks line (3.1) as a ticket line if and only if at least one of these holds:

1. its description contains a queue tag (2.3);
2. its block id matches `^t[1-9]\d*$` (6.1);
3. it is in a daily note (a file matching `daily_note_path`), at top level (no indent), under the level-2 heading whose text equals `tickets_heading`, and before the next heading of level 2 or lower.

The service MUST NOT treat any other checkbox line as a ticket.

**Subtasks.** An indented checkbox line directly under a ticket line that is not itself a ticket line is a subtask of that ticket. The service MUST return subtasks in the parent's `subtasks` field (text and done flag), and MUST NOT write to them.

### 3.4 Excluded regions and files

The service MUST NOT parse tickets inside:

- fenced code blocks (opened by ```` ``` ```` or `~~~`), including Tasks query blocks;
- the frontmatter block;
- HTML comments (`<!-- -->`).

The service MUST NOT index:

- paths under any folder in `excluded_paths`;
- any path with a segment starting with `.` (which covers the editor's configuration folder, version-control folders and the service's temporary files);
- any file whose name matches `conflict_pattern`. The service MUST report these under `conflicts` instead (8.6).

These exclusions apply to ticket indexing only. They do not prevent the service from reading the profile (0.3), `daily_note_template` and `task_note_template` (5.2, 5.4) or the state file (6.2), or from writing the state file (7.4), wherever those sit.

---

## 4. Ticket notes

### 4.1 Ticket note types

A file is a ticket note if and only if its frontmatter `type` is `task` or a type declared in `extra_note_types`, and its path is not excluded by 3.4.

### 4.2 Fields

The core fields below apply to every ticket note type:

| Field | Type | Writable by the service | Notes |
|---|---|---|---|
| `type` | string | no | |
| `ticket_id` | string `t<n>` | insert once (6.4) | |
| `status` | enum 2.1 | yes | |
| `priority` | integer 1-4 | yes | empty means 3 |
| `queue` | enum 2.3 | on `task` only | see below |
| `do_date` | ISO date or empty | yes | the note equivalent of ⏳ |
| `due_date` | ISO date or empty | on `task`, and on types with `due_date: writable` (1.4) | the note equivalent of 📅 |

For each type in `extra_note_types` (1.4):

- If the entry sets `queue`, the service MUST treat a note of that type with no `queue` field as having that queue, and MUST NOT write `queue` on it. If the entry sets no `queue`, `queue` is read-only on that type.
- `due_date` is writable on that type only if the entry sets `due_date: writable`.
- The fields listed in `fields` are read-only.

Further rules:

- The service MUST use the filename without `.md` as the note's title.
- Any frontmatter value of the form `"[[...]]"` is a wikilink. To resolve it, the service MUST strip `[[` and `]]` and anything after the first `|` or `#`; the result is the target note's filename. The service MUST return each wikilink field, resolved, in `links`.
- The service MUST treat a field with no value (e.g. `due_date:`) as `null`.
- The service MUST preserve fields it does not write byte for byte. It MUST return fields other than the core fields in `extra`.
- If a note has no queue and its status is `ready`, the service MUST report its status as `inbox`.
- Checkbox lines inside a ticket note are not subtasks of the note. They are ticket lines only if they qualify under 3.3 on their own.

*Example (informative), a type `assignment` declared with fixed queue `work` and an extra read-only `course` link:*

```yaml
---
type: assignment
course: "[[Some_Course]]"
due_date: 2026-09-10
status: ready
priority: 3
queue: work
do_date:
ticket_id: t150
---
```

---

## 5. Time, daily notes and new tickets

### 5.1 Time zone

- The service MUST determine "today" in the profile's `timezone`. It MUST NOT use the host clock's time zone.
- The same time zone MUST be used for `✅` and `❌` dates and for any timestamps the service or app records.

### 5.2 Daily notes

- Today's daily note is the path given by `daily_note_path` with `{date}` replaced by today's ISO date.
- If today's note does not exist when the service needs it, the service MUST create it from `daily_note_template`. It MUST replace every `{{date:YYYY-MM-DD}}`, `{{date}}` and `{{title}}` with today's ISO date, and MUST NOT change anything else.
- If the template contains any other `{{...}}` placeholder, or any template-plugin syntax such as Templater's &lt;% … %&gt; tags, the service MUST NOT create the note, and MUST refuse the write that needed it with `invalid_value`. *(Informative: such syntax would be copied into the note unexpanded, because a template plugin's on-create trigger does not run on a file that is written non-empty. The tags are written as HTML entities here so that this file, which lives in the templates folder, contains no real Templater tag.)*
- **Deployment:** the editor's daily-notes date format MUST produce the same filename as `daily_note_path`.

### 5.3 New ticket lines

- The service MUST append each new ticket line created by the app or an agent to the `tickets_heading` section of today's daily note: directly after the last non-blank line of that section, where the section ends at the next heading of level 2 or lower.
- If today's note exists but has no such heading, the service MUST append `\n## <tickets_heading>\n` at the end of the file and then the line.
- The service MUST NOT add a created date (`➕`) to new lines.

### 5.4 New ticket notes

- The service MUST create new task notes at `<task_note_folder>/<Slug>.md`. A slug consists of the characters `[A-Za-z0-9_-]`, with words joined by `_`. If that path exists, the service MUST refuse the write with `invalid_value`.
- The service MUST take only the frontmatter field names and their order from `task_note_template`, and MUST NOT copy any value, body text or template syntax from it. The template MAY use any template-plugin syntax.
- The frontmatter MUST contain exactly the template's fields, in the template's order, and every core field of 4.2 the template lacks MUST be appended after them.
- `type` MUST be `task`, and `ticket_id` MUST be a new id (6.2), written at creation. If the request provides a value for either, the service MUST refuse the write with `read_only_field`.
- The request MAY provide values for any other of these fields. For each field, the service MUST write the value the request provides, else the default of sections 2 and 4 (`status: ready`, or `inbox` with no queue; `priority: 3`), else leave it empty. Values MUST be written as 7.2 requires.
- If the request provides a value for a field the frontmatter does not contain, the service MUST refuse the write with `read_only_field`.
- Non-core fields are written only at creation. Afterwards they are read-only (7.2) and returned in `extra` (4.2).
- The body MUST consist only of a `# <Slug>` heading.
- The service MUST create the file with its full content in a single atomic write (8.5). *(Informative: template plugins with an on-create trigger then see a non-empty file and leave it alone.)*

---

## 6. Ticket ids

### 6.1 Format

- A ticket id MUST match `^t[1-9]\d*$`: `t` and a positive integer without leading zeros.
- A block id that starts with `t` but does not match this pattern is not a ticket id. The service MUST report `bad_id` and MUST NOT rewrite it.
- On a ticket line, the id MUST be written as ` ^t<n>` at the very end of the line. On a ticket note, it MUST be written as `ticket_id: t<n>` in the frontmatter.
- Ticket ids MUST be unique across all ticket lines and ticket notes, and MUST NOT be reused, even after the ticket is deleted.
- Only the service assigns ticket ids.

### 6.2 Counter

- The counter is stored in the frontmatter of the state file (`state_file`), as `next_ticket_id: <n>`.
- On start, the service MUST set its counter to the larger of `next_ticket_id` and `max(n of every existing id) + 1`.
- Before writing a ticket that receives a new id, the service MUST write the incremented counter to the state file. *(Informative: a crash can then skip an id, but never reuse one.)*
- **Deployment:** the state file SHOULD sit in a folder listed in `excluded_paths`, and people SHOULD NOT edit it.

### 6.3 Missing state file

If the state file does not exist, or has no `next_ticket_id:` line:

- the service MUST NOT create or repair it, except as permitted below;
- the service MUST NOT assign ids, and MUST report `state_missing` until the file is restored;
- the service MUST refuse every write that needs a new id (creating a ticket, completing a recurring line) with `state_missing`;
- writes that need no new id are unaffected.

When the user confirms during setup, the app MAY create the state file, with `next_ticket_id` set to one more than the highest id found by a full scan (9.4).

### 6.4 Assigning ids

- When the service indexes a ticket that has no id, it MUST assign one, but MUST NOT write it until the file has been unmodified (8.4) for at least `quiet_period`.
- For a ticket note, the service MUST insert `ticket_id: t<n>` as the last line of the frontmatter.

### 6.5 Duplicates

If two tickets carry the same id:

- if exactly one of them is `done` or `dropped`, that one MUST keep the id;
- otherwise, the ticket whose path is lexicographically smaller (compared by Unicode code point) MUST keep the id, and within the same file the one on the lower line number;
- the service MUST assign a new id to the other ticket, and MUST report `duplicate_id`.

### 6.6 Identity

A ticket's identity is its id. The service MUST NOT identify tickets by path or line number.

---

## 7. Permitted writes

The service MUST NOT write to the vault except as permitted by this section, by 5.2 to 5.4, and by 6.3.

### 7.1 Ticket lines

| Change | Required behaviour |
|---|---|
| status | Replace the checkbox character. When moving to `done`, append `✅ <today>` and remove any `❌`. When moving to `dropped`, append `❌ <today>` and remove any `✅`. When leaving `done` or `dropped`, remove that date. |
| priority | Set, replace or remove the priority emoji. |
| queue | Replace the queue tag, or append one to the end of the description. |
| do date / due date | Set, replace or remove `⏳` / `📅`. |
| description | Replace the description text, keeping every tag. |
| id | Append ` ^t<n>` (6.4). |
| create | Append a new line (5.3). |
| recurrence | See 7.3. |

On every rewrite, the service MUST re-serialise the whole line in canonical order (3.2), and MUST keep its indent and bullet.

### 7.2 Ticket notes

- The service MUST NOT write any frontmatter field except those 4.2 marks writable for the note's type, and `ticket_id` (insert once, 6.4).
- The service MUST edit frontmatter line by line: replace the single `key: value` line, or insert it directly before the closing `---`.
- The service MUST NOT parse and re-serialise the YAML block. *(Informative: re-serialising reorders keys, re-quotes links and drops comments.)*
- The service MUST write core-field values (4.2) unquoted, e.g. `status: in_progress`, `do_date: 2026-10-22`, `ticket_id: t142`, and `do_date:` for an empty value.
- The service MUST write every other string value in double quotes, with `\` escaped as `\\` and `"` as `\"`, e.g. `url: "https://github.com/owner/repo/issues/42"`. A number MUST be written as a plain decimal, and an ISO date (`YYYY-MM-DD`) unquoted, so that the editor reads it as a date. A value that is neither a string nor a number, or a string containing a line break, MUST be refused with `invalid_value`. *(Informative: always quoting free text avoids YAML's special cases, such as a leading `#` read as a comment, or `true`, `null` and `2026` read as other types.)*
- The service MUST NOT write the body of a ticket note.

### 7.3 Recurrence

When the app completes a ticket line that carries `🔁`, the service MUST behave as the Tasks plugin does:

1. Compute the next occurrence from the rule. The reference date is the due date if present, else the scheduled date, else the start date; for a rule ending in `when done`, it is today. `⏳`, `📅` and `🛫` are each shifted by the same interval relative to the reference date.
2. Insert a new line directly above the completed one, with the same indent, description, tags, priority and rule, the shifted dates, checkbox character ` `, no `✅`, and a new id.
3. Mark the original line `done` (7.1).

Further requirements:

- The service MUST support at least these rules, each optionally followed by `when done`: `every day`, `every N days`, `every week`, `every N weeks`, `every week on <Weekday>[, <Weekday>...]`, `every month`, `every month on the Nth`, `every year`. It MAY support more.
- If the service cannot evaluate a rule, it MUST refuse the completion with `unsupported_recurrence`.
- **Deployment:** the editor's Tasks settings "Next recurrence appears on the line below" and "Remove scheduled date on recurrence" MUST be off on every device.

### 7.4 State file

The service MAY replace the `next_ticket_id:` line in the state file, line by line as in 7.2. It MUST NOT write anything else in that file. The state file is created only as 6.3 permits.

---

## 8. Concurrency and sync

### 8.1 Indexing

- The service MUST detect changes to the vault in one of two ways:
  - by watching the vault for create, modify, move and delete events; or
  - where the host cannot watch the vault reliably, by polling: listing the vault and checking every file's modification time at least every `poll_interval`.
- In this document, a **change event** is a watch event, or the moment a poll first sees a file that is new, deleted or has a new modification time.
- On each change event, the service MUST re-parse the whole affected file and replace all of that file's tickets in its index.
- The service SHOULD debounce change events per path by `debounce`.
- *(Informative: mobile hosts such as Android cannot reliably watch a shared folder, so a service embedded in a phone app polls.)*

### 8.2 Hashes

- Every indexed ticket line MUST carry `hash = sha256(UTF-8 bytes of the line without its line terminator)`.
- Every indexed ticket note MUST carry `hash = sha256(UTF-8 bytes of the frontmatter block text)`. The frontmatter block text is the bytes strictly between the opening and closing `---` lines, with their original line endings.

### 8.3 Edits from the app

- An edit request MUST name the ticket `id` and the `expected_hash`.
- The service MUST re-read the file from disk, locate the ticket by id, and compare its current hash with `expected_hash`.
- If the hashes differ, the service MUST refuse the edit with `stale` and MUST return the current ticket. The app SHOULD then refresh.
- If the hashes match, the service MUST apply the change, write the file (8.5), and only then update its index from a re-parse of the written file.

*(Informative: the vault always wins.)*

### 8.4 Quiet period

- A file counts as modified at the later of its modification time and the last change event the service received for it (8.1). *(Informative: file synchronisers often keep the original modification time, so a just-arrived file can look quiet.)*
- Before any write, the service MUST check when the file was last modified.
- If the file was modified less than `quiet_period` earlier, the service MUST NOT write it yet. It MUST queue the write, retry until the file has been unmodified for `quiet_period`, and respond to the app with `pending` and a job id.
- When a queued write is retried, the service MUST repeat the check of 8.3 against the file as it is at that moment, and MUST drop the write with `stale` if the hash no longer matches.

### 8.5 Atomic writes

The service MUST write each file by writing the full content to `.<filename>.ticket-tmp` in the same folder, then replacing the target with an atomic rename.

*(Informative: this needs a host that gives the service real file paths and an atomic rename. Storage APIs that hand out only content URIs, such as Android's Storage Access Framework, provide neither, so a service on such a host cannot conform through them. On Android, "All files access" gives the app real paths.)*

### 8.6 File sync

Requirements in this subsection apply to the deployment unless they name the service.

- If the vault is shared between devices, it SHOULD be shared by one file synchroniser, and `conflict_pattern` MUST match that synchroniser's conflict copies.
- The synchroniser MUST NOT sync version-control folders, the editor's per-device workspace state, the editor's trash folder, or `*.ticket-tmp` files.
- Where the synchroniser keeps ignore rules per device, the rules SHOULD live in one synced file that every device's local rules include, so that all devices use the same rules.
- The service MUST NOT parse conflict copies, and MUST list them under `conflicts` until they are deleted.
- While a conflict copy of a file exists, the service MUST refuse every write to that file with `conflict_file_present`.

### 8.7 Version control

- Version control MAY be used for history. If a file synchroniser shares the vault, devices MUST NOT also sync the vault by pulling through version control.
- The service MUST NOT run version-control commands.

### 8.8 Single writer

- The deployment MUST NOT run more than one service instance that writes to the vault at any time, counting servers and embedded services on every host. Before starting a service instance on another host, the operator MUST stop the running one.
- An app whose embedded service is **paired** with a server (configured to use that server as its service) MUST NOT write the vault through its embedded service. It MUST send every write to the server, and MAY queue writes while the server is unreachable. *(Informative: the server checks hashes when it applies queued writes (8.3), so an edit made in the editor meanwhile wins.)*
- An embedded service that is paired MAY still read and index the vault, e.g. to show tickets offline.

---

## 9. Data model and codes

### 9.1 Ticket object

The service MUST return tickets as objects with the fields below. Dates MUST be ISO `YYYY-MM-DD` strings or `null`. `priority` MUST be an integer.

```json
{
  "id": "t142",
  "kind": "line",
  "note_type": null,
  "title": "Clean the kitchen",
  "status": "ready",
  "queue": "home",
  "priority": 2,
  "do_date": "2026-10-22",
  "due_date": null,
  "start_date": null,
  "created_date": null,
  "done_date": null,
  "cancelled_date": null,
  "recurrence": null,
  "tags": ["home"],
  "links": {},
  "path": "Daily/2026-10-21.md",
  "line": 18,
  "heading": "Tickets",
  "subtasks": [{"text": "buy sponges", "done": false}],
  "hash": "9f2c…",
  "raw": "- [ ] Clean the kitchen #home 🔼 ⏳ 2026-10-22 ^t142",
  "extra": {}
}
```

| Field       | Value                                                                                                                       |
| ----------- | --------------------------------------------------------------------------------------------------------------------------- |
| `kind`      | `line` or `note`                                                                                                            |
| `note_type` | the note's `type` for notes; `null` for lines                                                                               |
| `title`     | lines: the description with the queue tag removed; notes: the filename without `.md`                                        |
| `links`     | notes: each wikilink field name mapped to its resolved target (4.2); lines: `{}`                                            |
| `line`      | 1-based line number at index time, or `null` for notes. It is a hint only; the service MUST NOT use it to address a ticket. |
| `heading`   | the text of the nearest heading above the line, without `#`                                                                 |
| `raw`       | lines: the full line; notes: the frontmatter block text (8.2)                                                               |
| `subtasks`  | lines: 3.3; notes: always `[]`                                                                                              |

### 9.2 Write responses

| Code | Meaning |
|---|---|
| `pending` | not an error: the write is queued (8.4) and a job id is returned |
| `stale` | the ticket changed since the app read it, or before a queued write ran (8.3, 8.4) |
| `not_found` | no ticket has that id |
| `read_only_field` | the write targets a field 5.4, 7.1 or 7.2 does not permit |
| `invalid_value` | the new value violates sections 2 to 5 |
| `unsupported_recurrence` | the recurrence rule cannot be evaluated (7.3) |
| `unsupported_field` | the line contains `🆔`, `⛔` or `🏁` (3.1) |
| `unsupported_spec` | `spec_version` is higher than the service implements (0.3) |
| `conflict_file_present` | a conflict copy of the target file exists (8.6) |
| `state_missing` | the write needs a new id and the state file is missing (6.3) |

### 9.3 Diagnostics

Diagnostics are non-fatal. The service MUST list current diagnostics in its status output.

| Code | Raised when |
|---|---|
| `duplicate_id` | two tickets carried the same id (6.5) |
| `multiple_queue_tags` | a line carries more than one queue tag (2.3) |
| `unknown_status` | a note's `status` is missing, empty or unknown (2.1) |
| `unknown_queue` | a note's `queue` is not in the queue set (2.3) |
| `bad_date` | a date field is not a valid ISO date |
| `bad_id` | a block id starts with `t` but is not a valid ticket id (6.1) |
| `bad_priority` | a note's `priority` is not an integer from 1 to 4 (2.2) |
| `id_on_non_ticket` | a ticket id sits on a line that is not a ticket (2.1, 3.3) |
| `conflict_file` | a conflict copy exists (8.6) |
| `wip_over` | a queue exceeds `wip_warn_per_queue` (2.4) |
| `no_longer_ticket` | a line stopped qualifying as a ticket after a profile change (1.5) |
| `state_missing` | the state file or its counter is missing (6.3) |

### 9.4 Full scans and gone tickets

- A full scan parses every indexable file in the vault (3.4). The service MUST run a full scan on start, and SHOULD run one at least every `full_scan_interval`.
- A ticket is gone only if a full scan does not find its id. Re-parses after change events (8.1) MUST NOT mark a ticket gone. *(Informative: a line moved between files arrives as two separate events.)*
- When a ticket is gone, the service MUST mark it `gone` in its index, MUST retain its history, and MUST cancel any pending app-side action for it (such as a print job).

---

## Appendix A. Example configuration *(informative)*

A service configuration with the profile inside the vault:

```yaml
vault_root: /home/me/Vault
profile_path: Templates/Ticket_Profile.md
```

The file `Templates/Ticket_Profile.md` then contains this block (shown here with the info string `yaml` so it is not read as a real profile):

```yaml
spec_version: 5
timezone: Europe/Amsterdam
queues: [home, work, study]
daily_note_path: Journal/{date}.md
daily_note_template: Templates/Daily.md
tickets_heading: Tickets
task_note_folder: Tasks
task_note_template: Templates/Task.md
extra_note_types:
  assignment:
    queue: study
    due_date: read_only
    fields: [course, weight]
state_file: Templates/Ticket_State.md
excluded_paths: [Templates/, Archive/]
```

A service configuration with the profile outside the vault holds the same parameters under `profile:`, next to `vault_root`.

---

## Changelog

- **v1** (2026-10-21): first general edition, derived from a vault-specific edition (v3). Vault-specific values became profile parameters (1.2); `course` and `project` became the generic `links` map; extra ticket note types are declared in the profile (4.2); daily-note templates with unknown placeholders and existing task-note paths are refused with `invalid_value`. Clarified the same day: the 3.4 exclusions cover ticket indexing only, not reading the profile, the templates or the state file.
- **v2** (2026-10-21): service configuration with `vault_root`, and the profile either inline in it or in a `ticket-profile` block of a note in an excluded path (1.3); schema for `extra_note_types` entries (1.4); profile changes take effect on restart, trigger a full scan and raise `no_longer_ticket` (1.5); daily-note templates with template-plugin syntax are refused (5.2); new task notes take only field names and order from the template (5.4).
- **v3** (2026-10-21): the service MAY run embedded in an app or as a standalone server (0.2, 1.1); a paired embedded service MUST NOT write the vault, and the single-writer rule counts every host (8.8); changes may be detected by polling at `poll_interval` where the host cannot watch, and polls count as change events for the quiet period (1.2, 8.1, 8.4); each host has its own `vault_root` (1.3); note on hosts without real paths or atomic rename (8.5).
- **v4** (2026-10-22): a request creating a task note may set any template field except `type` and `ticket_id`, and the service writes `ticket_id` at creation (5.4); non-core fields are set only at creation; non-core string values are always double-quoted, and line breaks and non-scalar values are refused (7.2). Clarified the same day: ISO dates in non-core fields are written unquoted (7.2); `read_only_field` in 9.2 also covers 5.4. Editorial, 2026-10-23: the Templater tag example in 5.2 is written with HTML entities, so the file no longer contains an unclosed `<`+`%` that broke Templater's highlighting.
- **v5** (2026-10-27): when the user confirms during setup, the app MAY create a missing state file, with `next_ticket_id` set to one more than the highest id found by a full scan (6.3, 7, 7.4).
