package com.staging.sg.acquirer.service;

import com.staging.sg.common.dto.CampaignDto;
import com.staging.sg.common.dto.CampaignExecutionDto;
import com.staging.sg.common.dto.CampaignRequest;
import com.staging.sg.common.entity.Campaign;
import com.staging.sg.common.entity.CampaignExecution;
import com.staging.sg.common.entity.CampaignLoadStep;
import com.staging.sg.common.entity.User;
import com.staging.sg.common.repository.CampaignLoadStepRepository;
import com.staging.sg.common.repository.CampaignExecutionRepository;
import com.staging.sg.common.repository.CampaignRepository;
import com.staging.sg.common.repository.MessageTypeRepository;
import com.staging.sg.common.repository.NetworkRepository;
import com.staging.sg.common.entity.MessageType;
import com.staging.sg.common.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CampaignCrudService {

    private static final Logger log = LoggerFactory.getLogger(CampaignCrudService.class);

    private final CampaignRepository campaignRepo;
    private final CampaignLoadStepRepository stepRepo;
    private final UserRepository userRepo;
    private final CampaignExecutionRepository execRepo;
    private final MessageTypeRepository messageTypeRepo;
    private final NetworkRepository networkRepo;

    public CampaignCrudService(CampaignRepository campaignRepo,
                               CampaignLoadStepRepository stepRepo,
                               UserRepository userRepo,
                               CampaignExecutionRepository execRepo,
                               MessageTypeRepository messageTypeRepo,
                               NetworkRepository networkRepo) {
        this.campaignRepo = campaignRepo;
        this.stepRepo = stepRepo;
        this.userRepo = userRepo;
        this.execRepo = execRepo;
        this.messageTypeRepo = messageTypeRepo;
        this.networkRepo = networkRepo;
    }

    /** Lit une execution de campagne par son id (suivi / resultat). */
    @Transactional(readOnly = true)
    public CampaignExecutionDto findExecution(Long executionId) {
        CampaignExecution e = execRepo.findById(executionId)
                .orElseThrow(() -> new RuntimeException("Execution introuvable : " + executionId));
        return toExecDto(e);
    }

    /** Liste les executions d une campagne (plus recentes d abord). */
    @Transactional(readOnly = true)
    public List<CampaignExecutionDto> findExecutionsByCampaign(Long campaignId) {
        if (!campaignRepo.existsById(campaignId))
            throw new RuntimeException("Campagne introuvable : " + campaignId);
        return execRepo.findByCampaignIdOrderByIdDesc(campaignId)
                .stream().map(this::toExecDto).collect(Collectors.toList());
    }

    private CampaignExecutionDto toExecDto(CampaignExecution e) {
        CampaignExecutionDto d = new CampaignExecutionDto();
        d.setId(e.getId());
        d.setCampaignId(e.getCampaign() != null ? e.getCampaign().getId() : null);
        d.setCampaignName(e.getCampaign() != null ? e.getCampaign().getName() : null);
        d.setStatus(e.getStatus());
        d.setVerdict(e.getVerdict());
        d.setVerdictDetail(e.getVerdictDetail());
        d.setTpsTarget(e.getTpsTarget());
        d.setDurationSeconds(e.getDurationSeconds());
        d.setTxTotal(e.getTxTotal());
        d.setTxSent(e.getTxSent());
        d.setTxApproved(e.getTxApproved());
        d.setTxDeclined(e.getTxDeclined());
        d.setTpsActualAvg(e.getTpsActualAvg());
        d.setResponseTimeAvg(e.getResponseTimeAvg());
        d.setResponseTimeMin(e.getResponseTimeMin());
        d.setResponseTimeMax(e.getResponseTimeMax());
        d.setResponseTimeP95(e.getResponseTimeP95());
        d.setResponseTimeP99(e.getResponseTimeP99());
        d.setStartedAt(e.getStartedAt());
        d.setEndedAt(e.getEndedAt());
        return d;
    }

    @Transactional(readOnly = true)
    public List<CampaignDto> findAll() {
        return campaignRepo.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CampaignDto findById(Long id) {
        Campaign c = campaignRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Campagne introuvable : " + id));
        return toDto(c);
    }

    @Transactional
    public CampaignDto create(CampaignRequest req, String login) {
        if (req.getName() == null || req.getName().isBlank())
            throw new RuntimeException("Le nom de la campagne est obligatoire");
        User user = userRepo.findByLogin(login)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable : " + login));
        Campaign c = new Campaign();
        applyRequest(c, req);
        c.setCreatedBy(user);
        Campaign saved = campaignRepo.save(c);
        saveSteps(saved, req);
        log.info("[CAMPAIGN-CRUD] cree id={} name={} par {}", saved.getId(), saved.getName(), login);
        return toDto(campaignRepo.findById(saved.getId()).orElseThrow());
    }

    @Transactional
    public CampaignDto update(Long id, CampaignRequest req) {
        Campaign c = campaignRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Campagne introuvable : " + id));
        applyRequest(c, req);
        campaignRepo.save(c);
        // Remplacer les paliers : supprimer les anciens, recreer
        List<CampaignLoadStep> old = stepRepo.findByCampaignIdOrderByStepOrderAsc(id);
        stepRepo.deleteAll(old);
        saveSteps(c, req);
        log.info("[CAMPAIGN-CRUD] maj id={}", id);
        return toDto(campaignRepo.findById(id).orElseThrow());
    }

    @Transactional
    public void delete(Long id) {
        Campaign c = campaignRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Campagne introuvable : " + id));
        List<CampaignLoadStep> steps = stepRepo.findByCampaignIdOrderByStepOrderAsc(id);
        stepRepo.deleteAll(steps);
        campaignRepo.delete(c);
        log.info("[CAMPAIGN-CRUD] supprime id={}", id);
    }

    // ----- helpers -----

    private void validateNetworkConsistency(String network, String category, String initiator) {
        if (!networkRepo.existsByCode(network))
            throw new RuntimeException("Reseau inconnu : '" + network + "'. Voir table networks.");
        if (!"ACQUIRER".equals(initiator) && !"ISSUER".equals(initiator))
            throw new RuntimeException("Initiateur invalide : '" + initiator + "'. Valeurs : ACQUIRER | ISSUER.");
        if (category == null || category.isBlank()) {
            log.warn("[CAMPAIGN-VALIDATION] category vide (network={}) : direction ignoree", network);
            return;
        }
        java.util.List<MessageType> types = messageTypeRepo.findByNetworkAndCategory(network, category);
        if (types.isEmpty()) {
            log.warn("[CAMPAIGN-VALIDATION] aucun type pour (network={}, category={}) : coherence non verifiee", network, category);
            return;
        }
        for (MessageType mt : types) {
            String dir = mt.getDirection();
            boolean ok = "ACQUIRER".equals(initiator)
                    ? ("ACQ_TO_ISS".equals(dir) || "BOTH".equals(dir))
                    : ("ISS_TO_ACQ".equals(dir) || "BOTH".equals(dir));
            if (!ok)
                throw new RuntimeException("Incoherence sens : initiateur=" + initiator
                        + " mais type " + mt.getCode() + " (" + category + ") direction=" + dir
                        + ". ACQUIRER exige ACQ_TO_ISS/BOTH ; ISSUER exige ISS_TO_ACQ/BOTH.");
        }
        log.info("[CAMPAIGN-VALIDATION] OK network={} category={} initiator={}", network, category, initiator);
    }

    private void applyRequest(Campaign c, CampaignRequest req) {
        String network   = (req.getNetwork()   != null && !req.getNetwork().isBlank())   ? req.getNetwork()   : "DMAS";
        String initiator = (req.getInitiator() != null && !req.getInitiator().isBlank()) ? req.getInitiator() : "ACQUIRER";
        validateNetworkConsistency(network, req.getCategory(), initiator);
        c.setNetwork(network);
        c.setInitiator(initiator);
        c.setName(req.getName());
        c.setDescription(req.getDescription());
        c.setCategory(req.getCategory());
        c.setConfig(req.getConfig());
        c.setExpectedDe039(req.getExpectedDe039());
        if (req.getActive() != null) c.setActive(req.getActive());
        c.setSlaP95MaxMs(req.getSlaP95MaxMs());
        c.setSlaErrorRateMax(req.getSlaErrorRateMax());
        c.setSlaApprovalMin(req.getSlaApprovalMin());
        c.setStopOnErrorRate(req.getStopOnErrorRate());
    }

    private void saveSteps(Campaign c, CampaignRequest req) {
        if (req.getLoadSteps() == null) return;
        for (CampaignRequest.LoadStepRequest s : req.getLoadSteps()) {
            CampaignLoadStep step = new CampaignLoadStep();
            step.setCampaign(c);
            step.setStepOrder(s.getStepOrder());
            step.setStartSeconds(s.getStartSeconds());
            step.setEndSeconds(s.getEndSeconds());
            step.setTpsValue(s.getTpsValue());
            step.setConcurrency(s.getConcurrency());
            stepRepo.save(step);
        }
    }

    private CampaignDto toDto(Campaign c) {
        CampaignDto d = new CampaignDto();
        d.setId(c.getId());
        d.setName(c.getName());
        d.setDescription(c.getDescription());
        d.setCategory(c.getCategory());
        d.setNetwork(c.getNetwork());
        d.setInitiator(c.getInitiator());
        d.setConfig(c.getConfig());
        d.setExpectedDe039(c.getExpectedDe039());
        d.setActive(c.isActive());
        d.setCreatedAt(c.getCreatedAt());
        d.setCreatedByLogin(c.getCreatedBy() != null ? c.getCreatedBy().getLogin() : null);
        d.setSlaP95MaxMs(c.getSlaP95MaxMs());
        d.setSlaErrorRateMax(c.getSlaErrorRateMax());
        d.setSlaApprovalMin(c.getSlaApprovalMin());
        d.setStopOnErrorRate(c.getStopOnErrorRate());
        List<CampaignLoadStep> steps = stepRepo.findByCampaignIdOrderByStepOrderAsc(c.getId());
        d.setLoadSteps(steps.stream().map(s -> {
            CampaignDto.LoadStepDto sd = new CampaignDto.LoadStepDto();
            sd.setId(s.getId());
            sd.setStepOrder(s.getStepOrder());
            sd.setStartSeconds(s.getStartSeconds());
            sd.setEndSeconds(s.getEndSeconds());
            sd.setTpsValue(s.getTpsValue());
            sd.setConcurrency(s.getConcurrency());
            return sd;
        }).collect(Collectors.toList()));
        return d;
    }
}
