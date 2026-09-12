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

import jp.co.example.equipmentmanagement.dto.EmployeeForm;
import jp.co.example.equipmentmanagement.entity.Employee;
import jp.co.example.equipmentmanagement.repository.EmployeeRepository;
import jp.co.example.equipmentmanagement.repository.LendingRepository;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private LendingRepository lendingRepository;

    @InjectMocks
    private EmployeeService employeeService;

    @Test
    void findAll_ID順で全件を返す() {
        Employee a = employee(1L, "E-001", "山田太郎");
        Employee b = employee(2L, "E-002", "佐藤花子");
        when(employeeRepository.findAll()).thenReturn(List.of(b, a));

        List<Employee> result = employeeService.findAll();

        assertThat(result).extracting(Employee::getId).containsExactly(1L, 2L);
    }

    @Test
    void findById_存在しないIDならEmployeeNotFoundExceptionを投げる() {
        when(employeeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.findById(999L))
                .isInstanceOf(EmployeeNotFoundException.class);
    }

    @Test
    void isEmployeeNumberTaken_新規登録時は全件から重複チェックする() {
        when(employeeRepository.existsByEmployeeNumber("E-001")).thenReturn(true);

        assertThat(employeeService.isEmployeeNumberTaken("E-001", null)).isTrue();
        verify(employeeRepository, never()).existsByEmployeeNumberAndIdNot(any(), any());
    }

    @Test
    void isEmployeeNumberTaken_編集時は自分自身を除外して重複チェックする() {
        when(employeeRepository.existsByEmployeeNumberAndIdNot("E-001", 1L)).thenReturn(false);

        assertThat(employeeService.isEmployeeNumberTaken("E-001", 1L)).isFalse();
        verify(employeeRepository, never()).existsByEmployeeNumber(any());
    }

    @Test
    void create_入力内容で社員を登録する() {
        EmployeeForm form = new EmployeeForm();
        form.setEmployeeNumber("E-001");
        form.setName("山田太郎");
        form.setDepartment("営業部");
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Employee result = employeeService.create(form);

        assertThat(result.getEmployeeNumber()).isEqualTo("E-001");
        assertThat(result.getName()).isEqualTo("山田太郎");
        assertThat(result.getDepartment()).isEqualTo("営業部");
    }

    @Test
    void update_既存社員の内容を更新する() {
        Employee existing = employee(1L, "E-001", "山田太郎");
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(existing));

        EmployeeForm form = new EmployeeForm();
        form.setEmployeeNumber("E-001");
        form.setName("山田次郎");
        form.setDepartment("開発部");

        Employee result = employeeService.update(1L, form);

        assertThat(result.getName()).isEqualTo("山田次郎");
        assertThat(result.getDepartment()).isEqualTo("開発部");
    }

    @Test
    void delete_貸出履歴がなければ削除できる() {
        Employee existing = employee(1L, "E-001", "山田太郎");
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(lendingRepository.existsByEmployee(existing)).thenReturn(false);

        employeeService.delete(1L);

        verify(employeeRepository).delete(existing);
    }

    @Test
    void delete_貸出履歴があれば削除できない() {
        Employee existing = employee(1L, "E-001", "山田太郎");
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(lendingRepository.existsByEmployee(existing)).thenReturn(true);

        assertThatThrownBy(() -> employeeService.delete(1L))
                .isInstanceOf(EmployeeDeletionNotAllowedException.class);
        verify(employeeRepository, never()).delete(any());
    }

    private Employee employee(Long id, String employeeNumber, String name) {
        return Employee.builder()
                .id(id)
                .employeeNumber(employeeNumber)
                .name(name)
                .build();
    }
}
