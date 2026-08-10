package com.staging.sg.way4aura.service;

import com.staging.sg.way4aura.domain.*;
import com.staging.sg.way4aura.repository.AuraBindingRepository;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;

@Service
public class AuraBindingResolver {
    private final AuraBindingRepository bindings;
    public AuraBindingResolver(AuraBindingRepository bindings) { this.bindings = bindings; }
    public ResolvedBinding required(AuraBindingType type, String source, Instant at) {
        List<AuraBinding> matches = bindings.resolve(type, source, at);
        if (matches.isEmpty()) throw new AuraMappingBlockedException(type + " binding is missing for " + source);
        if (matches.size() > 1) throw new AuraMappingBlockedException(type + " binding is ambiguous for " + source);
        AuraBinding value = matches.get(0);
        return new ResolvedBinding(value.auraCode(), value.bindingVersion());
    }
    public record ResolvedBinding(String code, int version) {}
}
