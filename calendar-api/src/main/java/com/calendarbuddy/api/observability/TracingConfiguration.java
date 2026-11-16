package com.calendarbuddy.api.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import io.micrometer.observation.ObservationView;
import io.micrometer.tracing.exporter.SpanExportingPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * Tracing settings beyond application.yml (root capability homebuddy-tracing).
 *
 * <p>Health checks and Prometheus scrapes would create a trace every few seconds, so requests to
 * {@code /actuator/**} are not traced. Their HTTP server observation still runs, so the HTTP
 * metrics are unchanged: only its span is dropped before export, and the Spring Security
 * observations inside such a request are skipped, because their spans would otherwise be exported
 * without their parent.
 */
@Configuration(proxyBeanMethods = false)
class TracingConfiguration {

    private static final String ACTUATOR = "/actuator";

    @Bean
    SpanExportingPredicate skipActuatorSpans() {
        return span -> {
            String uri = span.getTags().get("uri");
            return uri == null || !uri.startsWith(ACTUATOR);
        };
    }

    @Bean
    ObservationPredicate skipSecurityObservationsOfActuatorRequests() {
        return (name, context) ->
                !(name.startsWith("spring.security.") && insideActuatorRequest(context));
    }

    static boolean insideActuatorRequest(Observation.ContextView context) {
        for (ObservationView parent = context.getParentObservation();
                parent != null;
                parent = parent.getContextView().getParentObservation()) {
            if (parent.getContextView() instanceof ServerRequestObservationContext server) {
                return server.getCarrier().getRequestURI().startsWith(ACTUATOR);
            }
        }
        return false;
    }
}
