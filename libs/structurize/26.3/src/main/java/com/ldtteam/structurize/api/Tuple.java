package com.ldtteam.structurize.api;

/**
 * A plain immutable pair, and the replacement for {@code net.minecraft.util.Tuple}, which no longer exists
 * in 26.2 (0 hits for {@code class Tuple} in the decompiled sources).
 *
 * <p><b>This is public, stable API.</b> It lives in {@code com.ldtteam.structurize.api} precisely so that
 * dependent mods may depend on it: it appears in the signatures of
 * {@link com.ldtteam.structurize.blockentities.interfaces.IBlueprintDataProviderBE#getSchematicCorners()},
 * {@link com.ldtteam.structurize.placement.handlers.placement.IPlacementHandler} and several placement
 * handlers, so there is no way to implement those interfaces without naming this type. It used to sit in
 * {@code com.ldtteam.structurize.compat.util} during the port; that package means "temporary port shim" and
 * was never a place to bind against.</p>
 *
 * <p>The {@code getA()} / {@code getB()} accessor shape is deliberately identical to the removed vanilla
 * class and will not change.</p>
 *
 * @param <A> first element type.
 * @param <B> second element type.
 */
public class Tuple<A, B>
{
    private final A a;
    private final B b;

    /**
     * @param a first element.
     * @param b second element.
     */
    public Tuple(final A a, final B b)
    {
        this.a = a;
        this.b = b;
    }

    /**
     * @return the first element.
     */
    public A getA()
    {
        return a;
    }

    /**
     * @return the second element.
     */
    public B getB()
    {
        return b;
    }

    @Override
    public boolean equals(final Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (!(obj instanceof final Tuple<?, ?> other))
        {
            return false;
        }
        return java.util.Objects.equals(a, other.a) && java.util.Objects.equals(b, other.b);
    }

    @Override
    public int hashCode()
    {
        return java.util.Objects.hash(a, b);
    }

    @Override
    public String toString()
    {
        return "Tuple[" + a + ", " + b + "]";
    }
}
