package com.expensetracker.config;

import org.slf4j.MDC;

import java.util.Map;

/**
 * Me-wrapper Runnable agar konteks MDC (trace id) dari thread pemanggil ikut
 * terbawa saat task dijalankan di thread pool asinkron, lalu dibersihkan setelah
 * selesai agar tidak bocor ke task lain pada thread yang sama.
 */
public final class MdcTask implements Runnable {

    private final Map<String, String> contextMap;
    private final Runnable delegate;

    public static Runnable wrap(Runnable task) {
        return new MdcTask(MDC.getCopyOfContextMap(), task);
    }

    private MdcTask(Map<String, String> contextMap, Runnable delegate) {
        this.contextMap = contextMap;
        this.delegate = delegate;
    }

    @Override
    public void run() {
        MDC.setContextMap(contextMap == null ? Map.of() : contextMap);
        try {
            delegate.run();
        } finally {
            MDC.clear();
        }
    }
}
