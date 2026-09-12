package jp.co.example.equipmentmanagement.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jp.co.example.equipmentmanagement.dto.EquipmentForm;
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

    public Equipment findById(Long id) {
        return equipmentRepository.findById(id)
                .orElseThrow(() -> new EquipmentNotFoundException(id));
    }

    /**
     * 管理番号の重複チェック。編集時は自分自身のIDを除外する（excludeIdがnullなら新規登録扱い）。
     */
    public boolean isAssetNumberTaken(String assetNumber, Long excludeId) {
        if (excludeId == null) {
            return equipmentRepository.existsByAssetNumber(assetNumber);
        }
        return equipmentRepository.existsByAssetNumberAndIdNot(assetNumber, excludeId);
    }

    @Transactional
    public Equipment create(EquipmentForm form) {
        Equipment equipment = Equipment.builder()
                .name(form.getName())
                .assetNumber(form.getAssetNumber())
                .location(form.getLocation())
                .status(resolveEditableStatus(form.getStatus()))
                .purchaseDate(form.getPurchaseDate())
                .build();
        return equipmentRepository.save(equipment);
    }

    @Transactional
    public Equipment update(Long id, EquipmentForm form) {
        Equipment equipment = findById(id);
        equipment.setName(form.getName());
        equipment.setAssetNumber(form.getAssetNumber());
        equipment.setLocation(form.getLocation());
        equipment.setPurchaseDate(form.getPurchaseDate());

        // 「貸出中」は貸出操作でのみ設定・解除されるため、編集画面からの状態変更は貸出中でない場合のみ許可する。
        if (equipment.getStatus() != EquipmentStatus.LENT) {
            equipment.setStatus(resolveEditableStatus(form.getStatus()));
        }

        return equipment;
    }

    /**
     * フォームから受け取った状態を登録・編集で許可される値（AVAILABLE/BROKEN）に正規化する。
     * LENTは貸出操作以外から設定できないため、不正なリクエストでLENTが送られてもAVAILABLEに丸める。
     */
    private EquipmentStatus resolveEditableStatus(EquipmentStatus requested) {
        if (requested == null || requested == EquipmentStatus.LENT) {
            return EquipmentStatus.AVAILABLE;
        }
        return requested;
    }
}
