package info.kgeorgiy.ja.zakharov.arrayset;

import java.util.*;

public class ReverseList<E> extends AbstractList<E> implements RandomAccess {
    private final boolean reversed;
    private final List<E> data;

    public ReverseList(List<E> other) {
        if (other instanceof ReverseList<E> castedData) {
            data = castedData.data;
            reversed = !castedData.reversed;
        } else {
            data = other;
            reversed = true;
        }
    }

    @Override
    public E get(int index) {
        if (reversed) {
            return data.get(data.size() - 1 - index);
        } else {
            return data.get(index);
        }
    }

    @Override
    public int size() {
        return data.size();
    }
}
