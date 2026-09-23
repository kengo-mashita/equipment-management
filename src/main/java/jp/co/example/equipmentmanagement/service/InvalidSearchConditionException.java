package jp.co.example.equipmentmanagement.service;

/**
 * 一覧・CSV出力の検索条件（状態）が定義済みの値に一致しない場合の例外（FR-017）。
 */
public class InvalidSearchConditionException extends RuntimeException {

    public InvalidSearchConditionException() {
        super("検索条件が不正です。条件を指定し直してください。");
    }
}
