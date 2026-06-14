package com.staging.sg.acquirer;

import com.staging.sg.common.dto.TestDto;
import com.staging.sg.common.dto.TpsStepDto;
import com.staging.sg.common.entity.MessageType;
import com.staging.sg.common.entity.Test;
import com.staging.sg.common.entity.TpsStep;
import com.staging.sg.common.entity.User;
import com.staging.sg.common.repository.MessageTypeRepository;
import com.staging.sg.common.repository.TestRepository;
import com.staging.sg.common.repository.TpsStepRepository;
import com.staging.sg.common.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TestService {

    private static final Logger log = LoggerFactory.getLogger(TestService.class);

    private final TestRepository        testRepository;
    private final MessageTypeRepository messageTypeRepository;
    private final TpsStepRepository     tpsStepRepository;
    private final UserRepository        userRepository;

    public TestService(TestRepository testRepository,
                       MessageTypeRepository messageTypeRepository,
                       TpsStepRepository tpsStepRepository,
                       UserRepository userRepository) {
        this.testRepository        = testRepository;
        this.messageTypeRepository = messageTypeRepository;
        this.tpsStepRepository     = tpsStepRepository;
        this.userRepository        = userRepository;
    }

    public List<TestDto> findAll() {
        return testRepository.findByActiveTrue().stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    public List<TestDto> findByUser(Long userId) {
        return testRepository.findByAssignedUserId(userId).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    public TestDto findById(Long id) {
        return testRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new RuntimeException("Test not found : " + id));
    }

    @Transactional
    public TestDto create(TestDto dto, String createdByLogin) {
        Test test = new Test();
        test.setName(dto.getName());
        test.setDescription(dto.getDescription());
        test.setCategory(dto.getCategory());
        test.setConfig(dto.getConfig());
        test.setExpectedDe039(dto.getExpectedDe039());
        test.setActive(true);

        if (dto.getMessageTypeId() != null) {
            MessageType mt = messageTypeRepository.findById(dto.getMessageTypeId())
                    .orElseThrow(() -> new RuntimeException("MessageType not found"));
            test.setMessageType(mt);
        }

        userRepository.findByLogin(createdByLogin).ifPresent(test::setCreatedBy);
        Test saved = testRepository.save(test);

        // TPS Steps
        if (dto.getTpsSteps() != null) {
            for (TpsStepDto stepDto : dto.getTpsSteps()) {
                TpsStep step = new TpsStep();
                step.setTest(saved);
                step.setStepOrder(stepDto.getStepOrder());
                step.setStartSeconds(stepDto.getStartSeconds());
                step.setEndSeconds(stepDto.getEndSeconds());
                step.setTpsValue(stepDto.getTpsValue());
                tpsStepRepository.save(step);
            }
        }

        log.info("[TEST] Created — name={}", saved.getName());
        return toDto(saved);
    }

    @Transactional
    public TestDto update(Long id, TestDto dto) {
        Test test = testRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Test not found : " + id));

        if (dto.getName()        != null) test.setName(dto.getName());
        if (dto.getDescription() != null) test.setDescription(dto.getDescription());
        if (dto.getCategory()    != null) test.setCategory(dto.getCategory());
        if (dto.getConfig()      != null) test.setConfig(dto.getConfig());
        if (dto.getExpectedDe039() != null) test.setExpectedDe039(dto.getExpectedDe039());

        if (dto.getMessageTypeId() != null) {
            messageTypeRepository.findById(dto.getMessageTypeId())
                    .ifPresent(test::setMessageType);
        }

        // Update TPS Steps
        if (dto.getTpsSteps() != null) {
            tpsStepRepository.deleteByTestId(id);
            for (TpsStepDto stepDto : dto.getTpsSteps()) {
                TpsStep step = new TpsStep();
                step.setTest(test);
                step.setStepOrder(stepDto.getStepOrder());
                step.setStartSeconds(stepDto.getStartSeconds());
                step.setEndSeconds(stepDto.getEndSeconds());
                step.setTpsValue(stepDto.getTpsValue());
                tpsStepRepository.save(step);
            }
        }

        return toDto(testRepository.save(test));
    }

    @Transactional
    public void delete(Long id) {
        Test test = testRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Test not found : " + id));
        test.setActive(false);
        testRepository.save(test);
    }

    private TestDto toDto(Test t) {
        TestDto dto = new TestDto();
        dto.setId(t.getId());
        dto.setName(t.getName());
        dto.setDescription(t.getDescription());
        dto.setCategory(t.getCategory());
        dto.setConfig(t.getConfig());
        dto.setExpectedDe039(t.getExpectedDe039());
        dto.setActive(t.isActive());
        if (t.getMessageType() != null) {
            dto.setMessageTypeId(t.getMessageType().getId());
            dto.setMessageTypeName(t.getMessageType().getName());
        }
        if (t.getTpsSteps() != null) {
            dto.setTpsSteps(t.getTpsSteps().stream().map(s -> {
                TpsStepDto sd = new TpsStepDto();
                sd.setId(s.getId());
                sd.setStepOrder(s.getStepOrder());
                sd.setStartSeconds(s.getStartSeconds());
                sd.setEndSeconds(s.getEndSeconds());
                sd.setTpsValue(s.getTpsValue());
                return sd;
            }).collect(Collectors.toList()));
        }
        return dto;
    }
}
