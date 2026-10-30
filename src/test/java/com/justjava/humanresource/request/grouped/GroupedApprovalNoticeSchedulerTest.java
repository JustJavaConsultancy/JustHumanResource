package com.justjava.humanresource.request.grouped;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GroupedApprovalNoticeSchedulerTest {
    @Test void slowMailDoesNotBlockTimerOrCreateAnUnboundedQueue() throws Exception {
        var dispatcher = mock(GroupedApprovalNoticeDispatcher.class);
        var started = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(invocation -> { started.countDown(); release.await(5,TimeUnit.SECONDS); return null; }).when(dispatcher).dispatch();
        var scheduler = new GroupedApprovalNoticeScheduler(dispatcher);
        try {
            scheduler.trigger(); assertThat(started.await(5,TimeUnit.SECONDS)).isTrue();
            scheduler.trigger(); scheduler.trigger();
            verify(dispatcher,times(1)).dispatch();
        } finally { release.countDown(); scheduler.close(); }
    }
}
