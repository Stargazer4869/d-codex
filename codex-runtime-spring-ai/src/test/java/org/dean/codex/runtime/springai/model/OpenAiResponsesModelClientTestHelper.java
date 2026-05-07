package org.dean.codex.runtime.springai.model;

import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

final class OpenAiResponsesModelClientTestHelper {

    private OpenAiResponsesModelClientTestHelper() {
    }

    static String capturedRequestBody(HttpRequest request) throws Exception {
        HttpRequest.BodyPublisher publisher = request.bodyPublisher().orElseThrow();
        BodyCollector collector = new BodyCollector();
        publisher.subscribe(collector);
        if (!collector.await()) {
            throw new IllegalStateException("Timed out collecting request body");
        }
        return new String(collector.bytes(), StandardCharsets.UTF_8);
    }

    private static final class BodyCollector implements Flow.Subscriber<ByteBuffer> {
        private final CountDownLatch completed = new CountDownLatch(1);
        private final List<ByteBuffer> buffers = new ArrayList<>();

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ByteBuffer item) {
            buffers.add(item.asReadOnlyBuffer());
        }

        @Override
        public void onError(Throwable throwable) {
            completed.countDown();
            throw new RuntimeException(throwable);
        }

        @Override
        public void onComplete() {
            completed.countDown();
        }

        boolean await() throws InterruptedException {
            return completed.await(1, TimeUnit.SECONDS);
        }

        byte[] bytes() {
            int total = buffers.stream().mapToInt(ByteBuffer::remaining).sum();
            byte[] bytes = new byte[total];
            int offset = 0;
            for (ByteBuffer buffer : buffers) {
                ByteBuffer copy = buffer.asReadOnlyBuffer();
                int remaining = copy.remaining();
                copy.get(bytes, offset, remaining);
                offset += remaining;
            }
            return bytes;
        }
    }
}
