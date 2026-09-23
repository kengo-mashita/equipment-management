# Research: 備品一覧のCSVエクスポート

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-09-23

Technical Context に NEEDS CLARIFICATION は残っていない（仕様の Clarifications で借用者の出力可否・
CSVインジェクション対策の方式・不正な検索条件時の挙動は確定済み）。以下は実装方式の選択に関する
調査・決定事項である。

---

## R-1. CSV生成方式（ライブラリの要否）

- **Decision**: 外部ライブラリを追加せず、`service` パッケージに自前の小さなCSV整形クラス
  （`CsvFormatter`）を作成し、`StringBuilder` で組み立てる。
- **Rationale**: 必要な処理は「フィールドのクォート・ダブルクォート二重化（FR-011）」「数式先頭文字の
  無害化（FR-012）」「CRLF 区切り」「BOM 付与（FR-010）」のみで数十行で書ける。憲章「技術的制約」は
  既存スタックで実現できる場合の依存追加を避けるよう求めている（SHOULD）。整形ロジックを
  自前クラスに閉じることで、単体テストで仕様どおりの出力を直接検証できる。
- **Alternatives considered**:
  - Apache Commons CSV / OpenCSV：エスケープは任せられるが、FR-012 の無害化は結局自前で書く必要があり、
    依存追加の正当化が弱い。
  - Thymeleaf の TEXT テンプレートモードで CSV を描画：エスケープ規則をテンプレート側で表現しにくく、
    単体テストもしにくい。

## R-2. レスポンスの返し方（JavaScript 不使用でのダウンロード）

- **Decision**: `GET /equipment/csv` を `EquipmentController` に追加し、`ResponseEntity<byte[]>` を返す。
  ヘッダーは `Content-Type: text/csv; charset=UTF-8`、
  `Content-Disposition: attachment; filename="equipment_yyyyMMdd_HHmmss.csv"`。
  CSV 本文はすべてメモリ上で組み立ててからレスポンスに書き出す。
- **Rationale**:
  - `Content-Disposition: attachment` によりブラウザは画面遷移せずファイル保存を行うため、FR-003
    「利用者は一覧画面に留まる」を通常のリンクだけで満たせる（憲章 II）。
  - 想定件数は数十件（SC-005）で、全体をメモリで組み立てても問題ない。レスポンスを書き出す前に
    CSV が完成しているため、生成中の例外はまだ何も送っていない状態で発生し、一覧画面への
    リダイレクト＋エラーメッセージ表示（FR-016）が確実に行える。
  - ファイル名は ASCII のみのため `filename*`（RFC 5987）エンコードは不要。
- **Alternatives considered**:
  - `HttpServletResponse` の `OutputStream` に直接書き込むストリーミング：大量データ向けだが、途中で例外が
    起きると不完全なファイルが送られてしまいエラー画面へ切り替えられない。本件の規模では不要。
  - `StreamingResponseBody`：同上。

## R-3. 文字コード・改行コード

- **Decision**: UTF-8 でエンコードし、先頭に BOM（`U+FEFF`、バイト列 `EF BB BF`）を付ける。
  行区切りは CRLF（`\r\n`）。最終行の後にも CRLF を付ける。
- **Rationale**: Excel（Windows）は BOM 付き UTF-8 をダブルクリックで開くと UTF-8 として認識し、日本語が
  文字化けしない（FR-010, SC-002）。CRLF は RFC 4180 の規定であり Excel とも相性がよい。
- **Alternatives considered**: Shift_JIS（MS932）：Excel では開けるが、`℃` 以外の機種依存文字や
  絵文字などが欠落しうるうえ、仕様で BOM 付き UTF-8 と明記されているため不採用。

## R-4. フィールドのエスケープ（FR-011）

- **Decision**: 値にカンマ（`,`）・ダブルクォート（`"`）・CR・LF のいずれかが含まれる場合のみ値全体を
  `"` で囲み、値中の `"` を `""` に置換する。それ以外はそのまま出力する。空値（null）は空文字。
- **Rationale**: RFC 4180 に準拠した最小限の規則。常にクォートする方式でも Excel は読めるが、
  「必要なときだけ囲む」方が出力を目視確認しやすく、テストの期待値も読みやすい。
- **Alternatives considered**: 全フィールドを常にクォート：実害はないが仕様（FR-011「含まれる場合は」）の
  文言と一致させるため不採用。

## R-5. CSVインジェクション対策（FR-012）

- **Decision**: 文字列項目（品名・管理番号・保管場所・借用者）の値が `=`・`+`・`-`・`@` のいずれかで
  始まる場合、先頭に `'` を付ける。処理順は「無害化 → エスケープ（R-4）」とする。
  状態（固定の日本語ラベル）・購入日（`yyyy-MM-dd`）は先頭が上記文字になり得ないため対象外。
  列見出しも固定値のため対象外。
- **Rationale**: 仕様の Clarifications で方式が確定済み。無害化を先に行うことで、`=A,B` のような値は
  `"'=A,B"` となり、クォートの内側に `'` が入る正しい形になる。
- **Notes**: OWASP はタブ（`\t`）・CR 始まりも対象に挙げているが、仕様（FR-012）は 4 文字に限定しているため
  仕様どおりとする。拡張が必要になった場合は `CsvFormatter` の対象文字集合を変更するだけで済む。
  また、無害化した値は Excel 上で先頭に `'` が見える形で表示される（仕様で許容済み）。そのため
  SC-003「画面との 100% 一致」は、FR-012 の対象となる値に限り `'` が付くことを除外条件として扱う。

## R-6. 検索条件の引き継ぎ（FR-004, FR-005）

- **Decision**: 一覧画面の「CSVダウンロード」ボタンを `<a>` リンクとし、
  `th:href="@{/equipment/csv(name=${name},status=${status})}"` で**現在一覧に適用されている**検索条件
  （＝直前の検索で送信された値）をクエリパラメータとして付与する。CSV 側は既存の
  `EquipmentService.search(name, status)` を呼び、抽出条件と並び順（ID 昇順）を一覧画面と完全に共有する。
- **Rationale**:
  - 画面に表示されている結果と同じ条件をサーバー側で再計算するため、JavaScript なしで一覧と CSV の
    一致（SC-003）が保証できる。
  - 検索フォームの入力欄を書き換えただけで「検索」を押していない状態では、画面の一覧は変わっていない
    ため、リンクに埋め込む条件もモデルの値（適用済み条件）とするのが「画面に表示されている備品」と一致する。
  - `name` / `status` が null の場合 Thymeleaf はパラメータを値なし（または空）で出力するが、
    コントローラーは空文字を「条件なし」として扱うため問題ない（既存 `list` と同じ扱い）。
- **Alternatives considered**:
  - 検索フォーム内に 2 つ目の送信ボタン（`formaction="/equipment/csv"`）を置く：未検索の入力値で
    出力されてしまい、画面の一覧と CSV が食い違う可能性があるため不採用。
  - セッションに直前の検索条件を保存：状態を持つ分だけ複雑になり、複数タブで食い違う。

## R-7. 不正な検索条件の扱い（FR-017）

- **Decision**: `status` パラメータの文字列 → `EquipmentStatus` への変換を、CSV 用エンドポイントでは
  変換失敗時に `InvalidSearchConditionException`（`service` パッケージ、新規）を送出する形で行い、
  `EquipmentController` の `@ExceptionHandler` で捕捉して `redirect:/equipment`（検索条件なし）＋
  フラッシュ属性 `error`「検索条件が不正です。条件を指定し直してください。」とする。
  変換処理は `EquipmentService.parseStatusFilter(String)` に置く。
- **Rationale**:
  - 既存の一覧画面は `EquipmentStatus.valueOf` の例外がそのまま 500 になるが、CSV では仕様上
    一覧画面へ戻してメッセージを出す必要がある。リダイレクト先には不正な条件を付けないため、
    リダイレクトのループは起きない。
  - 既存の `handleEquipmentError` と同じ「フラッシュ属性 `error` ＋一覧へリダイレクト」の流儀に揃える。
    一覧テンプレートは既に `${error}` を表示する領域を持つ。
- **Notes**: 一覧画面（`GET /equipment`）の不正 `status` による 500 は既存の挙動であり、本機能のスコープ外。
  ただし `parseStatusFilter` を共有すれば同じ扱いにできるため、必要なら別機能として対応する。

## R-8. 想定外エラー時の扱い（FR-016）

- **Decision**: CSV 組み立て中に想定外の `RuntimeException` が発生した場合、`EquipmentCsvExportService` が
  捕捉して `CsvExportException`（`service` パッケージ、新規）にラップして送出する。
  `EquipmentController` の `@ExceptionHandler(CsvExportException.class)` が SLF4J で ERROR ログ
  （原因例外のスタックトレース付き）を出力し、`redirect:/equipment` ＋フラッシュ属性 `error`
  「CSVファイルの出力中にエラーが発生しました。時間をおいて再度お試しください。」とする。
- **Rationale**: 全ての `RuntimeException` をコントローラーで握りつぶすと他の画面処理の異常まで
  隠れてしまうため、CSV 出力に限定した例外型で範囲を絞る。R-2 のとおりレスポンス送出前に CSV が
  完成しているため、リダイレクトが確実に機能する。スタックトレースはログにのみ出し、画面には出さない
  （憲章 VI）。

## R-9. 借用者名の取得と N+1

- **Decision**: 既存の `LendingService.findActiveLendingsByEquipmentId()`（有効な貸出記録を 1 クエリで取得し
  備品 ID → 貸出記録のマップにする）を再利用する。状態が `LENT` の備品に限り、マップから
  `lending.getEmployee().getName()` を取得する。マップに該当がない（データ不整合）場合は空欄とする。
  エクスポートサービスのメソッドは `@Transactional(readOnly = true)` とし、LAZY の `employee` を
  トランザクション内で解決する。
- **Rationale**: 一覧画面と同じ取得経路を使うことで表示内容の一致を保証する。数十件規模なので
  社員の LAZY ロードによる追加クエリは問題にならない。`readOnly` トランザクションにより
  Open Session in View の設定に依存しない。

## R-10. ファイル名の日時とテスト容易性（FR-013）

- **Decision**: ファイル名は `EquipmentCsvExportService.fileName(LocalDateTime)` で
  `equipment_` + `yyyyMMdd_HHmmss` + `.csv` を組み立てる純粋関数とし、コントローラーが
  `LocalDateTime.now()` を渡す。結合テストでは `Content-Disposition` を正規表現
  `attachment; filename="equipment_\d{8}_\d{6}\.csv"` で検証し、単体テストでは固定日時で完全一致を検証する。
- **Rationale**: `Clock` Bean の導入より変更が小さく、日時整形の正しさは単体テストで担保できる。
- **Notes**: 日時はサーバーのデフォルトタイムゾーン（WSL のローカル時刻）に従う。

## R-11. 認可（FR-002, FR-014）

- **Decision**: `SecurityConfig` は変更しない。`/equipment/csv` への GET は既存の `anyRequest().authenticated()`
  に該当し、ADMIN・USER の両方が利用でき、未ログイン時はフォームログインの仕組みで `/login` へ
  リダイレクトされる。
- **Rationale**: 閲覧操作であり一覧画面と同じ権限でよい（仕様 Assumptions）。ADMIN 専用ルールの
  パターン（`/equipment/new`, `/equipment/*/edit` など）とは衝突しない。
  パスは `/equipment/{id}` と重なるが、Spring MVC はリテラルパス `/equipment/csv` を優先するため問題ない。
- **Tests**: 憲章 III に従い、USER でダウンロードできること・未ログイン時に `/login` へリダイレクトされる
  ことを MockMvc で検証する。
