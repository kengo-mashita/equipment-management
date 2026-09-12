package jp.co.example.equipmentmanagement.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import jp.co.example.equipmentmanagement.dto.EquipmentForm;
import jp.co.example.equipmentmanagement.entity.Equipment;
import jp.co.example.equipmentmanagement.entity.EquipmentStatus;
import jp.co.example.equipmentmanagement.service.EquipmentNotFoundException;
import jp.co.example.equipmentmanagement.service.EquipmentService;
import jp.co.example.equipmentmanagement.service.LendingService;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/equipment")
@RequiredArgsConstructor
public class EquipmentController {

    /** 編集画面・登録画面で選択可能な状態（「貸出中」は貸出操作でのみ設定されるため除外） */
    private static final EquipmentStatus[] EDITABLE_STATUSES = { EquipmentStatus.AVAILABLE, EquipmentStatus.BROKEN };

    private final EquipmentService equipmentService;
    private final LendingService lendingService;

    @GetMapping
    public String list(@RequestParam(required = false) String name,
            @RequestParam(required = false) String status,
            Model model) {

        EquipmentStatus statusFilter = StringUtils.hasText(status) ? EquipmentStatus.valueOf(status) : null;

        model.addAttribute("equipmentList", equipmentService.search(name, statusFilter));
        model.addAttribute("name", name);
        model.addAttribute("status", statusFilter);
        model.addAttribute("statuses", EquipmentStatus.values());
        // 「貸出中」の行に借用者名を表示するため、備品ID -> 有効な貸出記録のマップを渡す
        model.addAttribute("activeLendings", lendingService.findActiveLendingsByEquipmentId());
        return "equipment/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("equipmentForm", EquipmentForm.empty());
        model.addAttribute("currentStatus", (EquipmentStatus) null);
        model.addAttribute("statuses", EDITABLE_STATUSES);
        return "equipment/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("equipmentForm") EquipmentForm form,
            BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {

        if (equipmentService.isAssetNumberTaken(form.getAssetNumber(), null)) {
            bindingResult.rejectValue("assetNumber", "duplicate", "この管理番号は既に使用されています");
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("currentStatus", (EquipmentStatus) null);
            model.addAttribute("statuses", EDITABLE_STATUSES);
            return "equipment/form";
        }

        equipmentService.create(form);
        redirectAttributes.addFlashAttribute("message", "備品を登録しました");
        return "redirect:/equipment";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Equipment equipment = equipmentService.findById(id);
        model.addAttribute("equipmentForm", EquipmentForm.from(equipment));
        model.addAttribute("currentStatus", equipment.getStatus());
        model.addAttribute("statuses", EDITABLE_STATUSES);
        return "equipment/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("equipmentForm") EquipmentForm form,
            BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {

        Equipment current = equipmentService.findById(id);

        if (equipmentService.isAssetNumberTaken(form.getAssetNumber(), id)) {
            bindingResult.rejectValue("assetNumber", "duplicate", "この管理番号は既に使用されています");
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("currentStatus", current.getStatus());
            model.addAttribute("statuses", EDITABLE_STATUSES);
            return "equipment/form";
        }

        equipmentService.update(id, form);
        redirectAttributes.addFlashAttribute("message", "備品を更新しました");
        return "redirect:/equipment";
    }

    @ExceptionHandler(EquipmentNotFoundException.class)
    public String handleNotFound(EquipmentNotFoundException ex, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", ex.getMessage());
        return "redirect:/equipment";
    }
}
