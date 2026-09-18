# bite-settings-backup

## ADDED Requirements

### Requirement: Targets
The system SHALL store a daily kcal target and protein target (defaults 2000 and 120) and optional fat and carbohydrate targets,
editable on the Settings screen, and the Today screen SHALL reflect changes immediately.

#### Scenario: Change kcal target
- **WHEN** the user sets the kcal target to 1900
- **THEN** the Today screen's remaining kcal is recomputed against 1900

### Requirement: Export backup
The system SHALL export one JSON file containing foods, diary entries, weights, day notes and targets
via the system file picker, with default name `bite-backup-yyyyMMdd.json`.

#### Scenario: Export
- **WHEN** the user taps 匯出備份 and picks a location
- **THEN** a file with `app = "bite"`, `schemaVersion = 1` and all four collections plus targets is written

### Requirement: Import merges by id and never deletes
The system SHALL import a backup chosen with the system file picker by merging foods, diary entries and weights by id
(imported wins), day notes by date, and applying targets when present. Existing data not in the file SHALL be kept.

#### Scenario: Import the converted spreadsheet
- **WHEN** the user imports `bite-import.json` into a fresh install
- **THEN** the library still has 78 foods (same ids as seed), the diary has 184 entries across 31 dates,
  weights has 7 entries and 2025-09-15 carries the note 感冒日

#### Scenario: Import twice
- **WHEN** the same file is imported again
- **THEN** counts do not change

### Requirement: Reject foreign or newer backups
The system SHALL refuse a file whose `app` is not "bite" or whose `schemaVersion` is greater than 1, with a message.

#### Scenario: Newer schema
- **WHEN** a file with `schemaVersion = 2` is imported
- **THEN** nothing changes and the message says the app needs updating first
