package com.oiep.config;

import java.util.List;

/**
 * Optional extension point for the response {@code Content-Security-Policy}.
 *
 * <p>OIEP ships one base policy (see {@link WebConfig#contentSecurityPolicy()}) and no
 * contributors by default, so the header is byte-for-byte unchanged on a normal install. An
 * optional, self-contained component (for example the AI companion) may register a bean
 * implementing this interface to add the directives it genuinely needs, such as a
 * {@code frame-src} for an embedded widget origin.</p>
 *
 * <p>Directives are appended after the base policy, separated by {@code "; "}. Implementations
 * must return complete, fixed directives ({@code "frame-src 'self' https://ai.example"}),
 * never user input, and must fail closed (return an empty list) when their configuration is
 * invalid. There is deliberately no way to remove or weaken a base directive.</p>
 */
public interface CspContributor {

    /**
     * Extra CSP directives to append to the base policy. An empty list means "add nothing".
     *
     * @return directives without a trailing semicolon; {@code null} is treated as empty
     */
    List<String> directives();
}
