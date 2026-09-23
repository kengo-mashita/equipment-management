# Implementation Plan: 備品一覧のCSVエクスポート

**Branch**: `try/csv-import-01` | **Date**: 2026-09-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-equipment-csv-export/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

備品一覧画面に「CSVダウンロード」リンクを追加し、一覧に現在適用されている検索条件（品名の部分一致・
状態の完全一致）で抽出した備品を、Excel でそのまま開ける CSV（BOM 付き UTF-8、CRLF、日本語列見出し）
としてダウンロードさせる。

技術的には、新規エンドポイント `GET /equipment/csv` が既存の `EquipmentService.search` と
`LendingService.findActiveLendingsByEquipmentId` を再利用して出力行（`EquipmentCsvRow`）を組み立て、
自前の `CsvFormatter` がエスケープ（RFC 4180）と CSV インジェクション対策（先頭 `=` `+` `-` `@` に `'` を付与）を
行う。CSV はメモリ上で完成させてから `ResponseEntity<byte[]>`（`Content-Disposition: attachment`）で返すため、
JavaScript なしで画面遷移せずに保存でき、生成失敗時は一覧へリダイレクトしてエラーメッセージを表示できる。
外部ライブラリ・DB スキーマ・Security 設定の変更はない。詳細な判断根拠は [research.md](./research.md)。

## Technical Context

**Language/Version**: Java 21（LTS）

**Primary Dependencies**: Spring Boot 4.1.1（spring-boot-starter-webmvc / thymeleaf / security / data-jpa / validation）、
thymeleaf-extras-springsecurity6、Lombok。**新規依存の追加なし**（R-1）

**Storage**: H2 Database（ファイルモード `./data/`）。読み取りのみ・スキーマ変更なし

**Testing**: JUnit 5 + Mockito（Service 単体テスト、`@ExtendWith(MockitoExtension.class)`）、
Spring Boot Test + MockMvc + spring-security-test（Controller 結合テスト、`@SpringBootTest` + `@AutoConfigureMockMvc`
+ `@WithMockUser`）。既存テストの書き方に合わせる

**Target Platform**: WSL 上で `./mvnw spring-boot:run` により起動する組み込み Tomcat。クライアントは PC ブラウザ＋Excel

**Project Type**: サーバーサイドレンダリングの Web アプリケーション（単一 Maven プロジェクト）

**Performance Goals**: 数十件規模で、ボタン押下からファイル保存開始まで 3 秒以内（SC-005）

**Constraints**: JavaScript 不使用（憲章 II）、Excel で追加設定なしに文字化けしないこと（SC-002）、
スタックトレースを画面に出さないこと（憲章 VI）

**Scale/Scope**: 備品は数十件。新規エンドポイント 1、テンプレート変更 1、新規クラス 5 程度

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原則 | 確認内容 | 判定 |
|---|---|---|
| I. レイヤードアーキテクチャと責務分離 | Controller は パラメータ受け取り・レスポンスヘッダー設定・例外→リダイレクト変換のみ。抽出・行組み立て・借用者判定は `EquipmentCsvExportService`、CSV 整形は `CsvFormatter`（service）、行データは `dto/EquipmentCsvRow`。検索条件の文字列→列挙変換も Service（`EquipmentService.parseStatusFilter`）に置く | ✅ PASS |
| II. サーバーサイドレンダリング（JS不使用） | ボタンは `<a href>` のみ。ダウンロードは通常の HTTP レスポンス（`Content-Disposition: attachment`）。外部 CDN なし、スタイルは既存 `btn` クラスを流用 | ✅ PASS |
| III. 業務ルールのテストによる担保 | `CsvFormatter`（エスケープ・無害化）と `EquipmentCsvExportService`（借用者は貸出中のみ、絞り込み、0 件）に単体テスト。Controller 結合テストで ADMIN/USER 可・未ログイン拒否・不正条件・ヘッダー・BOM を検証。完了条件は `./mvnw test` 成功 | ✅ PASS |
| IV. ロールベース認可 | 閲覧操作のため ADMIN・USER 両方に許可。サーバー側の `anyRequest().authenticated()` で未ログインを拒否（Security 設定変更不要）。ボタン表示はロールで出し分けない | ✅ PASS |
| V. データ整合性と状態遷移の一元管理 | 読み取りのみで状態を変更しない。`readOnly` トランザクション。借用者は「状態が LENT」を正として判定し、有効な貸出記録は既存 Service 経由で取得 | ✅ PASS |
| VI. 分かりやすい異常系処理とログ出力 | 不正条件・想定外エラーはいずれも一覧へリダイレクトし日本語メッセージを表示。想定外エラーは SLF4J で ERROR ログ。スタックトレースは画面に出さない | ✅ PASS |
| 技術的制約 | 新規依存なし。URL `/equipment/csv` はリソース名（既存 `/equipment`）配下のケバブケース。スコープ外事項（REST API 化等）は含まない | ✅ PASS |

**Gate 結果（Phase 0 前）**: 違反なし。Complexity Tracking への記載は不要。

**Post-Design 再確認（Phase 1 後）**: data-model・contract・quickstart の内容を踏まえても上表の判定に変化なし。
新規クラスはいずれも既存パッケージ構成に収まり、Controller に業務判断（借用者の出力可否、無害化規則）は置かない。✅ PASS

## Project Structure

### Documentation (this feature)

```text
specs/001-equipment-csv-export/
├── spec.md              # 機能仕様（/speckit-specify, /speckit-clarify）
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/
│   └── equipment-csv-download.md   # Phase 1 output：エンドポイント・UI・CSV形式
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
src/main/java/jp/co/example/equipmentmanagement/
├── controller/
│   └── EquipmentController.java          # [変更] GET /equipment/csv、InvalidSearchCondition/CsvExport 例外ハンドラー
├── service/
│   ├── EquipmentService.java             # [変更] parseStatusFilter(String) を追加
│   ├── EquipmentCsvExportService.java    # [新規] 行の組み立て・CSVバイト列生成・ファイル名生成
│   ├── CsvFormatter.java                 # [新規] 無害化（FR-012）・エスケープ（FR-011）・行連結
│   ├── InvalidSearchConditionException.java  # [新規] 不正な検索条件（FR-017）
│   └── CsvExportException.java           # [新規] CSV生成中の想定外エラー（FR-016）
└── dto/
    └── EquipmentCsvRow.java              # [新規] CSV 1 行分の record

src/main/resources/templates/equipment/
└── list.html                             # [変更] 「CSVダウンロード」リンクを page-actions に追加（両ロール表示）

src/test/java/jp/co/example/equipmentmanagement/
├── service/
│   ├── CsvFormatterTest.java             # [新規] エスケープ・無害化の単体テスト
│   ├── EquipmentCsvExportServiceTest.java # [新規] 行組み立て・借用者・0件・ファイル名の単体テスト（Mockito）
│   └── EquipmentServiceTest.java         # [変更] parseStatusFilter のテストを追加
└── controller/
    ├── EquipmentCsvExportControllerTest.java  # [新規] MockMvc：ヘッダー・BOM・絞り込み・順序・不正条件・USER可
    └── EquipmentSecurityIntegrationTest.java  # [変更] 未ログイン時 /login へリダイレクトのケースを追加
```

**Structure Decision**: 既存の単一 Maven プロジェクト（`jp.co.example.equipmentmanagement` 配下のレイヤー別パッケージ）に
そのまま追加する。CSV 関連の業務処理は既存 `EquipmentService` を肥大化させないよう専用の
`EquipmentCsvExportService` に分け、エンドポイントは一覧画面と同じリソースを扱うため既存 `EquipmentController` に
追加する（`@ExceptionHandler` によるリダイレクトの流儀も共有できる）。

### 主要な処理の流れ

```text
[list.html] <a href="/equipment/csv?name=..&status=..">
     │ GET
     ▼
EquipmentController.downloadCsv(name, status)
     │ status → EquipmentService.parseStatusFilter()   ──不正──▶ InvalidSearchConditionException ─┐
     │ EquipmentCsvExportService.export(name, statusFilter)                                        │
     │     ├ EquipmentService.search(name, status)          # 一覧と同じ抽出・ID昇順               │
     │     ├ LendingService.findActiveLendingsByEquipmentId # 借用者（LENTのみ）                   │
     │     ├ List<EquipmentCsvRow> 組み立て                                                       │
     │     └ CsvFormatter → BOM + 見出し + 行 (CRLF) → byte[]  ──想定外例外──▶ CsvExportException ─┤
     │ EquipmentCsvExportService.fileName(LocalDateTime.now())                                     │
     ▼                                                                                             ▼
200 text/csv + Content-Disposition: attachment        @ExceptionHandler → redirect:/equipment + flash error
```

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

違反なしのため記載なし。
