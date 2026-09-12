package jp.co.example.equipmentmanagement.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jp.co.example.equipmentmanagement.entity.EquipmentStatus;
import jp.co.example.equipmentmanagement.service.EquipmentService;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/equipment")
@RequiredArgsConstructor
public class EquipmentController {

    private final EquipmentService equipmentService;

    @GetMapping
    public String list(@RequestParam(required = false) String name,
            @RequestParam(required = false) String status,
            Model model) {

        EquipmentStatus statusFilter = StringUtils.hasText(status) ? EquipmentStatus.valueOf(status) : null;

        model.addAttribute("equipmentList", equipmentService.search(name, statusFilter));
        model.addAttribute("name", name);
        model.addAttribute("status", statusFilter);
        model.addAttribute("statuses", EquipmentStatus.values());
        return "equipment/list";
    }
}
