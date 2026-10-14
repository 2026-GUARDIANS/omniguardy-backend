package com.omniguardy.backend.domain.sound.presentation;

import com.omniguardy.backend.domain.sound.application.port.in.PlaySoundUseCase;
import com.omniguardy.backend.domain.sound.application.model.PlaySoundType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sounds")
public class SoundController {

    private final PlaySoundUseCase playSoundUseCase;

    @PostMapping("/{type}")
    public ResponseEntity<Void> play(
            @PathVariable PlaySoundType type
    ) {
        playSoundUseCase.play(type);

        return ResponseEntity.noContent().build();
    }
}
