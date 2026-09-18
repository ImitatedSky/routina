# Design: add-member-release-lookup

## Context

Hub 判斷「某個成員有沒有新版」的方式，原本是打 GitHub 的 `/repos/{repo}/releases/latest`。
那個端點回的是整個 repo 最新的一個 release，與「哪個產品」無關。

家族的成員現在各自一個 repo，但這個查法仍然脆弱：只要一個 repo 裡出現不只一種標籤
（例如 Hub 自己日後想替共用層單獨發版），判斷就會錯。而且錯的方式很安靜——Hub 會顯示一個
存在但不屬於該成員的版本號，使用者按更新才會發現裝到別的東西。

## Goals / Non-Goals

**Goals:**
- 成員的最新版只由「那個成員自己的 release」決定
- 舊名冊不必改就能繼續讀（欄位選用、`schemaVersion` 不動）
- 查不到就誠實說查不到，不要退化成「沒有更新」

**Non-Goals:**
- 不做 GitHub API 分頁（一頁 30 筆對家族的發版頻率足夠）
- 不改匿名 API 的額度策略（仍是每個成員一次呼叫、快取六小時）

## Decisions

### D1. 用 tag 前綴辨識成員，而不是靠 repo 只有一個產品
`RegistrySource` 加 `tagPrefix`，預設 `"v"`。`latest()` 改列 releases、取第一個
`!draft && !prerelease && tagName.startsWith(tagPrefix)` 的。GitHub 依建立時間倒序回傳，
所以第一個符合的就是最新的。

替代方案是「一個 repo 只放一個產品，直接用 `/releases/latest`」。家族現在確實是這樣分的，
但那是組織慣例、不是 API 保證；把判斷建立在慣例上，慣例一破就是安靜的錯誤答案。

### D2. `Version.normalize` 吃掉前綴
比較版本前先去掉成員的前綴再去 `v`，比出來的是純數字段落，與 APK 的 `versionName` 同一個形狀。
比不出來一律回「不確定」，寧可少提示一次也不要誤報。

### D3. `schemaVersion` 維持 1
只新增選用欄位，舊版 Hub 讀到會忽略它、行為與從前相同。把 schema 往上加會讓舊版直接拒收整份名冊，
代價遠大於收益。

## Risks / Trade-offs

- [一頁 30 筆：某個成員連續 30 版都沒發，會被擠出清單] → 以家族的發版頻率不切實際；真發生再加分頁。
- [舊版 Hub 看不懂 `tagPrefix`，對用前綴發版的成員會顯示「查不到可安裝的版本」] → 這是既有的誠實降級，
  使用者更新 Hub 即可。

## Migration Plan

沒有資料要遷移。名冊加欄位即可，Hub 舊版不受影響。
