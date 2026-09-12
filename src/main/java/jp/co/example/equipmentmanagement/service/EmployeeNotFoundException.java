package jp.co.example.equipmentmanagement.service;

public class EmployeeNotFoundException extends RuntimeException {

    public EmployeeNotFoundException(Long id) {
        super("指定された社員が見つかりません（id=" + id + "）");
    }
}
