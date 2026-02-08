package io.trino.driver;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class that converts ODBC SQL dialect to standard ANSI SQL using JSQLParser.
 * Handles {fn ...}, {ts ...}, {d ...}, {t ...}, and {oj ...} ODBC escape sequences.
 */
public final class SqlParserLogic {

    private static final Pattern TS_PATTERN = Pattern.compile("\\{ts\\s+'(.*?)'\\}", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern D_PATTERN = Pattern.compile("\\{d\\s+'(.*?)'\\}", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern T_PATTERN = Pattern.compile("\\{t\\s+'(.*?)'\\}", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private SqlParserLogic() {
        // Utility class - prevent instantiation
    }

    /**
     * Parses the SQL, converts ODBC escapes to ANSI SQL, and returns the cleaned statement.
     *
     * @param sql the original SQL string (may contain ODBC escape sequences)
     * @return the modified ANSI SQL string, or the original if parsing fails
     */
    public static String parse(String sql) {
        if (sql == null) {
            return null;
        }
        try {
            String preprocessed = preprocessOdbcEscapes(sql);
            Statement statement = CCJSqlParserUtil.parse(preprocessed);
            if (statement != null) {
                return statement.toString();
            }
        } catch (JSQLParserException e) {
            // If parsing fails after pre-processing, return pre-processed SQL
            try {
                return preprocessOdbcEscapes(sql);
            } catch (Exception ignored) {
                return sql;
            }
        }
        return sql;
    }

    /**
     * Pre-processes ODBC escape sequences at the string level before parsing.
     * Converts {fn ...}, {ts '...'}, {d '...'}, {t '...'}, {oj ...} to ANSI equivalents.
     */
    static String preprocessOdbcEscapes(String sql) {
        String result = sql;

        // {ts 'yyyy-mm-dd hh:mm:ss[.f...]'} -> TIMESTAMP '...'
        result = TS_PATTERN.matcher(result).replaceAll("TIMESTAMP '$1'");

        // {d 'yyyy-mm-dd'} -> DATE '...'
        result = D_PATTERN.matcher(result).replaceAll("DATE '$1'");

        // {t 'hh:mm:ss'} -> TIME '...'
        result = T_PATTERN.matcher(result).replaceAll("TIME '$1'");

        // {fn expression} -> expression (unwrap; expression can contain nested parens)
        result = unwrapOdbcEscape(result, "fn");

        // {oj from-clause} -> from-clause (unwrap outer join)
        result = unwrapOdbcEscape(result, "oj");

        return result;
    }

    /**
     * Unwraps ODBC escape {keyword ...} by finding the matching closing brace.
     */
    private static String unwrapOdbcEscape(String sql, String keyword) {
        String patternStr = "\\{" + keyword + "\\s+";
        Pattern startPattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
        StringBuilder result = new StringBuilder();
        Matcher matcher = startPattern.matcher(sql);
        int lastEnd = 0;

        while (matcher.find()) {
            result.append(sql, lastEnd, matcher.start());
            int contentStart = matcher.end();
            int braceDepth = 1;
            int i = contentStart - 1;
            while (i < sql.length()) {
                i++;
                char c = sql.charAt(i);
                if (c == '{') {
                    braceDepth++;
                } else if (c == '}') {
                    braceDepth--;
                    if (braceDepth == 0) {
                        String content = sql.substring(contentStart, i).trim();
                        result.append(content);
                        lastEnd = i + 1;
                        break;
                    }
                }
            }
            if (braceDepth != 0) {
                result.append(sql, matcher.start(), Math.min(i + 1, sql.length()));
                lastEnd = Math.min(i + 1, sql.length());
            }
        }
        result.append(sql.substring(lastEnd));
        return result.toString();
    }
}
