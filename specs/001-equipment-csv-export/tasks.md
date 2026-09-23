---

description: "Task list for 備品一覧のCSVエクスポート"
---

# Tasks: 備品一覧のCSVエクスポート

**Input**: Design documents from `/specs/001-equipment-csv-export/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/equipment-csv-download.md, quickstart.md

**Tests**: 憲章 III（NON-NEGOTIABLE）および plan.md の Testing / Project Structure でテスト作成が求められているため、
テストタスクを含める。各ストーリー内ではテストを先に書き、失敗することを確認してから実装する。

**Organization**: タスクはユーザーストーリー単位でまとめ、各ストーリーを独立して実装・検証できるようにする。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 並行実行可能（別ファイル・未完了タスクへの依存なし）
- **[Story]**: 対応するユーザーストーリー（US1, US2）
- 説明には正確なファイルパスを含める

## Path Conventions

- 単一 Maven プロジェクト。以下の略記を使う:
  - `MAIN` = `src/main/java/jp/co/example/equipmentmanagement`
  - `TEST` = `src/test/java/jp/co/example/equipmentmanagement`
  - テンプレート = `src/main/resources/templates`
- 新規依存の追加は禁止（R-1）。`pom.xml`・`SecurityConfig`・DB スキーマは変更しない。
- JavaScript は使用しない（憲章 II）。

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 既存プロジェクトへの追加のため新規初期化は不要。作業開始前のベースラインを確認する。

- [ ] T001 作業ブランチ `try/csv-import-01` 上で `./mvnw test` を実行し、既存テストがすべて成功する（`BUILD SUCCESS`）ことを確認する。失敗する場合は本機能の作業に入る前に原因を報告する（`pom.xml` は変更しない）

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: US1・US2 の両方が依存する例外型・行 DTO・検索条件変換を用意する

**⚠️ CRITICAL**: このフェーズが完了するまでユーザーストーリーの作業は開始しない

- [ ] T002 [P] `MAIN/service/InvalidSearchConditionException.java` を新規作成する。`RuntimeException` を継承し、引数なしコンストラクタで `super("検索条件が不正です。条件を指定し直してください。")` とする（既存 `EquipmentNotFoundException` と同じ書き方。メッセージは contracts (c) の文言と完全一致させる）
- [ ] T003 [P] `MAIN/service/CsvExportException.java` を新規作成する。`RuntimeException` を継承し、コンストラクタ `CsvExportException(Throwable cause)` で `super("CSVファイルの出力中にエラーが発生しました。時間をおいて再度お試しください。", cause)` とする（原因例外を保持する。メッセージは contracts (d) の文言と完全一致させる）
- [ ] T004 [P] `MAIN/dto/EquipmentCsvRow.java` を Java `record` として新規作成する。コンポーネントは data-model.md の順に `String name`（null 不可）, `String assetNumber`（null 不可）, `String location`（「可（空欄出力）」）, `String statusLabel`（null 不可、「利用可」「貸出中」「故障中」）, `String purchaseDate`（「可（空欄出力）」、`yyyy-MM-dd` 整形済み文字列）, `String borrowerName`（「可（空欄出力）」）。値は「無害化・エスケープ前の生の表示値」を保持する旨を Javadoc に記載する
- [ ] T005 `TEST/service/EquipmentServiceTest.java` に `parseStatusFilter` の単体テストを追加する（既存の Mockito スタイルに合わせる）: `null` → `null`、`""` → `null`、`"AVAILABLE"`/`"LENT"`/`"BROKEN"` → 対応する `EquipmentStatus`、`"UNKNOWN"` と `"available"`（小文字）→ `InvalidSearchConditionException`。実装前に失敗することを確認する
- [ ] T006 `MAIN/service/EquipmentService.java` に `public EquipmentStatus parseStatusFilter(String status)` を追加する。`StringUtils.hasText(status)` が false なら `null`（条件なし）、`EquipmentStatus` の列挙子名に完全一致すればその値、それ以外は `InvalidSearchConditionException` を送出する（`IllegalArgumentException` を捕捉して変換）。「空は条件なし・列挙子名以外は不正（FR-017）」である意図をコメントで記載する。既存 `list` メソッドの挙動は変更しない（R-7 Notes：スコープ外）。T005 が成功することを確認する

**Checkpoint**: 基盤完了 — ユーザーストーリーの実装を開始できる

---

## Phase 3: User Story 1 - 備品一覧をCSVでダウンロードする (Priority: P1) 🎯 MVP

**Goal**: 備品一覧画面の「CSVダウンロード」ボタンから、全備品を BOM 付き UTF-8・CRLF・日本語列見出しの CSV として、ADMIN・USER の両ロールがダウンロードできる

**Independent Test**: 検索条件なしで一覧画面を開き「CSVダウンロード」を押す。`equipment_yyyyMMdd_HHmmss.csv` が保存され、Excel で開くと文字化けなく 1 行目が `品名,管理番号,保管場所,状態,購入日,借用者`、以降が一覧と同じ順序・内容で出力され、貸出中の行だけ借用者名が入っている（quickstart S1・S2・S4、S5 の未ログイン行）

### Tests for User Story 1 ⚠️

> **NOTE: 先にテストを書き、実装前に失敗することを確認する**

- [ ] T007 [P] [US1] `TEST/service/CsvFormatterTest.java` を新規作成する（Spring 不要の純粋な単体テスト）。data-model.md「フィールド整形ルール」の表をそのまま検証する: `ノートPC`→`ノートPC`、`null`→空文字、`PC, 15インチ`→`"PC, 15インチ"`、`19"モニター`→`"19""モニター"`、`1行目\n2行目`→`"1行目\n2行目"`、CR を含む値もクォートされる、`=SUM(A1)`→`'=SUM(A1)`、`+1`→`'+1`、`@foo`→`'@foo`、`-20℃用冷蔵庫`→`'-20℃用冷蔵庫`、`=A,B`→`"'=A,B"`（無害化→エスケープの順）、`PC-001`→`PC-001`（先頭以外の `-` は変更しない）。さらに無害化対象外の列（状態・購入日）では `-` 始まり等でも `'` を付けないこと、1 行分の連結結果がカンマ区切り＋末尾 CRLF になることを検証する
- [ ] T008 [P] [US1] `TEST/service/EquipmentCsvExportServiceTest.java` を新規作成する（`@ExtendWith(MockitoExtension.class)`、`EquipmentService`・`LendingService` をモック、`CsvFormatter` は実物）。検証項目: (1) 返却バイト列の先頭 3 バイトが `EF BB BF`、(2) 1 行目が `品名,管理番号,保管場所,状態,購入日,借用者` + CRLF、(3) 状態が `利用可`/`貸出中`/`故障中` の日本語ラベルで出力される（FR-007）、(4) 購入日が `yyyy-MM-dd`・null は空欄、保管場所 null は空欄で列数が 6 のまま（FR-008）、(5) 状態 `LENT` かつ有効な貸出記録ありの備品のみ借用者名が出る。`LENT` だがマップに該当なし→空欄、`AVAILABLE`/`BROKEN` で有効な貸出記録がマップにあっても空欄（FR-009、状態を正とする）、(6) `search` が 0 件なら BOM + 見出し行のみ（FR-015）、(7) 最終行末尾も CRLF、(8) `fileName(LocalDateTime.of(2026, 9, 23, 14, 30, 15))` が `equipment_20260923_143015.csv`（FR-013）、(9) `EquipmentService.search` が `RuntimeException` を投げた場合に `CsvExportException` が送出され原因例外が保持される（FR-016）
- [ ] T009 [P] [US1] `TEST/controller/EquipmentCsvExportControllerTest.java` を新規作成する（`@SpringBootTest` + `@AutoConfigureMockMvc` + `@Transactional`、既存 `EquipmentControllerTest` と同じ import・書き方）。`EquipmentRepository`/`LendingRepository` でテストデータを保存する（初期データが存在しうるため、期待値は `EquipmentService.search(null, null)` の結果または自テストで作成した行の有無で判定する）。検証項目: `@WithMockUser(roles = "ADMIN")` で `GET /equipment/csv` が 200、`Content-Type` が `text/csv;charset=UTF-8` と互換、`Content-Disposition` が正規表現 `attachment; filename="equipment_\d{8}_\d{6}\.csv"` に一致、本文先頭が BOM、見出し行が一致、品名に `,` を含む備品が 1 セルにクォートされて出力される。`@WithMockUser(roles = "USER")` でも 200 かつ ADMIN と同じ本文になる（FR-002）。一覧画面 `GET /equipment` の HTML に ADMIN・USER いずれでも `/equipment/csv` へのリンクと文言「CSVダウンロード」が含まれる（FR-001）
- [ ] T010 [P] [US1] `TEST/controller/EquipmentSecurityIntegrationTest.java` に未ログインで `GET /equipment/csv` を行うと 302 で `/login` へリダイレクトされ CSV が返らないテストを追加する（FR-014、既存「未認証でアクセスするとログイン画面へリダイレクトされる」と同じ書き方）

### Implementation for User Story 1

- [ ] T011 [P] [US1] `MAIN/service/CsvFormatter.java` を `@Component`（service パッケージ）として新規作成する。メソッド例: `String formatField(String value, boolean sanitize)` と `String formatRow(List<String>/可変長の値, 無害化対象列の指定)`。規則は data-model.md の適用順どおり: 1) null → 空文字、2) 無害化対象（品名・管理番号・保管場所・借用者）で値が `=`、`+`、`-`、`@` のいずれかで始まる場合は先頭に `'` を付ける（FR-012）、3) 値にカンマ・ダブルクォート・CR・LF のいずれかが含まれる場合は `"` を `""` に置換し値全体を `"` で囲む（FR-011）。区切りは `,`、行末は `\r\n`。「無害化を先に行う理由（`=A,B` → `"'=A,B"`）」と「対象文字は仕様 FR-012 の 4 文字に限定（R-5 Notes）」をコメントで記載する。T007 を成功させる
- [ ] T012 [US1] `MAIN/service/EquipmentCsvExportService.java` を新規作成する（`@Service`、`@RequiredArgsConstructor`、SLF4J ロガー、依存: `EquipmentService`, `LendingService`, `CsvFormatter`）。`@Transactional(readOnly = true) public byte[] export(String name, EquipmentStatus status)`: `equipmentService.search(name, status)` と `lendingService.findActiveLendingsByEquipmentId()` から `EquipmentCsvRow` のリストを組み立て（借用者は `status == LENT` の場合のみマップから `lending.getEmployee().getName()`、該当なしは null）、BOM `﻿` + 見出し行 + 各行（CRLF）を UTF-8 の `byte[]` にして返す。処理中の `RuntimeException` は捕捉して `CsvExportException` にラップして送出する（R-8）。`public String fileName(LocalDateTime dateTime)` は `"equipment_" + yyyyMMdd_HHmmss + ".csv"` を返す純粋関数。「借用者は状態 LENT を正として判定する（FR-009）」「レスポンス送出前に CSV を完成させるため例外時にリダイレクト可能（R-2）」をコメントで記載する。T008 を成功させる（depends on T011）
- [ ] T013 [US1] `MAIN/controller/EquipmentController.java` に `@GetMapping("/csv") public ResponseEntity<byte[]> downloadCsv(@RequestParam(required = false) String name, @RequestParam(required = false) String status)` を追加する。処理は `equipmentService.parseStatusFilter(status)` → `equipmentCsvExportService.export(name, statusFilter)` → `ResponseEntity.ok()` に `Content-Type: text/csv;charset=UTF-8` と `Content-Disposition: attachment; filename="` + `equipmentCsvExportService.fileName(LocalDateTime.now())` + `"` を設定して返すのみ（業務判断を置かない、憲章 I）。`EquipmentCsvExportService` をフィールドに追加する。併せて `@ExceptionHandler(CsvExportException.class)` を追加し、SLF4J で ERROR ログ（原因例外のスタックトレース付き）を出力したうえで `redirectAttributes.addFlashAttribute("error", ex.getMessage())` と `redirect:/equipment` を返す（FR-016、画面にスタックトレースを出さない）。コントローラーに `@Slf4j` 等のロガーがなければ追加する（depends on T012）
- [ ] T014 [US1] `src/main/resources/templates/equipment/list.html` の `page-actions` を変更し、ADMIN・USER の両方に `<a class="btn btn--secondary" th:href="@{/equipment/csv}">CSVダウンロード</a>` を表示する。現在 `<div class="page-actions" sec:authorize="hasRole('ADMIN')">` に付いている `sec:authorize` を「＋ 新規登録」の `<a>` 側へ移し、新規登録は ADMIN のみ表示のまま維持する。JavaScript は使わない（contracts §2）
- [ ] T015 [US1] `./mvnw test` を実行し T007〜T010 を含む全テストが成功することを確認する

**Checkpoint**: US1 が単独で動作する（検索条件なしの全件ダウンロード、両ロール可、未ログイン拒否、想定外エラー時は一覧へ戻りメッセージ表示）

---

## Phase 4: User Story 2 - 検索で絞り込んだ備品だけをCSVでダウンロードする (Priority: P2)

**Goal**: 一覧に現在適用されている検索条件（品名の部分一致・状態の完全一致）をリンクで引き継ぎ、同じ条件・同じ順序で抽出した備品だけを CSV に出力する。不正な条件では一覧へ戻してエラーメッセージを表示する

**Independent Test**: 状態＝「故障中」や品名「PC」で検索後に「CSVダウンロード」を押し、CSV の件数・順序・内容が画面の一覧と一致すること。一致 0 件なら見出し行のみ。`/equipment/csv?status=UNKNOWN` では一覧へ戻り「検索条件が不正です。条件を指定し直してください。」が表示されること（quickstart S3・S5）

### Tests for User Story 2 ⚠️

> **NOTE: 先にテストを書き、実装前に失敗することを確認する**

- [ ] T016 [P] [US2] `TEST/service/EquipmentCsvExportServiceTest.java` に追加する: `export("PC", EquipmentStatus.BROKEN)` が `equipmentService.search("PC", EquipmentStatus.BROKEN)` に引数をそのまま渡すこと（`verify`）、`search` の返却順（例: ID 3, 1, 2 の順で返すモック）がそのまま CSV の行順になること（Service 側で並べ替えない＝一覧と同じ順序、FR-005）
- [ ] T017 [P] [US2] `TEST/controller/EquipmentCsvExportControllerTest.java` に追加する（テスト内で状態・品名の異なる備品を保存）: `?status=BROKEN` で故障中の備品のみ、`?name=<テスト用の一意な文字列>` で品名部分一致の備品のみ、`?name=...&status=...` で両条件を満たす備品のみ出力される（FR-004）。CSV のデータ行の品名の並びが同条件の `EquipmentService.search` の結果（ID 昇順）と一致する（FR-005）。一致 0 件の条件（例 `?name=存在しない品名`）で 200・BOM + 見出し行のみ（FR-015）。`?name=&status=`（空文字）は条件なしとして全件出力される。`?status=UNKNOWN` と `?status=available` で 302・`redirectedUrl("/equipment")`・`flash().attribute("error", "検索条件が不正です。条件を指定し直してください。")`（FR-017）。`GET /equipment?status=BROKEN` の HTML に含まれる CSV リンクの href が `status=BROKEN` を含む（現在の検索条件の引き継ぎ）

### Implementation for User Story 2

- [ ] T018 [US2] `src/main/resources/templates/equipment/list.html` の「CSVダウンロード」リンクを `th:href="@{/equipment/csv(name=${name},status=${status})}"` に変更し、一覧に**現在適用されている**検索条件（モデル属性 `name` と、列挙型の `status`）を引き継ぐ。検索フォーム内の送信ボタンにはしない（未検索の入力値で出力されるのを防ぐ、R-6）
- [ ] T019 [US2] `MAIN/controller/EquipmentController.java` に `@ExceptionHandler(InvalidSearchConditionException.class)` を追加し、`redirectAttributes.addFlashAttribute("error", ex.getMessage())` と `redirect:/equipment`（検索条件を付けない＝リダイレクトループ防止）を返す。既存 `handleEquipmentError` と同じ流儀とし、スタックトレースは画面に出さない。不正パラメータは利用者起因のため必要に応じて WARN/INFO レベルでログ出力する
- [ ] T020 [US2] `./mvnw test` を実行し T016・T017 を含む全テストが成功することを確認する

**Checkpoint**: US1・US2 の両方が独立して動作する

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: 全ストーリー横断の仕上げと完了条件の確認

- [ ] T021 [P] 追加・変更したクラス（`MAIN/service/CsvFormatter.java`, `MAIN/service/EquipmentCsvExportService.java`, `MAIN/service/EquipmentService.java`, `MAIN/controller/EquipmentController.java`）を見直し、業務ルール（無害化の対象・順序、借用者の判定、不正条件の扱い）に意図が伝わるコメント／Javadoc があること、Controller に業務判断が入っていないこと（憲章 I）を確認・修正する
- [ ] T022 [P] 制約の最終確認: `git diff feature/sdd-spec-kit -- pom.xml src/main/java/jp/co/example/equipmentmanagement/config` が空（新規依存・Security 設定変更なし）であること、`src/main/resources/templates/equipment/list.html` に `<script>` や `onclick` 等の JavaScript が含まれないこと（憲章 II）を確認する
- [ ] T023 `./mvnw clean test` を実行し、全テストが成功（`BUILD SUCCESS`）することを確認する（憲章 III の完了条件）
- [ ] T024 `./mvnw spring-boot:run` で起動し、`specs/001-equipment-csv-export/quickstart.md` のシナリオ S1〜S5（および任意で §4 の curl による BOM・ヘッダー確認）を実施して期待結果と一致することを確認する。Excel での確認ができない場合はその旨を報告する

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 依存なし
- **Foundational (Phase 2)**: Setup 完了後。全ユーザーストーリーをブロックする
- **US1 (Phase 3)**: Foundational 完了後
- **US2 (Phase 4)**: Foundational 完了後に着手可能だが、エンドポイント（T013）とリンク（T014）を拡張するため、**US1 完了後**に行うのが前提
- **Polish (Phase 5)**: US1・US2 完了後

### User Story Dependencies

- **US1 (P1)**: 他ストーリーに依存しない。MVP
- **US2 (P2)**: US1 の `GET /equipment/csv` と一覧のリンクに依存する（同じファイル `EquipmentController.java`・`list.html`・テストクラスを変更するため）。検証は US2 単独のシナリオで行える

### Within Each User Story

- テストを先に書き、失敗を確認してから実装する
- DTO／例外（Phase 2）→ `CsvFormatter` → `EquipmentCsvExportService` → `EquipmentController` → テンプレート
- 各ストーリーの最後に `./mvnw test` で確認してから次へ進む

### Task-level Dependencies

- T006 ← T002, T005
- T011 ← T007
- T012 ← T003, T004, T008, T011
- T013 ← T003, T006, T012
- T014 ← T013
- T018 ← T014, T017
- T019 ← T002, T013, T017

### Parallel Opportunities

- Phase 2: T002・T003・T004 は別ファイルで並行可能
- Phase 3: テスト T007・T008・T009・T010 は並行可能。実装 T011 はテストと並行着手可（別ファイル）
- Phase 4: T016・T017 は並行可能
- Phase 5: T021・T022 は並行可能
- `EquipmentController.java` を変更する T013・T019、`list.html` を変更する T014・T018 は同一ファイルのため並行不可

---

## Parallel Example: User Story 1

```bash
# US1 のテストをまとめて着手:
Task: "CsvFormatterTest を TEST/service/CsvFormatterTest.java に作成"
Task: "EquipmentCsvExportServiceTest を TEST/service/EquipmentCsvExportServiceTest.java に作成"
Task: "EquipmentCsvExportControllerTest を TEST/controller/EquipmentCsvExportControllerTest.java に作成"
Task: "未ログイン時のテストを TEST/controller/EquipmentSecurityIntegrationTest.java に追加"

# 並行して整形クラスを実装:
Task: "CsvFormatter を MAIN/service/CsvFormatter.java に作成"
```

## Parallel Example: User Story 2

```bash
Task: "引数の受け渡し・行順のテストを TEST/service/EquipmentCsvExportServiceTest.java に追加"
Task: "絞り込み・不正条件のテストを TEST/controller/EquipmentCsvExportControllerTest.java に追加"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1: Setup（ベースライン確認）
2. Phase 2: Foundational（例外・DTO・`parseStatusFilter`）
3. Phase 3: US1（全件ダウンロード）
4. **STOP and VALIDATE**: `./mvnw test` と quickstart S1・S2・S4・S5（未ログイン）で確認
5. 区切りとしてコミット

### Incremental Delivery

1. Setup + Foundational → 基盤完成
2. US1 追加 → 単独で検証 → コミット（MVP）
3. US2 追加 → 単独で検証（quickstart S3・S5）→ コミット
4. Polish → `./mvnw clean test` と quickstart 全シナリオで完了確認

---

## Notes

- [P] = 別ファイル・依存なし
- [Story] ラベルはタスクとユーザーストーリーの対応を示す
- 実装前にテストが失敗することを確認する
- メッセージ文言（`検索条件が不正です。条件を指定し直してください。` / `CSVファイルの出力中にエラーが発生しました。時間をおいて再度お試しください。`）と列見出し（`品名,管理番号,保管場所,状態,購入日,借用者`）は contracts と完全一致させる
- SC-003「画面との 100% 一致」は、FR-012 により先頭に `'` が付く値を除外条件として扱う（R-5 Notes）
