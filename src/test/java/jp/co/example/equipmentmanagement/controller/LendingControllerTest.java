package jp.co.example.equipmentmanagement.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import jp.co.example.equipmentmanagement.entity.Employee;
import jp.co.example.equipmentmanagement.entity.Equipment;
import jp.co.example.equipmentmanagement.entity.EquipmentStatus;
import jp.co.example.equipmentmanagement.repository.EmployeeRepository;
import jp.co.example.equipmentmanagement.repository.EquipmentRepository;

/**
 * 貸出・返却機能の結合テスト。ADMIN/USERどちらでも操作できることを含め、
 * Controller-Service間の連携（状態遷移・エラーハンドリング）を確認する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LendingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Test
    @WithMockUser(roles = "USER")
    void USERが利用可の備品を貸出でき状態が貸出中になる() throws Exception {
        Equipment available = equipmentRepository.findAll().stream()
                .filter(equipment -> equipment.getStatus() == EquipmentStatus.AVAILABLE)
                .findFirst()
                .orElseThrow();
        Employee employee = employeeRepository.findAll().get(0);

        mockMvc.perform(post("/equipment/{id}/lend", available.getId()).with(csrf())
                        .param("employeeId", employee.getId().toString())
                        .param("dueDate", "2026-12-31"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("message"));

        Equipment updated = equipmentRepository.findById(available.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(EquipmentStatus.LENT);
    }

    @Test
    @WithMockUser(roles = "USER")
    void 貸出中の備品への貸出はエラーになる() throws Exception {
        Equipment lent = equipmentRepository.findAll().stream()
                .filter(equipment -> equipment.getStatus() == EquipmentStatus.LENT)
                .findFirst()
                .orElseThrow();
        Employee employee = employeeRepository.findAll().get(0);

        mockMvc.perform(post("/equipment/{id}/lend", lent.getId()).with(csrf())
                        .param("employeeId", employee.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void 借用者が未選択だと貸出が拒否される() throws Exception {
        Equipment available = equipmentRepository.findAll().stream()
                .filter(equipment -> equipment.getStatus() == EquipmentStatus.AVAILABLE)
                .findFirst()
                .orElseThrow();

        mockMvc.perform(post("/equipment/{id}/lend", available.getId()).with(csrf())
                        .param("employeeId", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));

        Equipment unchanged = equipmentRepository.findById(available.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(EquipmentStatus.AVAILABLE);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void ADMINが貸出中の備品を返却すると利用可に戻る() throws Exception {
        Equipment lent = equipmentRepository.findAll().stream()
                .filter(equipment -> equipment.getStatus() == EquipmentStatus.LENT)
                .findFirst()
                .orElseThrow();

        mockMvc.perform(post("/equipment/{id}/return", lent.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("message"));

        Equipment updated = equipmentRepository.findById(lent.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(EquipmentStatus.AVAILABLE);
    }

    @Test
    @WithMockUser(roles = "USER")
    void 貸出履歴画面が表示できる() throws Exception {
        mockMvc.perform(get("/lendings"))
                .andExpect(status().isOk())
                .andExpect(view().name("lending/list"));
    }
}
