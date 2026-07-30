package com.staging.sg.mc.sms.issuer.network;

import org.jpos.iso.ISOMsg;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

@Service
public class McSmsAuthorizationProcessor {
    public record Decision(String responseCode, String authorizationCode) {}
    private final JdbcTemplate jdbc;

    public McSmsAuthorizationProcessor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Decision process(ISOMsg m) {
        try {
            Card card = jdbc.queryForObject(
                    "select pan,balance,currency,expiry,status from mc_sms_cards where pan=? for update",
                    (rs, n) -> new Card(rs.getString(1), rs.getLong(2), rs.getString(3),
                            rs.getString(4), rs.getString(5)), m.getString(2));
            String rc = validate(card, m);
            long amount = Long.parseLong(m.getString(4));
            if ("00".equals(rc)) {
                int changed = jdbc.update(
                        "update mc_sms_cards set balance=balance-?,updated_at=now() where pan=? and balance>=?",
                        amount, card.pan(), amount);
                if (changed == 0) rc = "51";
            }
            String auth = "00".equals(rc)
                    ? "%06d".formatted(Math.floorMod((m.getString(11) + m.getString(7)).hashCode(), 1_000_000))
                    : null;
            persist(m, rc, auth);
            return new Decision(rc, auth);
        } catch (EmptyResultDataAccessException e) {
            persist(m, "14", null);
            return new Decision("14", null);
        } catch (RuntimeException e) {
            return new Decision("96", null);
        }
    }

    @Transactional
    public Decision reverse(ISOMsg m) {
        try {
            Original original = jdbc.queryForObject(
                    "select id,amount,reversed_at is not null from mc_sms_iss_transactions "
                            + "where pan=? and retrieval_ref=? and response_code='00' "
                            + "order by created_at desc limit 1 for update",
                    (rs, n) -> new Original(rs.getLong(1), rs.getLong(2), rs.getBoolean(3)),
                    m.getString(2), m.getString(37));
            if (original != null && !original.reversed()) {
                jdbc.update("update mc_sms_cards set balance=balance+?,updated_at=now() where pan=?",
                        original.amount(), m.getString(2));
                jdbc.update("update mc_sms_iss_transactions set reversed_at=now(),status='REVERSED' where id=?",
                        original.id());
            }
            persist(m, "00", null);
            return new Decision("00", null);
        } catch (EmptyResultDataAccessException e) {
            persist(m, "25", null);
            return new Decision("25", null);
        }
    }

    private String validate(Card card, ISOMsg m) {
        if (!"ACTIVE".equals(card.status())) return "62";
        if (!card.expiry().equals(value(m, 14)) || expired(card.expiry())) return "54";
        if (!card.currency().equals(value(m, 49))) return "57";
        return "00";
    }

    private void persist(ISOMsg m, String rc, String auth) {
        jdbc.update("insert into mc_sms_iss_transactions "
                        + "(pan,stan,transmission_dt,mti,processing_code,amount,currency,response_code,"
                        + "auth_id_response,retrieval_ref,status) values (?,?,?,?,?,?,?,?,?,?,?) "
                        + "on conflict (stan,transmission_dt) do nothing",
                value(m,2), value(m,11), value(m,7), mti(m), value(m,3),
                amount(m), value(m,49), rc, auth, value(m,37),
                "00".equals(rc) ? "APPROVED" : "DECLINED");
    }

    private static boolean expired(String yymm) {
        try {
            return YearMonth.parse(yymm, DateTimeFormatter.ofPattern("yyMM")).isBefore(YearMonth.now());
        } catch (RuntimeException e) {
            return true;
        }
    }
    private static String value(ISOMsg m,int f){return m.hasField(f)?m.getString(f):null;}
    private static long amount(ISOMsg m){try{return Long.parseLong(value(m,4));}catch(Exception e){return 0;}}
    private static String mti(ISOMsg m){try{return m.getMTI();}catch(Exception e){return "0000";}}
    private record Card(String pan,long balance,String currency,String expiry,String status){}
    private record Original(long id,long amount,boolean reversed){}
}
