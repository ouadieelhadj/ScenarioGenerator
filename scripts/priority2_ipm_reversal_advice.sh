#!/bin/bash
# ═══════════════════════════════════════════════════════════
# PRIORITÉ 2 — Générer 1240 depuis 0400 Reversal et 0120 Advice
# ═══════════════════════════════════════════════════════════

cd "D:/MoneyCore/ScenarioGenerator"
GEN_SVC="sg-dmcs-generator/src/main/java/com/staging/sg/dmcs/generator/service/IpmGeneratorService.java"

echo "══════════════════════════════════════"
echo "  1. Imports + repositories Reversal/Advice"
echo "══════════════════════════════════════"

# Ajouter imports entités
sed -i 's/import com.staging.sg.common.entity.AcqAuthorization;/import com.staging.sg.common.entity.AcqAuthorization;\nimport com.staging.sg.common.entity.AcqReversal;\nimport com.staging.sg.common.entity.AcqAdvice;/' "$GEN_SVC"

# Ajouter imports repositories
sed -i 's/import com.staging.sg.common.repository.AcqAuthorizationRepository;/import com.staging.sg.common.repository.AcqAuthorizationRepository;\nimport com.staging.sg.common.repository.AcqReversalRepository;\nimport com.staging.sg.common.repository.AcqAdviceRepository;/' "$GEN_SVC"

# Ajouter champs repositories
sed -i 's/    private final AcqAuthorizationRepository acqAuthRepository;/    private final AcqAuthorizationRepository acqAuthRepository;\n    private final AcqReversalRepository      acqReversalRepository;\n    private final AcqAdviceRepository        acqAdviceRepository;/' "$GEN_SVC"

# Mettre à jour le constructeur — paramètres
sed -i 's/    public IpmGeneratorService(AcqAuthorizationRepository acqAuthRepository,/    public IpmGeneratorService(AcqAuthorizationRepository acqAuthRepository,\n                                AcqReversalRepository acqReversalRepository,\n                                AcqAdviceRepository acqAdviceRepository,/' "$GEN_SVC"

# Mettre à jour le constructeur — assignations
sed -i 's/        this.acqAuthRepository   = acqAuthRepository;/        this.acqAuthRepository    = acqAuthRepository;\n        this.acqReversalRepository = acqReversalRepository;\n        this.acqAdviceRepository  = acqAdviceRepository;/' "$GEN_SVC"

echo "✅ Imports + repositories OK"

echo ""
echo "══════════════════════════════════════"
echo "  2. Ajout build presentment Reversal + Advice"
echo "══════════════════════════════════════"

# Insérer les méthodes buildReversalPresentment + buildAdvicePresentment avant buildTrailer
sed -i 's/    private IpmRecord buildTrailer(IpmFile ipmFile, String fileId,/    \/\/ ── Build Presentment from Reversal 0400 (Function 200 + reason) ──\n    private IpmRecord buildReversalPresentment(IpmFile ipmFile,\n                                                AcqReversal rev, int msgNum) {\n        IpmRecord r = new IpmRecord();\n        r.setIpmFile(ipmFile);\n        r.setMessageNumber(msgNum);\n        r.setRecordType("PRESENTMENT_REV");\n        r.setMti("1240");\n        r.setFunctionCode("200");\n        r.setDe002Pan(rev.getDe002Pan());\n        r.setDe003ProcCode(rev.getDe003ProcCode());\n        r.setDe004Amount(rev.getDe004Amount());\n        r.setDe024FuncCode("200");\n        r.setDe025Reason("4000");        \/\/ Full reversal reason\n        r.setDe037Rrn(rev.getDe037Rrn());\n        r.setDe038AuthCode(rev.getDe038AuthCode());\n        r.setDe041TermId(rev.getDe041TermId());\n        r.setDe042MerchId(rev.getDe042MerchId());\n        r.setDe049Currency(rev.getDe049Currency());\n        r.setDe071MsgNum(String.format("%08d", msgNum));\n        r.setDe005AmountRecon(rev.getDe004Amount());\n        r.setDe050CurrencyRecon(rev.getDe049Currency());\n        r.setDe063NetworkData(rev.getDe037Rrn());\n        String ascii = String.format(\n            "1240|200|TYPE=REVERSAL|PAN=%s|PC=%s|AMT=%012d|RRN=%s|" +\n            "AUTH=%s|TID=%s|MID=%s|CCY=%s|REASON=4000|MSG=%08d",\n            safe(rev.getDe002Pan()), safe(rev.getDe003ProcCode()),\n            rev.getDe004Amount() != null ? rev.getDe004Amount() : 0,\n            safe(rev.getDe037Rrn()), safe(rev.getDe038AuthCode()),\n            safe(rev.getDe041TermId()), safe(rev.getDe042MerchId()),\n            safe(rev.getDe049Currency()), msgNum);\n        r.setRawAscii(ascii);\n        r.setRawHex(toHex(ascii.getBytes()));\n        return r;\n    }\n\n    \/\/ ── Build Presentment from Advice 0120 (Function 200) ──\n    private IpmRecord buildAdvicePresentment(IpmFile ipmFile,\n                                              AcqAdvice adv, int msgNum) {\n        IpmRecord r = new IpmRecord();\n        r.setIpmFile(ipmFile);\n        r.setMessageNumber(msgNum);\n        r.setRecordType("PRESENTMENT_ADV");\n        r.setMti("1240");\n        r.setFunctionCode("200");\n        r.setDe002Pan(adv.getDe002Pan());\n        r.setDe003ProcCode(adv.getDe003ProcCode());\n        r.setDe004Amount(adv.getDe004Amount());\n        r.setDe024FuncCode("200");\n        r.setDe025Reason("00");\n        r.setDe037Rrn(adv.getDe037Rrn());\n        r.setDe038AuthCode(adv.getDe038AuthCode());\n        r.setDe049Currency(adv.getDe049Currency());\n        r.setDe071MsgNum(String.format("%08d", msgNum));\n        r.setDe005AmountRecon(adv.getDe004Amount());\n        r.setDe050CurrencyRecon(adv.getDe049Currency());\n        r.setDe063NetworkData(adv.getDe037Rrn());\n        String ascii = String.format(\n            "1240|200|TYPE=ADVICE|PAN=%s|PC=%s|AMT=%012d|RRN=%s|" +\n            "AUTH=%s|CCY=%s|REASON=%s|MSG=%08d",\n            safe(adv.getDe002Pan()), safe(adv.getDe003ProcCode()),\n            adv.getDe004Amount() != null ? adv.getDe004Amount() : 0,\n            safe(adv.getDe037Rrn()), safe(adv.getDe038AuthCode()),\n            safe(adv.getDe049Currency()), safe(adv.getDe060Reason()), msgNum);\n        r.setRawAscii(ascii);\n        r.setRawHex(toHex(ascii.getBytes()));\n        return r;\n    }\n\n    private IpmRecord buildTrailer(IpmFile ipmFile, String fileId,/' "$GEN_SVC"

echo "✅ buildReversalPresentment + buildAdvicePresentment OK"

echo ""
echo "══════════════════════════════════════"
echo "  3. Chargement reversals/advices + records"
echo "══════════════════════════════════════"

# Après le chargement des authorizations, charger aussi reversals et advices
sed -i 's/        log.info("\[DMCS-GEN\] Found {} approved authorizations", authorizations.size());/        log.info("[DMCS-GEN] Found {} approved authorizations", authorizations.size());\n\n        \/\/ Load reversals + advices for this execution\n        List<AcqReversal> reversals = executionId != null\n                ? acqReversalRepository.findByExecutionId(executionId)\n                : java.util.Collections.emptyList();\n        List<AcqAdvice> advices = executionId != null\n                ? acqAdviceRepository.findByExecutionId(executionId)\n                : java.util.Collections.emptyList();\n        log.info("[DMCS-GEN] Found {} reversals, {} advices", reversals.size(), advices.size());/' "$GEN_SVC"

# Après la boucle des presentments authorizations, ajouter les reversals + advices
sed -i 's/        \/\/ Trailer\n        records.add(buildTrailer(ipmFile, fileId, msgNum,/        \/\/ Reversals → 1240\n        for (AcqReversal rev : reversals) {\n            records.add(buildReversalPresentment(ipmFile, rev, msgNum++));\n        }\n        \/\/ Advices → 1240\n        for (AcqAdvice adv : advices) {\n            records.add(buildAdvicePresentment(ipmFile, adv, msgNum++));\n        }\n\n        \/\/ Trailer\n        records.add(buildTrailer(ipmFile, fileId, msgNum,/' "$GEN_SVC"

echo "✅ Chargement + records OK"

echo ""
echo "══════════════════════════════════════"
echo "  4. Mise à jour compteurs (nbTx + total)"
echo "══════════════════════════════════════"

# Mettre à jour nbTransactions et totalAmount pour inclure reversals/advices
sed -i 's/        ipmFile.setNbTransactions(authorizations.size());/        int totalTx = authorizations.size() + reversals.size() + advices.size();\n        ipmFile.setNbTransactions(totalTx);/' "$GEN_SVC"

echo "✅ Compteurs OK"

echo ""
echo "══════════════════════════════════════"
echo "  5. BUILD"
echo "══════════════════════════════════════"
mvn clean install -DskipTests 2>&1 | tail -8
