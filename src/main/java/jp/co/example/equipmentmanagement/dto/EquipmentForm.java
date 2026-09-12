package jp.co.example.equipmentmanagement.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.NotBlank;
import jp.co.example.equipmentmanagement.entity.Equipment;
import jp.co.example.equipmentmanagement.entity.EquipmentStatus;
import lombok.Getter;
import lombok.Setter;

/**
 * 備品登録・編集フォームの入力用DTO。statusはAVAILABLE/BROKENのみ選択可能とし、
 * LENT（貸出中）への遷移は貸出操作を通じてのみ行う（Entityのstatusとフォームを分離する理由）。
 */
@Getter
@Setter
public class EquipmentForm {

    private Long id;

    @NotBlank(message = "品名は必須です")
    private String name;

    @NotBlank(message = "管理番号は必須です")
    private String assetNumber;

    private String location;

    private EquipmentStatus status;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate purchaseDate;

    public static EquipmentForm empty() {
        EquipmentForm form = new EquipmentForm();
        form.setStatus(EquipmentStatus.AVAILABLE);
        return form;
    }

    public static EquipmentForm from(Equipment equipment) {
        EquipmentForm form = new EquipmentForm();
        form.setId(equipment.getId());
        form.setName(equipment.getName());
        form.setAssetNumber(equipment.getAssetNumber());
        form.setLocation(equipment.getLocation());
        form.setStatus(equipment.getStatus());
        form.setPurchaseDate(equipment.getPurchaseDate());
        return form;
    }
}
