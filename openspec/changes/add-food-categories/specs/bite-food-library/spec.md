# bite-food-library

## ADDED Requirements

### Requirement: Food category
The system SHALL store an optional category name on each food as a plain string,
where an empty string means the food is uncategorised. Categories SHALL NOT exist
independently of the foods that reference them.

#### Scenario: Uncategorised food
- **WHEN** a food is saved with the category field left empty
- **THEN** the food is stored with `category = ""` and is listed under the uncategorised group

#### Scenario: Reading a library saved before categories existed
- **WHEN** the app loads a `foods.json` written by a version without the category field
- **THEN** every food loads successfully with `category = ""` and no data is lost

#### Scenario: Category travels with the backup
- **WHEN** the user exports a backup and imports it again
- **THEN** each food keeps its category, and the backup `schemaVersion` is still 1

### Requirement: Food library screen
The system SHALL provide a dedicated food library screen, reachable from the today screen's
top app bar, that lists every food in the library with a search field and a button to create a new food.

#### Scenario: Opening the library
- **WHEN** the user taps the food library icon on the today screen
- **THEN** the food library screen opens showing the categories of the library

#### Scenario: Empty library
- **WHEN** the library contains no foods
- **THEN** the screen shows a short message instead of an empty list

#### Scenario: Managing a food from the library
- **WHEN** the user opens the overflow (⋯) menu on a food row
- **THEN** the menu offers editing the food, toggling its favorite flag, and deleting it,
  and deletion asks for confirmation first

#### Scenario: Opening a food for editing
- **WHEN** the user taps a food row
- **THEN** the food editor opens for that food

### Requirement: Collapsible category groups
The system SHALL group foods by category, SHALL sort groups by category name with the
uncategorised group always last, and SHALL show each group as a header with the category name
and the number of foods in it. Tapping a header SHALL expand or collapse that group.
Groups SHALL start collapsed, and the set of expanded groups SHALL survive an app restart.

#### Scenario: Group header
- **WHEN** the library holds 36 foods whose category is "Subway"
- **THEN** a header reads "Subway（36）"

#### Scenario: Uncategorised sorts last
- **WHEN** the library holds both categorised and uncategorised foods
- **THEN** the uncategorised group is the last group on the screen

#### Scenario: Expanding a group
- **WHEN** the user taps a collapsed group header
- **THEN** the foods of that category appear beneath the header

#### Scenario: Expansion survives a restart
- **WHEN** the user expands a group and then force-stops and reopens the app
- **THEN** that group is still expanded and the others are still collapsed

### Requirement: Search results are not grouped
The system SHALL list search results as a flat list without category headers,
on both the food library screen and the add-entry screen.

#### Scenario: Searching in the library
- **WHEN** the user types a query on the food library screen
- **THEN** the matching foods are listed directly, with no category headers and regardless of
  which groups are expanded

#### Scenario: Clearing the query
- **WHEN** the user clears the search field
- **THEN** the grouped, collapsible list returns with the previous expansion state

### Requirement: Rename a category
The system SHALL let the user rename a category from its group header's overflow (⋯) menu,
and renaming SHALL update the category of every food in that group. Renaming to a blank name
SHALL make those foods uncategorised. The uncategorised group SHALL NOT offer renaming.

#### Scenario: Rename applies to the whole group
- **WHEN** the user renames the category "超商" to "便利商店"
- **THEN** every food that had the category "超商" now has the category "便利商店"
  and the group header reads "便利商店"

#### Scenario: Rename to a blank name
- **WHEN** the user renames a category to an empty string
- **THEN** the foods of that group move into the uncategorised group

#### Scenario: Rename onto an existing category
- **WHEN** the user renames a category to the name of another existing category
- **THEN** the two groups merge into one group under that name

#### Scenario: Uncategorised cannot be renamed
- **WHEN** the user opens the overflow menu on the uncategorised group header
- **THEN** no rename option is offered

### Requirement: Category field in the food editor
The system SHALL offer a free-text category field in the food editor,
accompanied by the list of categories already used in the library as one-tap suggestions.

#### Scenario: Typing a new category
- **WHEN** the user types a category name that does not exist yet and saves
- **THEN** the food is stored with that category and a new group appears in the library

#### Scenario: Picking an existing category
- **WHEN** the user taps one of the suggested categories
- **THEN** the category field is filled with that name

#### Scenario: Suggestions are deduplicated
- **WHEN** 36 foods share the category "Subway"
- **THEN** "Subway" is suggested once

### Requirement: Grouped food list on the add-entry screen
The system SHALL present the full food library on the add-entry screen using the same
grouped, collapsible list as the food library screen, sharing the same expansion state.
The "frequent" and "recent" chip rows SHALL remain unchanged.

#### Scenario: Shared expansion state
- **WHEN** the user expands the "Subway" group on the food library screen and then opens the add-entry screen
- **THEN** the "Subway" group is expanded there too

#### Scenario: Logging from a group
- **WHEN** the user taps a food inside a group on the add-entry screen
- **THEN** the servings dialog opens exactly as it does for a search result

## MODIFIED Requirements

### Requirement: Food record
The system SHALL store each food with a name, optional grams per serving, per-serving nutrients
(kcal, protein, fat, saturated fat, trans fat, carbohydrates, sugar, sodium in mg, cholesterol in mg),
an optional note, an optional category, a favorite flag and a creation timestamp, identified by a UUID string.

#### Scenario: Food without serving grams
- **WHEN** a food is saved with the grams-per-serving field left empty
- **THEN** the food is stored with `servingGrams = null` and can still be logged by servings

#### Scenario: Food without a category
- **WHEN** a food is saved with the category field left empty
- **THEN** the food is stored with `category = ""`

### Requirement: Seed on first launch
The system SHALL load `assets/seed_foods.json` into the food library when no `foods.json` exists yet,
and SHALL NOT reload it afterwards. The seed foods SHALL carry their categories.

#### Scenario: First launch
- **WHEN** the app starts and `foods.json` does not exist
- **THEN** the food library contains the 78 seed foods with their categories
  (Subway 36, uncategorised 20, 麥當勞 8, 好事多 5, 參考值 3, 蛋白粉 3, 池上便當 2, 超商 1)
  and `foods.json` is written

#### Scenario: Existing install upgrading to a seed with categories
- **WHEN** the app starts with a `foods.json` written before categories existed
- **THEN** the seed is NOT reloaded and the existing foods stay uncategorised until the user
  imports a backup that carries categories or edits them by hand

#### Scenario: User emptied the library
- **WHEN** the user has deleted every food and restarts the app
- **THEN** the library stays empty
