---

description: "Task list for 備品一覧のCSVエクスポート"
---

# Tasks: 備品一覧のCSVエクスポート

**Input**: Design documents from `/specs/001-equipment-csv-export/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/csv-download.md, quickstart.md

**Tests**: Constitution III（Service単体テスト・MockMvc結合テストは MUST）に従い、テストタスクを含める。
各ストーリーでテストを先に書き、失敗することを確認してから実装する。

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Include exact file paths in descriptions

## Path Conventions

単一Mavenプロジェクト。パッケージルートは `jp.co.example.equipmentmanagement`。

- 本体：`src/main/java/jp/co/example/equipmentmanagement/`（以下 `MAIN/`）
- テンプレート・CSS：`src/main/resources/`
- テスト：`src/test/java/jp/co/example/equipmentmanagement/`（以下 `TEST/`）

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 既存状態の確認（新規プロジェクト初期化・依存追加は不要：research.md R1）

- [X] T001 既存テストが全件成功することを確認する（`./mvnw test`）。失敗があれば本機能に着手せず原因を報告する。`pom.xml` は変更しない

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 全ストーリー共通の前提

本機能はエンティティ・リポジトリ・セキュリティ設定の変更がなく（plan.md「変更しないもの」）、
US1・US2 が共有する基盤は US1 の Service で足りるため、このフェーズのタスクはない。

**Checkpoint**: Phase 1 完了後、US1 に着手できる

---

## Phase 3: User Story 1 - 備品一覧をCSVでダウンロードする (Priority: P1) 🎯 MVP

**Goal**: 一覧画面の「CSVダウンロード」から、全備品を6列・BOM付きUTF-8のCSVとしてADMIN/USERがダウンロードできる

**Independent Test**: 検索条件なしで一覧を開きボタンを押す → `equipment_YYYYMMDD_HHmmss.csv` が保存され、
Excelで開くと全備品が文字化けなく1行1件で並ぶ（quickstart.md 手順1〜3, 7, 8）

### Tests for User Story 1 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T002 [P] [US1] `TEST/service/EquipmentCsvExportServiceTest.java` を新規作成する（`@ExtendWith(MockitoExtension.class)`、`EquipmentService` と `LendingService` を `@Mock`、`EquipmentCsvExportService` を `@InjectMocks`。既存 `LendingServiceTest` の書き方に合わせ、テストメソッド名は日本語）。以下を検証する:
  - 出力バイト列の先頭3バイトが BOM `EF BB BF` で、残りが UTF-8 でデコードできる
  - 1行目が見出し `品名,管理番号,保管場所,状態,購入日,借用者`（この順・この6列のみ。返却予定日等は出力しない）
  - 行区切りが CRLF で、最終行の後にも CRLF がある
  - 状態は日本語ラベル（`利用可`／`貸出中`／`故障中`）で出力される
  - 購入日は `YYYY-MM-DD`、未設定（null）なら空欄。保管場所 null も空欄
  - 状態が貸出中で有効な貸出記録（`findActiveLendingsByEquipmentId()` のMapに存在）があれば借用者列に社員の氏名、貸出中以外は空欄、貸出中でもMapに無ければ空欄（例外にしない）
  - 値に `,` `"` CR LF のいずれかを含む場合のみ値全体を `"` で囲み、`"` は `""` にする（例：`ケーブル "HDMI", 2m` → `"ケーブル ""HDMI"", 2m"`）。含まない値は囲まない
  - `EquipmentService.search` が空リストを返すと見出し行のみ（BOM + 見出し + CRLF）になる
  - 行の順序は `EquipmentService.search` の戻り値の順序のまま（並べ替えない）
  - `buildFileName(LocalDateTime.of(2026, 10, 3, 15, 30, 0))` が `equipment_20261003_153000.csv` を返す
- [X] T003 [P] [US1] `TEST/controller/EquipmentCsvControllerTest.java` を新規作成する（`@SpringBootTest` + `@AutoConfigureMockMvc` + `@Transactional`、既存 `EquipmentControllerTest` に合わせる。インメモリH2の初期データを利用）。以下を検証する:
  - `@WithMockUser(roles = "USER")` で `GET /equipment/csv` → 200、`Content-Type` が `text/csv;charset=UTF-8`、`Content-Disposition` が `attachment; filename="equipment_\d{8}_\d{6}.csv"` の形式
  - `@WithMockUser(roles = "ADMIN")` でも 200
  - 未認証（`@WithAnonymousUser`）で `GET /equipment/csv` → 302 でログイン画面（`**/login`）へリダイレクト
  - 条件なしのボディ行数（見出しを除く）が `EquipmentRepository.count()` と一致する
  - 貸出中の初期データ備品の行に、その有効な貸出記録の社員氏名が含まれる
  - `GET /equipment` のレスポンスHTMLに `href="/equipment/csv"` を含む「CSVダウンロード」リンクがある（USER・ADMINの両方）
  - 実装時の変更：T012 で検索条件をリンクに埋め込んだ結果、条件なしの一覧ではリンクが `/equipment/csv?name=&status=` と描画される（Thymeleaf は null のパラメータを空値で出力する）。空パラメータは条件なしとして扱われ、結合テスト「空文字の検索条件は条件なしと同じ結果になる」で担保されているため、テンプレートで分岐させずこのURLを採用し、本テストの期待値を `href="/equipment/csv?name=&amp;status="` とした

### Implementation for User Story 1

- [X] T004 [US1] `MAIN/service/EquipmentCsvExportService.java` を新規作成する（`@Service`、`@RequiredArgsConstructor`、`@Slf4j`、依存は `EquipmentService` と `LendingService`）:
  - `public byte[] export(String name, EquipmentStatus status)`：`equipmentService.search(name, status)` の結果を順序を変えずに、data-model.md の列定義（品名=name、管理番号=assetNumber、保管場所=location、状態=status.getLabel()、購入日=purchaseDate.toString()、借用者=有効な貸出記録の employee.name。「状態が貸出中」かつ「有効な貸出記録が存在する」場合のみ）で1行ずつ出力する。null は空文字。見出し行を先頭に付け、各行末に CRLF。UTF-8 でバイト列化し先頭に BOM `EF BB BF` を付与する
  - `private static String escape(String value)`：`,` `"` `\r` `\n` のいずれかを含む場合のみ `"` で囲み、`"` を `""` に置換（research.md R3）。CSVインジェクション対策の加工はしない旨（R4）をコメントで残す
  - `public String buildFileName(LocalDateTime now)`：`equipment_yyyyMMdd_HHmmss.csv`
  - 出力時に `log.info` で検索条件（name, status）と出力件数を1行出す。借用者名はログに出さない（R11）
  - 列順・BOM・借用者の判定条件など業務ルールの意図をJavadoc/コメントで記載する（Constitution II）
  - T002 が成功することを確認する（depends on T002）
- [X] T005 [US1] `MAIN/controller/EquipmentController.java` に `@GetMapping("/csv")` のハンドラを追加する。`EquipmentCsvExportService` をフィールド注入（`private final`）し、`export(null, null)` の結果を `ResponseEntity<byte[]>` で返す。ヘッダーは `Content-Type: text/csv;charset=UTF-8`（`MediaType` に charset UTF-8 を付与）と、`ContentDisposition.attachment().filename(buildFileName(LocalDateTime.now()))` による `Content-Disposition`。Controller に CSV 組み立てロジックを書かない（depends on T004）
- [X] T006 [US1] `src/main/resources/templates/equipment/list.html` の「備品リスト」カードヘッダー（`.card__header` 内、件数表示 `card__meta` の隣）に `<a class="btn btn--secondary btn--sm" th:href="@{/equipment/csv}">CSVダウンロード</a>` を追加する。`sec:authorize` で出し分けない（ADMIN/USER共通）。0件時も表示されるよう `th:if`/`th:unless` の外に置く。JavaScript は使わない
- [X] T007 [US1] `src/main/resources/static/css/app.css` で、`.card__header` 内にリンクを置いたときの配置（件数とリンクを右寄せで横並び）と、スマホ幅（`@media (max-width: 768px)` 内の既存 `.card__header` 定義付近）での折り返しを確認し、崩れる場合のみ最小限のスタイルを追加する。既存クラスで問題なければ変更しない
- [X] T008 [US1] `./mvnw test` を実行し、T002・T003 と既存テストがすべて成功することを確認する

**Checkpoint**: 全件のCSVダウンロードが単独で動作し、テストで担保されている（MVP）

---

## Phase 4: User Story 2 - 絞り込んだ結果だけをCSVでダウンロードする (Priority: P2)

**Goal**: 最後に実行した検索条件（品名・状態）で絞り込まれた一覧と同じ内容がCSVに出力される。不正な状態値はエラー表示

**Independent Test**: 状態「故障中」で検索後にダウンロード → 画面と件数・内容が一致。検索欄を書き換えて未検索のままダウンロード → 表示中の一覧の内容が出る（quickstart.md 手順4〜6, 9）

### Tests for User Story 2 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T009 [US2] `TEST/controller/EquipmentCsvControllerTest.java` にテストを追加する（depends on T003）:
  - `GET /equipment/csv?status=BROKEN` のボディ行（見出し除く）がすべて状態 `故障中` で、件数が `EquipmentService.search(null, EquipmentStatus.BROKEN)` の件数と一致する
  - `GET /equipment/csv?name=<初期データの品名の一部>` が品名部分一致の結果だけを出力する
  - `name` と `status` の両方指定で、両条件を満たす行だけが一覧と同じ順序で出力される
  - 一致しない品名（例：`name=存在しない品名XYZ`）で、ボディが BOM + 見出し行 + CRLF のみ
  - `name=` と `status=`（空文字）は条件なしと同じ結果になる
  - `GET /equipment/csv?status=XXX` → 302 で `/equipment` へリダイレクトし、フラッシュ属性 `error` が `検索条件が不正です`
  - `GET /equipment?status=XXX`（一覧画面）も同様に 302 で `/equipment` へリダイレクトし、`error` が `検索条件が不正です`
  - `GET /equipment?name=PC&status=BROKEN` のHTMLに、`/equipment/csv` へのリンクが `name=PC` と `status=BROKEN` を含んで描画される（＝描画時の検索条件がリンクに埋め込まれる。未検索の入力値は反映されない仕組みの担保）

### Implementation for User Story 2

- [X] T010 [US2] `MAIN/controller/EquipmentController.java` で、状態パラメータの解釈を private メソッド（例：`parseStatus(String status)`：空なら null、それ以外は `EquipmentStatus.valueOf`）に共通化し、既存の `list` と `/csv` ハンドラの両方から使う。`/csv` ハンドラに `@RequestParam(required = false) String name, @RequestParam(required = false) String status` を追加し、`export(name, parseStatus(status))` を呼ぶ（depends on T005）
- [X] T011 [US2] `MAIN/controller/EquipmentController.java` に `@ExceptionHandler(IllegalArgumentException.class)` を追加し、フラッシュ属性 `error` に「検索条件が不正です」を設定して `redirect:/equipment` を返す（パラメータなしへのリダイレクトなのでループしない）。不正な検索条件のための処理である旨をコメントで記載する（research.md R7、depends on T010）
  - 実装時の変更：`IllegalArgumentException` を Controller 全体で捕捉すると、他の操作で起きた想定外の例外まで「検索条件が不正です」に化けてログにも残らないため、`parseStatus` で専用の `MAIN/controller/InvalidSearchConditionException.java` に変換し、そのハンドラのみを追加した
- [X] T012 [US2] `src/main/resources/templates/equipment/list.html` の「CSVダウンロード」リンクを `th:href="@{/equipment/csv(name=${name},status=${status})}"` に変更し、Modelの `name`・`status`（＝描画時に実行済みの検索条件）を埋め込む。検索フォームの入力値は使わない（Clarifications 2026-10-03、research.md R5、depends on T006）
- [X] T013 [US2] `./mvnw test` を実行し、T009 を含む全テストが成功することを確認する

**Checkpoint**: US1・US2 ともに動作し、テストで担保されている

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: ドキュメント整備と最終確認

- [X] T014 [P] `README.md` を更新する：「機能」の備品管理に「一覧のCSVダウンロード（表示中の検索条件で出力、BOM付きUTF-8、ADMIN/USER共通）」を追記し、画面一覧に `/equipment/csv`（ADMIN, USER）を追加、末尾「スコープ外」のCSVエクスポートの記述を削除する
- [X] T015 [P] `equipment-management-spec.md` の7章「スコープ外事項」からCSVエクスポートの行を削除し、本機能の仕様（`specs/001-equipment-csv-export/spec.md`）への参照を追記する
- [X] T016 Constitution 制約の最終確認：`git diff` で `src/main/resources/templates/` に `<script` や `on*=` 属性が追加されていないこと、`pom.xml`・`SecurityConfig.java`・`entity/`・`repository/` に変更がないこと、Controller にCSV組み立てロジックがないことを確認する
- [X] T017 `./mvnw test` を実行し全件成功を確認したうえで、`./mvnw spring-boot:run` で quickstart.md の手順1〜10を実施する（Excelでの確認は利用者に依頼し、結果を記録する）
  - 実施結果（2026-10-03）：`./mvnw clean test` 90件成功。起動したアプリに curl で手順1, 3〜9 を確認し期待どおり（BOM・CRLF・ヘッダー・ファイル名、借用者出力、状態絞り込み、表示中条件のリンク埋め込み、0件は見出しのみ、ADMIN/USERで200、未ログインは `/login` へ302、不正な状態値は一覧へ302＋「検索条件が不正です」）。手順2（Excelでの表示）は利用者による確認待ち。手順10（カンマ・ダブルクォートを含む値）は開発用DBを汚さないため単体テストで代替

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 依存なし
- **Foundational (Phase 2)**: タスクなし
- **US1 (Phase 3)**: Phase 1 完了後
- **US2 (Phase 4)**: US1 の T003・T005・T006 に依存（同じファイルを拡張するため）
- **Polish (Phase 5)**: US1・US2 完了後

### User Story Dependencies

- **US1 (P1)**: 他ストーリーに依存しない
- **US2 (P2)**: US1 の Service（T004）・エンドポイント（T005）・リンク（T006）を拡張する。
  Service は条件付き検索に最初から対応しているため、US2 は Controller とテンプレートの変更のみ

### Within Each User Story

- テスト（T002/T003, T009）を先に書いて失敗を確認 → Service → Controller → テンプレート → テスト実行

### Parallel Opportunities

- T002 と T003（別ファイルのテスト作成）
- T014 と T015（別ファイルのドキュメント更新）
- T005・T010・T011 は同一ファイル（`EquipmentController.java`）、T006・T012 も同一ファイルのため並列不可

---

## Parallel Example: User Story 1

```bash
# US1 のテストを同時に作成:
Task: "EquipmentCsvExportService の単体テストを src/test/java/jp/co/example/equipmentmanagement/service/EquipmentCsvExportServiceTest.java に作成"
Task: "CSVダウンロードの結合テストを src/test/java/jp/co/example/equipmentmanagement/controller/EquipmentCsvControllerTest.java に作成"
```

## Parallel Example: Polish

```bash
Task: "README.md にCSVダウンロード機能を追記"
Task: "equipment-management-spec.md の7章からCSVエクスポートを削除"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1: 既存テストの成功を確認
2. Phase 3: US1（全件ダウンロード）を実装
3. **STOP and VALIDATE**: quickstart.md 手順1〜3, 7, 8 で確認

### Incremental Delivery

1. US1 → 全件CSVが使える（MVP）
2. US2 → 検索条件の反映と不正条件のハンドリング
3. Polish → ドキュメント更新・最終確認

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- テストは先に書き、失敗することを確認してから実装する
- CLAUDE.md の運用ルールに従い、`/speckit-implement` 完了時にコミットする（機能単位のコミットでもよい）

## Phase 6: Convergence

- [ ] T018 利用者に依頼して、日本語版Excelで quickstart.md 手順2（ダウンロードしたCSVをダブルクリックで開き、6列が文字化け・列ずれなく表示され件数が画面の「全 N 件」と一致すること）と手順10（カンマ・ダブルクォートを含む品名が1セルにそのまま表示されること）を実施し、結果を T017 の実施結果の下に追記する per SC-002 (partial)
- [X] T019 検索条件なしの一覧で「CSVダウンロード」リンクが `/equipment/csv?name=&status=` と描画され、T003 に記載の `href="/equipment/csv"` と異なる点（空パラメータは条件なしとして扱われ、結合テストで担保済み）を、T011 と同様の「実装時の変更」メモとして tasks.md に記録する per T003 (contradicts)
