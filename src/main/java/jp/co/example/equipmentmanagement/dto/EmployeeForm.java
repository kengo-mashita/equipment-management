package jp.co.example.equipmentmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jp.co.example.equipmentmanagement.entity.Employee;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmployeeForm {

    private Long id;

    @NotBlank(message = "社員番号は必須です")
    private String employeeNumber;

    @NotBlank(message = "氏名は必須です")
    private String name;

    private String department;

    public static EmployeeForm empty() {
        return new EmployeeForm();
    }

    public static EmployeeForm from(Employee employee) {
        EmployeeForm form = new EmployeeForm();
        form.setId(employee.getId());
        form.setEmployeeNumber(employee.getEmployeeNumber());
        form.setName(employee.getName());
        form.setDepartment(employee.getDepartment());
        return form;
    }
}
