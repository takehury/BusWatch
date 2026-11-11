package ru.krsk.buswatch;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic scrolling selection over a list of string keys. Used both for the
 * route picker and the stop list, so crown/touch behaviour is identical.
 */
public final class Selection {

    public static final int VISIBLE_ROWS = FocalList.VISIBLE;

    private final List<String> keys = new ArrayList<>();
    private int selected;
    private String selectedKey = "";

    public void setKeys(List<String> newKeys) {
        keys.clear();
        if (newKeys != null) keys.addAll(newKeys);
        int restored = -1;
        if (!selectedKey.isEmpty()) {
            for (int i = 0; i < keys.size(); i++) {
                if (keys.get(i).equals(selectedKey)) {
                    restored = i;
                    break;
                }
            }
        }
        selected = restored >= 0 ? restored : Geo.clamp(selected, 0, Math.max(0, keys.size() - 1));
        if (keys.isEmpty()) selected = 0;
        remember();
    }

    public void setKeysKeepPosition(List<String> newKeys) {
        int old = selected;
        setKeys(newKeys);
        selected = Geo.clamp(old, 0, Math.max(0, keys.size() - 1));
        remember();
    }

    public List<String> keys() {
        return keys;
    }

    public int size() {
        return keys.size();
    }

    public boolean isEmpty() {
        return keys.isEmpty();
    }

    public int selectedIndex() {
        return selected;
    }

    public String selectedKey() {
        return selectedKey;
    }

    public String keyAt(int index) {
        if (index < 0 || index >= keys.size()) return null;
        return keys.get(index);
    }

    public boolean move(int delta) {
        if (keys.isEmpty() || delta == 0) return false;
        int next = Geo.clamp(selected + delta, 0, keys.size() - 1);
        if (next == selected) return false;
        selected = next;
        remember();
        return true;
    }

    public boolean select(int index) {
        if (keys.isEmpty()) return false;
        int next = Geo.clamp(index, 0, keys.size() - 1);
        if (next == selected) return false;
        selected = next;
        remember();
        return true;
    }

    public int topIndex() {
        if (keys.isEmpty()) return 0;
        return FocalList.topIndex(keys.size(), selected);
    }

    public boolean hasMoreAbove() {
        return topIndex() > 0;
    }

    public boolean hasMoreBelow() {
        return topIndex() + VISIBLE_ROWS < keys.size();
    }

    public String keyAtRow(int row) {
        int idx = topIndex() + row;
        if (row < 0 || row >= VISIBLE_ROWS || idx < 0 || idx >= keys.size()) return null;
        return keys.get(idx);
    }

    public boolean selectRow(int row) {
        int idx = topIndex() + row;
        if (idx < 0 || idx >= keys.size()) return false;
        return select(idx);
    }

    private void remember() {
        selectedKey = selectedIndex() >= 0 && selectedIndex() < keys.size()
                ? keys.get(selectedIndex()) : "";
    }
}
