package jp.co.example.equipmentmanagement.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jp.co.example.equipmentmanagement.dto.LendingForm;
import jp.co.example.equipmentmanagement.entity.Equipment;
import jp.co.example.equipmentmanagement.entity.EquipmentStatus;
import jp.co.example.equipmentmanagement.entity.Lending;
import jp.co.example.equipmentmanagement.repository.EquipmentRepository;
import jp.co.example.equipmentmanagement.repository.LendingRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LendingService {

    private final LendingRepository lendingRepository;
    private final EquipmentRepository equipmentRepository;

    /**
     * 備品の貸出登録。「利用可」の備品のみ貸出でき、登録すると備品は自動的に「貸出中」になる。
     * 1備品につき有効な貸出記録は同時に1件のみという不変条件は、statusチェックによって担保される
     * （statusが「利用可」であれば、有効な貸出記録は存在し得ない）。
     */
    @Transactional
    public void lend(Long equipmentId, LendingForm form) {
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new EquipmentNotFoundException(equipmentId));

        if (equipment.getStatus() != EquipmentStatus.AVAILABLE) {
            throw new EquipmentNotAvailableException(equipment);
        }

        Lending lending = Lending.builder()
                .equipment(equipment)
                .borrowerName(form.getBorrowerName())
                .dueDate(form.getDueDate())
                .build();
        lendingRepository.save(lending);

        equipment.setStatus(EquipmentStatus.LENT);
    }

    /**
     * 備品の返却登録。対象の有効な貸出記録にreturnedAtを設定し、備品を「利用可」に戻す。
     */
    @Transactional
    public void returnEquipment(Long equipmentId) {
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new EquipmentNotFoundException(equipmentId));

        Lending lending = lendingRepository.findByEquipmentAndReturnedAtIsNull(equipment)
                .orElseThrow(() -> new NoActiveLendingException(equipmentId));

        lending.setReturnedAt(LocalDateTime.now());
        equipment.setStatus(EquipmentStatus.AVAILABLE);
    }

    /** 備品一覧画面で「貸出中」の行に借用者名を表示するための、備品ID -> 有効な貸出記録のマップ */
    public Map<Long, Lending> findActiveLendingsByEquipmentId() {
        return lendingRepository.findAllByReturnedAtIsNull().stream()
                .collect(Collectors.toMap(lending -> lending.getEquipment().getId(), Function.identity()));
    }

    public List<Lending> findAllOrderByLentAtDesc() {
        return lendingRepository.findAllByOrderByLentAtDesc();
    }
}
