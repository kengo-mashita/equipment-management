package jp.co.example.equipmentmanagement.service;

import jp.co.example.equipmentmanagement.entity.Equipment;

/**
 * 貸出履歴（Lending）が存在する備品は、参照整合性のため削除できない。
 */
public class EquipmentDeletionNotAllowedException extends RuntimeException {

    public EquipmentDeletionNotAllowedException(Equipment equipment) {
        super("「" + equipment.getName() + "」（" + equipment.getAssetNumber() + "）は貸出履歴が存在するため削除できません");
    }
}
