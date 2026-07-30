package com.staging.sg.waypos.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "pos_cards")
public class PosCard {
    @Id
    @Column(name = "pan_hash", length = 64)
    private String panHash;
    @Column(name = "pan_masked", nullable = false, length = 32)
    private String panMasked;
    @Column(name = "expiry_yymm", nullable = false, length = 4)
    private String expiryYymm;
    @Column(name = "status", nullable = false, length = 16)
    private String status;
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    @Column(name = "available_balance", nullable = false)
    private long availableBalance;
    @Column(name = "blocked_balance", nullable = false)
    private long blockedBalance;
    @Column(name = "pin_pvv", length = 4)
    private String pinPvv;
    @Column(name = "pin_pvki")
    private Integer pinPvki;
    @Column(name = "mdk_under_lmk")
    private String mdkUnderLmk;
    @Column(name = "mdk_kcv", length = 6)
    private String mdkKcv;
    @Column(name = "mdk_length")
    private Integer mdkLength;
    @Column(name = "pan_sequence_number", length = 2)
    private String panSequenceNumber;
    @Column(name = "arpc_arc_hex", length = 4)
    private String arpcArcHex;
    @Column(name = "last_atc")
    private Integer lastAtc;
    @Version
    private long version;

    protected PosCard() {
    }

    public static PosCard provisioned(
            String panHash, String panMasked, String expiryYymm,
            String currency, long availableBalance,
            String pinPvv, Integer pinPvki,
            String mdkUnderLmk, String mdkKcv, Integer mdkLength,
            String panSequenceNumber, String arpcArcHex) {
        PosCard card = new PosCard();
        card.panHash = panHash;
        card.panMasked = panMasked;
        card.expiryYymm = expiryYymm;
        card.status = "ACTIVE";
        card.currency = currency;
        card.availableBalance = availableBalance;
        card.blockedBalance = 0;
        card.pinPvv = pinPvv;
        card.pinPvki = pinPvki;
        card.mdkUnderLmk = mdkUnderLmk;
        card.mdkKcv = mdkKcv;
        card.mdkLength = mdkLength;
        card.panSequenceNumber = panSequenceNumber == null ? "00" : panSequenceNumber;
        card.arpcArcHex = arpcArcHex;
        return card;
    }

    public boolean isActive() { return "ACTIVE".equals(status); }
    public String getExpiryYymm() { return expiryYymm; }
    public String getCurrency() { return currency; }
    public long getAvailableBalance() { return availableBalance; }
    public String getPinPvv() { return pinPvv; }
    public Integer getPinPvki() { return pinPvki; }
    public String getMdkUnderLmk() { return mdkUnderLmk; }
    public String getMdkKcv() { return mdkKcv; }
    public Integer getMdkLength() { return mdkLength; }
    public String getPanSequenceNumber() { return panSequenceNumber; }
    public String getArpcArcHex() { return arpcArcHex; }
    public Integer getLastAtc() { return lastAtc; }

    public boolean isAtcFresh(int atc) {
        if (atc < 0 || atc > 0xFFFF) return false;
        return lastAtc == null || atc > lastAtc;
    }

    public void recordAtc(int atc) {
        if (!isAtcFresh(atc)) {
            throw new IllegalArgumentException("Stale ATC");
        }
        lastAtc = atc;
    }

    public void updatePinPvv(String pvv) {
        if (pvv == null || !pvv.matches("\\d{4}") || pinPvki == null) {
            throw new IllegalArgumentException("Invalid PVV");
        }
        pinPvv = pvv;
    }

    public void debit(long amount) {
        if (amount < 0 || availableBalance < amount) {
            throw new IllegalArgumentException("Insufficient balance");
        }
        availableBalance -= amount;
    }

    public void reserve(long amount) {
        if (amount < 0 || availableBalance < amount) {
            throw new IllegalArgumentException("Insufficient balance");
        }
        availableBalance -= amount;
        blockedBalance += amount;
    }

    public void release(long amount) {
        if (amount < 0 || blockedBalance < amount) {
            throw new IllegalArgumentException("Invalid hold release");
        }
        blockedBalance -= amount;
        availableBalance += amount;
    }

    public void capture(long amount) {
        if (amount < 0 || blockedBalance < amount) {
            throw new IllegalArgumentException("Invalid hold capture");
        }
        blockedBalance -= amount;
    }

    public void credit(long amount) {
        if (amount < 0) throw new IllegalArgumentException("Invalid credit");
        availableBalance = Math.addExact(availableBalance, amount);
    }
}
