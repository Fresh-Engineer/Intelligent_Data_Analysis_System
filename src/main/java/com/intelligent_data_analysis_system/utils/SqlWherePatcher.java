package com.intelligent_data_analysis_system.utils;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;

@SuppressWarnings("deprecation")
public class SqlWherePatcher {

    public static String addConditionSafely(String sql, String cond) {
        if (sql == null || sql.isBlank() || cond == null || cond.isBlank()) return sql;

        try {
            Statement st = CCJSqlParserUtil.parse(sql);
            if (!(st instanceof Select sel)) return sql;

            // 4.9: UNION/INTERSECT 等不是 PlainSelect，直接不注入，避免误伤中级题
            if (!(sel.getSelectBody() instanceof PlainSelect ps)) return sql;

            Expression condExpr = CCJSqlParserUtil.parseCondExpression(cond);

            Expression where = ps.getWhere();
            ps.setWhere(where == null ? condExpr : new AndExpression(where, condExpr));

            return sel.toString();
        } catch (Exception e) {
            // 解析失败直接返回原 SQL（不要字符串拼接）
            return sql;
        }
    }
}
