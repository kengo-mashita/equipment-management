package jp.co.example.equipmentmanagement.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import jp.co.example.equipmentmanagement.entity.Equipment;
import jp.co.example.equipmentmanagement.entity.EquipmentStatus;
import jp.co.example.equipmentmanagement.repository.EquipmentRepository;
import jp.co.example.equipmentmanagement.service.EquipmentService;

/**
 * 備品一覧CSVダウンロード（GET /equipment/csv）の結合テスト。
 * レスポンスヘッダー・BOM・列見出し・ロールごとの利用可否・一覧画面のリンクを確認する。
 * 初期データが存在しうるため、期待値は EquipmentService.search の結果または本テストで作成した行で判定する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EquipmentCsvExportControllerTest {

    private static final String HEADER = "品名,管理番号,保管場所,状態,購入日,借用者\r\n";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private EquipmentService equipmentService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void ADMINは全件のCSVをダウンロードできる() throws Exception {
        equipmentRepository.save(equipment("CSV試験機器, 15インチ", "EQ-CSV-001", EquipmentStatus.AVAILABLE));

        MvcResult result = mockMvc.perform(get("/equipment/csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("text/csv;charset=UTF-8")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        matchesPattern("attachment; filename=\"equipment_\\d{8}_\\d{6}\\.csv\"")))
                .andReturn();

        byte[] csv = result.getResponse().getContentAsByteArray();
        assertThat(Arrays.copyOf(csv, 3)).containsExactly(0xEF, 0xBB, 0xBF);
        String body = body(csv);
        assertThat(body).startsWith(HEADER);
        // カンマを含む品名は1セルとしてクォートされる
        assertThat(body).contains("\"CSV試験機器, 15インチ\",EQ-CSV-001,");
        // 見出し行 + 全備品の行
        assertThat(dataLines(body)).hasSize(equipmentService.search(null, null).size());
    }

    @Test
    void USERもADMINと同じ内容のCSVをダウンロードできる() throws Exception {
        equipmentRepository.save(equipment("CSV試験機器", "EQ-CSV-002", EquipmentStatus.AVAILABLE));

        byte[] adminCsv = mockMvc.perform(get("/equipment/csv").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        byte[] userCsv = mockMvc.perform(get("/equipment/csv").with(user("user").roles("USER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(userCsv).isEqualTo(adminCsv);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void ADMINの一覧画面にCSVダウンロードのリンクが表示される() throws Exception {
        mockMvc.perform(get("/equipment"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/equipment/csv")))
                .andExpect(content().string(containsString("CSVダウンロード")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void USERの一覧画面にもCSVダウンロードのリンクが表示される() throws Exception {
        mockMvc.perform(get("/equipment"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/equipment/csv")))
                .andExpect(content().string(containsString("CSVダウンロード")));
    }

    private String body(byte[] csv) {
        return new String(csv, 3, csv.length - 3, StandardCharsets.UTF_8);
    }

    /** 見出し行を除いたデータ行（本テストのデータは値に改行を含まないため CRLF で分割できる） */
    private List<String> dataLines(String body) {
        List<String> lines = Arrays.asList(body.split("\r\n"));
        return lines.subList(1, lines.size());
    }

    private Equipment equipment(String name, String assetNumber, EquipmentStatus status) {
        return Equipment.builder()
                .name(name)
                .assetNumber(assetNumber)
                .status(status)
                .build();
    }
}
