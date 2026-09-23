# 備品・機材管理アプリ

社内の備品・機材を管理するためのアプリケーション。ログイン機能、備品のCRUD、検索、貸出管理機能、社員マスタ管理機能を備える。

詳細な要件は [`equipment-management-spec.md`](./equipment-management-spec.md) を参照。

## 技術スタック

| レイヤー | 技術 |
|---|---|
| 言語 | Java 21 |
| フレームワーク | Spring Boot 4.1.1 |
| ビルドツール | Maven |
| 認証 | Spring Security（フォームログイン、ROLE_ADMIN / ROLE_USER） |
| テンプレートエンジン | Thymeleaf（サーバーサイドレンダリング、JS不使用） |
| ORM | Spring Data JPA |
| DB | H2 Database（ファイルモード） |
| バリデーション | Spring Validation |

フロントエンドはJavaScriptを使用せず、画面遷移はすべて通常のHTTPリクエスト（`<form>`のGET/POST送信）で行う。画面スタイルは共通CSS（`src/main/resources/static/css/app.css`）で統一しており、外部CDN・Webフォントは使用しない（ローカル完結）。

### レスポンシブ対応

CSSのみ（メディアクエリ）でスマホ・タブレット幅に対応している。

| 画面幅 | 表示 |
|---|---|
| 〜768px（スマホ） | ヘッダーを2段積み（ブランド＋ユーザー／ナビ）、検索フィルター・フォーム・ボタンを縦積み全幅化。一覧テーブル（備品・貸出履歴・社員）は各行を「項目名：値」のカード型に切り替え |
| 〜1024px（タブレット） | 余白を詰め、テーブルは列を折り返さずカード内で横スクロール |
| 1025px〜（デスクトップ） | 通常のテーブル表示 |

カード型表示は、各セルの `data-label` 属性をCSSの `::before { content: attr(data-label) }` で項目名として描画し、`order` プロパティで主要項目（品名・氏名）を先頭、操作ボタンを末尾に並べ替えて実現している。

> `./mvnw spring-boot:run` 実行中にCSS等の静的ファイルだけを編集した場合、`target/classes` に反映されず古いファイルが配信されることがある。その場合は `./mvnw process-resources` を実行する。

## セットアップ・起動

```bash
# アプリ起動（開発時、コード変更を自動リロード）
./mvnw spring-boot:run

# ビルド
./mvnw clean package

# テスト実行
./mvnw test
```

起動後、ブラウザで `http://localhost:8080` にアクセスするとログイン画面が表示される。H2のDBファイルはプロジェクト内（`./data/`）に生成され、追加インストールは不要。

### 初期ユーザー

アプリ起動時に以下の2ユーザーが自動投入される（パスワードはDB上でBCryptハッシュ化して保存）。

| ユーザー名 | パスワード | ロール |
|---|---|---|
| admin | admin123 | ROLE_ADMIN |
| user | user123 | ROLE_USER |

サンプルの社員データ（4名）・備品データ・貸出履歴もあわせて投入される。

## 機能

### 認証
- フォームベースのセッション認証
- `ROLE_ADMIN`：備品・社員の一覧閲覧・検索・登録・編集・削除がすべて可能
- `ROLE_USER`：備品・社員の一覧閲覧・検索、貸出・返却のみ可能（登録・編集・削除は不可）

### 備品管理（CRUD）
- 一覧表示・検索（品名部分一致、状態プルダウン）
- 新規登録・編集（ADMINのみ）：管理番号の重複チェック、必須項目バリデーション
- 削除（ADMINのみ）：貸出履歴が存在する備品は参照整合性のため削除不可
- 詳細画面：登録日時・更新日時・（貸出中の場合）借用者名を表示

### 貸出管理
- 「利用可」の備品のみ貸出登録が可能（ADMIN/USER共通）
- 借用者は社員マスタからプルダウンで選択する（自由入力ではない）
- 貸出登録すると備品は自動的に「貸出中」になり、返却登録で「利用可」に戻る
- 「貸出中」への状態変更は貸出操作を通じてのみ行われ、編集画面から直接指定することはできない
- 貸出履歴画面（`/lendings`）で全ての貸出・返却記録を確認できる

### 社員管理（社員マスタ）
- 貸出時の借用者として選択できる社員を管理する（社員番号・氏名・部署）
- 一覧表示（`/employees`）はADMIN/USER共通、登録・編集・削除はADMINのみ
- 社員番号の重複チェック、必須項目バリデーション
- 貸出履歴が存在する社員は参照整合性のため削除不可

## 画面一覧

| 画面 | パス | アクセス可能ロール |
|---|---|---|
| ログイン | `/login` | 全員（未ログイン） |
| 備品一覧（検索・貸出・返却） | `/equipment` | ADMIN, USER |
| 備品詳細 | `/equipment/{id}` | ADMIN, USER |
| 備品登録 / 編集 | `/equipment/new`, `/equipment/{id}/edit` | ADMIN |
| 貸出履歴 | `/lendings` | ADMIN, USER |
| 社員一覧 | `/employees` | ADMIN, USER |
| 社員登録 / 編集 | `/employees/new`, `/employees/{id}/edit` | ADMIN |

## パッケージ構成

```
jp.co.example.equipmentmanagement
├── controller   # 画面遷移・フォーム処理
├── service      # 業務ロジック（貸出可否判定、状態遷移、重複チェックなど）
├── repository   # Spring Data JPAリポジトリ
├── entity       # JPAエンティティ（Equipment, Lending, Employee, User）
├── dto          # フォーム入力用データクラス
└── config       # Spring Security設定、初期データ投入
```

## テスト

Service層の業務ロジック（貸出可否判定・状態遷移・重複チェック・削除制限）を中心とした単体テストと、MockMvcによるController層の結合テスト（ロール別のアクセス制御を含む）を用意している。結合テストは開発用DBファイルを汚さないよう、インメモリH2（`src/test/resources/application.properties`）で実行される。

```bash
./mvnw test
```

## スコープ外

CSVエクスポート機能は別途仕様化するため、本リポジトリのスコープには含まれない（詳細は要件定義書7章を参照）。
