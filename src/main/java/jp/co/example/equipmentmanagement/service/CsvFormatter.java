package jp.co.example.equipmentmanagement.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;

/**
 * CSVのフィールド・行を整形する。RFC 4180 のエスケープ（FR-011）と、
 * 表計算ソフトで数式として実行されるのを防ぐ CSVインジェクション対策（FR-012）を担う。
 */
@Component
public class CsvFormatter {

    static final String DELIMITER = ",";
    static final String LINE_SEPARATOR = "\r\n";

    /**
     * 先頭にあると表計算ソフトが数式として解釈しうる文字。
     * OWASP はタブ・CR 始まりも挙げているが、仕様（FR-012）の定める 4 文字に限定している。
     */
    private static final String FORMULA_PREFIXES = "=+-@";

    /**
     * 1フィールドを整形する。適用順は「null→空文字」「無害化」「エスケープ」。
     * 無害化を先に行うことで、例えば {@code =A,B} は {@code "'=A,B"} となり、
     * 付与した {@code '} がクォートの内側に入る。
     *
     * @param value    生の値（null可）
     * @param sanitize 無害化対象の列（利用者が自由入力する文字列項目）なら true
     */
    public String formatField(String value, boolean sanitize) {
        if (value == null) {
            return "";
        }
        String result = value;
        if (sanitize && !result.isEmpty() && FORMULA_PREFIXES.indexOf(result.charAt(0)) >= 0) {
            result = "'" + result;
        }
        if (needsQuoting(result)) {
            result = "\"" + result.replace("\"", "\"\"") + "\"";
        }
        return result;
    }

    /**
     * 1行分の値をカンマ区切りで連結し、末尾に CRLF を付ける。
     *
     * @param values          列順の値（null要素可）
     * @param sanitizeColumns 無害化を適用する列の 0 始まりインデックス
     */
    public String formatRow(List<String> values, Set<Integer> sanitizeColumns) {
        return IntStream.range(0, values.size())
                .mapToObj(i -> formatField(values.get(i), sanitizeColumns.contains(i)))
                .collect(Collectors.joining(DELIMITER, "", LINE_SEPARATOR));
    }

    private boolean needsQuoting(String value) {
        return value.contains(",") || value.contains("\"") || value.contains("\r") || value.contains("\n");
    }
}
