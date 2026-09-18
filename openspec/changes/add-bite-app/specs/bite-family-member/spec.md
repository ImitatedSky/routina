# bite-family-member

## ADDED Requirements

### Requirement: Family metadata
The Bite manifest SHALL declare `com.routina.family.member = true`, `family.id = bite`, `family.name = Routina Bite`,
`family.summary = 熱量飲食紀錄` and point `family.capabilities` at `res/xml/family_capabilities.xml`.

#### Scenario: Hub directory
- **WHEN** Bite is installed next to the Hub
- **THEN** the Hub directory lists "Routina Bite / 熱量飲食紀錄" and can launch it

### Requirement: open_today capability
Bite SHALL declare capability `open_today` and handle `com.routina.family.action.RUN_CAPABILITY` in an exported,
invisible, no-history trampoline Activity that brings the Today screen to the foreground.

#### Scenario: Called from another member
- **WHEN** `adb shell am start -a com.routina.family.action.RUN_CAPABILITY -n com.routina.bite/.CapabilityActivity --es com.routina.family.extra.CAPABILITY_ID open_today` runs
- **THEN** Bite's main screen comes to the foreground and no extra task is left behind

#### Scenario: Unknown capability
- **WHEN** the trampoline receives an unknown capability id
- **THEN** it shows a short toast and finishes without crashing

### Requirement: No permissions
Bite's manifest SHALL declare no `uses-permission`.

#### Scenario: Install
- **WHEN** the APK is installed
- **THEN** the system shows no permission requests
