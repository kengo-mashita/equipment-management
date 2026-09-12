package jp.co.example.equipmentmanagement.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import jp.co.example.equipmentmanagement.entity.Equipment;
import jp.co.example.equipmentmanagement.entity.EquipmentStatus;
import jp.co.example.equipmentmanagement.entity.Lending;
import jp.co.example.equipmentmanagement.repository.EquipmentRepository;
import jp.co.example.equipmentmanagement.repository.LendingRepository;

/**
 * 備品CRUD（登録・編集・削除）の結合テスト。バリデーション表示や
 * 貸出履歴による削除制限など、Controller-Service間の連携を確認する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "ADMIN")
class EquipmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private LendingRepository lendingRepository;

    @Test
    void 新規登録が成功すると一覧にリダイレクトされ保存される() throws Exception {
        mockMvc.perform(post("/equipment").with(csrf())
                        .param("name", "デスクトップPC")
                        .param("assetNumber", "EQ-9001")
                        .param("status", "AVAILABLE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/equipment"))
                .andExpect(flash().attributeExists("message"));

        assertThat(equipmentRepository.existsByAssetNumber("EQ-9001")).isTrue();
    }

    @Test
    void 管理番号が重複していると登録が拒否されエラーが表示される() throws Exception {
        Equipment existing = equipmentRepository.findAll().get(0);

        mockMvc.perform(post("/equipment").with(csrf())
                        .param("name", "テスト機器")
                        .param("assetNumber", existing.getAssetNumber())
                        .param("status", "AVAILABLE"))
                .andExpect(status().isOk())
                .andExpect(view().name("equipment/form"))
                .andExpect(model().attributeHasFieldErrors("equipmentForm", "assetNumber"));
    }

    @Test
    void 品名が空だと登録が拒否される() throws Exception {
        mockMvc.perform(post("/equipment").with(csrf())
                        .param("name", "")
                        .param("assetNumber", "EQ-9002")
                        .param("status", "AVAILABLE"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("equipmentForm", "name"));
    }

    @Test
    void 編集が成功すると内容が更新される() throws Exception {
        Equipment target = equipmentRepository.save(Equipment.builder()
                .name("編集前")
                .assetNumber("EQ-9003")
                .status(EquipmentStatus.AVAILABLE)
                .build());

        mockMvc.perform(post("/equipment/{id}/edit", target.getId()).with(csrf())
                        .param("name", "編集後")
                        .param("assetNumber", "EQ-9003")
                        .param("location", "本社4F")
                        .param("status", "AVAILABLE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/equipment"));

        Equipment updated = equipmentRepository.findById(target.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("編集後");
        assertThat(updated.getLocation()).isEqualTo("本社4F");
    }

    @Test
    void 存在しないIDの編集画面はエラーメッセージ付きで一覧にリダイレクトされる() throws Exception {
        mockMvc.perform(get("/equipment/{id}/edit", 999999L))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/equipment"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void 貸出履歴のある備品は削除が拒否される() throws Exception {
        Lending activeLending = lendingRepository.findAllByReturnedAtIsNull().get(0);
        Long equipmentId = activeLending.getEquipment().getId();

        mockMvc.perform(post("/equipment/{id}/delete", equipmentId).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));

        assertThat(equipmentRepository.existsById(equipmentId)).isTrue();
    }

    @Test
    void 貸出履歴のない備品は削除できる() throws Exception {
        Equipment noHistory = equipmentRepository.save(Equipment.builder()
                .name("削除確認用機器")
                .assetNumber("EQ-9999")
                .status(EquipmentStatus.AVAILABLE)
                .build());

        mockMvc.perform(post("/equipment/{id}/delete", noHistory.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("message"));

        assertThat(equipmentRepository.existsById(noHistory.getId())).isFalse();
    }
}
