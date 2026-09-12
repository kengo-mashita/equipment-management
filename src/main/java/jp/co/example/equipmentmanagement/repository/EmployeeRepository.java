package jp.co.example.equipmentmanagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import jp.co.example.equipmentmanagement.entity.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    boolean existsByEmployeeNumber(String employeeNumber);

    boolean existsByEmployeeNumberAndIdNot(String employeeNumber, Long id);
}
