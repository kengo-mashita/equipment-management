package jp.co.example.equipmentmanagement.service;

import jp.co.example.equipmentmanagement.entity.Equipment;

/**
 * 「利用可」以外の状態の備品に対して貸出登録が行われた場合の例外。
 */
public class EquipmentNotAvailableException extends RuntimeException {

    public EquipmentNotAvailableException(Equipment equipment) {
        super("この備品は現在貸出できません（状態: " + equipment.getStatus().getLabel() + "）");
    }
}
