package jp.co.example.equipmentmanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jp.co.example.equipmentmanagement.entity.Employee;
import jp.co.example.equipmentmanagement.entity.Equipment;
import jp.co.example.equipmentmanagement.entity.EquipmentStatus;
import jp.co.example.equipmentmanagement.entity.Lending;

@ExtendWith(MockitoExtension.class)
class EquipmentCsvExportServiceTest {

    private static final String HEADER = "品名,管理番号,保管場所,状態,購入日,借用者";

    @Mock
    private EquipmentService equipmentService;

    @Mock
    private LendingService lendingService;

    @InjectMocks
    private EquipmentCsvExportService equipmentCsvExportService;

    @Test
    void export_先頭にBOMが付きUTF8でデコードできる() {
        givenEquipment(List.of(equipment(1L, "ノートPC", "本社3F倉庫", EquipmentStatus.AVAILABLE, null)), Map.of());

        byte[] csv = equipmentCsvExportService.export(null, null);

        assertThat(Arrays.copyOf(csv, 3)).containsExactly(0xEF, 0xBB, 0xBF);
        assertThat(body(csv)).startsWith(HEADER);
    }

    @Test
    void export_見出し行と各行が画面と同じ6列で出力されCRLFで終わる() {
        givenEquipment(List.of(
                equipment(1L, "ノートPC", "本社3F倉庫", EquipmentStatus.AVAILABLE, LocalDate.of(2023, 4, 1)),
                equipment(2L, "プロジェクター", "本社2F会議室", EquipmentStatus.BROKEN, LocalDate.of(2021, 6, 1))),
                Map.of());

        String body = body(equipmentCsvExportService.export(null, null));

        assertThat(body).isEqualTo(HEADER + "\r\n"
                + "ノートPC,EQ-0001,本社3F倉庫,利用可,2023-04-01,\r\n"
                + "プロジェクター,EQ-0002,本社2F会議室,故障中,2021-06-01,\r\n");
    }

    @Test
    void export_貸出中で有効な貸出記録がある備品は借用者名を出力する() {
        Equipment lent = equipment(2L, "ノートPC", "本社3F倉庫", EquipmentStatus.LENT, LocalDate.of(2023, 4, 1));
        givenEquipment(List.of(lent), Map.of(2L, lending(lent, "山田太郎")));

        String body = body(equipmentCsvExportService.export(null, null));

        assertThat(body).contains("ノートPC,EQ-0002,本社3F倉庫,貸出中,2023-04-01,山田太郎\r\n");
    }

    @Test
    void export_貸出中でも有効な貸出記録がなければ借用者は空欄で例外にならない() {
        givenEquipment(List.of(equipment(2L, "ノートPC", null, EquipmentStatus.LENT, null)), Map.of());

        String body = body(equipmentCsvExportService.export(null, null));

        assertThat(body).contains("ノートPC,EQ-0002,,貸出中,,\r\n");
    }

    @Test
    void export_貸出中以外の備品は貸出記録があっても借用者を出力しない() {
        Equipment available = equipment(1L, "ノートPC", null, EquipmentStatus.AVAILABLE, null);
        givenEquipment(List.of(available), Map.of(1L, lending(available, "山田太郎")));

        String body = body(equipmentCsvExportService.export(null, null));

        assertThat(body).contains("ノートPC,EQ-0001,,利用可,,\r\n").doesNotContain("山田太郎");
    }

    @Test
    void export_保管場所と購入日が未設定なら空欄になる() {
        givenEquipment(List.of(equipment(8L, "モバイルプリンター", null, EquipmentStatus.AVAILABLE, null)), Map.of());

        String body = body(equipmentCsvExportService.export(null, null));

        assertThat(body).contains("モバイルプリンター,EQ-0008,,利用可,,\r\n");
    }

    @Test
    void export_カンマやダブルクォートを含む値はクォートで囲みダブルクォートを二重にする() {
        givenEquipment(List.of(equipment(3L, "ケーブル \"HDMI\", 2m", "棚A,上段", EquipmentStatus.BROKEN, null)),
                Map.of());

        String body = body(equipmentCsvExportService.export(null, null));

        assertThat(body).contains("\"ケーブル \"\"HDMI\"\", 2m\",EQ-0003,\"棚A,上段\",故障中,,\r\n");
    }

    @Test
    void export_改行を含む値はクォートで囲む() {
        givenEquipment(List.of(equipment(4L, "マイク", "本社\n2F", EquipmentStatus.AVAILABLE, null)), Map.of());

        String body = body(equipmentCsvExportService.export(null, null));

        assertThat(body).contains("マイク,EQ-0004,\"本社\n2F\",利用可,,\r\n");
    }

    @Test
    void export_該当0件なら見出し行のみになる() {
        when(equipmentService.search("存在しない", EquipmentStatus.BROKEN)).thenReturn(List.of());
        when(lendingService.findActiveLendingsByEquipmentId()).thenReturn(Map.of());

        String body = body(equipmentCsvExportService.export("存在しない", EquipmentStatus.BROKEN));

        assertThat(body).isEqualTo(HEADER + "\r\n");
    }

    @Test
    void export_検索条件をそのまま渡し検索結果の順序を変えない() {
        when(equipmentService.search("PC", EquipmentStatus.AVAILABLE)).thenReturn(List.of(
                equipment(5L, "PC-B", null, EquipmentStatus.AVAILABLE, null),
                equipment(1L, "PC-A", null, EquipmentStatus.AVAILABLE, null)));
        when(lendingService.findActiveLendingsByEquipmentId()).thenReturn(Map.of());

        String body = body(equipmentCsvExportService.export("PC", EquipmentStatus.AVAILABLE));

        assertThat(body).isEqualTo(HEADER + "\r\n"
                + "PC-B,EQ-0005,,利用可,,\r\n"
                + "PC-A,EQ-0001,,利用可,,\r\n");
    }

    @Test
    void buildFileName_日時を含むファイル名を返す() {
        assertThat(equipmentCsvExportService.buildFileName(LocalDateTime.of(2026, 10, 3, 15, 30, 0)))
                .isEqualTo("equipment_20261003_153000.csv");
    }

    private void givenEquipment(List<Equipment> equipmentList, Map<Long, Lending> activeLendings) {
        when(equipmentService.search(null, null)).thenReturn(equipmentList);
        when(lendingService.findActiveLendingsByEquipmentId()).thenReturn(activeLendings);
    }

    /** BOM（3バイト）を除いた本文をUTF-8で復元する */
    private String body(byte[] csv) {
        return new String(csv, 3, csv.length - 3, StandardCharsets.UTF_8);
    }

    private Equipment equipment(Long id, String name, String location, EquipmentStatus status, LocalDate purchaseDate) {
        return Equipment.builder()
                .id(id)
                .name(name)
                .assetNumber("EQ-000" + id)
                .location(location)
                .status(status)
                .purchaseDate(purchaseDate)
                .build();
    }

    private Lending lending(Equipment equipment, String employeeName) {
        return Lending.builder()
                .equipment(equipment)
                .employee(Employee.builder().id(1L).employeeNumber("E-001").name(employeeName).build())
                .build();
    }
}
