package org.example.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SPA fallback: any non-API, non-asset request returns index.html so that
 * React Router can handle client-side navigation directly.
 */
@RestController
public class DashboardController {

    // Match SPA routes only — exclude static asset directories (assets, api, ws, h2-console)
    // and any path that contains a dot (actual file requests like .js / .css).
    // Two levels deep covers / + /dashboard/:id + /settings/:section.
    @GetMapping(value = {
        "/",
        "/{p1:^(?!assets|api|ws|h2-console)[^\\.]+}",
        "/{p1:^(?!assets|api|ws|h2-console)[^\\.]+}/{p2:[^\\.]+}"
    })
    public ResponseEntity<Resource> spa() {
        Resource index = new ClassPathResource("static/index.html");
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(index);
    }
}
