package com.staging.sg.common.iso.sid;

import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOException;

import java.util.ArrayList;
import java.util.List;

/** Structural validation for transactional SID messages. */
public final class SidMessageValidator {
    private SidMessageValidator() {
    }

    public static void validate(ISOMsg message) throws ISOException, SidValidationException {
        String mti = message.getMTI();
        SidMessageProfile profile = SidMessageProfile.forMti(mti);
        List<String> violations = new ArrayList<>();

        for (int field : profile.mandatoryFields()) {
            if (!message.hasField(field) || empty(message, field)) {
                violations.add("MTI " + mti + ": DE" + field + " obligatoire absent");
            }
        }

        validateFunctionCode(message, violations);
        requireConditionalFields(message, violations);

        if (!violations.isEmpty()) {
            throw new SidValidationException(violations);
        }
    }

    public static void validateResponseTo(ISOMsg request, ISOMsg response)
            throws ISOException, SidValidationException {
        validate(response);
        SidMessageProfile responseProfile = SidMessageProfile.forMti(response.getMTI());
        List<String> violations = new ArrayList<>();
        for (int field : responseProfile.echoedFields()) {
            if (request.hasField(field)) {
                if (!response.hasField(field)) {
                    violations.add("MTI " + response.getMTI() + ": DE" + field + " retourne absent");
                } else if (!request.getString(field).equals(response.getString(field))) {
                    violations.add("MTI " + response.getMTI() + ": DE" + field + " doit etre retourne inchange");
                }
            }
        }
        if (!violations.isEmpty()) {
            throw new SidValidationException(violations);
        }
    }

    private static boolean empty(ISOMsg message, int field) throws ISOException {
        byte[] value = message.getBytes(field);
        return value == null || value.length == 0;
    }

    private static void validateFunctionCode(ISOMsg message, List<String> violations)
            throws ISOException {
        if (!message.hasField(24)) return;
        String mti = message.getMTI();
        String value = message.getString(24);
        boolean valid = switch (mti) {
            case "1100" -> SetValues.AUTHORIZATION.contains(value);
            case "1200" -> SetValues.FINANCIAL_REQUEST.contains(value);
            case "1220", "1221" -> SetValues.FINANCIAL_ADVICE.contains(value);
            case "1420", "1421" -> SetValues.REVERSAL.contains(value);
            default -> true;
        };
        if (!valid) violations.add("MTI " + mti + ": DE24 invalide: " + value);
    }

    private static void requireConditionalFields(ISOMsg message, List<String> violations)
            throws ISOException {
        if (message.hasField(5) && !message.hasField(9)) {
            violations.add("DE9 obligatoire lorsque DE5 est present");
        }
        if (message.hasField(6) && !message.hasField(10)) {
            violations.add("DE10 obligatoire lorsque DE6 est present");
        }
        if (message.hasField(52) && !message.hasField(53)) {
            violations.add("DE53 obligatoire lorsque DE52 est present");
        }
        if (message.hasField(55) && message.getBytes(55).length == 0) {
            violations.add("DE55 EMV ne peut pas etre vide");
        }
        if (("1420".equals(message.getMTI()) || "1421".equals(message.getMTI()))
                && "402".equals(message.getString(24)) && !message.hasField(30)) {
            violations.add("DE30 obligatoire pour un redressement partiel DE24=402");
        }
    }

    private static final class SetValues {
        private static final java.util.Set<String> AUTHORIZATION =
                java.util.Set.of("100", "101", "108", "181");
        private static final java.util.Set<String> FINANCIAL_REQUEST =
                java.util.Set.of("101", "200", "281");
        private static final java.util.Set<String> FINANCIAL_ADVICE =
                java.util.Set.of("200", "201", "205", "206");
        private static final java.util.Set<String> REVERSAL =
                java.util.Set.of("400", "401", "402");
    }
}
