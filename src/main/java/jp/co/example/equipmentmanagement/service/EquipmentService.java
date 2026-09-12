package jp.co.example.equipmentmanagement.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jp.co.example.equipmentmanagement.entity.Equipment;
import jp.co.example.equipmentmanagement.entity.EquipmentStatus;
import jp.co.example.equipmentmanagement.repository.EquipmentRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;

    /**
     * 品名（部分一致）・状態（完全一致）で備品を絞り込む。
     * 条件がnull/空の場合はその条件を無視する。
     */
    public List<Equipment> search(String name, EquipmentStatus status) {
        return equipmentRepository.findAll().stream()
                .filter(equipment -> !StringUtils.hasText(name) || equipment.getName().contains(name))
                .filter(equipment -> status == null || equipment.getStatus() == status)
                .sorted(Comparator.comparing(Equipment::getId))
                .toList();
    }
}
