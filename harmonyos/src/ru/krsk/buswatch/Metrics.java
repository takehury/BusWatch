package ru.krsk.buswatch;

/** Abstracts text measurement so layout truncation is identical everywhere. */
public interface Metrics {
    float width(String text, float sizePx, boolean bold);
}
