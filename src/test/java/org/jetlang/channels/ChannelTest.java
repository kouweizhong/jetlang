package org.jetlang.channels;

import org.jetlang.PerfTimer;
import org.jetlang.core.Callback;
import org.jetlang.core.Disposable;
import org.jetlang.core.Filter;
import org.jetlang.core.MessageReader;
import org.jetlang.core.RunnableExecutorImpl;
import org.jetlang.core.SynchronousDisposingExecutor;
import org.jetlang.fibers.Fiber;
import org.jetlang.fibers.FiberStub;
import org.jetlang.fibers.PoolFiberFactory;
import org.jetlang.fibers.ThreadFiber;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChannelTest {

    @Test
    public void basicPubSubWithThreads() throws InterruptedException {

        //start receiver thread
        Fiber receiver = new ThreadFiber();
        receiver.start();

        final CountDownLatch latch = new CountDownLatch(1);

        MemoryChannel<String> channel = new MemoryChannel<String>();

        Callback<String> onMsg = new Callback<String>() {
            public void onMessage(String message) {
                latch.countDown();
            }
        };
        //add subscription for message on receiver thread
        channel.subscribe(receiver, onMsg);

        //publish message to receive thread. the publish method is thread safe.
        channel.publish("Hello");

        //wait for receiving thread to receive message
        latch.await(10, TimeUnit.SECONDS);

        //shutdown thread
        receiver.dispose();
    }


    @Test
    public void PubSub() {
        MemoryChannel<String> channel = new MemoryChannel<String>();
        SynchronousDisposingExecutor queue = new SynchronousDisposingExecutor();
        channel.publish("hello");
        final List<String> received = new ArrayList<String>();
        Callback<String> onReceive = new Callback<String>() {
            public void onMessage(String data) {
                received.add(data);
            }
        };
        channel.subscribe(queue, onReceive);
        channel.publish("hello");
        assertEquals(1, received.size());
        assertEquals("hello", received.get(0));

        channel.clearSubscribers();
        channel.publish("hello");
    }

    @Test
    public void pubSubFilterTest() {
        MemoryChannel<Integer> channel = new MemoryChannel<Integer>();
        SynchronousDisposingExecutor execute = new SynchronousDisposingExecutor();
        final List<Integer> received = new ArrayList<Integer>();
        Callback<Integer> onReceive = new Callback<Integer>() {
            public void onMessage(Integer num) {
                received.add(num);
            }
        };

        Filter<Integer> filter = new Filter<Integer>() {
            public boolean passes(Integer msg) {
                return msg % 2 == 0;
            }
        };
        ChannelSubscription<Integer> subber = new ChannelSubscription<Integer>(execute, onReceive, filter);

        channel.subscribeOnProducerThread(execute, subber);
        for (int i = 0; i <= 4; i++) {
            channel.publish(i);
        }
        assertEquals(3, received.size());
        assertEquals(0, received.get(0).intValue());
        assertEquals(2, received.get(1).intValue());
        assertEquals(4, received.get(2).intValue());

    }

    @Test
    public void pubSubUnsubscribe() {
        MemoryChannel<String> channel = new MemoryChannel<String>();
        SynchronousDisposingExecutor execute = new SynchronousDisposingExecutor();
        final boolean[] received = new boolean[1];
        Callback<String> onReceive = new Callback<String>() {
            public void onMessage(String message) {
                assertEquals("hello", message);
                received[0] = true;
            }
        };
        Disposable unsub = channel.subscribe(execute, onReceive);
        channel.publish("hello");
        assertTrue(received[0]);
        unsub.dispose();
        channel.publish("hello");
        unsub.dispose();
    }

    @Test
    public void SubToBatch() {
        MemoryChannel<String> channel = new MemoryChannel<String>();
        FiberStub execute = new FiberStub();
        final boolean[] received = new boolean[1];
        Callback<List<String>> onReceive = new Callback<List<String>>() {
            public void onMessage(List<String> data) {
                assertEquals(5, data.size());
                assertEquals("0", data.get(0));
                assertEquals("4", data.get(4));
                received[0] = true;
            }
        };

        BatchSubscriber<String> subscriber = new BatchSubscriber<String>(execute, onReceive, 10, TimeUnit.MILLISECONDS);
        channel.subscribe(subscriber);

        for (int i = 0; i < 5; i++) {
            channel.publish(i + "");
        }
        assertEquals(1, execute.Scheduled.size());
        execute.Scheduled.get(0).run();
        assertTrue(received[0]);
        execute.Scheduled.clear();
        received[0] = false;

        channel.publish("5");
        assertFalse(received[0]);
        assertEquals(1, execute.Scheduled.size());
    }

    @Test
    public void subToKeyedBatch() {
        MemoryChannel<Integer> channel = new MemoryChannel<Integer>();
        FiberStub execute = new FiberStub();
        final boolean[] received = new boolean[1];
        Callback<Map<String, Integer>> onReceive = new Callback<Map<String, Integer>>() {
            public void onMessage(Map<String, Integer> data) {
                assertEquals(2, data.keySet().size());
                assertEquals(data.get("0"), new Integer(0));
                received[0] = true;
            }
        };
        Converter<Integer, String> key = new Converter<Integer, String>() {
            public String convert(Integer msg) {
                return msg.toString();
            }
        };
        KeyedBatchSubscriber<String, Integer> subscriber
                = new KeyedBatchSubscriber<String, Integer>(execute, onReceive, 0, TimeUnit.MILLISECONDS, key);
        channel.subscribe(subscriber);

        for (int i = 0; i < 5; i++) {
            channel.publish(i % 2);
        }

        assertEquals(1, execute.Scheduled.size());
        execute.Scheduled.get(0).run();
        assertTrue(received[0]);
        execute.Scheduled.clear();
        received[0] = false;
        channel.publish(999);
        assertFalse(received[0]);
        assertEquals(1, execute.Scheduled.size());
    }


    @Test
    public void SubscribeToLast() {
        MemoryChannel<Integer> channel = new MemoryChannel<Integer>();
        FiberStub execute = new FiberStub();
        final List<Integer> received = new ArrayList<Integer>();
        Callback<Integer> onReceive = new Callback<Integer>() {
            public void onMessage(Integer data)

            {
                received.add(data);
            }
        };
        LastSubscriber<Integer> lastSub = new LastSubscriber<Integer>(execute, onReceive, 3, TimeUnit.MILLISECONDS);
        channel.subscribe(lastSub);
        for (int i = 0; i < 5; i++) {
            channel.publish(i);
        }
        assertEquals(1, execute.Scheduled.size());
        assertEquals(0, received.size());
        execute.Scheduled.get(0).run();
        assertEquals(1, received.size());
        assertEquals(4, received.get(0).intValue());

        received.clear();
        execute.Scheduled.clear();
        channel.publish(5);
        assertEquals(1, execute.Scheduled.size());
        execute.Scheduled.get(0).run();
        assertEquals(5, received.get(0).intValue());
    }

    //

    @Test
    public void AsyncRequestReplyWithPrivateChannel() throws InterruptedException {
        MemoryChannel<MemoryChannel<String>> requestChannel = new MemoryChannel<MemoryChannel<String>>();
        MemoryChannel<String> replyChannel = new MemoryChannel<String>();
        Fiber responder = startFiber();
        Fiber receiver = startFiber();
        final CountDownLatch reset = new CountDownLatch(1);
        Callback<MemoryChannel<String>> onRequest = new Callback<MemoryChannel<String>>() {
            public void onMessage(MemoryChannel<String> message) {
                message.publish("hello");
            }
        };

        requestChannel.subscribe(responder, onRequest);
        Callback<String> onMsg = new Callback<String>() {
            public void onMessage(String message) {
                assertEquals("hello", message);
                reset.countDown();
            }
        };
        replyChannel.subscribe(receiver, onMsg);
        requestChannel.publish(replyChannel);
        assertTrue(reset.await(10, TimeUnit.SECONDS));
        responder.dispose();
        receiver.dispose();
    }

    @Test
    public void asyncRequestReplyWithBlockingQueue() throws InterruptedException {
        MemoryChannel<BlockingQueue<String>> requestChannel = new MemoryChannel<BlockingQueue<String>>();
        Fiber responder = startFiber();
        Callback<BlockingQueue<String>> onRequest = new Callback<BlockingQueue<String>>() {
            public void onMessage(BlockingQueue<String> message) {
                for (int i = 0; i < 5; i++)
                    message.add("hello" + i);
            }
        };

        requestChannel.subscribe(responder, onRequest);

        BlockingQueue<String> requestQueue = new ArrayBlockingQueue<String>(5);

        requestChannel.publish(requestQueue);
        for (int i = 0; i < 5; i++) {
            assertEquals("hello" + i, requestQueue.poll(30, TimeUnit.SECONDS));
        }
    }


    private int count = 0;

    private Fiber startFiber() {
        Fiber responder = new ThreadFiber(new RunnableExecutorImpl(), "thread" + (count++), true);
        responder.start();
        return responder;
    }

    @Test
    public void pointToPointPerfTestWithThread() throws InterruptedException {
        ThreadFiber bus = new ThreadFiber();
        bus.start();
        runPerfTest(bus);
    }

    @Test
    public void pointToPointPerfTestWithPool() throws InterruptedException {
        ExecutorService serv = Executors.newFixedThreadPool(3);
        PoolFiberFactory fact = new PoolFiberFactory(serv);
        Fiber bus = fact.create();
        bus.start();
        runPerfTest(bus);
        fact.dispose();
        serv.shutdown();
    }

    private void runPerfTest(Fiber bus) throws InterruptedException {
        MemoryChannel<String> channel = new MemoryChannel<String>();

        final int max = 10000000;
        final CountDownLatch reset = new CountDownLatch(1);
        Callback<String> onMsg = new Callback<String>() {
            int count = 0;

            public void onMessage(String msg) {
                if (++count == max) {
                    reset.countDown();
                }
            }
        };
        channel.subscribe(bus, onMsg);
        PerfTimer timer = new PerfTimer(max);
        try {
            for (int i = 0; i < max; i++) {
                channel.publish("msg");
            }
            boolean result = reset.await(30, TimeUnit.SECONDS);
            assertTrue(result);
        } finally {
            timer.dispose();
            bus.dispose();
        }
    }

    @Test
    public void batchingPerfTest() throws InterruptedException {
        ThreadFiber bus = new ThreadFiber();
        bus.start();

        MemoryChannel<String> channel = new MemoryChannel<String>();

        final int max = 10000000;
        final CountDownLatch reset = new CountDownLatch(1);
        Callback<List<String>> cb = new Callback<List<String>>() {
            int total = 0;

            public void onMessage(List<String> batch) {
                total += batch.size();
                if (total == max) {
                    reset.countDown();
                }
            }
        };

        BatchSubscriber<String> batch = new BatchSubscriber<String>(bus, cb, 0, TimeUnit.MILLISECONDS);
        channel.subscribe(batch);
        PerfTimer timer = new PerfTimer(max);
        try {
            for (int i = 0; i < max; i++) {
                channel.publish("msg");
            }
            boolean result = reset.await(30, TimeUnit.SECONDS);
            assertTrue(result);
        } finally {
            timer.dispose();
            bus.dispose();
        }
    }

    @Test
    public void recyclingBatchingTest() throws InterruptedException {
        ThreadFiber bus = new ThreadFiber();
        bus.start();

        MemoryChannel<String> channel = new MemoryChannel<String>();

        final int max = 10000000;
        final CountDownLatch reset = new CountDownLatch(1);
        Callback<MessageReader<String>> cb = new Callback<MessageReader<String>>() {
            int total = 0;

            public void onMessage(MessageReader<String> batch) {
                total += batch.size();
                if (total == max) {
                    reset.countDown();
                }
            }
        };

        RecyclingBatchSubscriber<String> batch = new RecyclingBatchSubscriber<String>(bus, cb, 0, TimeUnit.MILLISECONDS);
        channel.subscribe(batch);
        PerfTimer timer = new PerfTimer(max);
        try {
            for (int i = 0; i < max; i++) {
                channel.publish("msg");
            }
            boolean result = reset.await(30, TimeUnit.SECONDS);
            assertTrue(result);
        } finally {
            timer.dispose();
            bus.dispose();
        }
    }


}

