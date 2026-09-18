# bite-weight-log

## ADDED Requirements

### Requirement: Weight entry
The system SHALL store weight entries with a date, kilograms, optional body-fat percentage,
a source (家用體重計 or InBody) and an optional note.

#### Scenario: Add a home-scale weight
- **WHEN** the user saves 84.3 kg for 2025-09-18 with no body fat
- **THEN** the entry is stored with `bodyFatPct = null` and source 家用體重計

### Requirement: Weight list
The Weight screen SHALL list entries newest first showing date, kg, body fat when present and the source,
and SHALL let the user edit an entry or delete it via an overflow menu with confirmation.

#### Scenario: Delete
- **WHEN** the user confirms deletion of an entry
- **THEN** it disappears from the list and from `weights.json`

### Requirement: Validation
The system SHALL require kilograms greater than 0 and body fat, when given, between 0 and 100.

#### Scenario: Invalid body fat
- **WHEN** the user enters 120 for body fat
- **THEN** the save button is disabled and the field shows an error
