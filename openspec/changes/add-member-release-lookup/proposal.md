# Change: add-member-release-lookup

## Why

Hub 原本用 GitHub 的 `/releases/latest` 判斷某個成員有沒有新版。那個端點回的是「整個 repo 最新的 release」，
只要一個 repo 裡住著不只一個產品，Hub 就會拿到別人的版本號，對自己與對成員都會誤判。

家族的成員現在各自一個 repo（Flow、Bite…），但這個問題不會因此消失：Hub 自己的 repo 仍可能同時
出現不同前綴的標籤，而且判斷邏輯不該建立在「一個 repo 只有一個產品」這個假設上。

## What Changes

- `RegistrySource` 新增選用欄位 `tagPrefix`（預設 `"v"`），`schemaVersion` 維持 1，舊名冊照樣讀得懂。
- `RegistryClient.latest` 改成列出該 repo 的 releases，取第一個非 draft、非 prerelease 且 tag 以該成員前綴
  開頭的，再依 `assetPattern` 挑附件。查不到就誠實回報，不假裝沒有更新。
- `Version.normalize` 先去掉成員的 tag 前綴再比數字段落。
- CI：一般建置改成建全部 module。

## Capabilities

### New Capabilities
- `hub-member-releases`: Hub 對成員最新版的偵測與版本比對

### Modified Capabilities
（無）

## Impact

- `hub/src/main/java/com/routina/hub/catalog/Registry.kt`、`RegistryClient.kt`
- `apps.json`、`.github/workflows/build.yml`、`README.md`
- 一個成員仍然只花一次 GitHub API 呼叫，匿名額度負擔不變
