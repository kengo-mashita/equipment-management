# 備品・機材管理アプリ

社内の備品・機材を管理するための土台アプリケーション。ログイン機能、備品のCRUD、検索、貸出管理機能を備える。

Spec-Driven Development（SDD）のデモンストレーション用ベースアプリとして作成されたもので、詳細な要件は [`equipment-management-spec.md`](./equipment-management-spec.md) を参照。

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

フロントエンドはJavaScriptを使用せず、画面遷移はすべて通常のHTTPリクエスト（`<form>`のGET/POST送信）で行う。

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

サンプルの備品データ・貸出履歴もあわせて投入される。

## 機能

### 認証
- フォームベースのセッション認証
- `ROLE_ADMIN`：備品の一覧閲覧・検索・登録・編集・削除がすべて可能
- `ROLE_USER`：一覧閲覧・検索・貸出・返却のみ可能（登録・編集・削除は不可）

### 備品管理（CRUD）
- 一覧表示・検索（品名部分一致、状態プルダウン）
- 新規登録・編集（ADMINのみ）：管理番号の重複チェック、必須項目バリデーション
- 削除（ADMINのみ）：貸出履歴が存在する備品は参照整合性のため削除不可
- 詳細画面：登録日時・更新日時・（貸出中の場合）借用者名を表示

### 貸出管理
- 「利用可」の備品のみ貸出登録が可能（ADMIN/USER共通）
- 貸出登録すると備品は自動的に「貸出中」になり、返却登録で「利用可」に戻る
- 「貸出中」への状態変更は貸出操作を通じてのみ行われ、編集画面から直接指定することはできない
- 貸出履歴画面（`/lendings`）で全ての貸出・返却記録を確認できる

## パッケージ構成

```
jp.co.example.equipmentmanagement
├── controller   # 画面遷移・フォーム処理
├── service      # 業務ロジック（貸出可否判定、状態遷移、重複チェックなど）
├── repository   # Spring Data JPAリポジトリ
├── entity       # JPAエンティティ（Equipment, Lending, User）
├── dto          # フォーム入力用データクラス
└── config       # Spring Security設定、初期データ投入
```

## テスト

Service層の業務ロジック（貸出可否判定・状態遷移・重複チェック）を中心とした単体テストと、MockMvcによるController層の結合テストを用意している。結合テストは開発用DBファイルを汚さないよう、インメモリH2（`src/test/resources/application.properties`）で実行される。

```bash
./mvnw test
```

## スコープ外

CSVエクスポート機能はSDDデモ本編で別途仕様化するため、本リポジトリのスコープには含まれない（詳細は要件定義書7章を参照）。
