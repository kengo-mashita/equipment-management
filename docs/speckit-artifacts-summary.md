# spec-kit 各フェーズの成果物サマリー：備品一覧のCSVエクスポート

- 対象機能：備品一覧のCSVエクスポート（依頼文：[`csv-export-request.md`](./csv-export-request.md)）
- ブランチ：`feature/csv-export`
- spec-kit：1.0.10
- 作成日：2026-10-03

ソースコード・テストは除き、各フェーズで作成・更新したドキュメントをまとめる。
表中のパスは、特記がない限り `specs/001-equipment-csv-export/` からの相対パス。

## フェーズ一覧

| # | フェーズ | 主な成果物 | コミット |
|---|---|---|---|
| 1 | `/speckit-constitution` | `constitution.md` | `2c7adaa` |
| 2 | `/speckit-specify` | `spec.md`、`checklists/requirements.md` | `211ed27` |
| 3 | `/speckit-clarify` | `spec.md`（更新） | `a6217e4` |
| 4 | `/speckit-plan` | `plan.md`、`research.md`、`data-model.md`、`contracts/csv-download.md`、`quickstart.md` | `e048f2a` |
| 5 | `/speckit-tasks` | `tasks.md` | `1dc3efd` |
| 6 | `/speckit-implement` | `tasks.md`・`README.md`・`equipment-management-spec.md`（更新） | `b07484d`、`46fa4b9`、`20ed316` |
| 7 | `/speckit-converge` | `tasks.md`（Phase 6 を追記） | `0e57a71` |
| 8 | `/speckit-implement`（2回目） | `tasks.md`（更新） | `6eab241`、`82f7fb8` |

依頼文 `docs/csv-export-request.md` は、フェーズ6の後に別途コミットした（`5a55f06`）。

## 1. /speckit-constitution

| ファイル | 行数 | 内容 |
|---|---|---|
| [`.specify/memory/constitution.md`](../.specify/memory/constitution.md) | 92 | プロジェクト憲章 v1.0.0 |

定義した内容：

- **5原則**
  - I. JavaScriptを使わずサーバー側で描画する（NON-NEGOTIABLE）
  - II. レイヤーの責務分離
  - III. 業務ルールをテストで担保する
  - IV. ロールによるアクセス制御をサーバー側で行う
  - V. ローカル完結とシンプルさ
- **品質基準**：入力検証、例外処理、ログ
- **開発ワークフロー**：要件の正となる文書、spec-kit のブランチとコミットのルール
- **Governance**：改訂手順、バージョニング

## 2. /speckit-specify

| ファイル | 行数 | 内容 |
|---|---|---|
| [`spec.md`](../specs/001-equipment-csv-export/spec.md) | 151 | 機能仕様 |
| [`checklists/requirements.md`](../specs/001-equipment-csv-export/checklists/requirements.md) | 39 | 仕様の品質チェックリスト（16項目すべて合格） |

`spec.md` の主な内容：

- **ユーザーストーリー**：US1 全件ダウンロード（P1）、US2 絞り込み結果のダウンロード（P2）
- **要件**：機能要件 FR-001〜013、成功基準 SC-001〜005
- **Edge Cases**：カンマ・改行を含む値、0件、検索せずにダウンロードした場合、不正な検索条件など
- **Assumptions**：出力範囲、出力列、利用できるロール、「使いやすい形式」をどう解釈したか

## 3. /speckit-clarify

| ファイル | 内容 |
|---|---|
| [`spec.md`](../specs/001-equipment-csv-export/spec.md)（更新） | `## Clarifications` 節を追加し、決定内容を FR-004・FR-005・FR-009、Edge Cases、Assumptions に反映 |

決定事項（4問）：

| # | 質問 | 決定 |
|---|---|---|
| 1 | 借用者名を出力するか | 出力する（ADMIN・USER共通） |
| 2 | 文字コード | BOM付きUTF-8 |
| 3 | 検索欄を書き換えたが検索していない値の扱い | 無視する。最後に検索を実行した条件（＝今表示中の一覧）で出力する |
| 4 | 返却予定日を出力するか | 出力しない（画面と同じ6列のみ） |

## 4. /speckit-plan

| ファイル | 行数 | 内容 |
|---|---|---|
| [`plan.md`](../specs/001-equipment-csv-export/plan.md) | 101 | 技術的な前提、Constitution との照合、変更するファイル・変更しないファイル |
| [`research.md`](../specs/001-equipment-csv-export/research.md) | 93 | 設計判断 R1〜R11 |
| [`data-model.md`](../specs/001-equipment-csv-export/data-model.md) | 44 | CSVの各列が既存データのどこから来るか、ファイル全体の書式ルール |
| [`contracts/csv-download.md`](../specs/001-equipment-csv-export/contracts/csv-download.md) | 57 | 画面側と `GET /equipment/csv` の約束事、ボディ例 |
| [`quickstart.md`](../specs/001-equipment-csv-export/quickstart.md) | 45 | 自動テストの実行方法と、手で確かめる10手順（各手順と FR・SC の対応付き） |

Constitution との照合は、ブランチ名に関する注意1件を除いてすべて合格。
`data-model.md` のとおり、エンティティの変更はない。

設計判断（`research.md`）の要点：

- **ライブラリ**：追加せず、Java標準機能でCSVを組み立てる（R1）
- **書式**：BOM付きUTF-8・CRLF（R2）。エスケープはRFC 4180相当で、必要な値だけクォートで囲む（R3）
- **CSVインジェクション対策**：行わない。データを登録できるのはADMINのみで、加工すると画面とCSVの値が一致しなくなるため（R4）
- **検索条件の受け渡し**：一覧を描画したときの検索条件を `<a>` リンクに埋め込む。JavaScriptは使わない（R5）
- **不正な状態値**：一覧とCSVのどちらでも、一覧画面に戻してエラーを表示する（R7）
- **責務の配置**：CSVの組み立ては新規Service、Controllerは条件の受け取りとレスポンスヘッダーの設定だけ（R9）
- **アクセス制御**：`SecurityConfig` は変更しない（R10）

## 5. /speckit-tasks

| ファイル | 行数 | 内容 |
|---|---|---|
| [`tasks.md`](../specs/001-equipment-csv-export/tasks.md) | 217 | タスク、依存関係、並行できる作業、MVP の方針 |

行数は、フェーズ7・8での追記を含む最終版のもの。

作成時のタスク構成（17件）：

| フェーズ | 件数 | 内容 |
|---|---|---|
| Phase 1 Setup | 1 | 既存テストがすべて通ることの確認 |
| Phase 2 Foundational | 0 | 全ストーリー共通で先に作るものがない |
| Phase 3 US1（MVP） | 7 | テスト2本、Service、Controller、リンク、CSS、テスト実行 |
| Phase 4 US2 | 5 | 結合テスト、状態値の解釈の共通化、エラー処理、リンクへの条件埋め込み、テスト実行 |
| Phase 5 Polish | 4 | README・要件定義書の更新、制約の確認、quickstart での確認 |

テストタスクは、Constitution III がテストを必須（MUST）としているため含めた。

## 6. /speckit-implement

| ファイル | 内容 |
|---|---|
| [`tasks.md`](../specs/001-equipment-csv-export/tasks.md)（更新） | 全タスクを [X] にし、実装時の変更（T011）と quickstart の実施結果を追記 |
| [`README.md`](../README.md)（更新） | 機能説明と画面一覧に CSVダウンロード を追加し、「スコープ外」の節を削除 |
| [`equipment-management-spec.md`](../equipment-management-spec.md)（更新） | 7章「スコープ外」から CSVエクスポートを外し、spec.md への参照を追加 |

`tasks.md` に記録した実装時の変更（T011）：

- 不正な状態値の処理は、`IllegalArgumentException` をController全体で受け止める計画だった。
- それだと他の操作で起きた予期しないエラーまで「検索条件が不正です」と表示され、ログにも残らない。
- そのため専用の `InvalidSearchConditionException` を作り、それだけを受け止めるようにした。

## 7. /speckit-converge

| ファイル | 内容 |
|---|---|
| [`tasks.md`](../specs/001-equipment-csv-export/tasks.md)（更新） | `Phase 6: Convergence` として2件を追記 |

| ID | 種類 | 重要度 | 内容 |
|---|---|---|---|
| T018 | partial | MEDIUM | Excelでの確認（quickstart の手順2・10）が未実施（SC-002） |
| T019 | contradicts | LOW | 条件なしのときのリンクURLが T003 の記載と違うのに、記録していない |

Constitution 違反と、計画にない追加実装は検出されなかった。

## 8. /speckit-implement（2回目）

| ファイル | 内容 |
|---|---|
| [`tasks.md`](../specs/001-equipment-csv-export/tasks.md)（更新） | T019 の記録を T003 の下に、T018 の結果（利用者がExcelで確認、両方OK）を T017 の下に追記。全19タスク完了 |

## ファイル一覧（最終状態）

```text
.specify/memory/constitution.md          # 1. constitution
docs/csv-export-request.md               # 依頼文
docs/speckit-artifacts-summary.md        # 本ファイル
specs/001-equipment-csv-export/
├── spec.md                              # 2. specify / 3. clarify
├── checklists/requirements.md           # 2. specify
├── plan.md                              # 4. plan
├── research.md                          # 4. plan
├── data-model.md                        # 4. plan
├── contracts/csv-download.md            # 4. plan
├── quickstart.md                        # 4. plan
└── tasks.md                             # 5. tasks / 6〜8 で更新
```

ドキュメントの合計は約860行（本ファイルを除く）。
