package com.caracrea.voice.net;

/**
 * Session id to value, read without locks nor allocation by the receiving thread (no boxed {@code Long} per packet),
 * written rarely (a player joins or leaves) by copying the table. Open addressing with linear probing.
 */
final class PeerMap<T> {
    private static final class Table {
        final long[] keys;
        final Object[] values;
        final int size;

        Table(int capacity, int size) {
            keys = new long[capacity];
            values = new Object[capacity];
            this.size = size;
        }
    }

    private volatile Table table = new Table(16, 0);

    private static int slot(long key, int mask) {
        long h = key * 0x9E3779B97F4A7C15L;
        return (int) (h ^ (h >>> 32)) & mask;
    }

    @SuppressWarnings("unchecked")
    T get(long key) {
        Table t = table;
        int mask = t.keys.length - 1;
        for (int i = slot(key, mask); ; i = (i + 1) & mask) {
            Object value = t.values[i];
            if (value == null) {
                return null;
            }
            if (t.keys[i] == key) {
                return (T) value;
            }
        }
    }

    synchronized void put(long key, T value) {
        Table old = table;
        int size = old.size + (get(key) == null ? 1 : 0);
        int capacity = old.keys.length;
        while (size * 2 > capacity) {
            capacity *= 2;
        }
        Table next = new Table(capacity, size);
        copy(old, next, key);
        insert(next, key, value);
        table = next;
    }

    synchronized void remove(long key) {
        Table old = table;
        if (get(key) == null) {
            return;
        }
        Table next = new Table(old.keys.length, old.size - 1);
        copy(old, next, key);
        table = next;
    }

    int size() {
        return table.size;
    }

    @SuppressWarnings("unchecked")
    void forEach(java.util.function.Consumer<T> action) {
        Table t = table;
        for (Object value : t.values) {
            if (value != null) {
                action.accept((T) value);
            }
        }
    }

    private static void copy(Table from, Table to, long except) {
        for (int i = 0; i < from.keys.length; i++) {
            if (from.values[i] != null && from.keys[i] != except) {
                insert(to, from.keys[i], from.values[i]);
            }
        }
    }

    private static void insert(Table t, long key, Object value) {
        int mask = t.keys.length - 1;
        int i = slot(key, mask);
        while (t.values[i] != null) {
            i = (i + 1) & mask;
        }
        t.keys[i] = key;
        t.values[i] = value;
    }
}
