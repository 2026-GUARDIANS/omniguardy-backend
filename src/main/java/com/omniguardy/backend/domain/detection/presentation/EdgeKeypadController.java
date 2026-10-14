package com.omniguardy.backend.domain.detection.presentation;

import com.omniguardy.backend.domain.detection.application.port.in.TriggerKeypadCameraUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/keypad")
public class EdgeKeypadController {

    private final TriggerKeypadCameraUseCase triggerKeypadCameraUseCase;

    @PostMapping("/trigger")
    public ResponseEntity<Void> trigger() {

        triggerKeypadCameraUseCase.trigger();

        return ResponseEntity.ok().build();
    }
}
