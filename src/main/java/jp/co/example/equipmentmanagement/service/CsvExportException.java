package jp.co.example.equipmentmanagement.service;

/**
 * CSV組み立て中に想定外のエラーが発生した場合の例外（FR-016）。原因例外はログ出力のために保持する。
 */
public class CsvExportException extends RuntimeException {

    public CsvExportException(Throwable cause) {
        super("CSVファイルの出力中にエラーが発生しました。時間をおいて再度お試しください。", cause);
    }
}
