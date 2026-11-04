package com.omniguardy.backend.domain.securityevent.infrastructure.response;

import com.omniguardy.backend.domain.securityevent.application.port.in.DispatchEarlyWarningInputPort;
import com.omniguardy.backend.domain.securityevent.application.port.out.SecurityResponseLedger.Channel;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;

/** Dedicated pools: a stalled FCM request cannot occupy the sound publisher's worker. */
@Configuration @Slf4j
public class EarlyWarningWorkers {
    @Bean(name = "earlyWarningFcmExecutor")
    ThreadPoolTaskExecutor fcm() { return executor("early-fcm-"); }
    @Bean(name = "earlyWarningSoundExecutor")
    ThreadPoolTaskExecutor sound() { return executor("early-sound-"); }
    private static ThreadPoolTaskExecutor executor(String prefix) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1); executor.setMaxPoolSize(1); executor.setQueueCapacity(1);
        executor.setThreadNamePrefix(prefix);
        executor.setWaitForTasksToCompleteOnShutdown(true); executor.setAwaitTerminationSeconds(10);
        return executor;
    }
    @Bean(name = "earlyWarningTaskScheduler")
    ThreadPoolTaskScheduler scheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1); scheduler.setThreadNamePrefix("early-recovery-");
        return scheduler;
    }
    @Bean EarlyWarningKick kick(DispatchEarlyWarningInputPort service,
            @org.springframework.beans.factory.annotation.Qualifier("earlyWarningFcmExecutor") ThreadPoolTaskExecutor fcm,
            @org.springframework.beans.factory.annotation.Qualifier("earlyWarningSoundExecutor") ThreadPoolTaskExecutor sound) {
        return new EarlyWarningKick(service, fcm, sound);
    }

    public record EarlyWarningKick(DispatchEarlyWarningInputPort service, ThreadPoolTaskExecutor fcm,
                                   ThreadPoolTaskExecutor sound) {
        public void request() {
            submit(fcm, () -> service.dispatch(Channel.FCM));
            submit(sound, () -> service.dispatch(Channel.SOUND));
        }
        private static void submit(ThreadPoolTaskExecutor executor, Runnable task) {
            try { executor.execute(task); }
            catch (org.springframework.core.task.TaskRejectedException ignored) {
                // A bounded queue is already occupied; the independent recovery scheduler will retry PENDING work.
            }
        }
        @Scheduled(initialDelayString = "${omniguardy.early-warning.recovery-delay-ms:1000}",
                fixedDelayString = "${omniguardy.early-warning.recovery-delay-ms:1000}", scheduler = "earlyWarningTaskScheduler")
        public void recover() {
            try { service.recover(); }
            catch (RuntimeException failure) { log.warn("Early warning recovery deferred failureType={}", failure.getClass().getSimpleName()); }
            request();
        }
    }
}
