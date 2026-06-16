#!/bin/bash
# ═══════════════════════════════════════════════════════════
# PRIORITÉ 1 — DE obligatoires IPM 1240
# DE005, DE031, DE050, DE063
# ═══════════════════════════════════════════════════════════

cd "D:/MoneyCore/ScenarioGenerator"

echo "══════════════════════════════════════"
echo "  1. ALTER TABLE ipm_records"
echo "══════════════════════════════════════"
export PATH="/d/MoneyCore/PostgreSQL/18/bin:$PATH"
PGPASSWORD=postgres123 psql -U postgres -d scenariogenerator << 'EOF'
ALTER TABLE ipm_records ADD COLUMN IF NOT EXISTS de005_amount_recon  BIGINT;
ALTER TABLE ipm_records ADD COLUMN IF NOT EXISTS de031_acq_ref_data  VARCHAR(23);
ALTER TABLE ipm_records ADD COLUMN IF NOT EXISTS de050_currency_recon VARCHAR(3);
ALTER TABLE ipm_records ADD COLUMN IF NOT EXISTS de063_network_data  VARCHAR(50);
EOF
echo "✅ Colonnes ajoutées"

echo ""
echo "══════════════════════════════════════"
echo "  2. Ajout champs dans IpmRecord.java"
echo "══════════════════════════════════════"

# Ajouter les champs après de071_msg_num
sed -i 's/    @Column(name = "de071_msg_num",  length = 8)          private String  de071MsgNum;/    @Column(name = "de071_msg_num",  length = 8)          private String  de071MsgNum;\n    @Column(name = "de005_amount_recon")                 private Long    de005AmountRecon;\n    @Column(name = "de031_acq_ref_data", length = 23)    private String  de031AcqRefData;\n    @Column(name = "de050_currency_recon", length = 3)   private String  de050CurrencyRecon;\n    @Column(name = "de063_network_data", length = 50)    private String  de063NetworkData;/' \
    sg-common/src/main/java/com/staging/sg/common/entity/IpmRecord.java

# Ajouter getters après getDe071MsgNum
sed -i 's/    public String           getDe071MsgNum()   { return de071MsgNum; }/    public String           getDe071MsgNum()   { return de071MsgNum; }\n    public Long             getDe005AmountRecon()   { return de005AmountRecon; }\n    public String           getDe031AcqRefData()    { return de031AcqRefData; }\n    public String           getDe050CurrencyRecon() { return de050CurrencyRecon; }\n    public String           getDe063NetworkData()   { return de063NetworkData; }/' \
    sg-common/src/main/java/com/staging/sg/common/entity/IpmRecord.java

# Ajouter setters après setDe071MsgNum
sed -i 's/    public void setDe071MsgNum(String v)            { this.de071MsgNum = v; }/    public void setDe071MsgNum(String v)            { this.de071MsgNum = v; }\n    public void setDe005AmountRecon(Long v)         { this.de005AmountRecon = v; }\n    public void setDe031AcqRefData(String v)        { this.de031AcqRefData = v; }\n    public void setDe050CurrencyRecon(String v)     { this.de050CurrencyRecon = v; }\n    public void setDe063NetworkData(String v)       { this.de063NetworkData = v; }/' \
    sg-common/src/main/java/com/staging/sg/common/entity/IpmRecord.java

echo "✅ IpmRecord.java mis à jour"

echo ""
echo "══════════════════════════════════════"
echo "  3. Mapping dans IpmGeneratorService"
echo "══════════════════════════════════════"

# Ajouter le mapping dans buildPresentment (après setDe071MsgNum)
sed -i 's/        r.setDe071MsgNum(String.format("%08d", msgNum));/        r.setDe071MsgNum(String.format("%08d", msgNum));\n        \/\/ Priority 1 — Mandatory IPM fields\n        r.setDe005AmountRecon(auth.getDe004Amount());           \/\/ DE005 = DE004 (same currency)\n        r.setDe050CurrencyRecon(auth.getDe049Currency());       \/\/ DE050 = DE049\n        r.setDe031AcqRefData(buildAcqRefData(auth, msgNum));    \/\/ DE031 ARN 23 pos\n        r.setDe063NetworkData(auth.getDe037Rrn());              \/\/ DE063 = RRN trace/' \
    sg-dmcs-generator/src/main/java/com/staging/sg/dmcs/generator/service/IpmGeneratorService.java

echo "✅ Mapping ajouté dans buildPresentment"

echo ""
echo "══════════════════════════════════════"
echo "  4. Méthode buildAcqRefData + ASCII"
echo "══════════════════════════════════════"

# Ajouter la méthode buildAcqRefData avant le dernier } (helper safe)
sed -i 's/    private String safe(String s)  { return s != null ? s : ""; }/    \/\/ ── DE031 Acquirer Reference Data (23 positions) ────────\n    private String buildAcqRefData(AcqAuthorization auth, int msgNum) {\n        \/\/ Pos 1 : Mixed use (0)\n        \/\/ Pos 2-7 : Acquirer BIN (6 digits)\n        \/\/ Pos 8-11 : Julian date YDDD\n        \/\/ Pos 12-22 : Sequence (11 digits)\n        \/\/ Pos 23 : Check digit\n        String acqBin = safe(auth.getDe032AcqId());\n        if (acqBin.length() > 6) acqBin = acqBin.substring(0, 6);\n        acqBin = String.format("%6s", acqBin).replace(" ", "0");\n        java.time.LocalDate now = java.time.LocalDate.now();\n        int year = now.getYear() % 10;\n        int doy  = now.getDayOfYear();\n        String julian = String.format("%d%03d", year, doy);\n        String seq = String.format("%011d", msgNum);\n        String base = "0" + acqBin + julian + seq;\n        if (base.length() > 22) base = base.substring(0, 22);\n        base = String.format("%-22s", base).replace(" ", "0");\n        int checkDigit = computeLuhn(base);\n        return base + checkDigit;\n    }\n\n    private int computeLuhn(String num) {\n        int sum = 0; boolean alt = false;\n        for (int i = num.length() - 1; i >= 0; i--) {\n            int d = Character.getNumericValue(num.charAt(i));\n            if (alt) { d *= 2; if (d > 9) d -= 9; }\n            sum += d; alt = !alt;\n        }\n        return (10 - (sum % 10)) % 10;\n    }\n\n    private String safe(String s)  { return s != null ? s : ""; }/' \
    sg-dmcs-generator/src/main/java/com/staging/sg/dmcs/generator/service/IpmGeneratorService.java

# Mettre à jour la ligne ASCII pour inclure les nouveaux champs
sed -i 's/            "ACQ=%s|RRN=%s|AUTH=%s|TID=%s|MID=%s|CCY=%s|MSG=%08d",/            "ACQ=%s|RRN=%s|AUTH=%s|TID=%s|MID=%s|CCY=%s|" +\n            "AMT_RECON=%012d|CCY_RECON=%s|ARN=%s|NET=%s|MSG=%08d",/' \
    sg-dmcs-generator/src/main/java/com/staging/sg/dmcs/generator/service/IpmGeneratorService.java

# Ajouter les arguments correspondants (après safe(auth.getDe049Currency()),)
sed -i 's/            safe(auth.getDe049Currency()),\n            msgNum);/            safe(auth.getDe049Currency()),\n            auth.getDe004Amount() != null ? auth.getDe004Amount() : 0,\n            safe(auth.getDe049Currency()),\n            buildAcqRefData(auth, msgNum),\n            safe(auth.getDe037Rrn()),\n            msgNum);/' \
    sg-dmcs-generator/src/main/java/com/staging/sg/dmcs/generator/service/IpmGeneratorService.java

echo "✅ buildAcqRefData + ASCII mis à jour"

echo ""
echo "══════════════════════════════════════"
echo "  5. BUILD"
echo "══════════════════════════════════════"
mvn clean install -DskipTests 2>&1 | tail -8
