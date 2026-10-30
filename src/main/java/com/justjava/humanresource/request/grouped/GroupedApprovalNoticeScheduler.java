package com.justjava.humanresource.request.grouped;

import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Keep SMTP work off the application's shared scheduling thread without replacing its scheduler bean. */
@Component
public class GroupedApprovalNoticeScheduler {
    private final GroupedApprovalNoticeDispatcher dispatcher;
    private final AtomicBoolean running = new AtomicBoolean();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon(true).name("grouped-approval-mail").factory());

    public GroupedApprovalNoticeScheduler(GroupedApprovalNoticeDispatcher dispatcher) { this.dispatcher = dispatcher; }

    @Scheduled(fixedDelayString="${app.requests.grouped-approval.notification-delay-ms:15000}")
    public void trigger() {
        if (!running.compareAndSet(false,true)) return;
        try {
            worker.execute(() -> { try { dispatcher.dispatch(); } finally { running.set(false); } });
        } catch (RejectedExecutionException closed) { running.set(false); }
    }

    @PreDestroy
    public void close() {
        worker.shutdown();
        try { if (!worker.awaitTermination(5,TimeUnit.SECONDS)) worker.shutdownNow(); }
        catch (InterruptedException interrupted) { worker.shutdownNow(); Thread.currentThread().interrupt(); }
    }
}
