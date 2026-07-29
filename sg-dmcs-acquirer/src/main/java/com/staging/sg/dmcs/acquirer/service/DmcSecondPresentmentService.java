package com.staging.sg.dmcs.acquirer.service;

import com.staging.sg.common.entity.DmcsAcquirerClearingTransaction;
import com.staging.sg.common.repository.DmcsAcquirerClearingTransactionRepository;
import com.staging.sg.dmcs.common.ipm.DmcIpmFileCodec;
import com.staging.sg.dmcs.common.ipm.DmcIpmFileWriter;
import com.staging.sg.dmcs.common.ipm.DmcIpmMessageFactory;
import com.staging.sg.dmcs.common.ipm.DmcIpmPackager;
import com.staging.sg.dmcs.common.ipm.DmcOutgoingDisputeMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

@Service
public class DmcSecondPresentmentService {
    private final DmcsAcquirerClearingTransactionRepository repository;
    private final DmcIpmPackager packager = new DmcIpmPackager();

    @Value("${dmcs.base-dir:${user.dir}/runtime/dmcs/acquirer}")
    private String baseDirectory;
    @Value("${dmcs.file-type:002}")
    private String fileType;
    @Value("${dmcs.processor-id:22905}")
    private String processorId;
    @Value("${mc.acquirer.defaults.DE093_DEST_ID:00000000000}")
    private String destinationId;
    @Value("${mc.acquirer.defaults.DE094_ORIGIN_ID:00000000000}")
    private String originId;

    public DmcSecondPresentmentService(
            DmcsAcquirerClearingTransactionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public OutgoingFile generate(Command command) throws Exception {
        if (!"205".equals(command.functionCode())
                && !"282".equals(command.functionCode())) {
            throw new IllegalArgumentException(
                    "Second Presentment: DE24 doit valoir 205 ou 282");
        }
        var parent = repository.findById(command.parentTransactionId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "First Chargeback parent introuvable"));
        if (!"FIRST_CHARGEBACK".equals(parent.getLifecycleStage())) {
            throw new IllegalArgumentException(
                    "Le parent doit être un First Chargeback");
        }
        var disputeCommand = new DmcOutgoingDisputeMapper.DisputeCommand(
                command.functionCode(), command.amount(),
                command.messageReasonCode(), command.issuerReference(),
                command.pdsData(), command.businessDate());
        var transaction = DmcOutgoingDisputeMapper.populate(
                new DmcsAcquirerClearingTransaction(), parent, disputeCommand,
                "SECOND_PRESENTMENT", destinationId, originId);

        LocalDate businessDate = transaction.getBusinessDate();
        var parameters = new DmcIpmMessageFactory.FileParameters(
                fileType, businessDate, processorId, command.fileSequence(),
                command.processingMode(), destinationId, originId);
        var built = new DmcIpmMessageFactory(packager).buildDisputes(
                parameters,
                List.of(DmcOutgoingDisputeMapper.toMessage(transaction)));
        Path path = new DmcIpmFileWriter(new DmcIpmFileCodec(packager)).write(
                Path.of(baseDirectory, "outgoing", "second-presentment"),
                built.fileId(), built);
        transaction.setStatus("GENERATED");
        transaction = repository.save(transaction);
        return new OutgoingFile(
                transaction.getId(), built.fileId(), path.toString(),
                built.messages().size(), built.amountChecksum());
    }

    public record Command(
            Long parentTransactionId,
            String functionCode,
            Long amount,
            String messageReasonCode,
            String issuerReference,
            String pdsData,
            LocalDate businessDate,
            int fileSequence,
            String processingMode) {
    }

    public record OutgoingFile(
            Long transactionId,
            String fileId,
            String path,
            int messageCount,
            long amountChecksum) {
    }
}
