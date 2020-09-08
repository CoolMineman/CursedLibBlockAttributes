package stolen;

import com.google.common.collect.Lists;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class DefaultedList<E> extends AbstractList<E> {
    private final List<E> delegate;
    private final E initialElement;

    public static <E> DefaultedList<E> of() {
        return new DefaultedList();
    }

    public static <E> DefaultedList<E> ofSize(int size, E defaultValue) {
        notNull(defaultValue);
        Object[] objects = new Object[size];
        Arrays.fill(objects, defaultValue);
        return new DefaultedList(Arrays.asList(objects), defaultValue);
    }

    @SafeVarargs
    public static <E> DefaultedList<E> copyOf(E defaultValue, E... values) {
        return new DefaultedList(Arrays.asList(values), defaultValue);
    }

    protected DefaultedList() {
        this(Lists.newArrayList(), null);
    }

    protected DefaultedList(List<E> delegate, @Nullable E initialElement) {
        this.delegate = delegate;
        this.initialElement = initialElement;
    }

    @Nonnull
    public E get(int index) {
        return this.delegate.get(index);
    }

    @Override
    public E set(int index, E element) {
        notNull(element);
        return this.delegate.set(index, element);
    }

    @Override
    public void add(int value, E element) {
        notNull(element);
        this.delegate.add(value, element);
    }

    @Override
    public E remove(int index) {
        return this.delegate.remove(index);
    }

    public int size() {
        return this.delegate.size();
    }

    @Override
    public void clear() {
        if (this.initialElement == null) {
            super.clear();
        } else {
            for (int i = 0; i < this.size(); ++i) {
                this.set(i, this.initialElement);
            }
        }

    }

    // Stolen From org.apache.commons.lang3.Validate
    public static <T> T notNull(final T object) {
        return notNull(object, "The validated object is null");
    }

    public static <T> T notNull(final T object, final String message, final Object... values) {
        if (object == null) {
            throw new NullPointerException(String.format(message, values));
        }
        return object;
    }
}
