package jp.co.example.equipmentmanagement.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * CSVのフィールド整形（FR-011 エスケープ、FR-012 CSVインジェクション対策）の単体テスト。
 * 期待値は data-model.md「フィールド整形ルール」の表に対応する。
 */
class CsvFormatterTest {

    private final CsvFormatter formatter = new CsvFormatter();

    @Test
    void formatField_特殊文字を含まない値はそのまま出力される() {
        assertThat(formatter.formatField("ノートPC", true)).isEqualTo("ノートPC");
    }

    @Test
    void formatField_nullは空文字になる() {
        assertThat(formatter.formatField(null, true)).isEmpty();
        assertThat(formatter.formatField(null, false)).isEmpty();
    }

    @Test
    void formatField_カンマを含む値はダブルクォートで囲まれる() {
        assertThat(formatter.formatField("PC, 15インチ", true)).isEqualTo("\"PC, 15インチ\"");
    }

    @Test
    void formatField_ダブルクォートは二重化され値全体が囲まれる() {
        assertThat(formatter.formatField("19\"モニター", true)).isEqualTo("\"19\"\"モニター\"");
    }

    @Test
    void formatField_改行を含む値はダブルクォートで囲まれる() {
        assertThat(formatter.formatField("1行目\n2行目", true)).isEqualTo("\"1行目\n2行目\"");
        assertThat(formatter.formatField("1行目\r2行目", true)).isEqualTo("\"1行目\r2行目\"");
        assertThat(formatter.formatField("1行目\r\n2行目", true)).isEqualTo("\"1行目\r\n2行目\"");
    }

    @Test
    void formatField_数式として解釈されうる先頭文字には無害化のためシングルクォートが付く() {
        assertThat(formatter.formatField("=SUM(A1)", true)).isEqualTo("'=SUM(A1)");
        assertThat(formatter.formatField("+1", true)).isEqualTo("'+1");
        assertThat(formatter.formatField("@foo", true)).isEqualTo("'@foo");
        assertThat(formatter.formatField("-20℃用冷蔵庫", true)).isEqualTo("'-20℃用冷蔵庫");
    }

    @Test
    void formatField_無害化の後にエスケープが行われる() {
        assertThat(formatter.formatField("=A,B", true)).isEqualTo("\"'=A,B\"");
    }

    @Test
    void formatField_先頭以外の記号は無害化の対象にならない() {
        assertThat(formatter.formatField("PC-001", true)).isEqualTo("PC-001");
    }

    @Test
    void formatField_無害化対象外の列では先頭文字にかかわらずシングルクォートを付けない() {
        assertThat(formatter.formatField("-20℃用冷蔵庫", false)).isEqualTo("-20℃用冷蔵庫");
        assertThat(formatter.formatField("=A,B", false)).isEqualTo("\"=A,B\"");
    }

    @Test
    void formatRow_カンマ区切りで連結し末尾にCRLFを付ける() {
        List<String> values = Arrays.asList("ノートPC", "EQ-0001", null, "利用可", "2024-04-01", null);

        assertThat(formatter.formatRow(values, Set.of(0, 1, 2, 5)))
                .isEqualTo("ノートPC,EQ-0001,,利用可,2024-04-01,\r\n");
    }

    @Test
    void formatRow_無害化は指定した列にのみ適用される() {
        List<String> values = List.of("-品名", "-状態");

        assertThat(formatter.formatRow(values, Set.of(0))).isEqualTo("'-品名,-状態\r\n");
    }
}
