# Contract: 備品一覧CSVダウンロード

**Feature**: [../spec.md](../spec.md) | **Data model**: [../data-model.md](../data-model.md)

## 画面（UI）契約：備品一覧画面 `/equipment`

- 「備品リスト」カードのヘッダー（件数表示の横）に「CSVダウンロード」リンク（ボタン風の `<a>`）を表示する。
- ADMIN・USER の両方に表示する（`sec:authorize` による出し分けはしない）。
- リンク先は、**その一覧を描画したときの検索条件**をクエリに含めた `/equipment/csv` とする。
  - 例：一覧を `/equipment?name=PC&status=BROKEN` で表示 → リンクは `/equipment/csv?name=PC&status=BROKEN`
  - 検索欄を書き換えただけ（未検索）の値はリンクに反映されない。
- 0件表示の場合もリンクを表示する（見出し行のみのCSVが得られる）。
- JavaScript は使用しない。

## HTTP契約：`GET /equipment/csv`

### リクエスト

| パラメータ | 必須 | 値 | 意味 |
|---|---|---|---|
| `name` | 任意 | 文字列 | 品名の部分一致。空・未指定なら条件なし |
| `status` | 任意 | `AVAILABLE` / `LENT` / `BROKEN` | 状態の完全一致。空・未指定なら条件なし |

一覧画面 `GET /equipment` と同じパラメータ仕様。

### レスポンス

| ケース | ステータス | 内容 |
|---|---|---|
| ログイン済み（ADMIN / USER） | 200 OK | CSVファイル（下記） |
| 未ログイン | 302 Found | `/login` へリダイレクト（Spring Security 既定動作） |
| `status` が列挙値以外 | 302 Found | `/equipment` へリダイレクト、フラッシュ属性 `error` に「検索条件が不正です」 |

200 の場合のヘッダー：

```text
Content-Type: text/csv;charset=UTF-8
Content-Disposition: attachment; filename="equipment_20261003_153000.csv"
```

ファイル名の日時部分（`yyyyMMdd_HHmmss`）はダウンロード時点のサーバーローカル時刻。

### ボディ例

（`<BOM>` はバイト列 `EF BB BF`、各行末は CRLF）

```text
<BOM>品名,管理番号,保管場所,状態,購入日,借用者
ノートPC,EQ-0001,本社3F倉庫,貸出中,2024-04-01,山田 太郎
プロジェクター,EQ-0002,,利用可,,
"ケーブル ""HDMI"", 2m",EQ-0003,本社2F,故障中,2023-10-15,
```

## 一覧画面への付随変更

- `GET /equipment` でも `status` が列挙値以外の場合は、同様に `/equipment` へリダイレクトし
  「検索条件が不正です」を表示する（現状はシステムエラー画面になるため、CSVと挙動を揃える）。
