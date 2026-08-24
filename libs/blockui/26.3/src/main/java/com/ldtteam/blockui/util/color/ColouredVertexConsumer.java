package com.ldtteam.blockui.util.color;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Wrapper for having default color for vertex consumer.
 * <p>
 * Despite the name this class does <b>not</b> inject, override, filter or multiply anything: it is a plain
 * pass-through delegate that merely carries a mutable {@link #defaultColor} field plus the opt-in
 * {@link #setDefaultColor()} shorthand. The caller must invoke that method per vertex, exactly as
 * {@code setColor(r, g, b, a)} would be invoked.
 * <p>
 * <b>26.2/Fabric port note — do not delete as dead code.</b> This is a BlockUI-specific restoration of a public
 * API that upstream LDTTeam removed during their own 1.21.1 &rarr; 26.x rewrite (imported here in commit
 * {@code a44a40e}). It has no in-tree caller; it exists solely for downstream consumers (MineColonies'
 * {@code ColonyBorderRenderer}) that still depend on it. A future upstream merge must keep it.
 * <p>
 * Only the eight {@code abstract} methods of 26.2's {@link VertexConsumer} are overridden here. Every
 * {@code default} method of that interface (the {@code addVertex}/{@code setNormal} pose and vector overloads,
 * {@code addVertexWith2DPose}, {@code setColor(float,float,float,float)}, {@code setLight}, {@code setOverlay},
 * {@code putBakedQuad}, {@code putBlockBakedQuad}) is implemented in terms of {@code this.<abstract method>},
 * so inheriting them routes through this wrapper and cannot silently bypass it.
 */
public class ColouredVertexConsumer implements VertexConsumer
{
    protected final VertexConsumer parent;
    public IColour defaultColor = null;

    public ColouredVertexConsumer(final VertexConsumer parent)
    {
        this.parent = parent;
    }

    @Override
    public ColouredVertexConsumer addVertex(final float x, final float y, final float z)
    {
        parent.addVertex(x, y, z);
        return this;
    }

    @Override
    public ColouredVertexConsumer setColor(final int r, final int g, final int b, final int a)
    {
        parent.setColor(r, g, b, a);
        return this;
    }

    /**
     * 26.2: was a default method in 1.21.1, is abstract now, hence the explicit delegation.
     */
    @Override
    public ColouredVertexConsumer setColor(final int color)
    {
        parent.setColor(color);
        return this;
    }

    /**
     * Applies previously set defaultColor, will shamelessly NPE if you forgot to set it
     */
    public ColouredVertexConsumer setDefaultColor()
    {
        defaultColor.writeIntoBuffer(this);
        return this;
    }

    @Override
    public ColouredVertexConsumer setUv(final float u, final float v)
    {
        parent.setUv(u, v);
        return this;
    }

    @Override
    public ColouredVertexConsumer setUv1(final int u, final int v)
    {
        parent.setUv1(u, v);
        return this;
    }

    @Override
    public ColouredVertexConsumer setUv2(final int u, final int v)
    {
        parent.setUv2(u, v);
        return this;
    }

    /**
     * 26.3: new abstract {@code VertexConsumer#setUv3(float, float)} — the decal/glint UV channel
     * used by the decal/glint pass. Plain pass-through like the other UV setters; the return type is
     * narrowed to keep this class' covariant chain intact.
     */
    @Override
    public ColouredVertexConsumer setUv3(final float u, final float v)
    {
        parent.setUv3(u, v);
        return this;
    }

    @Override
    public ColouredVertexConsumer setNormal(final float x, final float y, final float z)
    {
        parent.setNormal(x, y, z);
        return this;
    }

    /**
     * 26.2: new abstract method, no 1.21.1 counterpart.
     */
    @Override
    public ColouredVertexConsumer setLineWidth(final float width)
    {
        parent.setLineWidth(width);
        return this;
    }
}
