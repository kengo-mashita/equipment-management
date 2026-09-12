package jp.co.example.equipmentmanagement.service;

public class EquipmentNotFoundException extends RuntimeException {

    public EquipmentNotFoundException(Long id) {
        super("指定された備品が見つかりません（id=" + id + "）");
    }
}
