# Research: 備品一覧のCSVエクスポート

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-10-03

Technical Context に NEEDS CLARIFICATION は残っていない。以下は設計上の判断事項を記録する。

## R1. CSV生成方法（ライブラリの要否）

- **Decision**: 外部ライブラリ（OpenCSV、Apache Commons CSV等）を追加せず、Java標準ライブラリで
  CSV文字列を組み立てる。
- **Rationale**: 出力は6列・数十件の単純な表であり、必要なのは「値のエスケープ」と「改行コード」の
  2点のみ。Constitution V（依存追加は標準機能で実現できない場合に限る）に適合する。
- **Alternatives considered**:
  - Apache Commons CSV：RFC 4180準拠を任せられるが、依存追加の正当化ができない。
  - Jackson CSV（jackson-dataformat-csv）：同上。

## R2. 文字コード・BOM・改行コード

- **Decision**: UTF-8でエンコードし、先頭にBOM（`EF BB BF`）を付与する。改行コードはCRLF。
  レスポンスの Content-Type は `text/csv; charset=UTF-8`。
- **Rationale**: Clarifications（2026-10-03）でBOM付きUTF-8に確定。BOMがあると日本語版Excelが
  UTF-8と判定して文字化けしない。CRLFはRFC 4180の規定でありExcelとも相性がよい。
- **Alternatives considered**: Shift_JIS（Windows-31J）、BOMなしUTF-8 — clarifyで不採用。

## R3. エスケープ規則（RFC 4180相当）

- **Decision**: 値にカンマ `,`・ダブルクォート `"`・CR・LF のいずれかを含む場合のみ、値全体を
  ダブルクォートで囲み、値中の `"` は `""` に置換する。それ以外はそのまま出力する。null は空文字。
- **Rationale**: FR-010 を満たす最小限の規則。不要なクォートを付けないことでファイルが読みやすい。
- **Alternatives considered**: 全フィールドを常にクォート — 実装はさらに単純だが、
  テストで期待値が読みにくくなるだけで利点が少ない。

## R4. CSVインジェクション（数式インジェクション）対策

- **Decision**: 対策（先頭が `=` `+` `-` `@` の値にアポストロフィ等を付与する加工）は行わない。
- **Rationale**: 備品データを登録・編集できるのはADMINのみ（信頼された利用者）であり、
  利用者は開発者本人を想定（要件定義書2章）。加工すると画面表示とCSVの値が一致しなくなり
  SC-003（画面との100%一致）に反する。
- **Alternatives considered**: 先頭記号のエスケープ — 将来、外部入力のデータを扱うようになった場合に再検討する。

## R5. 「現在表示中の一覧」と同じ条件の受け渡し方法（JS不使用）

- **Decision**: 一覧画面の「CSVダウンロード」は `<a>` リンクとし、URLのクエリパラメータに
  **サーバー側で一覧を描画したときの検索条件**（Modelの `name`・`status`）を埋め込む
  （例：`/equipment/csv?name=PC&status=BROKEN`）。
- **Rationale**: Clarifications（2026-10-03）で「最後に検索を実行した条件で出力し、未実行の入力は無視する」
  に確定。検索フォーム内の送信ボタンにすると未実行の入力値が送信されてしまうが、
  描画時の値をリンクに埋め込めばJSなしで要件を満たせる（Constitution I）。
- **Alternatives considered**:
  - 検索フォーム内に `formaction` 付きの2つ目の送信ボタンを置く — 未実行の入力値が送られ、clarifyの決定に反する。
  - セッションに直近の検索条件を保存する — 状態管理が増え、複数タブで食い違う。

## R6. 検索ロジックの再利用と並び順

- **Decision**: 既存の `EquipmentService.search(name, status)` をそのまま再利用し、借用者名は
  既存の `LendingService.findActiveLendingsByEquipmentId()` から取得する。
- **Rationale**: 一覧画面と同じメソッドを使うことで、絞り込み結果・並び順（ID昇順）が構造的に一致する（FR-004）。

## R7. 不正な検索条件（存在しない状態値）の扱い

- **Decision**: `status` に列挙値以外が指定された場合、一覧・CSVの両方で、一覧画面へリダイレクトし
  「検索条件が不正です」とエラーメッセージを表示する。状態文字列の解釈は1か所（Controller内の
  共通メソッド）にまとめ、`IllegalArgumentException` を `@ExceptionHandler` で処理する。
- **Rationale**: spec の Edge Cases「一覧画面と同じ扱いとし、システムエラー画面を表示しない」。
  現状の一覧画面は `EquipmentStatus.valueOf` の例外がそのままエラー画面になるため、
  一覧側も同じハンドリングに揃える（Constitution の品質基準「想定される異常系をハンドリング」）。
- **Alternatives considered**: 不正値を無視して全件扱い — 利用者が誤った条件に気付けない。

## R8. ファイル名とダウンロード方法

- **Decision**: `Content-Disposition: attachment; filename="equipment_yyyyMMdd_HHmmss.csv"`。
  日時はサーバーのローカル時刻（ダウンロード時点）。ASCIIのみのためRFC 5987形式は不要。
- **Rationale**: FR-011。`attachment` 指定によりブラウザは画面遷移せず保存のみ行う（Edge Cases）。

## R9. 責務の配置

- **Decision**: CSVの組み立て（列定義・エスケープ・BOM付きバイト列化・ファイル名生成）は
  新規 `EquipmentCsvExportService`（service パッケージ）に実装する。Controller は検索条件の受け取りと
  HTTPレスポンス（ヘッダー設定）のみを担う。
- **Rationale**: Constitution II（Controllerに業務ロジックを書かない）と III（Service層を単体テスト）。
  既存 `EquipmentService` に混ぜると「CRUD」と「出力形式」の関心が混在するため分離する。

## R10. アクセス制御

- **Decision**: `SecurityConfig` は変更しない。`GET /equipment/csv` は既存の `anyRequest().authenticated()`
  により ADMIN・USER とも許可され、未ログイン時はログイン画面へリダイレクトされる。
- **Rationale**: FR-002、FR-012。ADMIN限定ルールはGETの `/equipment/new` `/equipment/*/edit` のみで
  `/equipment/csv` とは一致しない。ロール別のアクセスはMockMvcテストで検証する（Constitution IV）。

## R11. ログ

- **Decision**: エクスポート実行時に INFO ログで「検索条件と出力件数」を1行出力する。
- **Rationale**: 運用時の問い合わせ（「出力件数が画面と違う」等）の調査に使える。個人名は出力しない。
