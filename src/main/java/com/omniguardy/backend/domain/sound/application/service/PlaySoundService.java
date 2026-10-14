package com.omniguardy.backend.domain.sound.application.service;

import com.omniguardy.backend.domain.sound.application.port.in.PlaySoundUseCase;
import com.omniguardy.backend.domain.sound.application.model.PlaySoundType;
import com.omniguardy.backend.domain.sound.application.port.out.SoundCommandPort;
import com.omniguardy.backend.domain.sound.domain.model.SoundType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlaySoundService implements PlaySoundUseCase {

    private final SoundCommandPort soundCommandPort;

    @Override
    public void play(PlaySoundType type) {
        SoundType soundType = switch (type) {
            case WARNING -> SoundType.WARNING;
            case SIREN -> SoundType.SIREN;
        };
        soundCommandPort.send(soundType);
    }
}
