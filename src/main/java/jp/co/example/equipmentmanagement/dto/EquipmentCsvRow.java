package jp.co.example.equipmentmanagement.dto;

/**
 * 備品一覧CSVの1行分。値はCSVインジェクション対策（無害化）・エスケープ前の生の表示値を保持し、
 * 整形はCsvFormatterが行う。
 *
 * @param name         品名（null不可）
 * @param assetNumber  管理番号（null不可）
 * @param location     保管場所（null可、空欄で出力）
 * @param statusLabel  状態の日本語表記（「利用可」「貸出中」「故障中」、null不可）
 * @param purchaseDate 購入日（yyyy-MM-dd に整形済み、null可、空欄で出力）
 * @param borrowerName 借用者の氏名（貸出中の備品のみ、それ以外はnull）
 */
public record EquipmentCsvRow(
        String name,
        String assetNumber,
        String location,
        String statusLabel,
        String purchaseDate,
        String borrowerName) {
}
