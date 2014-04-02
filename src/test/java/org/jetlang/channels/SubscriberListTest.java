package org.jetlang.channels;

import org.jetlang.core.Callback;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SubscriberListTest {

    @Test
    public void addAndRemove() {
        SubscriberList<String> list = new SubscriberList<>();
        final List<String> received = new ArrayList<>();
        Callback<String> cb = new Callback<String>() {
            public void onMessage(String message) {
                received.add(message);
            }
        };

        list.add(cb);
        assertEquals(1, list.size());
        list.publish("hello");
        assertEquals(1, received.size());

        assertTrue(list.remove(cb));
        assertEquals(0, list.size());

        list.publish("bye");
        assertEquals(1, received.size());
    }

    @Test
    public void addAndRemoveWithTwo() {
        SubscriberList<String> list = new SubscriberList<>();
        final List<String> received = new ArrayList<>();
        Callback<String> cb = new Callback<String>() {
            public void onMessage(String message) {
                received.add(message);
            }
        };

        list.add(cb);
        list.add(cb);
        assertEquals(2, list.size());
        list.publish("hello");
        assertEquals(2, received.size());

        assertTrue(list.remove(cb));
        assertEquals(1, list.size());

        list.publish("bye");
        assertEquals(3, received.size());
    }


    @Test
    @Ignore
    public void perfTest() {
        SubscriberList<String> list = new SubscriberList<>();
        Callback<String> cb = new Callback<String>() {
            public void onMessage(String message) {
            }
        };

        list.add(cb);
        list.add(cb);
        list.add(cb);

        for (int i = 0; i < 100000000; i++)
            list.publish("hello");

    }

    @Test
    @Ignore
    public void perfTestWithCopyOnWrite() {
        CopyOnWriteArrayList<Callback<String>> list = new CopyOnWriteArrayList<>();
        Callback<String> cb = new Callback<String>() {
            public void onMessage(String message) {
            }
        };

        list.add(cb);
        list.add(cb);
        list.add(cb);

        for (int i = 0; i < 100000000; i++)
            for (Callback<String> each : list)
                each.onMessage("hello");

    }

}
