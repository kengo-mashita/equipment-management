# Quickstart: 備品一覧のCSVエクスポートの動作確認

**Feature**: [spec.md](./spec.md) | **Contract**: [contracts/equipment-csv-download.md](./contracts/equipment-csv-download.md)

実装完了後、以下の手順で機能が仕様どおりに動くことを確認する。形式の詳細はコントラクトと
[data-model.md](./data-model.md) を参照し、ここでは確認手順と期待結果だけを示す。

---

## 前提

- WSL 上で Java 21 が使えること（Docker 不要）
- 作業ブランチ：`try/csv-import-01`

## 1. 自動テスト

```bash
./mvnw test
```

**期待結果**：すべて成功（`BUILD SUCCESS`）。少なくとも以下の観点のテストが含まれていること。

| 観点 | 対応する要件 |
|---|---|
| エスケープ（カンマ・ダブルクォート・改行）と数式先頭文字の無害化 | FR-011, FR-012 |
| 列見出し・列順・状態の日本語表記・購入日形式・空欄 | FR-006〜FR-008 |
| 借用者は貸出中の備品のみ | FR-009 |
| 検索条件での絞り込み・並び順・0 件時の見出しのみ出力 | FR-004, FR-005, FR-015 |
| BOM・`Content-Type`・`Content-Disposition` のファイル名形式 | FR-010, FR-013 |
| USER でもダウンロード可能／未ログインは `/login` へ | FR-002, FR-014 |
| 不正な `status` で一覧へ戻りエラーメッセージ | FR-017 |

## 2. アプリ起動

```bash
./mvnw spring-boot:run
```

ブラウザで `http://localhost:8080` を開き、`admin` / `admin123` でログインする。

## 3. 画面での確認シナリオ

### S1. 全件ダウンロード（US1）

1. 備品一覧画面で検索条件なしのまま「CSVダウンロード」を押す。
2. **期待**：画面は一覧のまま、`equipment_yyyyMMdd_HHmmss.csv` が保存される。
3. Excel でダブルクリックして開く。
4. **期待**：日本語が文字化けせず、1 行目が `品名 / 管理番号 / 保管場所 / 状態 / 購入日 / 借用者`。
   行数・順序・値が画面の一覧と一致し、貸出中の行だけ借用者名が入っている。

### S2. 一般ユーザーでのダウンロード（US1）

1. ログアウトし `user` / `user123` でログインする。
2. 「CSVダウンロード」ボタンが表示されていることを確認して押す。
3. **期待**：S1 と同じ内容の CSV が保存される。

### S3. 絞り込み結果のダウンロード（US2）

1. 状態＝「故障中」で検索し、続けて「CSVダウンロード」を押す。 **期待**：故障中の備品だけが出力される。
2. 品名に「PC」を入れて検索し、ダウンロード。 **期待**：品名に「PC」を含む備品だけ。
3. 品名と状態の両方を指定して検索し、ダウンロード。 **期待**：両方を満たす備品だけ。
4. 一致しない品名（例：`存在しない品名`）で検索し、ダウンロード。 **期待**：列見出し行のみ。

### S4. 特殊文字を含む備品（Edge Cases）

1. 管理者で以下の備品を登録する。
   - 品名 `プロジェクター, 4K`、保管場所 `倉庫"B"`
   - 品名 `=SUM(A1)`
   - 品名 `-20℃用冷蔵庫`
2. 全件で CSV をダウンロードし Excel で開く。
3. **期待**：列ずれがなく、`プロジェクター, 4K` と `倉庫"B"` がそれぞれ 1 セルに表示される。
   `=SUM(A1)` は数式として計算されず `'=SUM(A1)` と文字列で表示され、`-20℃用冷蔵庫` も `'-20℃用冷蔵庫` と表示される。

### S5. 異常系

| 操作 | 期待結果 |
|---|---|
| ログアウトした状態で `http://localhost:8080/equipment/csv` に直接アクセス | ログイン画面へリダイレクトされ、ファイルは保存されない |
| ログイン状態で `http://localhost:8080/equipment/csv?status=UNKNOWN` にアクセス | 一覧画面に戻り「検索条件が不正です。条件を指定し直してください。」が表示される。スタックトレースは出ない |

## 4. コマンドラインでの補助確認（任意）

ブラウザでログイン後、開発者ツールから `JSESSIONID` Cookie の値を取得して実行する。

```bash
curl -s -D - -o equipment.csv -b "JSESSIONID=<値>" "http://localhost:8080/equipment/csv?status=BROKEN"
head -c 3 equipment.csv | xxd   # → efbb bf（BOM）
file equipment.csv              # → UTF-8 (with BOM) text, with CRLF line terminators
```

**期待**：レスポンスヘッダーに `Content-Type: text/csv;charset=UTF-8` と
`Content-Disposition: attachment; filename="equipment_........_......csv"` が含まれる。
