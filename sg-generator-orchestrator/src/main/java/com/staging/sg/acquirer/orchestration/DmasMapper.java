package com.staging.sg.acquirer.orchestration;

import com.staging.sg.common.entity.GeneratedTransaction;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduit une GeneratedTransaction (données neutres) vers le body JSON
 * attendu par l'endpoint acquéreur POST /api/admin/dmas/auth :
 *   { type, pan, amount, pin?, terminalId?, acceptorId? }
 */
public class DmasMapper {

    /** Construit le body /auth à partir d'une transaction générée. */
    public static Map<String,Object> toAuthBody(GeneratedTransaction t) {
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("type", t.getTxType() != null ? t.getTxType() : "purchase");
        body.put("pan", t.getDe2Pan());
        body.put("amount", formatAmount(t.getDe4Amount()));   // n-12 "000000030000"
        if (t.getDe41TerminalId() != null) body.put("terminalId", t.getDe41TerminalId());
        if (t.getDe42MerchantId() != null) body.put("acceptorId", t.getDe42MerchantId());
        // pas de PIN : nos transactions n'en génèrent pas (purchase sans PIN)
        return body;
    }

    /** Long centimes -> string n-12 zéro-paddée. */
    private static String formatAmount(Long amount) {
        long v = amount != null ? amount : 0L;
        return String.format("%012d", v);
    }
}
