package info.kgeorgiy.ja.zakharov.arrayset;


import java.util.*;

public class ArraySet<T> extends AbstractSet<T> implements NavigableSet<T> {
    private final List<T> data;
    private final Comparator<? super T> comparator;

    public ArraySet() {
        comparator = null;
        data = new ReverseList<>(Collections.emptyList());
    }

    private List<T> unique(Collection<? extends T> data) {
        List<T> rawData = new ArrayList<>(data);
        rawData.sort(comparator);
        List<T> ret = new ArrayList<>();
        for (T elem : rawData) {
            if (Objects.nonNull(elem) && (ret.size() == 0 || compare(ret.get(ret.size() - 1), elem) != 0)) {
                ret.add(elem);
            }
        }
        return ret;
    }

    public ArraySet(Collection<? extends T> data) {
        this(data, null);
    }

    public ArraySet(Collection<? extends T> data, Comparator<? super T> comparator) {
        this.comparator = comparator;
        this.data = unique(data);
    }

    private ArraySet(List<T> data, Comparator<? super T> comparator, boolean fake) {
        this.data = data;
        this.comparator = comparator;
    }
    private T getElement(int index) {
        if (index < 0 || index >= data.size()) {
            return null;
        }
        return data.get(index);
    }

    @SuppressWarnings("unchecked")
    private int compare(T t1, T t2) {
        if (comparator != null) {
            return comparator.compare(t1, t2);
        }
        return ((Comparable<T>) t1).compareTo(t2);
    }

    private int higherIndex(T t, boolean inclusive) {
        int pos = Collections.binarySearch(data, t, comparator);
        if (pos < 0) {
            pos = -pos - 1;
        } else {
            if (!inclusive) {
                pos += 1;
            }
        }
        return pos;
    }

    private int lowerIndex(T t, boolean inclusive) {
        int pos = higherIndex(t, false) - 1;
        if (pos >= 0 && !inclusive && compare(getElement(pos), t) == 0) {
            --pos;
        }
        return pos;
    }

    @Override
    public T lower(T t) {
        return getElement(lowerIndex(t, false));
    }

    @Override
    public T floor(T t) {
        return getElement(lowerIndex(t, true));
    }

    @Override
    public T ceiling(T t) {
        return getElement(higherIndex(t, true));
    }

    @Override
    public T higher(T t) {
        return getElement(higherIndex(t, false));
    }

    @Override
    public T pollFirst() {
        throw new UnsupportedOperationException("ArraySet is Immutable");
    }

    @Override
    public T pollLast() {
        throw new UnsupportedOperationException("ArraySet is Immutable");
    }

    @Override
    public int size() {
        return data.size();
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean contains(Object o) {
        if (o == null) {
            return false;
        }
        return Collections.binarySearch(data, (T) o, comparator) >= 0;
    }

    @Override
    public Iterator<T> iterator() {
        return Collections.unmodifiableList(data).iterator();
    }

    @Override
    public NavigableSet<T> descendingSet() {
        ReverseList<T> tmp = new ReverseList<>(data);
        return new ArraySet<>(tmp, Collections.reverseOrder(comparator), false);
    }

    @Override
    public Iterator<T> descendingIterator() {
        return descendingSet().iterator();
    }

    // [fromIndex, toIndex)
    private NavigableSet<T> posSubSet(int fromIndex, int toIndex) {
        if (fromIndex > toIndex) {
            return new ArraySet<>(Collections.emptyList(), comparator);
        }
        return new ArraySet<>(data.subList(fromIndex, toIndex), comparator, false);
    }

    @Override
    public NavigableSet<T> subSet(T fromElement, boolean fromInclusive, T toElement, boolean toInclusive) {
        if (compare(fromElement, toElement) > 0) {
            throw new IllegalArgumentException("left border less than right border");
        }
        int fromIndex = higherIndex(fromElement, fromInclusive);
        int toIndex = lowerIndex(toElement, toInclusive);
        return posSubSet(fromIndex, toIndex + 1);
    }

    @Override
    public NavigableSet<T> headSet(T toElement, boolean inclusive) {
        int toIndex = lowerIndex(toElement, inclusive);
        return posSubSet(0, toIndex + 1);
    }

    @Override
    public NavigableSet<T> tailSet(T fromElement, boolean inclusive) {
        int fromIndex = higherIndex(fromElement, inclusive);
        return posSubSet(fromIndex, size());
    }

    @Override
    public Comparator<? super T> comparator() {
        return comparator;
    }

    @Override
    public SortedSet<T> subSet(T fromElement, T toElement) {
        return subSet(fromElement, true, toElement, false);
    }

    @Override
    public SortedSet<T> headSet(T toElement) {
        return headSet(toElement, false);
    }

    @Override
    public SortedSet<T> tailSet(T fromElement) {
        return tailSet(fromElement, true);
    }

    private void checkNotEmpty() {
        if (data.isEmpty()) {
            throw new NoSuchElementException();
        }
    }

    @Override
    public T first() {
        checkNotEmpty();
        return data.get(0);
    }

    @Override
    public T last() {
        checkNotEmpty();
        return data.get(data.size() - 1);
    }
}
