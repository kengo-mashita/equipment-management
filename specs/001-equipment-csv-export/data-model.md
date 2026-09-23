# Data Model: 備品一覧のCSVエクスポート

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-09-23

本機能はデータベースのスキーマを変更しない。既存エンティティを読み取り、永続化しない表示用データ
（CSV出力行）を組み立てるだけである。

---

## 既存エンティティ（読み取りのみ・変更なし）

### Equipment（備品） — `entity/Equipment.java`

| フィールド | 型 | 制約 | CSVでの扱い |
|---|---|---|---|
| id | Long | PK | 出力しない（並び順のキーとして使用：ID 昇順） |
| name | String | NOT NULL | 「品名」列 |
| assetNumber | String | NOT NULL, UNIQUE | 「管理番号」列 |
| location | String | nullable | 「保管場所」列（null は空欄） |
| status | EquipmentStatus | NOT NULL | 「状態」列（`getLabel()` の日本語表記） |
| purchaseDate | LocalDate | nullable | 「購入日」列（`yyyy-MM-dd`、null は空欄） |
| createdAt / updatedAt | LocalDateTime | NOT NULL | 出力しない |

### EquipmentStatus（備品の状態） — `entity/EquipmentStatus.java`

| 値 | label（CSV出力値） |
|---|---|
| AVAILABLE | 利用可 |
| LENT | 貸出中 |
| BROKEN | 故障中 |

検索条件 `status` パラメータは上記の**列挙子名**（`AVAILABLE` / `LENT` / `BROKEN`）で受け取る。
空文字・未指定は「条件なし」。それ以外の値は不正な検索条件（FR-017）。

### Lending（貸出記録） — `entity/Lending.java`

| フィールド | 型 | 制約 | CSVでの扱い |
|---|---|---|---|
| equipment | Equipment | NOT NULL, LAZY | 備品との対応付け |
| employee | Employee | NOT NULL, LAZY | 借用者の参照元 |
| returnedAt | LocalDateTime | nullable | null の記録が「有効な貸出記録」（1備品につき最大1件：憲章 V） |

### Employee（社員） — `entity/Employee.java`

| フィールド | 型 | 制約 | CSVでの扱い |
|---|---|---|---|
| name | String | NOT NULL | 「借用者」列（状態が貸出中の備品のみ） |

---

## 新規：EquipmentCsvRow（CSV出力行） — `dto/EquipmentCsvRow.java`

備品 1 件を CSV の 1 行として表す不変の値オブジェクト（Java `record`）。永続化しない。
値は**無害化・エスケープ前**の生の表示値を保持し、整形は `CsvFormatter` が行う。

| フィールド | 型 | 列見出し | 値の決め方 | null |
|---|---|---|---|---|
| name | String | 品名 | `equipment.name` | 不可 |
| assetNumber | String | 管理番号 | `equipment.assetNumber` | 不可 |
| location | String | 保管場所 | `equipment.location` | 可（空欄出力） |
| statusLabel | String | 状態 | `equipment.status.label`（「利用可」「貸出中」「故障中」） | 不可 |
| purchaseDate | String | 購入日 | `equipment.purchaseDate` を `yyyy-MM-dd` で整形 | 可（空欄出力） |
| borrowerName | String | 借用者 | `status == LENT` かつ有効な貸出記録がある場合のみ `lending.employee.name`。それ以外は null | 可（空欄出力） |

### 組み立てルール（`EquipmentCsvExportService`）

1. 対象備品 = `EquipmentService.search(name, status)` の結果（品名は部分一致、状態は完全一致、ID 昇順）。
   一覧画面と同一の抽出・並び順（FR-004, FR-005）。
2. 有効な貸出記録 = `LendingService.findActiveLendingsByEquipmentId()`（備品 ID → Lending）。
3. 各備品を `EquipmentCsvRow` に変換する。借用者は **状態が `LENT` の備品に限り** 2. から取得する（FR-009）。
   `LENT` でない備品に有効な貸出記録が存在しても出力しない（状態を正とする）。
4. 0 件の場合は行リストが空になり、列見出し行のみの CSV となる（FR-015）。

---

## CSVファイルの構造（出力形式）

詳細は [contracts/equipment-csv-download.md](./contracts/equipment-csv-download.md) を参照。

| 項目 | 値 |
|---|---|
| 文字コード | UTF-8、先頭に BOM（`EF BB BF`） |
| 改行 | CRLF。最終行の末尾にも CRLF |
| 区切り | カンマ |
| 1行目 | `品名,管理番号,保管場所,状態,購入日,借用者` |
| 2行目以降 | `EquipmentCsvRow` 1 件につき 1 行、上記の列順 |

### フィールド整形ルール（`CsvFormatter`、適用順）

1. **null → 空文字**。
2. **CSVインジェクション対策（FR-012）**：無害化対象の列（品名・管理番号・保管場所・借用者）で、
   値が `=`、`+`、`-`、`@` のいずれかで始まる場合、先頭に `'` を付ける。状態・購入日・列見出しは対象外。
3. **エスケープ（FR-011）**：値にカンマ・ダブルクォート・CR・LF のいずれかが含まれる場合、値中の `"` を `""` に
   置換し、値全体を `"` で囲む。含まれない場合はそのまま。

| 入力値 | 出力 |
|---|---|
| `ノートPC` | `ノートPC` |
| null | （空） |
| `PC, 15インチ` | `"PC, 15インチ"` |
| `19"モニター` | `"19""モニター"` |
| `1行目⏎2行目` | `"1行目⏎2行目"` |
| `=SUM(A1)` | `'=SUM(A1)` |
| `-20℃用冷蔵庫` | `'-20℃用冷蔵庫` |
| `=A,B` | `"'=A,B"` |
| `PC-001`（先頭以外の `-`） | `PC-001` |

---

## 新規：例外型（`service` パッケージ）

| 例外 | 発生条件 | 画面での扱い |
|---|---|---|
| `InvalidSearchConditionException` | `status` パラメータが空でなく、`EquipmentStatus` の列挙子名に一致しない | 一覧へリダイレクト、`error`「検索条件が不正です。条件を指定し直してください。」 |
| `CsvExportException` | CSV 組み立て中に想定外の `RuntimeException` が発生（原因例外を保持） | ERROR ログ出力の上、一覧へリダイレクト、`error`「CSVファイルの出力中にエラーが発生しました。時間をおいて再度お試しください。」 |

状態遷移：本機能は読み取りのみで、備品・貸出記録の状態を一切変更しない。
