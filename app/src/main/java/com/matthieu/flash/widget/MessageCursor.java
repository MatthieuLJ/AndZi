package com.matthieu.flash.widget;

import android.database.AbstractCursor;

public class MessageCursor extends AbstractCursor {
    private final String message;

    public MessageCursor(String message) {
        this.message = message;
    }

    @Override
    public int getCount() {
        return 1;
    }

    @Override
    public String[] getColumnNames() {
        return new String[]{"_id", "chinese", "pinyin", "english"};
    }

    @Override
    public String getString(int i) {
        switch (i) {
            case 3:
                return message;
            case 0:
            case 1:
            case 2:
            default:
                return "";
        }
    }

    @Override
    public short getShort(int i) {
        return 0;
    }

    @Override
    public int getInt(int i) {
        return 0;
    }

    @Override
    public long getLong(int i) {
        return 0;
    }

    @Override
    public float getFloat(int i) {
        return 0;
    }

    @Override
    public double getDouble(int i) {
        return 0;
    }

    @Override
    public boolean isNull(int i) {
        return !((i >= 0) && (i <= 4));
    }
}
