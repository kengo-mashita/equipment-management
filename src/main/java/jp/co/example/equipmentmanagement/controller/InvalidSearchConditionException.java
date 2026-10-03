package jp.co.example.equipmentmanagement.controller;

/**
 * 一覧・CSVの検索条件（状態）に列挙値以外が指定された場合の例外。
 * URLの直接編集などで発生し得るため、システムエラーではなく一覧画面のエラー表示として扱う。
 */
public class InvalidSearchConditionException extends RuntimeException {

    public InvalidSearchConditionException(String status, Throwable cause) {
        super("検索条件が不正です（状態=" + status + "）", cause);
    }
}
