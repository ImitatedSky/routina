# bite-food-library

## ADDED Requirements

### Requirement: Food record
The system SHALL store each food with a name, optional grams per serving, per-serving nutrients
(kcal, protein, fat, saturated fat, trans fat, carbohydrates, sugar, sodium in mg, cholesterol in mg),
an optional note, a favorite flag and a creation timestamp, identified by a UUID string.

#### Scenario: Food without serving grams
- **WHEN** a food is saved with the grams-per-serving field left empty
- **THEN** the food is stored with `servingGrams = null` and can still be logged by servings

### Requirement: Seed on first launch
The system SHALL load `assets/seed_foods.json` into the food library when no `foods.json` exists yet,
and SHALL NOT reload it afterwards.

#### Scenario: First launch
- **WHEN** the app starts and `foods.json` does not exist
- **THEN** the food library contains the 78 seed foods and `foods.json` is written

#### Scenario: User emptied the library
- **WHEN** the user has deleted every food and restarts the app
- **THEN** the library stays empty

### Requirement: Create and edit foods
The system SHALL let the user create a food and edit any field of an existing food.
Name is required; kcal is required; every other nutrient defaults to 0 when left blank.

#### Scenario: Save with blank nutrients
- **WHEN** the user saves a new food with only name and kcal filled
- **THEN** the food is stored with the remaining nutrients equal to 0

#### Scenario: Missing name
- **WHEN** the user tries to save a food with an empty name
- **THEN** the save is refused and the name field shows an error

### Requirement: Delete food via overflow menu
The system SHALL offer deletion only through an overflow (⋯) menu followed by a confirmation dialog,
and deleting a food SHALL NOT change any diary entry.

#### Scenario: Delete a logged food
- **WHEN** the user confirms deletion of a food that appears in earlier diary entries
- **THEN** the food disappears from the library and those diary entries still show their name and nutrients

### Requirement: Favorite flag
The system SHALL let the user toggle a favorite flag on a food; favorites SHALL sort before non-favorites in search results.

#### Scenario: Favorite ordering
- **WHEN** a search matches a favorite food and a non-favorite food
- **THEN** the favorite is listed first

### Requirement: Search by name and note
The system SHALL match foods whose name or note contains the query, case-insensitively.

#### Scenario: Brand in note
- **WHEN** the user searches "subway"
- **THEN** every food whose note contains "Subway" is listed

#### Scenario: English part of a bilingual name
- **WHEN** the user searches "tuna"
- **THEN** the food "鮪魚Tuna" is listed
