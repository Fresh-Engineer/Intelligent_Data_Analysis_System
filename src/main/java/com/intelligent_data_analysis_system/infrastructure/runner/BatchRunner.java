package com.intelligent_data_analysis_system.infrastructure.runner;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelligent_data_analysis_system.infrastructure.runner.dto.ProblemItem;
import com.intelligent_data_analysis_system.service.*;
import com.intelligent_data_analysis_system.utils.*;
import com.intelligent_data_analysis_system.service.SqlExecuteService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;

@Component
@RequiredArgsConstructor
public class BatchRunner implements CommandLineRunner {

    private final ObjectMapper objectMapper;
    private final SqlGenerator sqlGenerator;           // 现在是 Stub，将来换成 LLM 版
    private final SqlExecuteService sqlExecuteService;
    private final AnswerRenderer answerRenderer;

    private static final int EXCEL_CELL_MAX = 32767;

    private String safeCell(String s) {
        if (s == null) return "";
        // Excel 单元格限制：最多 32767
        if (s.length() <= EXCEL_CELL_MAX) return s;
        return s.substring(0, EXCEL_CELL_MAX - 50) + "\n...[TRUNCATED " + (s.length() - (EXCEL_CELL_MAX - 50)) + " chars]";
    }

    private String dumpIfTooLong(String tag, int id, String s) {
        if (s == null) return "";
        if (s.length() <= EXCEL_CELL_MAX) return ""; // 不需要落盘
        try {
            String name = "target/" + tag + "_" + id + "_" + System.currentTimeMillis() + ".txt";
            try (var fos = new FileOutputStream(name)) {
                fos.write(s.getBytes(StandardCharsets.UTF_8));
            }
            return name; // 返回文件路径，写到 error_msg 或单独一列都行
        } catch (Exception e) {
            return "dump_failed:" + e.getMessage();
        }
    }


    @Override
    public void run(String... args) throws Exception {

        Random random = new Random();
        String mode = parseMode(args); // dev / submit
        System.out.println("Batch mode = " + mode);

        // 输出文件：target/resultsXX.xlsx
        String outPath = Paths.get("target", "results" + random.nextInt(100) + ".xlsx").toString();

        List<ProblemItem> all = new ArrayList<>();
        all.addAll(loadFromClasspath("static/merge_sql_problems1.json", "FINANCE"));
        all.addAll(loadFromClasspath("static/merge_sql_problems2.json", "HEALTHCARE"));

        String[] headers = {
                "id","level","domain","problem",
                "gold_sql","pred_sql",
                "gold_answer","pred_answer",
                "is_correct",
                "status","error_msg"
        };

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("results");

            // 自动换行样式
            CellStyle wrapStyle = workbook.createCellStyle();
            wrapStyle.setWrapText(true);
            wrapStyle.setVerticalAlignment(VerticalAlignment.TOP);

            // 表头
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            int rowIdx = 1;

            for (ProblemItem item : all) {

                int id = item.getId() == null ? -1 : item.getId();
                String level = item.getLevel() == null ? "" : item.getLevel();
                String problem = item.getProblem() == null ? "" : item.getProblem().trim();

                // domain：以你 loadFromClasspath set 的为准
                String domain = item.getDomain() == null ? "FINANCE" : item.getDomain().trim();

                String goldSql = item.getSql() == null ? "" : item.getSql().trim();
                String predSql = "";

                // 展示用（写Excel）：用 renderer，短、可读
                String goldAnsShow = "";
                String predAnsShow = "";

                // 判分用（不写Excel，避免超长）：canon 串
                String goldCanon = "";
                String predCanon = "";

                String status = "fail";
                String error = "";
                boolean isCorrect = false;

                long t0 = System.currentTimeMillis();
                try {
                    // 1) gold 执行
                    if (!goldSql.isBlank()) {
                        SqlGuard.validateReadOnlySingleStatement(goldSql);
                        List<Map<String, Object>> goldRows = sqlExecuteService.query(domain, goldSql);

                        goldCanon = ResultCanonicalizer.canonicalize(goldRows);
                        goldAnsShow = answerRenderer.render(problem, goldRows);
                    }

                    // 2) pred 生成 + 执行
                    SqlGenResult gen = sqlGenerator.generate(problem);
                    predSql = gen.getSql() == null ? "" : gen.getSql().trim();
                    if (predSql.isBlank()) throw new IllegalStateException("pred_sql为空");

                    SqlGuard.validateReadOnlySingleStatement(predSql);
                    List<Map<String, Object>> predRows = sqlExecuteService.query(domain, predSql);

                    predCanon = ResultCanonicalizer.canonicalize(predRows);
                    predAnsShow = answerRenderer.render(problem, predRows);

                    // 3) 判分：canon 严格对比
                    isCorrect = !goldCanon.isBlank() && goldCanon.equals(predCanon);

                    status = "success";
                } catch (Exception ex) {
                    status = "fail";
                    error = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                }

                long t1 = System.currentTimeMillis();
                String errorWithLatency = error + (error.isBlank() ? "" : (" | latency_ms=" + (t1 - t0)));

                // --- 写入 Excel ---
                Row row = sheet.createRow(rowIdx++);
                int col = 0;

                row.createCell(col++).setCellValue(id);
                row.createCell(col++).setCellValue(level);
                row.createCell(col++).setCellValue(domain);

                Cell pCell = row.createCell(col++);
                pCell.setCellValue(excelSafe(problem));
                pCell.setCellStyle(wrapStyle);

                Cell gSqlCell = row.createCell(col++);
                gSqlCell.setCellValue(excelSafe(goldSql));
                gSqlCell.setCellStyle(wrapStyle);

                Cell pSqlCell = row.createCell(col++);
                pSqlCell.setCellValue(excelSafe(predSql));
                pSqlCell.setCellStyle(wrapStyle);

                Cell gAnsCell = row.createCell(col++);
                gAnsCell.setCellValue(excelSafe(goldAnsShow));
                gAnsCell.setCellStyle(wrapStyle);

                Cell pAnsCell = row.createCell(col++);
                pAnsCell.setCellValue(excelSafe(predAnsShow));
                pAnsCell.setCellStyle(wrapStyle);

                row.createCell(col++).setCellValue(isCorrect);
                row.createCell(col++).setCellValue(status);

                Cell errCell = row.createCell(col++);
                errCell.setCellValue(excelSafe(errorWithLatency));
                errCell.setCellStyle(wrapStyle);
            }

            // 关键列拉宽（否则看起来像没写进去）
            sheet.setColumnWidth(3, 60 * 256);  // problem
            sheet.setColumnWidth(4, 80 * 256);  // gold_sql
            sheet.setColumnWidth(5, 80 * 256);  // pred_sql
            sheet.setColumnWidth(6, 60 * 256);  // gold_answer（renderer后不需要100那么夸张）
            sheet.setColumnWidth(7, 60 * 256);  // pred_answer
            sheet.setColumnWidth(10, 60 * 256); // error_msg

            try (FileOutputStream fos = new FileOutputStream(outPath)) {
                workbook.write(fos);
            }
        }

        System.out.println("Loaded problems: " + all.size());
        System.out.println("Batch finished. Output: " + outPath);
    }

    /**
     * Excel 单元格文本最大 32767，POI 会直接抛异常。
     * 这里做硬截断兜底，避免再炸。
     */
    private String excelSafe(String s) {
        if (s == null) return "";
        // Excel 上限 32767，留一点余量给你加标记
        int MAX = 32000;
        if (s.length() <= MAX) return s;
        return s.substring(0, MAX) + "\n...(truncated, len=" + s.length() + ")";
    }



    private List<ProblemItem> loadFromClasspath(String classpath, String domainFromFile) throws Exception
    {
        ClassPathResource res = new ClassPathResource(classpath);
        try (InputStream in = res.getInputStream()) {
            String txt = new String(in.readAllBytes());
            txt = txt.trim();

            // 1) 顶层是数组：直接读
            if (txt.startsWith("[")) {
                return objectMapper.readValue(txt, new TypeReference<List<ProblemItem>>() {});
            }

            // 2) 顶层是对象：可能是 {data:[...]} 或 {初级:[...],中级:[...],高级:[...]}
            Map<String, Object> root = objectMapper.readValue(txt, new TypeReference<Map<String, Object>>() {});

            // 2.1 data 模式
            if (root.containsKey("data")) {
                String dataJson = objectMapper.writeValueAsString(root.get("data"));
                return objectMapper.readValue(dataJson, new TypeReference<List<ProblemItem>>() {});
            }

            // 2.2 分级模式：初级/中级/高级
            List<ProblemItem> all = new ArrayList<>();
            for (String lvl : List.of("初级", "中级", "高级")) {
                Object v = root.get(lvl);
                if (v instanceof List<?> list) {
                    String arrJson = objectMapper.writeValueAsString(list);
                    List<ProblemItem> items = objectMapper.readValue(arrJson, new TypeReference<List<ProblemItem>>() {});
                    // 写入 level（如果你加了字段）
                    for (ProblemItem it : items) {
                        it.setLevel(lvl);
                        it.setDomain(domainFromFile);
                    }

                    all.addAll(items);
                }
            }

            // 兜底：如果键名不是这三个，也把所有 list<dict> 都拼起来
            if (all.isEmpty()) {
                for (Map.Entry<String, Object> e : root.entrySet()) {
                    Object v = e.getValue();
                    if (v instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map) {
                        String arrJson = objectMapper.writeValueAsString(list);
                        List<ProblemItem> items = objectMapper.readValue(arrJson, new TypeReference<List<ProblemItem>>() {});
                        for (ProblemItem it : items) {
                            it.setLevel(e.getKey());
                            it.setDomain(domainFromFile);
                        }

                        all.addAll(items);
                    }
                }
            }

            return all;
        }
    }


    private String parseMode(String... args) {
        // 默认 submit（更贴近最终评测）
        String mode = "submit";
        if (args == null) return mode;
        for (String a : args) {
            if (a == null) continue;
            if (a.startsWith("--mode=")) {
                mode = a.substring("--mode=".length()).trim();
            }
        }
        return mode;
    }



}
