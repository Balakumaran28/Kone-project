package com.liftcare.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scheduled background runner on the local building server that periodically
 * inspects active incidents, evaluates SLA response and resolution deadlines,
 * triggers approaching deadline warnings, and initiates multi-tier escalations.
 */
@Component
public class SlaEvaluationScheduler {

    private static final Logger log = LoggerFactory.getLogger(SlaEvaluationScheduler.class);

    private final SlaEvaluationService slaEvaluationService;
    private final AtomicBoolean isEvaluating = new AtomicBoolean(false);

    @Value("${liftcare.sla.enabled:true}")
    private boolean enabled;

    public SlaEvaluationScheduler(SlaEvaluationService slaEvaluationService) {
        this.slaEvaluationService = slaEvaluationService;
    }

    @Scheduled(fixedDelayString = "${liftcare.sla.evaluation-interval-ms:60000}")
    public void runEvaluationCycle() {
        if (!enabled) {
            return;
        }

        if (!isEvaluating.compareAndSet(false, true)) {
            log.warn("SLA evaluation cycle already in progress; skipping overlapping trigger.");
            return;
        }

        try {
            int evaluated = slaEvaluationService.evaluateSlaPoliciesAndEscalate();
            if (evaluated > 0) {
                log.debug("Completed SLA evaluation cycle for {} open incident(s).", evaluated);
            }
        } catch (Exception ex) {
            log.error("Unhandled exception during scheduled SLA evaluation cycle: {}", ex.getMessage(), ex);
        } finally {
            isEvaluating.set(false);
        }
    }
}
