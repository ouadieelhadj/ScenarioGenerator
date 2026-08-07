package com.staging.sg.switchlab.bff.service;

import com.staging.sg.switchlab.contracts.SwitchLabIndustrialReadiness;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SwitchLabIndustrializationService {
    private final SwitchLabOverviewService overview;
    public SwitchLabIndustrializationService(SwitchLabOverviewService overview){this.overview=overview;}
    public List<SwitchLabIndustrialReadiness> readiness(){return List.of(
        ready("INSTALL_UPDATE","Installation et mise à jour","BFF et bundles séparés"),
        ready("BACKUP","Sauvegarde de configuration","Export JSON non sensible"),
        partial("RESTORE","Restauration","Workflow Maker/Checker absent"),
        partial("AUDIT","Audit complet","Traces BFF en mémoire uniquement"),
        partial("LICENSE","Gestion des licences","Dépend du backend simulateur"),
        partial("SECURITY","Durcissement sécurité","Résolveur de secrets et plusieurs API backend manquent"),
        partial("OBSERVABILITY","Observabilité et alertes","Health disponible, moteur d'alertes persistant absent"),
        ready("DOCUMENTATION","Documentation exploitation","Cadrage et journaux de reprise présents"),
        partial("QUALIFICATION","Performance et non-régression","Compilations réussies, tests différés par décision utilisateur"));}
    public Map<String,Object> backup(){Map<String,Object> result=new LinkedHashMap<>();result.put("schemaVersion","1.0");result.put("product","SWITCHLAB");result.put("generatedAt",Instant.now());result.put("environments",overview.environments());result.put("features",List.of("POS","TEST_CENTER","ONLINE","CLEARING","ECOMMERCE_3DS"));result.put("containsSecrets",false);result.put("restorePolicy","MAKER_CHECKER_REQUIRED_NOT_AVAILABLE");return result;}
    private SwitchLabIndustrialReadiness ready(String code,String label,String evidence){return new SwitchLabIndustrialReadiness(code,label,"READY",evidence,null);}
    private SwitchLabIndustrialReadiness partial(String code,String label,String limitation){return new SwitchLabIndustrialReadiness(code,label,"PARTIAL",null,limitation);}
}
