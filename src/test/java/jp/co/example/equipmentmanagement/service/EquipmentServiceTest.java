package jp.co.example.equipmentmanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jp.co.example.equipmentmanagement.dto.EquipmentForm;
import jp.co.example.equipmentmanagement.entity.Equipment;
import jp.co.example.equipmentmanagement.entity.EquipmentStatus;
import jp.co.example.equipmentmanagement.repository.EquipmentRepository;
import jp.co.example.equipmentmanagement.repository.LendingRepository;

@ExtendWith(MockitoExtension.class)
class EquipmentServiceTest {

    @Mock
    private EquipmentRepository equipmentRepository;

    @Mock
    private LendingRepository lendingRepository;

    @InjectMocks
    private EquipmentService equipmentService;

    @Test
    void search_品名の部分一致で絞り込める() {
        Equipment pc = equipment(1L, "ノートPC", "EQ-0001", EquipmentStatus.AVAILABLE);
        Equipment projector = equipment(2L, "プロジェクター", "EQ-0002", EquipmentStatus.AVAILABLE);
        when(equipmentRepository.findAll()).thenReturn(List.of(pc, projector));

        List<Equipment> result = equipmentService.search("ノート", null);

        assertThat(result).containsExactly(pc);
    }

    @Test
    void search_状態の完全一致で絞り込める() {
        Equipment available = equipment(1L, "ノートPC", "EQ-0001", EquipmentStatus.AVAILABLE);
        Equipment lent = equipment(2L, "タブレット", "EQ-0002", EquipmentStatus.LENT);
        when(equipmentRepository.findAll()).thenReturn(List.of(available, lent));

        List<Equipment> result = equipmentService.search(null, EquipmentStatus.LENT);

        assertThat(result).containsExactly(lent);
    }

    @Test
    void search_条件なしなら全件をID順で返す() {
        Equipment a = equipment(1L, "ノートPC", "EQ-0001", EquipmentStatus.AVAILABLE);
        Equipment b = equipment(2L, "タブレット", "EQ-0002", EquipmentStatus.LENT);
        when(equipmentRepository.findAll()).thenReturn(List.of(b, a));

        List<Equipment> result = equipmentService.search(null, null);

        assertThat(result).extracting(Equipment::getId).containsExactly(1L, 2L);
    }

    @Test
    void findById_存在しないIDならEquipmentNotFoundExceptionを投げる() {
        when(equipmentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> equipmentService.findById(999L))
                .isInstanceOf(EquipmentNotFoundException.class);
    }

    @Test
    void isAssetNumberTaken_新規登録時は全件から重複チェックする() {
        when(equipmentRepository.existsByAssetNumber("EQ-0001")).thenReturn(true);

        assertThat(equipmentService.isAssetNumberTaken("EQ-0001", null)).isTrue();
        verify(equipmentRepository, never()).existsByAssetNumberAndIdNot(any(), any());
    }

    @Test
    void isAssetNumberTaken_編集時は自分自身を除外して重複チェックする() {
        when(equipmentRepository.existsByAssetNumberAndIdNot("EQ-0001", 1L)).thenReturn(false);

        assertThat(equipmentService.isAssetNumberTaken("EQ-0001", 1L)).isFalse();
        verify(equipmentRepository, never()).existsByAssetNumber(any());
    }

    @Test
    void create_statusが未指定ならAVAILABLEになる() {
        EquipmentForm form = new EquipmentForm();
        form.setName("ノートPC");
        form.setAssetNumber("EQ-0001");
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Equipment result = equipmentService.create(form);

        assertThat(result.getStatus()).isEqualTo(EquipmentStatus.AVAILABLE);
    }

    @Test
    void create_statusにLENTを指定してもAVAILABLEに丸められる() {
        EquipmentForm form = new EquipmentForm();
        form.setName("ノートPC");
        form.setAssetNumber("EQ-0001");
        form.setStatus(EquipmentStatus.LENT);
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Equipment result = equipmentService.create(form);

        assertThat(result.getStatus()).isEqualTo(EquipmentStatus.AVAILABLE);
    }

    @Test
    void update_貸出中でない場合はstatusを変更できる() {
        Equipment existing = equipment(1L, "ノートPC", "EQ-0001", EquipmentStatus.AVAILABLE);
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(existing));

        EquipmentForm form = new EquipmentForm();
        form.setName("ノートPC");
        form.setAssetNumber("EQ-0001");
        form.setStatus(EquipmentStatus.BROKEN);

        Equipment result = equipmentService.update(1L, form);

        assertThat(result.getStatus()).isEqualTo(EquipmentStatus.BROKEN);
    }

    @Test
    void update_貸出中の備品はstatusを変更できない() {
        Equipment existing = equipment(1L, "ノートPC", "EQ-0001", EquipmentStatus.LENT);
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(existing));

        EquipmentForm form = new EquipmentForm();
        form.setName("ノートPC");
        form.setAssetNumber("EQ-0001");
        form.setStatus(EquipmentStatus.BROKEN);

        Equipment result = equipmentService.update(1L, form);

        assertThat(result.getStatus()).isEqualTo(EquipmentStatus.LENT);
    }

    @Test
    void update_statusにLENTを不正指定してもAVAILABLEに丸められる() {
        Equipment existing = equipment(1L, "ノートPC", "EQ-0001", EquipmentStatus.BROKEN);
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(existing));

        EquipmentForm form = new EquipmentForm();
        form.setName("ノートPC");
        form.setAssetNumber("EQ-0001");
        form.setStatus(EquipmentStatus.LENT);

        Equipment result = equipmentService.update(1L, form);

        assertThat(result.getStatus()).isEqualTo(EquipmentStatus.AVAILABLE);
    }

    @Test
    void delete_貸出履歴がなければ削除できる() {
        Equipment existing = equipment(1L, "ノートPC", "EQ-0001", EquipmentStatus.AVAILABLE);
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(lendingRepository.existsByEquipment(existing)).thenReturn(false);

        equipmentService.delete(1L);

        verify(equipmentRepository).delete(existing);
    }

    @Test
    void delete_貸出履歴があれば削除できない() {
        Equipment existing = equipment(1L, "ノートPC", "EQ-0001", EquipmentStatus.AVAILABLE);
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(lendingRepository.existsByEquipment(existing)).thenReturn(true);

        assertThatThrownBy(() -> equipmentService.delete(1L))
                .isInstanceOf(EquipmentDeletionNotAllowedException.class);
        verify(equipmentRepository, never()).delete(any());
    }

    @Test
    void parseStatusFilter_nullや空文字は条件なしとしてnullを返す() {
        assertThat(equipmentService.parseStatusFilter(null)).isNull();
        assertThat(equipmentService.parseStatusFilter("")).isNull();
    }

    @Test
    void parseStatusFilter_列挙子名に一致すれば対応する状態を返す() {
        assertThat(equipmentService.parseStatusFilter("AVAILABLE")).isEqualTo(EquipmentStatus.AVAILABLE);
        assertThat(equipmentService.parseStatusFilter("LENT")).isEqualTo(EquipmentStatus.LENT);
        assertThat(equipmentService.parseStatusFilter("BROKEN")).isEqualTo(EquipmentStatus.BROKEN);
    }

    @Test
    void parseStatusFilter_列挙子名に一致しない値はInvalidSearchConditionExceptionを投げる() {
        assertThatThrownBy(() -> equipmentService.parseStatusFilter("UNKNOWN"))
                .isInstanceOf(InvalidSearchConditionException.class)
                .hasMessage("検索条件が不正です。条件を指定し直してください。");
        assertThatThrownBy(() -> equipmentService.parseStatusFilter("available"))
                .isInstanceOf(InvalidSearchConditionException.class);
    }

    private Equipment equipment(Long id, String name, String assetNumber, EquipmentStatus status) {
        return Equipment.builder()
                .id(id)
                .name(name)
                .assetNumber(assetNumber)
                .status(status)
                .build();
    }
}
