# Spec: WIMS2 — 分類篩選式全圖找物 Mod（Necesse 1.3）

## Objective
重寫類似 Where Is My Stuff?? (W.I.M.S.) 的客戶端找物 Mod，但 UI 改為：
- 文字搜尋 + 物品分類篩選（沿用遊戲內 `ItemCategory` / 箱子篩選概念）
- 結果列表（哪個箱子、有幾組、距離），不只靠金色星星粒子
- 支援 Vault / 模組箱子 / 1.3 新增容器

使用者故事：
1. 按 Y 開啟搜尋窗，輸入「copper」或選「材料 > 礦石」分類，按搜尋。
2. 範圍內（預設 64 格，可調 16/32/64/128）符合的容器冒金星，並在列表顯示名稱、數量、距離。
3. 找不到則紅色提示，不再洗版 console。

成功標準：
- `gameVersion` 對應 1.3.x，`clientside=true`，伺服器不用裝。
- 能搜到原版箱子 + Vault（`OEInventory` 介面），不只 `InventoryObjectEntity`。
- 分類篩選可用：全部 / 關鍵字 / 指定 ItemCategory（含子分類）。

## Tech Stack
- Java 17（Temurin），language level 8，Gradle（沿用 ExampleMod）
- Necesse 1.3 `Necesse.jar` 為 compileOnly 依賴
- `@ModEntry` + `@ModMethodPatch(target=MainGame, name=frameTick)` 沿用舊 WIMS 的掛鉤方式（已在 1.3 驗證 `ModMethodPatch` 仍存在）

## Commands
```bat
REM 設定 build.gradle 的 necesseInstallDir 指向你的遊戲目錄
gradlew runClient        :: 跑遊戲測試
gradlew buildModJar      :: 輸出 build\jar\*.jar
```
上架：jar 內需 `mod.info` + `resources/preview.png`，用 `-mod` 啟動後在主選單 Mod 頁上傳。

## Project Structure
```
WIMS2/
  build.gradle
  settings.gradle
  src/main/java/wims2/
    ModMain.java              :: Control 註冊 + client command
    ItemMatcher.java          :: 關鍵字 / 分類匹配介面
    SearchEngine.java         :: 遍歷 OEInventory + 回傳結果列表
    ParticleSpawner.java      :: 成功金星 / 失敗紅叉
    forms/SearchForm.java     :: Y 叫出的 UI：文字框 + 分類下拉 + 結果列表
    patches/MainGamePatch.java:: frameTick 掛鉤
  src/main/resources/locale/en.lang
```

## Code Style
- client-only：`postInit()` 第一行檢查 `GlobalData.isServer()` 直接 return。
- 搜尋不用 `InventoryObjectEntity` 寫死，改用 `instanceof OEInventory` + `getInventory()`，才能吃到 Vault。
- 分類用 `ItemCategory.getItemsCategory(item).isOrHasParent(...)` 或 `containsItemOrInChildren` 判斷，預設選項直接列 `masterManager` 下的頂層分類。
- Form 寬 440x320，`FormTextInput` + `FormDropdownSelectionButton` + `FormContentBox` 結果列表。

## Testing Strategy
- 手動：`runClient` + `runDevClient` 雙開，localhost 連線，地圖放 3 種箱子（含 Vault）驗證。
- 指令：`/wims2 copper 64` 與 UI 搜尋結果一致。
- 1.3 相容：Mod 選單不顯示 wrong game version。

## Boundaries
- Always: client-only、不發封包、不改存檔、可與其他箱子 Mod 共存
- Ask first: 新增依賴、改半徑上限超過 128、常駐 highlight 改為每 tick 掃描
- Never: 複製 Aeyos 原版 class 原樣貼上（授權不明，採 clean-room 重寫）、提交含 Steam 私鑰的 jar

## Open Questions
- 結果列表點擊後要不要自動標記路徑箭頭？v1 先只做粒子 + 座標，v2 再做尋路箭頭。
- 半徑預設 32 還是 64？暫定 64，下拉可選。
