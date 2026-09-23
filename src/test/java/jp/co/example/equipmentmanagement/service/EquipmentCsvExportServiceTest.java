package jp.co.example.equipmentmanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jp.co.example.equipmentmanagement.entity.Employee;
import jp.co.example.equipmentmanagement.entity.Equipment;
import jp.co.example.equipmentmanagement.entity.EquipmentStatus;
import jp.co.example.equipmentmanagement.entity.Lending;

@ExtendWith(MockitoExtension.class)
class EquipmentCsvExportServiceTest {

    private static final String HEADER = "品名,管理番号,保管場所,状態,購入日,借用者\r\n";

    @Mock
    private EquipmentService equipmentService;

    @Mock
    private LendingService lendingService;

    private EquipmentCsvExportService exportService;

    @BeforeEach
    void setUp() {
        exportService = new EquipmentCsvExportService(equipmentService, lendingService, new CsvFormatter());
    }

    @Test
    void export_先頭にBOMが付き1行目が列見出しになる() {
        when(equipmentService.search(null, null)).thenReturn(List.of());
        when(lendingService.findActiveLendingsByEquipmentId()).thenReturn(Map.of());

        byte[] csv = exportService.export(null, null);

        assertThat(Arrays.copyOf(csv, 3)).containsExactly(0xEF, 0xBB, 0xBF);
        assertThat(body(csv)).startsWith(HEADER);
    }

    @Test
    void export_対象が0件なら列見出し行のみを出力する() {
        when(equipmentService.search(null, null)).thenReturn(List.of());
        when(lendingService.findActiveLendingsByEquipmentId()).thenReturn(Map.of());

        assertThat(body(exportService.export(null, null))).isEqualTo(HEADER);
    }

    @Test
    void export_状態は日本語表記で購入日はyyyyMMdd形式で出力し最終行もCRLFで終わる() {
        Equipment available = equipment(1L, "ノートPC", "EQ-0001", "会議室A", EquipmentStatus.AVAILABLE,
                LocalDate.of(2024, 4, 1));
        Equipment broken = equipment(2L, "プロジェクター", "EQ-0002", "倉庫", EquipmentStatus.BROKEN,
                LocalDate.of(2023, 12, 15));
        when(equipmentService.search(null, null)).thenReturn(List.of(available, broken));
        when(lendingService.findActiveLendingsByEquipmentId()).thenReturn(Map.of());

        assertThat(body(exportService.export(null, null))).isEqualTo(HEADER
                + "ノートPC,EQ-0001,会議室A,利用可,2024-04-01,\r\n"
                + "プロジェクター,EQ-0002,倉庫,故障中,2023-12-15,\r\n");
    }

    @Test
    void export_保管場所と購入日が未登録なら空欄で出力し列数は6のまま() {
        Equipment noDetail = equipment(1L, "ノートPC", "EQ-0001", null, EquipmentStatus.AVAILABLE, null);
        when(equipmentService.search(null, null)).thenReturn(List.of(noDetail));
        when(lendingService.findActiveLendingsByEquipmentId()).thenReturn(Map.of());

        assertThat(body(exportService.export(null, null))).isEqualTo(HEADER + "ノートPC,EQ-0001,,利用可,,\r\n");
    }

    @Test
    void export_借用者名は貸出中かつ有効な貸出記録がある備品のみ出力する() {
        Equipment lent = equipment(1L, "ノートPC", "EQ-0001", "会議室A", EquipmentStatus.LENT, null);
        Equipment lentWithoutRecord = equipment(2L, "タブレット", "EQ-0002", "会議室B", EquipmentStatus.LENT, null);
        Equipment availableWithRecord = equipment(3L, "モニター", "EQ-0003", "倉庫", EquipmentStatus.AVAILABLE, null);
        Equipment brokenWithRecord = equipment(4L, "プリンター", "EQ-0004", "倉庫", EquipmentStatus.BROKEN, null);
        when(equipmentService.search(null, null))
                .thenReturn(List.of(lent, lentWithoutRecord, availableWithRecord, brokenWithRecord));
        // 状態が LENT 以外の備品に有効な貸出記録が残っている不整合データでも、状態を正として借用者は出さない
        when(lendingService.findActiveLendingsByEquipmentId()).thenReturn(Map.of(
                1L, lending(lent, "山田 太郎"),
                3L, lending(availableWithRecord, "佐藤 花子"),
                4L, lending(brokenWithRecord, "鈴木 一郎")));

        assertThat(body(exportService.export(null, null))).isEqualTo(HEADER
                + "ノートPC,EQ-0001,会議室A,貸出中,,山田 太郎\r\n"
                + "タブレット,EQ-0002,会議室B,貸出中,,\r\n"
                + "モニター,EQ-0003,倉庫,利用可,,\r\n"
                + "プリンター,EQ-0004,倉庫,故障中,,\r\n");
    }

    @Test
    void export_検索条件はそのままEquipmentServiceに渡される() {
        when(equipmentService.search("PC", EquipmentStatus.BROKEN)).thenReturn(List.of());
        when(lendingService.findActiveLendingsByEquipmentId()).thenReturn(Map.of());

        exportService.export("PC", EquipmentStatus.BROKEN);

        verify(equipmentService).search("PC", EquipmentStatus.BROKEN);
    }

    @Test
    void export_行順は検索結果の順序のまま並べ替えない() {
        Equipment third = equipment(3L, "品C", "EQ-0003", null, EquipmentStatus.AVAILABLE, null);
        Equipment first = equipment(1L, "品A", "EQ-0001", null, EquipmentStatus.AVAILABLE, null);
        Equipment second = equipment(2L, "品B", "EQ-0002", null, EquipmentStatus.AVAILABLE, null);
        when(equipmentService.search(null, null)).thenReturn(List.of(third, first, second));
        when(lendingService.findActiveLendingsByEquipmentId()).thenReturn(Map.of());

        assertThat(body(exportService.export(null, null))).isEqualTo(HEADER
                + "品C,EQ-0003,,利用可,,\r\n"
                + "品A,EQ-0001,,利用可,,\r\n"
                + "品B,EQ-0002,,利用可,,\r\n");
    }

    @Test
    void export_検索中に想定外のエラーが発生するとCsvExportExceptionに原因を保持して送出する() {
        RuntimeException cause = new IllegalStateException("DB接続エラー");
        when(equipmentService.search(null, null)).thenThrow(cause);

        assertThatThrownBy(() -> exportService.export(null, null))
                .isInstanceOf(CsvExportException.class)
                .hasMessage("CSVファイルの出力中にエラーが発生しました。時間をおいて再度お試しください。")
                .hasCause(cause);
    }

    @Test
    void fileName_日時をyyyyMMdd_HHmmss形式で埋め込む() {
        assertThat(exportService.fileName(LocalDateTime.of(2026, 9, 23, 14, 30, 15)))
                .isEqualTo("equipment_20260923_143015.csv");
    }

    private String body(byte[] csv) {
        return new String(csv, 3, csv.length - 3, StandardCharsets.UTF_8);
    }

    private Equipment equipment(Long id, String name, String assetNumber, String location,
            EquipmentStatus status, LocalDate purchaseDate) {
        return Equipment.builder()
                .id(id)
                .name(name)
                .assetNumber(assetNumber)
                .location(location)
                .status(status)
                .purchaseDate(purchaseDate)
                .build();
    }

    private Lending lending(Equipment equipment, String employeeName) {
        return Lending.builder()
                .equipment(equipment)
                .employee(Employee.builder().name(employeeName).build())
                .build();
    }
}
