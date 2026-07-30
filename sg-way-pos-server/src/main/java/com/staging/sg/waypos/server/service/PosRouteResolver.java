package com.staging.sg.waypos.server.service;

import com.staging.sg.waypos.server.domain.PosBinRoute;
import com.staging.sg.waypos.server.repository.PosBinRouteRepository;
import org.springframework.stereotype.Service;

@Service
public class PosRouteResolver {
    private final PosBinRouteRepository repository;

    public PosRouteResolver(PosBinRouteRepository repository) {
        this.repository = repository;
    }

    public String resolve(String pan) {
        return repository.findByActiveTrueOrderByPriorityDesc().stream()
                .filter(route -> contains(route, pan))
                .findFirst()
                .map(PosBinRoute::getInterfaceCode)
                .orElse(null);
    }

    private boolean contains(PosBinRoute route, String pan) {
        int length = route.getBinFrom().length();
        if (pan == null || pan.length() < length || route.getBinTo().length() != length) {
            return false;
        }
        String candidate = pan.substring(0, length);
        return candidate.compareTo(route.getBinFrom()) >= 0
                && candidate.compareTo(route.getBinTo()) <= 0;
    }
}
