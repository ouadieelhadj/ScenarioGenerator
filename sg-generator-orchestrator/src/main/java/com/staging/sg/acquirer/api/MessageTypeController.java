package com.staging.sg.acquirer.api;

import com.staging.sg.common.entity.MessageType;
import com.staging.sg.common.repository.MessageTypeRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/message-types")
public class MessageTypeController {

    private final MessageTypeRepository messageTypeRepository;

    public MessageTypeController(MessageTypeRepository messageTypeRepository) {
        this.messageTypeRepository = messageTypeRepository;
    }

    // GET /api/admin/message-types?network=DMAS&category=AUTHORIZATION (filtres optionnels)
    @GetMapping(params = {"network"})
    public ResponseEntity<List<MessageType>> findByNetwork(
            @RequestParam String network,
            @RequestParam(required = false) String category) {
        List<MessageType> result = (category != null && !category.isBlank())
                ? messageTypeRepository.findByNetworkAndCategory(network, category)
                : messageTypeRepository.findByNetwork(network);
        return ResponseEntity.ok(result);
    }

    // GET /api/admin/message-types
    @GetMapping
    public ResponseEntity<List<MessageType>> findAll() {
        return ResponseEntity.ok(messageTypeRepository.findByActiveTrue());
    }

    // POST /api/admin/message-types
    @PostMapping
    @PreAuthorize("hasAuthority('CATALOG_MANAGE')")
    public ResponseEntity<MessageType> create(@RequestBody MessageType mt) {
        return ResponseEntity.ok(messageTypeRepository.save(mt));
    }

    // PUT /api/admin/message-types/{id}
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CATALOG_MANAGE')")
    public ResponseEntity<MessageType> update(@PathVariable Long id,
                                               @RequestBody MessageType mt) {
        mt.setId(id);
        return ResponseEntity.ok(messageTypeRepository.save(mt));
    }

    // DELETE /api/admin/message-types/{id}
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CATALOG_MANAGE')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        messageTypeRepository.findById(id).ifPresent(m -> {
            m.setActive(false);
            messageTypeRepository.save(m);
        });
        return ResponseEntity.ok(Map.of("message", "MessageType disabled"));
    }
}
