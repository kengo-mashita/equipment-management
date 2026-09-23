# CLAUDE.md

## プロジェクト概要

備品・機材管理アプリ。

詳細な要件は `equipment-management-spec.md`（要件定義書）を参照すること。

---

## 技術スタック

| レイヤー | 技術 |
|---|---|
| 言語 | Java 21（LTS） |
| フレームワーク | Spring Boot 4.1.1系 |
| ビルドツール | Maven |
| 認証 | Spring Security（フォームログイン、ROLE_ADMIN / ROLE_USER） |
| テンプレートエンジン | Thymeleaf（サーバーサイドレンダリング、JS不使用） |
| ORM | Spring Data JPA |
| DB | H2 Database（ファイルモード、組み込み・Docker不使用） |
| バリデーション | Spring Validation（`@NotBlank`等） |

- Group: `jp.co.example`
- Artifact / Package: `jp.co.example.equipmentmanagement`
- フロントエンドはJavaScriptを使用しない。画面遷移はすべて通常のHTTPリクエスト（GET/POST、`<form>`送信）で行う。Ajax・SPA的な実装は禁止。

---

## 起動コマンド

開発環境はWSL（Docker不使用）。

```bash
# アプリ起動（開発時）
./mvnw spring-boot:run

# ビルド
./mvnw clean package

# テスト実行（作成した場合）
./mvnw test
```

- 起動後、ブラウザで `http://localhost:8080` にアクセス
- H2のDBファイルはプロジェクト内に生成される（追加インストール不要）
- 初期ユーザー：`admin` / `admin123`（管理者）、`user` / `user123`（一般ユーザー）

---

## コーディング規約

### アーキテクチャ・パッケージ構成
- レイヤードアーキテクチャを採用し、以下のパッケージ構成に従う。
  ```
  com.example.equipmentmanagement
  ├── controller   # @Controller、画面遷移・フォーム処理
  ├── service      # ビジネスロジック（貸出可否判定、状態遷移など）
  ├── repository   # Spring Data JPAリポジトリ
  ├── entity       # JPAエンティティ（Equipment, Lending, Userなど）
  ├── dto          # フォーム入力・表示用のデータクラス（必要な場合）
  └── config       # Spring Security設定、初期データ投入設定など
  ```
- Controllerに業務ロジックを書かない。状態遷移や貸出可否のチェックはServiceに実装する。

### 命名規則
- クラス名：`PascalCase`（例：`EquipmentController`, `LendingService`）
- メソッド名・変数名：`camelCase`
- Thymeleafテンプレートファイル：`snake_case`または画面に対応する分かりやすい名前（例：`equipment_list.html`）
- URLパス：ケバブケース、リソース名は複数形（例：`/equipment`, `/lendings`）

### 実装方針（業務アプリとしての品質を意識する）
- 単体テスト・結合テストを作成する。特にサービス層の業務ロジック（貸出可否判定、状態遷移、重複チェックなど）はテストで担保する
- レイヤー間の責務分離を意識する（Controllerは入出力の変換、Serviceは業務ロジック、Repositoryはデータアクセスに専念させる）
- 例外処理は適切に行う。想定される異常系（存在しないIDへのアクセス、権限のない操作、バリデーションエラーなど）はハンドリングし、ユーザーに分かりやすいエラー表示を行う
- 業務ルールが複雑な箇所（貸出可否判定、状態遷移まわりなど）には、意図が伝わるようコメントを付与する
- ログ出力（起動時の初期データ投入状況、想定外エラーの発生など）を適切に行う

### バリデーション・エラー表示
- 入力チェックは`@Valid` + Bean Validationアノテーションで行う
- バリデーションエラーはThymeleafの`th:errors`を用いて、利用者が原因を理解できるメッセージで画面に表示する

### Git運用
- コミット単位は機能ごとで問題ない

---

## spec-kit試行の運用ルール

- `feature/sdd-spec-kit` はspec-kit試行のベースブランチ（タグ `spec-kit-base`）。直接コミットしない。
- 試行は必ずベースから新しいブランチ `try/<テーマ>-<連番>`（例：`try/csv-export-01`）を切って行う。
- spec-kit 1.0.10 の `/speckit-specify` はgitブランチを自動作成しないため、実行前に上記ブランチへ切り替えておく。
- `/speckit-constitution` → `/speckit-specify` → `/speckit-plan` → `/speckit-tasks` → `/speckit-implement` の各ステップ完了ごとにコミットする。
- 最初からやり直す場合は、既存の試行ブランチを修正せず、ベースから新しい試行ブランチを切る。
