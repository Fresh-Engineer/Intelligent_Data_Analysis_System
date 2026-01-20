package com.intelligent_data_analysis_system.infrastructure.runner;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelligent_data_analysis_system.LLM.QWenSqlGenerator;
import com.intelligent_data_analysis_system.infrastructure.runner.dto.AdvancedReportItem;
import com.intelligent_data_analysis_system.infrastructure.runner.dto.ProblemItem;
import com.intelligent_data_analysis_system.infrastructure.runner.dto.QueryResult;
import com.intelligent_data_analysis_system.infrastructure.runner.dto.SubmitItem;
import com.intelligent_data_analysis_system.service.AiText2SqlService;
import com.intelligent_data_analysis_system.service.SqlExecuteService;
import com.intelligent_data_analysis_system.service.SqlSelfCheckService;
import com.intelligent_data_analysis_system.utils.Fixer.FinanceProjectionFixer;
import com.intelligent_data_analysis_system.utils.Fixer.HealthcareProjectionFixer;
import com.intelligent_data_analysis_system.utils.Generator.SqlGenResult;
import com.intelligent_data_analysis_system.utils.Generator.SqlGuard;
import com.intelligent_data_analysis_system.utils.Pruner.QueryResultPruner;
import com.intelligent_data_analysis_system.utils.RuleFallback;
import com.intelligent_data_analysis_system.utils.SqlExceptionRepair;
import com.intelligent_data_analysis_system.utils.SqlPatchPipeline;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Profile("batch")
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.batch.enabled", havingValue = "true", matchIfMissing = false)
public class BatchRunner implements CommandLineRunner {

    private final ObjectMapper objectMapper;
    private final QWenSqlGenerator sqlGenerator;
    private final SqlExecuteService sqlExecuteService;
    private final SqlSelfCheckService sqlSelfCheckService;
    private final AiText2SqlService aiText2SqlService;

    // 你可以按需要调
    private static final int MAX_RETRY_PER_PROBLEM = 3;
    private static final int ADV_TABLE_MAX_ROWS = 30;     // 高级报告表格最多展示前 N 行
    private static final int SUBMIT_MAX_CHARS = 180;       // 初中级 answer 字段最大长度（避免“答案太长影响得分”）
    private static final int SUBMIT_MAX_ROWS_INLINE = 12;  // 初中级多行结果最多拼接多少行
    private static final DecimalFormat DF = new DecimalFormat("0.########");

    @Override
    public void run(String... args) throws Exception {
        String mode = parseMode(args); // dev / submit（你现在其实不靠它也行）
        System.out.println("Batch mode = " + mode);

        List<ProblemItem> all = new ArrayList<>();
        all.addAll(loadFromClasspath("static/merge_sql_problems1.json", "FINANCE"));
        all.addAll(loadFromClasspath("static/merge_sql_problems2.json", "HEALTHCARE"));

        // ✅ 初中级：按领域分别写入这两个数组，最后落地 answer_finance.json / answer_healthcare.json
        List<SubmitItem> financeSubmit = new ArrayList<>();
        List<SubmitItem> healthcareSubmit = new ArrayList<>();

        // ✅ 高级：按领域分别写入两份报告 items，最后落地 finance_report.html / healthcare_report.html
        List<AdvancedReportItem> financeAdvancedItems = new ArrayList<>();
        List<AdvancedReportItem> healthcareAdvancedItems = new ArrayList<>();

        for (ProblemItem item : all) {
            int id = item.getId() == null ? -1 : item.getId();
            String level = safe(item.getLevel());
            String problem = safe(item.getProblem()).trim();
            String domain = safe(item.getDomain());
            if (domain.isBlank()) domain = "FINANCE";

            boolean isFinance = "FINANCE".equalsIgnoreCase(domain);
            boolean isHealthcare = "HEALTHCARE".equalsIgnoreCase(domain);

            boolean isAdvanced = "高级".equals(level);

            // 题目必须不丢：即使失败也要输出占位（初中级必须输出四字段）
            String finalSql = "";
            String finalAnswer = "";

            QueryResult predQrForChart = null;

            // ====== 核心：最多重试 3 次，失败也继续下一个题 ======
            for (int attempt = 1; attempt <= MAX_RETRY_PER_PROBLEM; attempt++) {
                try {
                    // 1) 生成 SQL（LLM -> fallback）
                    SqlGenResult gen = sqlGenerator.generate(domain, problem);
                    String predSql = gen == null ? "" : safe(gen.getSql()).trim();

                    if (predSql.isBlank()) {
                        predSql = safe(RuleFallback.tryBuild(domain, problem)).trim();
                    }
                    if (predSql.isBlank()) {
                        // 继续重试
                        continue;
                    }

                    // 2) 清洗：去代码块围栏 + 反转义 \n \t \r（修你说的“sql 里存进 \\n”）
                    predSql = normalizeSqlText(predSql);

                    // 3) 只读单条校验
                    SqlGuard.validateReadOnlySingleStatement(predSql);

                    // 4) SQL 自检（结构 hint），必要时重写一次
                    SqlSelfCheckService.CheckResult check = sqlSelfCheckService.check(problem, predSql);
                    if (check != null && !check.ok) {
                        SqlGenResult retry = aiText2SqlService.rewriteWithHint(domain, problem, predSql, check.hint);
                        if (retry != null && retry.getSql() != null && !retry.getSql().isBlank()) {
                            predSql = normalizeSqlText(retry.getSql());
                            SqlGuard.validateReadOnlySingleStatement(predSql);
                        }
                    }

                    // 5) 领域投影兜底 + patch pipeline
                    if (isFinance) predSql = FinanceProjectionFixer.fix(problem, predSql);
                    if (isHealthcare) predSql = HealthcareProjectionFixer.fix(problem, predSql);
                    predSql = SqlPatchPipeline.apply(domain, problem, predSql, check);

                    // 6) 执行（初中级允许做异常修复，高级不做“错误展示”，但依旧可尝试轻修复一次）
                    List<Map<String, Object>> predRows;
                    try {
                        predRows = sqlExecuteService.query(domain, predSql);
                    } catch (Exception e) {
                        // 初/中：尝试别名/语法修复再执行
                        if (!isAdvanced) {
                            String aliased = SqlExceptionRepair.tryAliasRepair(domain, predSql, e);
                            if (!aliased.equals(predSql)) {
                                predSql = aliased;
                                predRows = sqlExecuteService.query(domain, predSql);
                            } else {
                                String repaired = SqlExceptionRepair.tryRepair(predSql, e);
                                if (!repaired.equals(predSql)) {
                                    predSql = repaired;
                                    predRows = sqlExecuteService.query(domain, predSql);
                                } else {
                                    throw e;
                                }
                            }
                        } else {
                            // 高级：不展示错误、不阻塞整个 batch；进入下一次 attempt
                            continue;
                        }
                    }

                    // 7) QueryResult + 裁剪
                    QueryResult predQr = toQueryResult(predRows);
                    predQr = QueryResultPruner.pruneByIntent(domain, problem, predQr);

                    // 8) 初中级：提交用极简答案（修你说的 answer 多余一堆东西）
                    finalSql = predSql;
                    finalAnswer = buildSubmitAnswer(problem, predQr);

                    // 9) 高级：图表数据来源（用裁剪后的结果）
                    predQrForChart = predQr;

                    // 成功就 break
                    break;

                } catch (Exception ignore) {
                    // 不打印、不阻塞，继续下一次 attempt
                }
            }

            // ====== 输出归档 ======
            if (!isAdvanced) {
                // ✅ 初/中：严格 4 字段
                SubmitItem si = new SubmitItem(
                        String.valueOf(id),
                        problem,
                        safe(finalSql),
                        safe(finalAnswer)
                );
                if (isFinance) financeSubmit.add(si);
                else if (isHealthcare) healthcareSubmit.add(si);
            } else {
                // ✅ 高级：按题分节，绝不包含 gold/pred/error/耗时
                AdvancedReportItem ari = (predQrForChart == null)
                        ? buildEmptyAdvancedItem(String.valueOf(id), problem, "无可用数据（执行未成功或结果为空）")
                        : buildAdvancedItemFromResult(String.valueOf(id), problem, predQrForChart);

                if (isFinance) financeAdvancedItems.add(ari);
                else if (isHealthcare) healthcareAdvancedItems.add(ari);
            }
        }

        // ✅ 文件名固定（你要求的）
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(new File("answer_finance.json"), financeSubmit);
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(new File("answer_healthcare.json"), healthcareSubmit);

        generatePerQuestionReportHtml("金融", financeAdvancedItems, "finance_report.html");
        generatePerQuestionReportHtml("医疗", healthcareAdvancedItems, "healthcare_report.html");

        System.out.println("输出完成：answer_finance.json / answer_healthcare.json / finance_report.html / healthcare_report.html");
        System.out.println("FINANCE 初中级条目数=" + financeSubmit.size() + "，FINANCE 高级条目数=" + financeAdvancedItems.size());
        System.out.println("HEALTHCARE 初中级条目数=" + healthcareSubmit.size() + "，HEALTHCARE 高级条目数=" + healthcareAdvancedItems.size());
    }

    private AdvancedReportItem buildEmptyAdvancedItem(String id, String query, String note) {
        AdvancedReportItem a = new AdvancedReportItem();
        a.setId(id);
        a.setQuery(query);
        a.setNarrative("本题为进阶分析题。当前暂无可用结果用于可视化展示：" + note + "。");
        a.setTableHtml("<div class='muted'>无数据表可展示</div>");
        a.getBullets().add("建议：检查 SQL 生成是否命中关键表与关键字段。");
        a.getBullets().add("建议：如需可视化，优先返回“分类/时间序列/TopN”结构化结果。");
        return a;
    }

    // =========================
    // 初中级：提交用极简答案（避免“多余一堆东西”）
    // =========================
    private String buildSubmitAnswer(String problem, QueryResult qr) {
        if (qr == null || qr.getRows() == null || qr.getRows().isEmpty() || qr.getColumns() == null) {
            return "";
        }

        List<String> cols = qr.getColumns();
        List<List<Object>> rows = qr.getRows();

        // 1) 单值（最常见、最稳）
        if (rows.size() == 1 && cols.size() == 1) {
            Object v = rows.get(0).get(0);
            return clip(oneValueToString(v), SUBMIT_MAX_CHARS);
        }

        // 2) 1 行多列：拼成 “col=value; col=value”
        if (rows.size() == 1 && cols.size() <= 6) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < cols.size(); i++) {
                if (i > 0) sb.append("；");
                sb.append(cols.get(i)).append("=").append(oneValueToString(rows.get(0).get(i)));
            }
            return clip(sb.toString(), SUBMIT_MAX_CHARS);
        }

        // 3) 多行：最多拼接前 N 行（每行做紧凑键值）
        int take = Math.min(rows.size(), SUBMIT_MAX_ROWS_INLINE);
        int takeCols = Math.min(cols.size(), 4); // 过多列会变长影响评分
        List<String> lineParts = new ArrayList<>();
        for (int r = 0; r < take; r++) {
            List<Object> row = rows.get(r);
            if (takeCols == 1) {
                lineParts.add(oneValueToString(row.get(0)));
            } else {
                StringBuilder one = new StringBuilder();
                for (int c = 0; c < takeCols; c++) {
                    if (c > 0) one.append(",");
                    one.append(oneValueToString(row.get(c)));
                }
                lineParts.add(one.toString());
            }
        }
        String joined = String.join("；", lineParts);
        return clip(joined, SUBMIT_MAX_CHARS);
    }

    private String oneValueToString(Object v) {
        if (v == null) return "";
        if (v instanceof Number n) return DF.format(n.doubleValue());
        String s = String.valueOf(v).trim();
        // 去掉多余空白
        s = s.replaceAll("\\s+", " ");
        return s;
    }

    private String clip(String s, int max) {
        if (s == null) return "";
        s = s.trim();
        if (s.length() <= max) return s;
        return s.substring(0, max);
    }

    // =========================
    // 高级报告：从结果构建图文并茂内容
    // 规则：一定有 table；图最多 2 张；不展示错误/耗时/对比
    // =========================
    private AdvancedReportItem buildAdvancedItemFromResult(String id, String query, QueryResult qr) {
        AdvancedReportItem a = new AdvancedReportItem();
        a.setId(id);
        a.setQuery(query);

        // 1) 叙述（轻量、通用，不引入 gold/pred）
        a.setNarrative(buildNarrative(query, qr));

        // 2) 表格（一定展示：修“没图就连数据都不放”）
        a.setTableHtml(toHtmlTable(qr, ADV_TABLE_MAX_ROWS));

        // 3) 图（最多 2 张：修“图片拥塞”）
        List<String> charts = buildCharts(qr);
        if (charts.size() > 2) charts = charts.subList(0, 2);
        a.setChartSvgs(charts);

        // 4) 要点 bullets（给评委看的）
        a.getBullets().addAll(buildBullets(qr));

        return a;
    }

    private String buildNarrative(String query, QueryResult qr) {
        int rowCount = qr.getRows() == null ? 0 : qr.getRows().size();
        int colCount = qr.getColumns() == null ? 0 : qr.getColumns().size();

        // 简单意图识别：时间序列/TopN/占比
        boolean timeLike = looksLikeTimeSeries(qr);
        boolean categoryLike = looksLikeCategory(qr);

        StringBuilder sb = new StringBuilder();
        sb.append("本题为进阶分析题，系统已从数据中抽取结构化结果用于展示。");
        sb.append("当前返回 ").append(rowCount).append(" 行、").append(colCount).append(" 列。");

        if (timeLike) sb.append("结果呈现出典型的时间序列形态，可用于观察趋势与波动。");
        else if (categoryLike) sb.append("结果呈现出分类汇总形态，可用于对比不同类别的差异与占比。");
        else sb.append("结果可用于进一步挖掘关键指标与异常点。");

        // 轻量结论：用前几行/最大最小做一句话（不胡编业务）
        String stat = quickStatSentence(qr);
        if (!stat.isBlank()) sb.append(stat);

        return sb.toString();
    }

    private String quickStatSentence(QueryResult qr) {
        // 找一个数值列做 max/min（如果有）
        if (qr.getRows() == null || qr.getRows().isEmpty() || qr.getColumns() == null) return "";
        int ncol = qr.getColumns().size();
        int numericIdx = -1;
        for (int c = 0; c < ncol; c++) {
            Object v = qr.getRows().get(0).get(c);
            if (v instanceof Number) {
                numericIdx = c;
                break;
            }
        }
        if (numericIdx < 0) return "";
        double max = Double.NEGATIVE_INFINITY, min = Double.POSITIVE_INFINITY;
        for (List<Object> row : qr.getRows()) {
            Object v = row.get(numericIdx);
            if (v instanceof Number n) {
                double d = n.doubleValue();
                max = Math.max(max, d);
                min = Math.min(min, d);
            }
        }
        if (!Double.isFinite(max) || !Double.isFinite(min)) return "";
        return " 数值列“" + qr.getColumns().get(numericIdx) + "”的区间大致在 [" + DF.format(min) + ", " + DF.format(max) + "]。";
    }

    private List<String> buildBullets(QueryResult qr) {
        List<String> b = new ArrayList<>();
        int rowCount = qr.getRows() == null ? 0 : qr.getRows().size();
        int colCount = qr.getColumns() == null ? 0 : qr.getColumns().size();

        b.add("数据规模：返回 " + rowCount + " 行 × " + colCount + " 列。");
        if (looksLikeTimeSeries(qr)) b.add("形态判断：时间序列结果，适合展示趋势/周期性。");
        if (looksLikeCategory(qr)) b.add("形态判断：分类汇总结果，适合展示对比/占比。");
        if (!looksLikeTimeSeries(qr) && !looksLikeCategory(qr)) b.add("形态判断：明细或混合结构，适合表格+重点指标解读。");
        b.add("展示策略：默认展示表格前 " + ADV_TABLE_MAX_ROWS + " 行，并自动生成不超过 2 张可视化图。");
        return b;
    }

    private List<String> buildCharts(QueryResult qr) {
        List<String> charts = new ArrayList<>();
        if (qr.getRows() == null || qr.getRows().isEmpty() || qr.getColumns() == null || qr.getColumns().isEmpty()) {
            return charts;
        }

        // 规则：尽量生成“折线(时间序列)” 或 “柱状(TopN)” 或 “饼图(占比)”
        if (looksLikeTimeSeries(qr)) {
            String svg = svgLineChart(qr, 0, findFirstNumericCol(qr));
            if (svg != null) charts.add(svg);
            return charts;
        }

        // 分类：优先柱状 +（如果适合）饼图
        int labelCol = 0;
        int valueCol = findFirstNumericCol(qr);
        if (valueCol >= 0) {
            charts.add(svgBarChart(qr, labelCol, valueCol, 12));
            if (qr.getRows().size() <= 8) {
                charts.add(svgPieChart(qr, labelCol, valueCol, 8));
            }
        }
        return charts;
    }

    private int findFirstNumericCol(QueryResult qr) {
        if (qr.getRows() == null || qr.getRows().isEmpty() || qr.getColumns() == null) return -1;
        int ncol = qr.getColumns().size();
        for (int c = 0; c < ncol; c++) {
            for (int r = 0; r < Math.min(3, qr.getRows().size()); r++) {
                Object v = qr.getRows().get(r).get(c);
                if (v instanceof Number) return c;
            }
        }
        return -1;
    }

    private boolean looksLikeTimeSeries(QueryResult qr) {
        if (qr.getColumns() == null || qr.getColumns().isEmpty()) return false;
        // 第一列列名包含 date/time/month/year 或值像 yyyy-mm
        String c0 = qr.getColumns().get(0).toLowerCase(Locale.ROOT);
        if (c0.contains("date") || c0.contains("time") || c0.contains("month") || c0.contains("year")) return true;
        if (qr.getRows() == null || qr.getRows().isEmpty()) return false;
        String v0 = String.valueOf(qr.getRows().get(0).get(0));
        return v0.matches("\\d{4}-\\d{1,2}(-\\d{1,2})?.*") || v0.matches("\\d{1,2}"); // month_num
    }

    private boolean looksLikeCategory(QueryResult qr) {
        if (qr.getRows() == null || qr.getRows().isEmpty() || qr.getColumns() == null) return false;
        // 第一列非数值，且存在数值列
        Object v0 = qr.getRows().get(0).get(0);
        if (v0 instanceof Number) return false;
        return findFirstNumericCol(qr) >= 0;
    }

    // =========================
    // HTML 报告：按题分节（无对比/无错误/无耗时）
    // =========================
    private void generatePerQuestionReportHtml(String titleDomain, List<AdvancedReportItem> items, String outputPath) throws Exception {
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html lang='zh-CN'><head><meta charset='utf-8'/>");
        html.append("<meta name='viewport' content='width=device-width,initial-scale=1'/>");
        html.append("<title>").append(esc(titleDomain)).append("进阶分析报告</title>");
        html.append("<style>");
        html.append("body{font-family:Microsoft YaHei,Arial,sans-serif;background:#f6f7fb;margin:0;padding:24px;color:#1f2328;}");
        html.append(".wrap{max-width:1100px;margin:0 auto;}");
        html.append(".card{background:#fff;border:1px solid #e6e8ee;border-radius:14px;box-shadow:0 4px 14px rgba(0,0,0,.04);padding:18px 18px;margin:14px 0;}");
        html.append(".title{font-size:22px;font-weight:800;margin:0 0 10px 0;}");
        html.append(".meta{color:#667085;font-size:13px;margin:0 0 10px 0;}");
        html.append(".q{font-size:15px;line-height:1.55;margin:10px 0 12px 0;}");
        html.append(".grid{display:grid;grid-template-columns:1fr;gap:10px;}");
        html.append("@media(min-width:980px){.grid{grid-template-columns:1fr 1fr;}}");
        html.append(".chart{background:#fff;border:1px dashed #d0d5dd;border-radius:12px;padding:10px;overflow:auto;}");
        html.append(".tbl{overflow:auto;border-radius:12px;border:1px solid #eaecf0;}");
        html.append("table{border-collapse:collapse;width:100%;font-size:13px;}");
        html.append("th,td{border-bottom:1px solid #eaecf0;padding:8px 10px;text-align:left;white-space:nowrap;}");
        html.append("th{background:#f9fafb;color:#344054;font-weight:700;}");
        html.append(".muted{color:#98a2b3;font-size:13px;}");
        html.append("ul{margin:8px 0 0 18px;color:#344054;}");
        html.append("</style></head><body><div class='wrap'>");

        html.append("<div class='card'>");
        html.append("<div class='title'>").append(esc(titleDomain)).append(" · 进阶分析报告</div>");
        html.append("<div class='meta'>报告按题目分节生成；展示包含：结构化数据表 + 自动可视化（柱状/饼图/折线）+ 简要分析要点。</div>");
        html.append("</div>");

        if (items == null || items.isEmpty()) {
            html.append("<div class='card'><div class='muted'>当前无进阶分析题目可生成报告。</div></div>");
        } else {
            for (AdvancedReportItem it : items) {
                html.append("<div class='card'>");
                html.append("<div class='title'>题目 ").append(esc(it.getId())).append("</div>");
                html.append("<div class='q'><b>问题：</b>").append(esc(it.getQuery())).append("</div>");
                html.append("<div class='q'>").append(esc(it.getNarrative())).append("</div>");

                html.append("<div class='grid'>");

                // 左：表格（一定有）
                html.append("<div class='tbl'>").append(it.getTableHtml()).append("</div>");

                // 右：图（最多两张，没图就提示但不空白）
                html.append("<div class='chart'>");
                if (it.getChartSvgs() == null || it.getChartSvgs().isEmpty()) {
                    html.append("<div class='muted'>暂无可自动生成的可视化图表（结果形态不满足时间序列/分类汇总）。已提供数据表供人工分析。</div>");
                } else {
                    for (String svg : it.getChartSvgs()) {
                        html.append(svg);
                        html.append("<div style='height:10px'></div>");
                    }
                }
                html.append("</div>");

                html.append("</div>"); // grid

                if (it.getBullets() != null && !it.getBullets().isEmpty()) {
                    html.append("<div style='margin-top:10px'><b>分析要点：</b><ul>");
                    for (String b : it.getBullets()) {
                        html.append("<li>").append(esc(b)).append("</li>");
                    }
                    html.append("</ul></div>");
                }
                html.append("</div>");
            }
        }

        html.append("</div></body></html>");

        Files.write(Paths.get(outputPath), html.toString().getBytes(StandardCharsets.UTF_8));
    }

    // =========================
    // 表格渲染：一定输出（最多 N 行）
    // =========================
    private String toHtmlTable(QueryResult qr, int maxRows) {
        if (qr == null || qr.getColumns() == null || qr.getColumns().isEmpty()) {
            return "<div class='muted'>无数据表可展示</div>";
        }
        List<String> cols = qr.getColumns();
        List<List<Object>> rows = qr.getRows() == null ? List.of() : qr.getRows();

        int take = Math.min(rows.size(), Math.max(0, maxRows));

        StringBuilder sb = new StringBuilder();
        sb.append("<table><thead><tr>");
        for (String c : cols) sb.append("<th>").append(esc(c)).append("</th>");
        sb.append("</tr></thead><tbody>");

        for (int i = 0; i < take; i++) {
            sb.append("<tr>");
            List<Object> r = rows.get(i);
            for (int j = 0; j < cols.size(); j++) {
                Object v = j < r.size() ? r.get(j) : "";
                sb.append("<td>").append(esc(oneValueToString(v))).append("</td>");
            }
            sb.append("</tr>");
        }
        sb.append("</tbody></table>");
        if (rows.size() > take) {
            sb.append("<div class='muted' style='padding:8px 10px'>仅展示前 ")
                    .append(take).append(" 行，共 ").append(rows.size()).append(" 行</div>");
        }
        return sb.toString();
    }

    // =========================
    // SVG 图表（内联，避免 base64 拥塞）
    // =========================
    private String svgBarChart(QueryResult qr, int labelCol, int valueCol, int topN) {
        List<List<Object>> rows = qr.getRows();
        if (rows == null || rows.isEmpty() || valueCol < 0) return null;

        int n = Math.min(rows.size(), topN);
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            List<Object> r = rows.get(i);
            labels.add(String.valueOf(r.get(labelCol)));
            values.add(toDouble(r.get(valueCol)));
        }

        double max = values.stream().mapToDouble(x -> x == null ? 0 : x).max().orElse(1);
        if (max <= 0) max = 1;

        StringBuilder sb = new StringBuilder();
        sb.append("<svg viewBox='0 0 1000 360'>");
        sb.append("<rect x='20' y='20' width='960' height='320' rx='16' ry='16' fill='#fff' stroke='#e5e5e5'/>");
        sb.append("<text x='40' y='55' font-size='16' font-weight='700' fill='#111' font-family='Microsoft YaHei, Arial'>柱状图（Top")
                .append(n).append("）</text>");

        double left = 80, right = 960, top = 80, bottom = 320;
        double w = right - left, h = bottom - top;
        double bw = w / n;

        for (int i = 0; i < n; i++) {
            double v = values.get(i) == null ? 0 : values.get(i);
            double bh = (v / max) * (h - 20);
            double x = left + i * bw + 10;
            double y = bottom - bh;
            sb.append("<rect x='").append(x).append("' y='").append(y)
                    .append("' width='").append(Math.max(12, bw - 20))
                    .append("' height='").append(bh)
                    .append("' rx='8' ry='8' fill='#1a73e8' opacity='0.85'/>");

            sb.append("<text x='").append(x).append("' y='").append(340)
                    .append("' font-size='12' fill='#333' font-family='Microsoft YaHei, Arial'>")
                    .append(esc(shortLabel(labels.get(i), 10))).append("</text>");
        }

        sb.append("</svg>");
        return sb.toString();
    }

    private String svgLineChart(QueryResult qr, int labelCol, int valueCol) {
        if (valueCol < 0) return null;
        List<List<Object>> rows = qr.getRows();
        if (rows == null || rows.isEmpty()) return null;

        int n = Math.min(rows.size(), 40); // 线图最多 40 点
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            List<Object> r = rows.get(i);
            labels.add(String.valueOf(r.get(labelCol)));
            values.add(toDouble(r.get(valueCol)));
        }

        double max = Double.NEGATIVE_INFINITY, min = Double.POSITIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            double v = values.get(i) == null ? 0 : values.get(i);
            max = Math.max(max, v);
            min = Math.min(min, v);
        }
        if (!Double.isFinite(min)) min = 0;
        if (!Double.isFinite(max)) max = min + 1;
        if (max == min) max = min + 1;

        double left = 80, right = 940, top = 90, bottom = 300;
        double w = right - left, h = bottom - top;

        StringBuilder sb = new StringBuilder();
        sb.append("<svg viewBox='0 0 1000 360'>");
        sb.append("<rect x='20' y='20' width='960' height='320' rx='16' ry='16' fill='#fff' stroke='#e5e5e5'/>");
        sb.append("<text x='40' y='55' font-size='16' font-weight='700' fill='#111' font-family='Microsoft YaHei, Arial'>折线图（趋势）</text>");
        sb.append("<line x1='").append(left).append("' y1='").append(bottom).append("' x2='").append(right).append("' y2='").append(bottom).append("' stroke='#555' stroke-width='2'/>");
        sb.append("<line x1='").append(left).append("' y1='").append(top).append("' x2='").append(left).append("' y2='").append(bottom).append("' stroke='#555' stroke-width='2'/>");

        StringBuilder pts = new StringBuilder();
        for (int i = 0; i < n; i++) {
            double v = values.get(i) == null ? 0 : values.get(i);
            double x = left + (n == 1 ? 0 : (w * (i / (double) (n - 1))));
            double y = bottom - ((v - min) / (max - min)) * h;
            pts.append(String.format(Locale.US, "%.2f,%.2f ", x, y));
        }
        sb.append("<polyline fill='none' stroke='#1a73e8' stroke-width='3' points='").append(pts).append("'/>");

        int step = Math.max(1, n / 6);
        for (int i = 0; i < n; i += step) {
            double x = left + (n == 1 ? 0 : (w * (i / (double) (n - 1))));
            sb.append("<text x='").append(x).append("' y='335' font-size='12' fill='#333' font-family='Microsoft YaHei, Arial'>")
                    .append(esc(shortLabel(labels.get(i), 10))).append("</text>");
        }

        sb.append("</svg>");
        return sb.toString();
    }

    private String svgPieChart(QueryResult qr, int labelCol, int valueCol, int maxSlices) {
        List<List<Object>> rows = qr.getRows();
        if (rows == null || rows.isEmpty() || valueCol < 0) return null;

        int n = Math.min(rows.size(), maxSlices);
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        double sum = 0;
        for (int i = 0; i < n; i++) {
            List<Object> r = rows.get(i);
            String lab = String.valueOf(r.get(labelCol));
            double v = toDouble(r.get(valueCol));
            if (v < 0) v = 0;
            labels.add(lab);
            values.add(v);
            sum += v;
        }
        if (sum <= 0) return null;

        double cx = 230, cy = 190, rad = 110;
        String[] colors = new String[]{"#1a73e8", "#34a853", "#fbbc05", "#ea4335", "#7e57c2", "#00acc1", "#f06292", "#8d6e63"};
        double start = -Math.PI / 2;

        StringBuilder sb = new StringBuilder();
        sb.append("<svg viewBox='0 0 1000 360'>");
        sb.append("<rect x='20' y='20' width='960' height='320' rx='16' ry='16' fill='#fff' stroke='#e5e5e5'/>");
        sb.append("<text x='40' y='55' font-size='16' font-weight='700' fill='#111' font-family='Microsoft YaHei, Arial'>饼图（占比）</text>");

        for (int i = 0; i < n; i++) {
            double frac = values.get(i) / sum;
            double end = start + frac * Math.PI * 2;
            sb.append("<path d='").append(arcPath(cx, cy, rad, start, end))
                    .append("' fill='").append(colors[i % colors.length]).append("' opacity='0.9'/>");
            start = end;
        }

        // legend
        double lx = 420, ly = 110;
        for (int i = 0; i < n; i++) {
            sb.append("<rect x='").append(lx).append("' y='").append(ly + i * 28).append("' width='14' height='14' fill='")
                    .append(colors[i % colors.length]).append("'/>");
            sb.append("<text x='").append(lx + 22).append("' y='").append(ly + i * 28 + 12)
                    .append("' font-size='13' fill='#333' font-family='Microsoft YaHei, Arial'>")
                    .append(esc(shortLabel(labels.get(i), 18))).append(" (")
                    .append(DF.format(values.get(i))).append(")</text>");
        }

        sb.append("</svg>");
        return sb.toString();
    }

    private String arcPath(double cx, double cy, double r, double a1, double a2) {
        double x1 = cx + r * Math.cos(a1);
        double y1 = cy + r * Math.sin(a1);
        double x2 = cx + r * Math.cos(a2);
        double y2 = cy + r * Math.sin(a2);
        int largeArc = (a2 - a1) > Math.PI ? 1 : 0;
        return String.format(Locale.US,
                "M %.2f %.2f L %.2f %.2f A %.2f %.2f 0 %d 1 %.2f %.2f Z",
                cx, cy, x1, y1, r, r, largeArc, x2, y2);
    }

    private double toDouble(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private String shortLabel(String s, int max) {
        if (s == null) return "";
        s = s.trim();
        if (s.length() <= max) return s;
        return s.substring(0, max) + "…";
    }

    // =========================
    // SQL 清洗：修 “\\n 存进字符串”
    // =========================
    private String normalizeSqlText(String sql) {
        if (sql == null) return "";
        String s = sql.trim();

        // 去 code fence
        s = stripCodeFence(s);

        // 把字面量 \n \t \r 反转义为真实字符（关键：避免 JSON 里出现 \\n）
        s = s.replace("\\r\\n", "\n").replace("\\n", "\n").replace("\\t", "\t").replace("\\r", "\r");

        // 把多余的首尾引号去掉（有些模型会输出 "SELECT ...;"）
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            s = s.substring(1, s.length() - 1).trim();
        }

        // 防止末尾多余 ```
        s = s.replaceAll("```\\s*$", "").trim();

        return s;
    }

    private String stripCodeFence(String s) {
        // ```sql ... ```
        Pattern p = Pattern.compile("^```[a-zA-Z]*\\s*([\\s\\S]*?)\\s*```$", Pattern.MULTILINE);
        Matcher m = p.matcher(s.trim());
        if (m.find()) return m.group(1).trim();
        return s;
    }

    // =========================
    // 读题库（支持数组 / {data:[]} / {初级:[],中级:[],高级:[]}
    // =========================
    private List<ProblemItem> loadFromClasspath(String classpath, String domainFromFile) throws Exception {
        ClassPathResource res = new ClassPathResource(classpath);
        try (InputStream in = res.getInputStream()) {
            String txt = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();

            // 1) 顶层是数组
            if (txt.startsWith("[")) {
                List<ProblemItem> items = objectMapper.readValue(txt, new TypeReference<List<ProblemItem>>() {});
                for (ProblemItem it : items) it.setDomain(domainFromFile);
                return items;
            }

            // 2) 顶层是对象
            Map<String, Object> root = objectMapper.readValue(txt, new TypeReference<Map<String, Object>>() {});

            // 2.1 data 模式
            if (root.containsKey("data")) {
                String dataJson = objectMapper.writeValueAsString(root.get("data"));
                List<ProblemItem> items = objectMapper.readValue(dataJson, new TypeReference<List<ProblemItem>>() {});
                for (ProblemItem it : items) it.setDomain(domainFromFile);
                return items;
            }

            // 2.2 分级模式
            List<ProblemItem> all = new ArrayList<>();
            for (String lvl : List.of("初级", "中级", "高级")) {
                Object v = root.get(lvl);
                if (v instanceof List<?> list) {
                    String arrJson = objectMapper.writeValueAsString(list);
                    List<ProblemItem> items = objectMapper.readValue(arrJson, new TypeReference<List<ProblemItem>>() {});
                    for (ProblemItem it : items) {
                        it.setLevel(lvl);
                        it.setDomain(domainFromFile);
                    }
                    all.addAll(items);
                }
            }

            // 兜底：把所有 list<dict> 拼起来
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
        String mode = "submit";
        if (args == null) return mode;
        for (String a : args) {
            if (a == null) continue;
            if (a.startsWith("--mode=")) mode = a.substring("--mode=".length()).trim();
        }
        return mode;
    }

    private QueryResult toQueryResult(List<Map<String, Object>> rows) {
        QueryResult qr = new QueryResult();
        qr.setSuccess(true);
        qr.setStatus("success");

        if (rows == null || rows.isEmpty()) {
            qr.setColumns(new ArrayList<>());
            qr.setRows(new ArrayList<>());
            return qr;
        }

        List<String> cols = new ArrayList<>(rows.get(0).keySet()); // LinkedHashMap 顺序
        qr.setColumns(cols);

        List<List<Object>> data = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            List<Object> one = new ArrayList<>(cols.size());
            for (String c : cols) one.add(r.get(c));
            data.add(one);
        }
        qr.setRows(data);
        return qr;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
