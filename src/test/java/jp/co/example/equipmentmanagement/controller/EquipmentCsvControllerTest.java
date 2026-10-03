package jp.co.example.equipmentmanagement.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import jp.co.example.equipmentmanagement.entity.EquipmentStatus;
import jp.co.example.equipmentmanagement.entity.Lending;
import jp.co.example.equipmentmanagement.repository.EquipmentRepository;
import jp.co.example.equipmentmanagement.repository.LendingRepository;
import jp.co.example.equipmentmanagement.service.EquipmentService;

/**
 * 備品一覧CSVダウンロード（GET /equipment/csv）の結合テスト。
 * ロール別のアクセス可否、レスポンスヘッダー、出力内容と一覧画面との一致を確認する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EquipmentCsvControllerTest {

    private static final String HEADER = "品名,管理番号,保管場所,状態,購入日,借用者";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private LendingRepository lendingRepository;

    @Autowired
    private EquipmentService equipmentService;

    @Test
    @WithMockUser(roles = "USER")
    void USERはCSVをダウンロードできる() throws Exception {
        mockMvc.perform(get("/equipment/csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        Matchers.matchesPattern("attachment; filename=\"equipment_\\d{8}_\\d{6}\\.csv\"")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void ADMINはCSVをダウンロードできる() throws Exception {
        mockMvc.perform(get("/equipment/csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"));
    }

    @Test
    @WithAnonymousUser
    void 未認証ではCSVを取得できずログイン画面へリダイレクトされる() throws Exception {
        mockMvc.perform(get("/equipment/csv"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void 条件なしでは全備品が見出し行付きで出力される() throws Exception {
        List<String> lines = csvLines(mockMvc.perform(get("/equipment/csv")).andReturn());

        assertThat(lines.get(0)).isEqualTo(HEADER);
        assertThat(lines).hasSize((int) equipmentRepository.count() + 1);
    }

    @Test
    @WithMockUser(roles = "USER")
    void 貸出中の備品の行には現在の借用者名が出力される() throws Exception {
        List<Lending> activeLendings = lendingRepository.findAllByReturnedAtIsNull();
        assertThat(activeLendings).isNotEmpty();

        List<String> lines = csvLines(mockMvc.perform(get("/equipment/csv")).andReturn());

        for (Lending lending : activeLendings) {
            String assetNumber = lending.getEquipment().getAssetNumber();
            assertThat(lines).anySatisfy(line -> assertThat(line)
                    .contains("," + assetNumber + ",")
                    .contains(",貸出中,")
                    .endsWith("," + lending.getEmployee().getName()));
        }
    }

    @Test
    @WithMockUser(roles = "USER")
    void USERの一覧画面にCSVダウンロードリンクが表示される() throws Exception {
        mockMvc.perform(get("/equipment"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("href=\"/equipment/csv?name=&amp;status=\"")))
                .andExpect(content().string(Matchers.containsString("CSVダウンロード")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void ADMINの一覧画面にCSVダウンロードリンクが表示される() throws Exception {
        mockMvc.perform(get("/equipment"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("href=\"/equipment/csv?name=&amp;status=\"")));
    }

    @Test
    @WithMockUser(roles = "USER")
    void 状態で絞り込むと該当状態の備品だけが出力される() throws Exception {
        List<String> lines = csvLines(mockMvc.perform(get("/equipment/csv").param("status", "BROKEN")).andReturn());

        List<String> rows = lines.subList(1, lines.size());
        assertThat(rows).isNotEmpty().allSatisfy(row -> assertThat(row).contains(",故障中,"));
        assertThat(rows).hasSize(equipmentService.search(null, EquipmentStatus.BROKEN).size());
    }

    @Test
    @WithMockUser(roles = "USER")
    void 品名で絞り込むと部分一致する備品だけが出力される() throws Exception {
        List<String> lines = csvLines(mockMvc.perform(get("/equipment/csv").param("name", "PC")).andReturn());

        List<String> rows = lines.subList(1, lines.size());
        assertThat(rows).isNotEmpty().allSatisfy(row -> assertThat(row.split(",")[0]).contains("PC"));
        assertThat(rows).hasSize(equipmentService.search("PC", null).size());
    }

    @Test
    @WithMockUser(roles = "USER")
    void 品名と状態の両方で絞り込むと一覧と同じ備品が同じ順序で出力される() throws Exception {
        List<String> lines = csvLines(mockMvc.perform(get("/equipment/csv")
                .param("name", "PC").param("status", "LENT")).andReturn());

        List<String> expectedAssetNumbers = equipmentService.search("PC", EquipmentStatus.LENT).stream()
                .map(equipment -> equipment.getAssetNumber())
                .toList();
        assertThat(expectedAssetNumbers).isNotEmpty();
        assertThat(lines.subList(1, lines.size()).stream().map(row -> row.split(",")[1]).toList())
                .isEqualTo(expectedAssetNumbers);
    }

    @Test
    @WithMockUser(roles = "USER")
    void 該当0件なら見出し行のみが出力される() throws Exception {
        List<String> lines = csvLines(mockMvc.perform(get("/equipment/csv").param("name", "存在しない品名XYZ")).andReturn());

        assertThat(lines).containsExactly(HEADER);
    }

    @Test
    @WithMockUser(roles = "USER")
    void 空文字の検索条件は条件なしと同じ結果になる() throws Exception {
        List<String> lines = csvLines(mockMvc.perform(get("/equipment/csv")
                .param("name", "").param("status", "")).andReturn());

        assertThat(lines).hasSize((int) equipmentRepository.count() + 1);
    }

    @Test
    @WithMockUser(roles = "USER")
    void 不正な状態値でCSVを要求すると一覧へリダイレクトしエラーを表示する() throws Exception {
        mockMvc.perform(get("/equipment/csv").param("status", "XXX"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/equipment"))
                .andExpect(flash().attribute("error", "検索条件が不正です"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void 不正な状態値で一覧を表示しようとすると一覧へリダイレクトしエラーを表示する() throws Exception {
        mockMvc.perform(get("/equipment").param("status", "XXX"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/equipment"))
                .andExpect(flash().attribute("error", "検索条件が不正です"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void 一覧のCSVダウンロードリンクには表示中の検索条件が埋め込まれる() throws Exception {
        mockMvc.perform(get("/equipment").param("name", "PC").param("status", "BROKEN"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("href=\"/equipment/csv?name=PC&amp;status=BROKEN\"")));
    }

    /** レスポンスのBOMを検証して除去し、CRLFで行に分割する（末尾の空要素は含めない） */
    private List<String> csvLines(MvcResult result) {
        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertThat(Arrays.copyOf(bytes, 3)).containsExactly(0xEF, 0xBB, 0xBF);
        String body = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        assertThat(body).endsWith("\r\n");
        return List.of(body.split("\r\n"));
    }
}
