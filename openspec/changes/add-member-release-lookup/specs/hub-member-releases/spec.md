# hub-member-releases

## ADDED Requirements

### Requirement: Tag prefix in registry
`RegistrySource` SHALL accept an optional `tagPrefix` (default `"v"`), and `apps.json` SHALL list Bite with
`tagPrefix = "bite-v"` and `assetPattern = "routina-bite-*.apk"` while `schemaVersion` stays 1.

#### Scenario: Old registry entry
- **WHEN** an entry has no `tagPrefix`
- **THEN** it behaves as `"v"`

### Requirement: Latest release by prefix
The Hub SHALL determine a member's latest version by listing the repository's releases and taking the first
that is not draft, not prerelease and whose tag starts with the member's `tagPrefix`, then matching an asset by `assetPattern`.

#### Scenario: Monorepo with mixed tags
- **WHEN** the repository's newest release is `bite-v0.1.0` and the newest Hub release is `v0.3.1`
- **THEN** the Hub entry resolves to 0.3.1 and the Bite entry resolves to 0.1.0

#### Scenario: No release for the prefix
- **WHEN** no release tag starts with the prefix
- **THEN** the member shows 查不到可安裝的版本

### Requirement: Version normalization strips the prefix
`Version.normalize` SHALL strip the member's tag prefix before comparing numeric parts against the installed versionName.

#### Scenario: Update available
- **WHEN** the installed Bite versionName is 0.1.0 and the latest tag is `bite-v0.2.0`
- **THEN** the Hub shows 更新

### Requirement: CI builds every module and releases Bite on its own tag
The build job SHALL compile all modules on pushes and pull requests; a `bite-v*` tag SHALL produce a signed release
whose APK is named `routina-bite-vX.Y.Z.apk`, after checking the tag matches the versionName in `apps/bite/build.gradle.kts`.

#### Scenario: Tag mismatch
- **WHEN** tag `bite-v0.1.1` is pushed while versionName is 0.1.0
- **THEN** the release job fails before building
