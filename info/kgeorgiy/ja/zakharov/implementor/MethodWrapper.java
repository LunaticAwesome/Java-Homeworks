package info.kgeorgiy.ja.zakharov.implementor;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;

/**
 * Wrapper for {@link Method} that compares and computes hashcode two methods using only name and parameter types.
 */
public record MethodWrapper(Method method) {
    /**
     * Compares with different MethodWrapper by arguments and return type.
     *
     * @param obj the reference object with which to compare.
     * @return Equal for object<ul><li>true if other MethodWrapper has equal {@link Method#getName()}
     * and equal {@link Method#getParameterTypes()}</li>
     * <li>false otherwise</li>
     * </ul>
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MethodWrapper method1) {
            return method1.method().getName().equals(method.getName())
                    && Arrays.equals(method1.method().getParameterTypes(), method.getParameterTypes());
        }
        return false;
    }

    /**
     * Get hashcode of this methodWrapper using parameterTypes and name.
     *
     * @return hashCode representation
     */
    @Override
    public int hashCode() {
        return Objects.hash(method.getName().hashCode(), Arrays.hashCode(method.getParameterTypes()));
    }
}
