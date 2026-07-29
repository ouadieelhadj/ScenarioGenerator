package com.staging.sg.common.service;

import com.staging.sg.common.entity.AbstractDmcClearingTransaction;
import com.staging.sg.common.entity.AbstractMcDmasAuthorizationTransaction;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;

/**
 * Transformation EOD du journal DMAS (ISO 8583:1987) vers la transaction
 * consolidee DMC (ISO 8583:1993).
 */
public final class DmcAuthorizationToClearingMapper {

    private static final Map<String, Character> PAN_INPUT_MODE = Map.ofEntries(
            Map.entry("00", '0'), Map.entry("01", '1'), Map.entry("02", '2'),
            Map.entry("03", '0'), Map.entry("04", '0'), Map.entry("05", 'C'),
            Map.entry("07", 'M'), Map.entry("09", 'R'), Map.entry("10", '7'),
            Map.entry("79", '6'), Map.entry("80", 'B'), Map.entry("81", 'S'),
            Map.entry("82", 'T'), Map.entry("90", 'B'), Map.entry("91", 'A'),
            Map.entry("95", 'C'));

    private DmcAuthorizationToClearingMapper() {
    }

    public static <T extends AbstractDmcClearingTransaction> T populateFirstPresentment(
            T target,
            AbstractMcDmasAuthorizationTransaction authorization,
            LocalDate businessDate,
            String destinationId,
            String originId) {
        target.setBusinessDate(businessDate);
        target.setSourceType("LOCAL_AUTH");
        target.setDirection("OUT");
        target.setLocalAuthorizationId(authorization.getId());
        target.setCorrelationKey(correlationKey(authorization));
        target.setLifecycleStage("FIRST_PRESENTMENT");
        target.setStatus("READY");
        target.setMatchStatus("LOCAL_ONLY");
        target.setMti("1240");
        target.setFunctionCode("200");
        target.setPan(authorization.getPan());
        target.setMaskedPan(authorization.getMaskedPan());
        target.setProcessingCode(authorization.getProcessingCode());
        target.setAmount(authorization.getAmount());
        target.setTransactionDatetime(toDmcTransactionDatetime(authorization, businessDate));
        target.setExpiry(authorization.getExpiry());
        target.setPosDataCode(toDmcPosDataCode(
                authorization.getPosEntryMode(), authorization.getPosData(),
                authorization.getAdditionalData()));
        target.setMcc(authorization.getMcc());
        // DE31/ARN est volontairement differe par decision d'architecture.
        target.setAcquirerReference(null);
        target.setAcquiringInstitutionId(authorization.getAcquiringInstitutionId());
        target.setForwardingInstitutionId(authorization.getForwardingInstitutionId());
        target.setRrn(authorization.getRrn());
        target.setAuthorizationCode(authorization.getAuthorizationCode());
        target.setTerminalId(authorization.getTerminalId());
        target.setAcceptorId(authorization.getAcceptorId());
        target.setAcceptorNameLocation(authorization.getAcceptorNameLocation());
        target.setCurrency(authorization.getCurrency());
        target.setDestinationId(destinationId);
        target.setOriginId(originId);
        return target;
    }

    /**
     * Mapping DMC Guide p. 286-289. Quand l'autorisation ne transporte pas
     * le sous-champ CIS requis, une valeur "unknown/not available" admise par
     * le modèle DMC est appliquee; aucune information n'est inventee.
     */
    public static String toDmcPosDataCode(
            String cisDe22, String cisDe61, String cisDe48) {
        String entry = left(cisDe22, 2, "00");
        char s1 = mapTerminalInputCapability(charAt(cisDe61, 10, '9'));
        char s2 = mapAuthenticationCapability(charAt(cisDe22, 2, '0'));
        char s3 = mapCardCapture(charAt(cisDe61, 5, '0'));
        char s4 = mapOperatingEnvironment(charAt(cisDe61, 0, '9'), charAt(cisDe61, 2, '9'));
        char s5 = mapDirectDigit(charAt(cisDe61, 3, '0'), '0');
        char s6 = charAt(cisDe61, 4, '0') == '1' ? '0' : '1';
        char s7 = PAN_INPUT_MODE.getOrDefault(entry, '0');
        char s8 = containsPinService(cisDe48) ? '1' : '9';
        char s9 = '9';
        char s10 = '0';
        char s11 = '0';
        char s12 = mapPinCapture(charAt(cisDe22, 2, '0'));
        return new String(new char[]{
                s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, s12});
    }

    private static String toDmcTransactionDatetime(
            AbstractMcDmasAuthorizationTransaction authorization, LocalDate businessDate) {
        String localDate = authorization.getLocalDate();
        String localTime = authorization.getLocalTime();
        if (localDate == null || localDate.length() != 4
                || localTime == null || localTime.length() != 6) {
            return businessDate.format(DateTimeFormatter.ofPattern("yyMMdd")) + "000000";
        }
        int month = Integer.parseInt(localDate.substring(0, 2));
        int day = Integer.parseInt(localDate.substring(2, 4));
        MonthDay monthDay = MonthDay.of(month, day);
        LocalDate candidate = monthDay.atYear(businessDate.getYear());
        if (candidate.isAfter(businessDate.plusMonths(6))) {
            candidate = monthDay.atYear(businessDate.getYear() - 1);
        } else if (candidate.isBefore(businessDate.minusMonths(6))) {
            candidate = monthDay.atYear(businessDate.getYear() + 1);
        }
        return candidate.format(DateTimeFormatter.ofPattern("yyMMdd")) + localTime;
    }

    private static String correlationKey(AbstractMcDmasAuthorizationTransaction authorization) {
        String source = String.join("|",
                safe(authorization.getPan()), safe(authorization.getRrn()),
                safe(authorization.getStan()), safe(authorization.getAmount()),
                safe(authorization.getCurrency()), safe(authorization.getAuthorizationCode()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withUpperCase().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    private static char mapTerminalInputCapability(char value) {
        return switch (value) {
            case '0' -> '1'; case '2' -> '2'; case '3' -> 'M';
            case '4' -> 'A'; case '5' -> 'D'; case '6' -> '6';
            case '7' -> 'B'; case '8' -> 'C'; case '9' -> '5';
            default -> '9';
        };
    }

    private static char mapAuthenticationCapability(char value) {
        return switch (value) {
            case '0' -> '9'; case '1' -> '1'; case '2' -> '0'; case '8' -> '5';
            default -> '9';
        };
    }

    private static char mapCardCapture(char value) {
        return value == '1' ? '1' : '0';
    }

    private static char mapOperatingEnvironment(char s1, char s3) {
        if (s1 == '2' && s3 == '3') return '0';
        if (s1 == '0' && s3 == '0') return '1';
        if (s1 == '1' && (s3 == '0' || s3 == '4')) return '2';
        if (s1 == '0' && s3 == '1') return '3';
        if (s1 == '1' && (s3 == '1' || s3 == '2')) return '4';
        if (s3 == '8') return '8';
        return '9';
    }

    private static char mapDirectDigit(char value, char fallback) {
        return value >= '0' && value <= '5' ? value : fallback;
    }

    private static char mapPinCapture(char value) {
        return switch (value) {
            case '1' -> '1'; case '2' -> '0'; default -> '0';
        };
    }

    private static boolean containsPinService(String de48) {
        return de48 != null && (de48.contains("PV") || de48.contains("TV"));
    }

    private static char charAt(String value, int index, char fallback) {
        return value != null && value.length() > index ? value.charAt(index) : fallback;
    }

    private static String left(String value, int length, String fallback) {
        return value != null && value.length() >= length ? value.substring(0, length) : fallback;
    }

    private static String safe(Object value) {
        return value == null ? "" : value.toString();
    }
}
