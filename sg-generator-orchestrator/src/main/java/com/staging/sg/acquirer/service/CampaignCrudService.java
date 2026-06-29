package com.staging.sg.acquirer.service;

import com.staging.sg.common.dto.CampaignDto;
import com.staging.sg.common.dto.CampaignRequest;
import com.staging.sg.common.entity.Campaign;
import com.staging.sg.common.entity.CampaignLoadStep;
import com.staging.sg.common.entity.User;
import com.staging.sg.common.repository.CampaignLoadStepRepository;
import com.staging.sg.common.repository.CampaignRepository;
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

    public CampaignCrudService(CampaignRepository campaignRepo,
                               CampaignLoadStepRepository stepRepo,
                               UserRepository userRepo) {
        this.campaignRepo = campaignRepo;
        this.stepRepo = stepRepo;
        this.userRepo = userRepo;
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

    private void applyRequest(Campaign c, CampaignRequest req) {
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
