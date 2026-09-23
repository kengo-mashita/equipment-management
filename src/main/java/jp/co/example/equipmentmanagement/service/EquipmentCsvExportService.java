package jp.co.example.equipmentmanagement.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jp.co.example.equipmentmanagement.dto.EquipmentCsvRow;
import jp.co.example.equipmentmanagement.entity.Equipment;
import jp.co.example.equipmentmanagement.entity.EquipmentStatus;
import jp.co.example.equipmentmanagement.entity.Lending;
import lombok.RequiredArgsConstructor;

/**
 * 備品一覧のCSVエクスポート。一覧画面と同じ抽出条件・並び順で備品を取得し、
 * Excel でそのまま開ける形式（BOM付きUTF-8、CRLF、日本語列見出し）のバイト列を組み立てる。
 */
@Service
@RequiredArgsConstructor
public class EquipmentCsvExportService {

    private static final Logger log = LoggerFactory.getLogger(EquipmentCsvExportService.class);

    /** Excel に UTF-8 と認識させるための BOM（FR-010） */
    private static final String BOM = "﻿";

    private static final List<String> HEADERS = List.of("品名", "管理番号", "保管場所", "状態", "購入日", "借用者");

    /**
     * 無害化（FR-012）の対象列：利用者が自由入力する品名・管理番号・保管場所・借用者。
     * 状態（固定ラベル）・購入日（日付書式）は先頭が数式文字になり得ないため対象外。
     */
    private static final Set<Integer> SANITIZE_COLUMNS = Set.of(0, 1, 2, 5);

    private static final DateTimeFormatter PURCHASE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FILE_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final EquipmentService equipmentService;
    private final LendingService lendingService;
    private final CsvFormatter csvFormatter;

    /**
     * 検索条件に一致する備品のCSVを生成する。
     * レスポンス送出前にCSV全体をメモリ上で完成させるため、生成中に例外が起きても
     * 呼び出し側は一覧画面へのリダイレクトに切り替えられる（R-2）。
     * 想定外の例外は CsvExportException にラップして送出する（FR-016）。
     *
     * @param name   品名（部分一致、null/空は条件なし）
     * @param status 状態（完全一致、null は条件なし）
     */
    @Transactional(readOnly = true)
    public byte[] export(String name, EquipmentStatus status) {
        try {
            List<Equipment> equipmentList = equipmentService.search(name, status);
            Map<Long, Lending> activeLendings = lendingService.findActiveLendingsByEquipmentId();

            StringBuilder csv = new StringBuilder(BOM);
            csv.append(csvFormatter.formatRow(HEADERS, Set.of()));
            for (Equipment equipment : equipmentList) {
                csv.append(csvFormatter.formatRow(toValues(toRow(equipment, activeLendings)), SANITIZE_COLUMNS));
            }

            log.info("備品一覧CSVを生成しました（品名条件={}, 状態条件={}, 件数={}）", name, status, equipmentList.size());
            return csv.toString().getBytes(StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            throw new CsvExportException(e);
        }
    }

    /** ダウンロードファイル名 {@code equipment_yyyyMMdd_HHmmss.csv} を返す（FR-013）。 */
    public String fileName(LocalDateTime dateTime) {
        return "equipment_" + dateTime.format(FILE_NAME_FORMAT) + ".csv";
    }

    /**
     * 借用者は備品の状態が「貸出中（LENT）」の場合に限り、有効な貸出記録から取得する（FR-009）。
     * 状態を正とするため、LENT 以外の備品に有効な貸出記録が残っていても出力しない。
     * LENT なのに有効な貸出記録がない（データ不整合）場合は空欄とする。
     */
    private EquipmentCsvRow toRow(Equipment equipment, Map<Long, Lending> activeLendings) {
        String borrowerName = null;
        if (equipment.getStatus() == EquipmentStatus.LENT) {
            Lending lending = activeLendings.get(equipment.getId());
            if (lending != null) {
                borrowerName = lending.getEmployee().getName();
            }
        }
        return new EquipmentCsvRow(
                equipment.getName(),
                equipment.getAssetNumber(),
                equipment.getLocation(),
                equipment.getStatus().getLabel(),
                equipment.getPurchaseDate() == null ? null : equipment.getPurchaseDate().format(PURCHASE_DATE_FORMAT),
                borrowerName);
    }

    /** 列見出し（HEADERS）と同じ列順の値リストに変換する */
    private List<String> toValues(EquipmentCsvRow row) {
        return Arrays.asList(row.name(), row.assetNumber(), row.location(), row.statusLabel(),
                row.purchaseDate(), row.borrowerName());
    }
}
