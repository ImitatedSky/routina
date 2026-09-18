# Tasks: add-member-release-lookup

## 1. 名冊與版本

- [x] 1.1 `RegistrySource.tagPrefix: String = "v"`（選用欄位，`schemaVersion` 維持 1）
- [x] 1.2 `Version.normalize(raw, prefix)` 先去前綴再去 `v`，既有呼叫點改帶前綴
- [x] 1.3 `apps.json` 的成員各自指向自己的 repo

## 2. 最新版查詢

- [x] 2.1 `RegistryClient.latest` 改打 `/repos/{repo}/releases?per_page=30`
- [x] 2.2 取第一個非 draft／prerelease 且 tag 以前綴開頭者，再依 `assetPattern` 挑附件
- [x] 2.3 查不到符合前綴的 release 時回 failure，訊息誠實（畫面顯示「查不到可安裝的版本」）

## 3. CI 與文件

- [x] 3.1 一般建置改成建全部 module
- [x] 3.2 `README.md` 說明每個成員各自一個 repo、各自發版

## 4. 驗證

- [x] 4.1 `:hub:assembleRelease` 綠燈
- [x] 4.2 實機（BlueStacks）：Hub 對自己解析到 v0.4.x、對成員解析到各自 repo 的最新版
