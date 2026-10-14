package com.omniguardy.backend.domain.sound.application.port.out;

import com.omniguardy.backend.domain.sound.domain.model.SoundType;

public interface SoundCommandPort {

    void send(SoundType type);
}
