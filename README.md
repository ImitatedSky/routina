# Routina

Routina 家族的主體：一個 Hub，管理各自獨立安裝的日常工具 App。

家族成員是**獨立的 APK**——各自的權限、各自的版本、各自的 release。Hub 負責把它們收在一起：列出裝了哪些、點一下開啟，並讓成員之間能互相呼叫。

## 為什麼成員要分開成獨立 APK

因為權限。`Routina Flow`（自動化）需要 47 個權限，含通知存取、使用情況存取、顯示在上層、背景定位、NFC。如果把它併進 Hub 的單一 APK，那 Hub 就繼承了這 47 個權限——只想開個計算機的人，安裝時會看到「這個 App 要讀你所有通知」。

分開之後，每個成員只要求自己真正需要的權限，Hub 本身**零權限**。

## 目前狀態

| 版本 | 內容 |
|---|---|
| v0.1.0 | 家族目錄：掃描已安裝的成員、顯示、點一下開啟 |
| v0.2.0 | 能力契約：成員可宣告對外開放的能力，彼此互相呼叫 |

之後：v0.3 從 GitHub 下載安裝與更新（registry + 驗簽章）、v0.4 抽出共用層與統一備份。

## 家族契約

契約刻意只用 **manifest meta-data + Intent extras**——字串層級的約定，不是程式碼依賴。成員因此可以待在自己的 repo、用自己的版本節奏，不必引用本專案的任何 library。

權威定義在 [`core/contract/Family.kt`](core/contract/src/main/java/com/routina/core/contract/Family.kt)。

### 掛進家族

在成員的 `AndroidManifest.xml` 的 `<application>` 裡宣告：

```xml
<meta-data android:name="com.routina.family.member"  android:value="true" />
<meta-data android:name="com.routina.family.id"      android:value="flow" />
<meta-data android:name="com.routina.family.name"    android:value="Routina Flow" />
<meta-data android:name="com.routina.family.summary" android:value="日常自動化" />
```

`family.name` 與 `app_name` 是分開的：Hub 裡顯示什麼名字，與桌面圖示的名稱、與 applicationId 都互不牽動。

Hub 靠「有啟動圖示的 App」＋這個標記來找成員，用的是 `<queries>` 套件可見性宣告，**不需要 `QUERY_ALL_PACKAGES`**。加新成員不必改 Hub。

### 開放能力

能力是「可以被別人呼叫的動作」。宣告一份 XML：

```xml
<!-- res/xml/family_capabilities.xml -->
<capabilities contract="1">
    <capability id="add_note" label="新增便條" summary="把文字存成一張新便條">
        <param name="text" label="內容" type="text" required="true" />
    </capability>
</capabilities>
```

在 manifest 指向它：

```xml
<meta-data android:name="com.routina.family.capabilities"
           android:resource="@xml/family_capabilities" />
```

然後放一個 exported 的透明跳板 Activity 收請求：

```xml
<activity
    android:name=".CapabilityActivity"
    android:excludeFromRecents="true"
    android:exported="true"
    android:noHistory="true"
    android:taskAffinity=""
    android:theme="@style/Theme.Routina.Invisible">
    <intent-filter>
        <action android:name="com.routina.family.action.RUN_CAPABILITY" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
```

請求的 extras：能力 id 放 `com.routina.family.extra.CAPABILITY_ID`，參數放 `com.routina.family.param.<參數名>`，值一律字串。可以直接用 adb 測：

```sh
adb shell am start -a com.routina.family.action.RUN_CAPABILITY \
  -n <套件>/<跳板 Activity> \
  --es com.routina.family.extra.CAPABILITY_ID add_note \
  --es com.routina.family.param.text hello
```

呼叫是**單向**的，送出即結束，不等回傳值。

## 家族成員

| 顯示名稱 | applicationId | 位置 |
|---|---|---|
| Routina | `com.routina.hub` | 本專案 `:hub` |
| Routina Flow | `com.routina.app` | [routina-flow](https://github.com/ImitatedSky/routina-flow)（自動化；applicationId 改名遷移中） |
| Routina Bite | `com.routina.bite` | 本專案 `:apps:bite`（熱量飲食紀錄） |

住在本專案裡的子 App 用**前綴標籤**發版（Bite 是 `bite-v0.1.0`），Hub 自己用裸的 `v0.1.0`，
兩者的 release 因此分得開——Hub 查最新版時只看 tag 以自己前綴開頭的那些。

## 建置

需要 JDK 17 與 Android SDK（`local.properties` 的 `sdk.dir`）。

```sh
./gradlew :hub:assembleDebug
```

Release 會在有完整簽章資訊時（根目錄 `keystore.properties` 或 CI 的 `KEYSTORE_*` 環境變數）用正式簽章，否則 fallback 沿用 debug 簽章。家族全體共用同一把 keystore，這樣每個成員都能就地更新，Hub 之後也能在安裝前驗簽章。
