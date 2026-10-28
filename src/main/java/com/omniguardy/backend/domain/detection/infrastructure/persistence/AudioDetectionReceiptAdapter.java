package com.omniguardy.backend.domain.detection.infrastructure.persistence;

import com.omniguardy.backend.domain.detection.application.port.out.AudioDetectionReceiptPort;
import com.omniguardy.backend.domain.detection.domain.error.DuplicateAudioDetectionException;
import com.omniguardy.backend.domain.detection.domain.model.AudioDetectionReceipt;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;

@Repository
public class AudioDetectionReceiptAdapter implements AudioDetectionReceiptPort {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void claim(String detectionId) {
        try {
            entityManager.persist(new AudioDetectionReceipt(detectionId));
            entityManager.flush();
        } catch (RuntimeException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sql &&
                        (sql.getErrorCode() == 1062 || "23505".equals(sql.getSQLState()))) {
                    throw new DuplicateAudioDetectionException(exception);
                }
            }
            throw exception;
        }
    }
}
