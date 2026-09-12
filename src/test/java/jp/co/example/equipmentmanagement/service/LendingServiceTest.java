package jp.co.example.equipmentmanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jp.co.example.equipmentmanagement.dto.LendingForm;
import jp.co.example.equipmentmanagement.entity.Employee;
import jp.co.example.equipmentmanagement.entity.Equipment;
import jp.co.example.equipmentmanagement.entity.EquipmentStatus;
import jp.co.example.equipmentmanagement.entity.Lending;
import jp.co.example.equipmentmanagement.repository.EmployeeRepository;
import jp.co.example.equipmentmanagement.repository.EquipmentRepository;
import jp.co.example.equipmentmanagement.repository.LendingRepository;

@ExtendWith(MockitoExtension.class)
class LendingServiceTest {

    @Mock
    private LendingRepository lendingRepository;

    @Mock
    private EquipmentRepository equipmentRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private LendingService lendingService;

    @Test
    void lend_利用可の備品は貸出でき状態が貸出中になる() {
        Equipment equipment = equipment(1L, EquipmentStatus.AVAILABLE);
        Employee employee = employee(1L, "山田太郎");
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(equipment));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

        LendingForm form = new LendingForm();
        form.setEmployeeId(1L);
        form.setDueDate(LocalDate.now().plusDays(7));

        lendingService.lend(1L, form);

        assertThat(equipment.getStatus()).isEqualTo(EquipmentStatus.LENT);

        ArgumentCaptor<Lending> captor = ArgumentCaptor.forClass(Lending.class);
        verify(lendingRepository).save(captor.capture());
        assertThat(captor.getValue().getEmployee()).isEqualTo(employee);
        assertThat(captor.getValue().getEquipment()).isEqualTo(equipment);
    }

    @Test
    void lend_貸出中の備品は貸出できない() {
        Equipment equipment = equipment(1L, EquipmentStatus.LENT);
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(equipment));

        LendingForm form = new LendingForm();
        form.setEmployeeId(1L);

        assertThatThrownBy(() -> lendingService.lend(1L, form))
                .isInstanceOf(EquipmentNotAvailableException.class);
        verify(lendingRepository, never()).save(any());
    }

    @Test
    void lend_故障中の備品は貸出できない() {
        Equipment equipment = equipment(1L, EquipmentStatus.BROKEN);
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(equipment));

        LendingForm form = new LendingForm();
        form.setEmployeeId(1L);

        assertThatThrownBy(() -> lendingService.lend(1L, form))
                .isInstanceOf(EquipmentNotAvailableException.class);
        verify(lendingRepository, never()).save(any());
    }

    @Test
    void lend_存在しない備品はEquipmentNotFoundExceptionを投げる() {
        when(equipmentRepository.findById(999L)).thenReturn(Optional.empty());

        LendingForm form = new LendingForm();
        form.setEmployeeId(1L);

        assertThatThrownBy(() -> lendingService.lend(999L, form))
                .isInstanceOf(EquipmentNotFoundException.class);
    }

    @Test
    void lend_存在しない社員はEmployeeNotFoundExceptionを投げる() {
        Equipment equipment = equipment(1L, EquipmentStatus.AVAILABLE);
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(equipment));
        when(employeeRepository.findById(999L)).thenReturn(Optional.empty());

        LendingForm form = new LendingForm();
        form.setEmployeeId(999L);

        assertThatThrownBy(() -> lendingService.lend(1L, form))
                .isInstanceOf(EmployeeNotFoundException.class);
        verify(lendingRepository, never()).save(any());
    }

    @Test
    void returnEquipment_返却すると備品が利用可に戻り返却日時が設定される() {
        Equipment equipment = equipment(1L, EquipmentStatus.LENT);
        Lending lending = Lending.builder().id(10L).equipment(equipment).employee(employee(1L, "山田太郎")).build();
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(equipment));
        when(lendingRepository.findByEquipmentAndReturnedAtIsNull(equipment)).thenReturn(Optional.of(lending));

        lendingService.returnEquipment(1L);

        assertThat(equipment.getStatus()).isEqualTo(EquipmentStatus.AVAILABLE);
        assertThat(lending.getReturnedAt()).isNotNull();
    }

    @Test
    void returnEquipment_有効な貸出記録がなければ例外を投げる() {
        Equipment equipment = equipment(1L, EquipmentStatus.LENT);
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(equipment));
        when(lendingRepository.findByEquipmentAndReturnedAtIsNull(equipment)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lendingService.returnEquipment(1L))
                .isInstanceOf(NoActiveLendingException.class);
    }

    @Test
    void findActiveLendingsByEquipmentId_備品IDをキーにしたマップを返す() {
        Equipment eq1 = equipment(1L, EquipmentStatus.LENT);
        Equipment eq2 = equipment(2L, EquipmentStatus.LENT);
        Lending lending1 = Lending.builder().id(10L).equipment(eq1).employee(employee(1L, "山田太郎")).build();
        Lending lending2 = Lending.builder().id(11L).equipment(eq2).employee(employee(2L, "佐藤花子")).build();
        when(lendingRepository.findAllByReturnedAtIsNull()).thenReturn(List.of(lending1, lending2));

        Map<Long, Lending> result = lendingService.findActiveLendingsByEquipmentId();

        assertThat(result).containsEntry(1L, lending1).containsEntry(2L, lending2);
    }

    private Equipment equipment(Long id, EquipmentStatus status) {
        return Equipment.builder()
                .id(id)
                .name("ノートPC")
                .assetNumber("EQ-000" + id)
                .status(status)
                .build();
    }

    private Employee employee(Long id, String name) {
        return Employee.builder()
                .id(id)
                .employeeNumber("E-00" + id)
                .name(name)
                .build();
    }
}
