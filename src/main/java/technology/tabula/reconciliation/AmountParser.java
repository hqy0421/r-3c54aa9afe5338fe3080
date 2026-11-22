package technology.tabula.reconciliation;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Parses statement amounts into exact {@link BigDecimal} values.
 *
 * <p>The parser is intentionally locale-agnostic because a single statement may
 * mix currencies. It accepts the common printed forms:</p>
 *
 * <ul>
 *   <li>{@code 1234.56}, {@code -1234.56}, {@code +12}</li>
 *   <li>{@code 1,234.56} and {@code 1.234,56} (the right-most separator is the
 *       decimal separator when both are present)</li>
 *   <li>{@code 1 234,56}, {@code 1'234.56}, {@code 1_234.56}</li>
 *   <li>parenthesised negatives: {@code (1,234.56)}</li>
 *   <li>surrounding currency symbols ({@code $}, {@code €}, ...) and a single
 *       leading or trailing upper-case three-letter currency code separated by
 *       spaces ({@code USD 1,234.56}, {@code 1,234.56 EUR})</li>
 * </ul>
 *
 * <p>When only one separator is present, a comma followed by exactly three
 * digits is read as a thousands separator ({@code 1,234} is 1234); a single dot
 * is always read as a decimal separator. Separator groups are validated, so
 * {@code 12.3.4} and {@code 1,23,456} are rejected rather than silently
 * reinterpreted.</p>
 *
 * <p>Anything that is not a plain decimal amount (exponents, percent signs,
 * letters, multiple signs, &hellip;) is rejected.</p>
 */
public final class AmountParser {

    private static final String CURRENCY_SYMBOLS =
            "$€£¥￥₹₩₽¢₺₦₴₱₡₪₫₭₮₸₼₾؋﷼";

    private static final Pattern LEADING_CURRENCY_CODE = Pattern.compile("^[A-Z]{3}\\s+.*$");
    private static final Pattern TRAILING_CURRENCY_CODE = Pattern.compile("^.*\\s+[A-Z]{3}$");

    private AmountParser() {
    }

    /**
     * @param raw the cell text
     * @return the exact amount
     * @throws InvalidAmountException if the text is not a valid decimal amount
     */
    public static BigDecimal parse(String raw) {
        if (raw == null) {
            throw new InvalidAmountException("amount is missing");
        }

        String s = Normalizer.normalize(raw, Normalizer.Form.NFKC).trim();
        if (s.isEmpty()) {
            throw new InvalidAmountException("amount is blank");
        }

        s = s.replace('−', '-')   // minus sign
             .replace('–', '-')   // en dash
             .replace('—', '-');  // em dash

        boolean negative = false;

        // Accounting parentheses, e.g. (1,234.56).
        if (s.startsWith("(") || s.endsWith(")")) {
            if (!s.startsWith("(") || !s.endsWith(")")) {
                throw new InvalidAmountException("unbalanced parentheses in amount: " + raw);
            }
            negative = true;
            s = s.substring(1, s.length() - 1).trim();
            if (s.isEmpty()) {
                throw new InvalidAmountException("amount is blank: " + raw);
            }
        }

        // Optional leading/trailing ISO-style currency code, e.g. "USD 1,234.56".
        if (LEADING_CURRENCY_CODE.matcher(s).matches()) {
            s = s.substring(s.indexOf(' ') + 1).trim();
        }
        if (TRAILING_CURRENCY_CODE.matcher(s).matches()) {
            s = s.substring(0, s.lastIndexOf(' ')).trim();
        }

        StringBuilder cleaned = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || c == ' ' || c == ' ' || c == ' '
                    || c == '\'' || c == '’' || c == '_') {
                continue;
            }
            if (CURRENCY_SYMBOLS.indexOf(c) >= 0) {
                continue;
            }
            cleaned.append(c);
        }
        s = cleaned.toString();

        if (s.startsWith("+")) {
            s = s.substring(1);
        } else if (s.startsWith("-")) {
            negative = !negative;
            s = s.substring(1);
        }
        if (s.indexOf('-') >= 0 || s.indexOf('+') >= 0) {
            throw new InvalidAmountException("misplaced sign in amount: " + raw);
        }
        if (s.isEmpty()) {
            throw new InvalidAmountException("amount is blank: " + raw);
        }

        int lastDot = s.lastIndexOf('.');
        int lastComma = s.lastIndexOf(',');

        char decimalSep = 0;
        char groupSep = 0;

        if (lastDot >= 0 && lastComma >= 0) {
            if (lastDot > lastComma) {
                decimalSep = '.';
                groupSep = ',';
            } else {
                decimalSep = ',';
                groupSep = '.';
            }
            if (count(s, decimalSep) != 1) {
                throw new InvalidAmountException("ambiguous decimal separator in amount: " + raw);
            }
        } else if (lastComma >= 0) {
            if (count(s, ',') > 1) {
                groupSep = ',';
            } else {
                String before = s.substring(0, lastComma);
                String after = s.substring(lastComma + 1);
                if (after.length() == 3 && isDigits(before) && !isAllZeros(before)) {
                    groupSep = ',';
                } else {
                    decimalSep = ',';
                }
            }
        } else if (lastDot >= 0) {
            if (count(s, '.') > 1) {
                groupSep = '.';
            } else {
                decimalSep = '.';
            }
        }

        String integerPart;
        String fractionPart = "";

        if (decimalSep != 0) {
            int index = s.indexOf(decimalSep);
            integerPart = s.substring(0, index);
            fractionPart = s.substring(index + 1);
            if (fractionPart.isEmpty() || !isDigits(fractionPart)) {
                throw new InvalidAmountException("malformed decimal amount: " + raw);
            }
        } else {
            integerPart = s;
        }

        String digits;
        if (groupSep != 0) {
            digits = validateAndStripGroups(integerPart, groupSep, raw);
        } else {
            if (!isDigits(integerPart)) {
                throw new InvalidAmountException("malformed amount: " + raw);
            }
            digits = integerPart;
        }

        if (digits.isEmpty()) {
            throw new InvalidAmountException("amount has no digits: " + raw);
        }

        String canonical = fractionPart.isEmpty() ? digits : digits + "." + fractionPart;
        try {
            BigDecimal value = new BigDecimal(canonical);
            return negative ? value.negate() : value;
        } catch (NumberFormatException e) {
            throw new InvalidAmountException("malformed amount: " + raw, e);
        }
    }

    private static String validateAndStripGroups(String integerPart, char groupSep, String raw) {
        String[] groups = integerPart.split(Pattern.quote(String.valueOf(groupSep)), -1);
        if (groups.length < 2) {
            // Both separators were present but the grouping separator was not in
            // the integer part, e.g. "1,5.0": the fraction contains the group
            // separator, so this is malformed.
            throw new InvalidAmountException("misplaced thousands separator in amount: " + raw);
        }
        if (groups[0].isEmpty() || groups[0].length() > 3 || !isDigits(groups[0])) {
            throw new InvalidAmountException("malformed thousands grouping in amount: " + raw);
        }
        StringBuilder digits = new StringBuilder(groups[0]);
        for (int i = 1; i < groups.length; i++) {
            if (groups[i].length() != 3 || !isDigits(groups[i])) {
                throw new InvalidAmountException("malformed thousands grouping in amount: " + raw);
            }
            digits.append(groups[i]);
        }
        return digits.toString();
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }

    private static boolean isDigits(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) < '0' || s.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }

    private static boolean isAllZeros(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) != '0') {
                return false;
            }
        }
        return !s.isEmpty();
    }

    /**
     * Locale-independent currency identity key used for accounting. The original
     * spelling is kept for reporting.
     */
    static String currencyKey(String currency) {
        return currency.trim().toUpperCase(Locale.ROOT);
    }
}
