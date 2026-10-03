package jp.co.example.equipmentmanagement.service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import jp.co.example.equipmentmanagement.entity.Equipment;
import jp.co.example.equipmentmanagement.entity.EquipmentStatus;
import jp.co.example.equipmentmanagement.entity.Lending;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 備品一覧をCSVとして出力する。
 *
 * <ul>
 * <li>対象・並び順は一覧画面と同じ {@link EquipmentService#search} の結果をそのまま使う（画面とCSVの一致を担保）</li>
 * <li>列は一覧画面の表示項目に揃える（品名・管理番号・保管場所・状態・購入日・借用者）</li>
 * <li>日本語版Excelでダブルクリックして文字化けしないよう、BOM付きUTF-8・CRLFで出力する</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EquipmentCsvExportService {

    private static final String HEADER = "品名,管理番号,保管場所,状態,購入日,借用者";
    private static final String LINE_SEPARATOR = "\r\n";
    private static final byte[] UTF8_BOM = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
    private static final DateTimeFormatter FILE_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final EquipmentService equipmentService;
    private final LendingService lendingService;

    /**
     * 検索条件（一覧画面と同じ）で絞り込んだ備品をCSVのバイト列にする。該当0件なら見出し行のみ。
     */
    public byte[] export(String name, EquipmentStatus status) {
        List<Equipment> equipmentList = equipmentService.search(name, status);
        Map<Long, Lending> activeLendings = lendingService.findActiveLendingsByEquipmentId();

        StringBuilder csv = new StringBuilder();
        csv.append(HEADER).append(LINE_SEPARATOR);
        for (Equipment equipment : equipmentList) {
            csv.append(String.join(",",
                    escape(equipment.getName()),
                    escape(equipment.getAssetNumber()),
                    escape(equipment.getLocation()),
                    escape(equipment.getStatus().getLabel()),
                    escape(equipment.getPurchaseDate() == null ? null : equipment.getPurchaseDate().toString()),
                    escape(borrowerName(equipment, activeLendings))))
                    .append(LINE_SEPARATOR);
        }

        // 借用者名（個人名）はログに出さない
        log.info("備品一覧CSVを出力しました（品名={}, 状態={}, 件数={}）", name, status, equipmentList.size());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(UTF8_BOM);
        out.writeBytes(csv.toString().getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    public String buildFileName(LocalDateTime now) {
        return "equipment_" + now.format(FILE_NAME_FORMAT) + ".csv";
    }

    /**
     * 一覧画面と同じく、「貸出中」かつ有効な貸出記録がある場合のみ借用者名を返す。
     * 貸出中なのに有効な貸出記録がない（データ不整合）場合も、出力を止めず空欄にする。
     */
    private String borrowerName(Equipment equipment, Map<Long, Lending> activeLendings) {
        if (equipment.getStatus() != EquipmentStatus.LENT) {
            return null;
        }
        Lending lending = activeLendings.get(equipment.getId());
        return lending == null ? null : lending.getEmployee().getName();
    }

    /**
     * RFC 4180相当のエスケープ。カンマ・ダブルクォート・改行を含む値のみクォートで囲み、
     * 値中のダブルクォートは二重にする。null は空欄。
     * 先頭が「=」等の値の加工（CSVインジェクション対策）は、データを登録できるのがADMINのみであり、
     * 加工すると画面表示と値が一致しなくなるため行わない。
     */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\r") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
