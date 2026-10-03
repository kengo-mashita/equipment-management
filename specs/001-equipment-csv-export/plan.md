# Implementation Plan: 備品一覧のCSVエクスポート

**Branch**: `feature/csv-export` | **Date**: 2026-10-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-equipment-csv-export/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

備品一覧画面に「CSVダウンロード」リンクを追加し、表示中の一覧（最後に実行した検索条件の結果）を
BOM付きUTF-8・CRLF・RFC 4180相当のエスケープで、6列（品名・管理番号・保管場所・状態・購入日・借用者）の
CSVとしてダウンロードさせる。検索・借用者取得は既存Serviceを再利用し、CSV組み立ては新規Service、
HTTPレスポンス化は既存 `EquipmentController` の新規ハンドラ `GET /equipment/csv` が担う。
外部ライブラリ・エンティティ・セキュリティ設定の変更はない（詳細は [research.md](./research.md)）。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.1（Spring MVC, Spring Security, Spring Data JPA, Thymeleaf +
thymeleaf-extras-springsecurity6）, Lombok。**本機能での依存追加なし**

**Storage**: H2 Database（ファイルモード）。本機能は読み取りのみ、スキーマ変更なし

**Testing**: JUnit 5 + AssertJ（Service単体）、`@SpringBootTest` + MockMvc + `@WithMockUser`（結合、インメモリH2）

**Target Platform**: WSL上のローカルJVM、ブラウザ（日本語版Excelで開くことを想定）

**Project Type**: サーバーサイドレンダリングのWebアプリケーション（単一Mavenプロジェクト）

**Performance Goals**: 数十件のCSV生成がボタン押下から数秒以内（SC-005）。特別な最適化は不要

**Constraints**: JavaScript不使用、外部通信なし、依存追加なし、Controllerに業務ロジックを書かない

**Scale/Scope**: 備品数十件・単一利用者。変更はService 1クラス新規、Controller・テンプレート・CSSの小修正

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原則 | 判定 | 根拠 |
|---|---|---|
| I. SSR・JavaScript不使用 | ✅ PASS | ダウンロードはサーバー描画時の検索条件を埋め込んだ `<a>` リンク（R5）。JSなし |
| II. レイヤード・責務分離 | ✅ PASS | CSV組み立ては `EquipmentCsvExportService`、Controllerは条件受け取りとヘッダー設定のみ（R9）。URLは `/equipment/csv`（既存リソース配下） |
| III. 業務ルールのテスト担保 | ✅ PASS | Service単体テスト＋MockMvc結合テスト（ロール別・未ログイン・不正条件）を計画。インメモリH2 |
| IV. ロールベースのアクセス制御 | ✅ PASS | 既存 `anyRequest().authenticated()` で ADMIN/USER 許可・未ログイン拒否をサーバー側で強制（R10）。テストで検証 |
| V. ローカル完結とシンプルさ | ✅ PASS | 依存追加なし（R1）、外部通信なし、ページング・非同期なし |
| 品質基準 | ✅ PASS | 不正な状態値をハンドリングしエラー表示（R7）、エクスポート時にINFOログ（R11） |
| 開発ワークフロー | ⚠️ 注意 | 現在のブランチは `feature/csv-export` であり、CLAUDE.md の試行ブランチ命名 `try/<テーマ>-<連番>` と異なる。ベースブランチ `feature/sdd-spec-kit` への直接コミットではないため違反ではないが、運用意図との整合は利用者が判断する |

**Post-Design Re-check（Phase 1 後）**: 上記判定に変更なし。設計で新たな逸脱は生じていない。
一覧画面の不正な状態値のハンドリング追加（R7）は spec の Edge Cases を満たすための付随変更であり、範囲内。

## Project Structure

### Documentation (this feature)

```text
specs/001-equipment-csv-export/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/
│   └── csv-download.md  # Phase 1 output: 画面・HTTP契約
├── checklists/
│   └── requirements.md  # /speckit-specify output
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
src/main/java/jp/co/example/equipmentmanagement/
├── controller/
│   └── EquipmentController.java         # 変更: GET /equipment/csv 追加、状態値パースの共通化、
│                                         #       IllegalArgumentException のハンドリング追加
└── service/
    └── EquipmentCsvExportService.java   # 新規: CSV組み立て（列・エスケープ・BOM・CRLF）、ファイル名生成

src/main/resources/
├── templates/equipment/list.html        # 変更: 「CSVダウンロード」リンクを備品リストのヘッダーに追加
└── static/css/app.css                   # 変更（必要な場合のみ）: ヘッダー内リンクの配置・スマホ幅での折り返し

src/test/java/jp/co/example/equipmentmanagement/
├── service/
│   └── EquipmentCsvExportServiceTest.java    # 新規: 単体テスト
└── controller/
    └── EquipmentCsvControllerTest.java       # 新規: MockMvc結合テスト（ロール別・ヘッダー・条件反映・異常系）
```

変更しないもの：`entity/`、`repository/`、`config/SecurityConfig.java`、`pom.xml`。

**Structure Decision**: 既存の単一Mavenプロジェクト・レイヤード構成（Constitution II）にそのまま追加する。
CSV出力は備品リソースの別表現であるため、新規Controllerを作らず `EquipmentController` に
`/equipment/csv` を追加する。

## Complexity Tracking

Constitution Check に正当化が必要な違反はないため、記載なし。
