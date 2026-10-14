package com.omniguardy.backend.domain.sound.application.port.in;

import com.omniguardy.backend.domain.sound.application.model.PlaySoundType;

public interface PlaySoundUseCase {

    void play(PlaySoundType type);
}
