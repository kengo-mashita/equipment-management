package jp.co.example.equipmentmanagement.service;

/**
 * 返却対象の有効な貸出記録（returnedAtがnull）が存在しない場合の例外。
 * 通常は発生しないが、データ不整合時の防御として扱う。
 */
public class NoActiveLendingException extends RuntimeException {

    public NoActiveLendingException(Long equipmentId) {
        super("有効な貸出記録が見つかりません（equipmentId=" + equipmentId + "）");
    }
}
