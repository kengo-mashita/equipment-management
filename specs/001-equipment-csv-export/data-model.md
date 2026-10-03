# Data Model: 備品一覧のCSVエクスポート

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-10-03

本機能は**読み取り専用**であり、エンティティ・テーブルの追加や変更は行わない。
既存エンティティから CSV の1行を組み立てる対応関係を定義する。

## 参照する既存エンティティ

| エンティティ | 使う項目 | 用途 |
|---|---|---|
| Equipment（備品） | name, assetNumber, location, status, purchaseDate, id | CSVの1行。id は並び順（昇順）にのみ使用し、出力しない |
| Lending（貸出記録） | equipment, employee, returnedAt | `returnedAt` が null の記録＝現在有効な貸出。備品ごとに最大1件（既存の業務ルール） |
| Employee（社員） | name | 借用者名として出力 |

## CSV行（永続化しない出力物）

見出し行 + 備品1件につき1行。列順は固定。

| # | 見出し | 値の出所 | 未設定時 | 書式 |
|---|---|---|---|---|
| 1 | 品名 | Equipment.name | （必須項目のため発生しない） | そのまま |
| 2 | 管理番号 | Equipment.assetNumber | （必須項目のため発生しない） | そのまま |
| 3 | 保管場所 | Equipment.location | 空欄 | そのまま |
| 4 | 状態 | Equipment.status | （必須項目のため発生しない） | 日本語ラベル（利用可／貸出中／故障中） |
| 5 | 購入日 | Equipment.purchaseDate | 空欄 | `YYYY-MM-DD` |
| 6 | 借用者 | 有効な Lending の Employee.name | 空欄 | 状態が「貸出中」の場合のみ出力 |

### 行の選択・並び順のルール

- 対象は `EquipmentService.search(name, status)` の結果と同一（品名＝部分一致、状態＝完全一致、
  条件が空なら無視）。
- 並び順は一覧画面と同じ ID 昇順。
- 借用者は「状態が貸出中」かつ「有効な貸出記録が存在する」場合に出力する。
  状態が貸出中なのに有効な貸出記録がない（データ不整合）場合は空欄とし、エラーにはしない。

### ファイル全体の規則

- 文字コード：UTF-8、先頭に BOM（`EF BB BF`）
- 改行：CRLF（最終行の後にも CRLF を付与）
- エスケープ：値に `,` `"` CR LF を含む場合のみダブルクォートで囲み、`"` は `""` にする
- 0件の場合：見出し行のみ

詳細なHTTPインターフェースは [contracts/csv-download.md](./contracts/csv-download.md) を参照。
