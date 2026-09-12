package jp.co.example.equipmentmanagement.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jp.co.example.equipmentmanagement.dto.EmployeeForm;
import jp.co.example.equipmentmanagement.entity.Employee;
import jp.co.example.equipmentmanagement.repository.EmployeeRepository;
import jp.co.example.equipmentmanagement.repository.LendingRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final LendingRepository lendingRepository;

    public List<Employee> findAll() {
        return employeeRepository.findAll().stream()
                .sorted(Comparator.comparing(Employee::getId))
                .toList();
    }

    public Employee findById(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));
    }

    /**
     * 社員番号の重複チェック。編集時は自分自身のIDを除外する（excludeIdがnullなら新規登録扱い）。
     */
    public boolean isEmployeeNumberTaken(String employeeNumber, Long excludeId) {
        if (excludeId == null) {
            return employeeRepository.existsByEmployeeNumber(employeeNumber);
        }
        return employeeRepository.existsByEmployeeNumberAndIdNot(employeeNumber, excludeId);
    }

    @Transactional
    public Employee create(EmployeeForm form) {
        Employee employee = Employee.builder()
                .employeeNumber(form.getEmployeeNumber())
                .name(form.getName())
                .department(form.getDepartment())
                .build();
        return employeeRepository.save(employee);
    }

    @Transactional
    public Employee update(Long id, EmployeeForm form) {
        Employee employee = findById(id);
        employee.setEmployeeNumber(form.getEmployeeNumber());
        employee.setName(form.getName());
        employee.setDepartment(form.getDepartment());
        return employee;
    }

    /**
     * 社員を削除する。貸出履歴（Lending）が存在する場合は参照整合性のため削除を拒否する
     * （Lending.employeeは必須の外部キーであり、カスケード削除は設定していないため）。
     */
    @Transactional
    public void delete(Long id) {
        Employee employee = findById(id);
        if (lendingRepository.existsByEmployee(employee)) {
            throw new EmployeeDeletionNotAllowedException(employee);
        }
        employeeRepository.delete(employee);
    }
}
