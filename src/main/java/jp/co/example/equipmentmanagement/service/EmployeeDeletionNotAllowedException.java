package jp.co.example.equipmentmanagement.service;

import jp.co.example.equipmentmanagement.entity.Employee;

/**
 * 貸出履歴（Lending）が存在する社員は、参照整合性のため削除できない。
 */
public class EmployeeDeletionNotAllowedException extends RuntimeException {

    public EmployeeDeletionNotAllowedException(Employee employee) {
        super("「" + employee.getName() + "」（" + employee.getEmployeeNumber() + "）は貸出履歴が存在するため削除できません");
    }
}
