package com.unknownwq.worldmap.map;

/**
 * Turns a tile's two stored planes -- an unshaded base colour and a surface height per column -- into the
 * ARGB pixels that are uploaded to the GPU or written to a PNG.
 *
 * <h2>Why this is not part of the scan</h2>
 * <p>The scan sees one chunk. Relief does not: the brightness of a column depends on the columns around it,
 * and four of a chunk's sixteen-column edges are in some other chunk that may not have loaded yet. Vanilla's
 * filled map lives with that by comparing each column against <i>only</i> the one to its north and bucketing
 * the answer into three brightnesses, which is why a filled map reads flat -- a continuous height field is
 * being quantised to three values off a one-directional gradient.</p>
 *
 * <p>Deferring the colour to draw time removes the constraint entirely. By then the tile holds every column
 * it is ever going to hold, so a real surface normal is available in both axes, the shading is continuous
 * rather than three-valued, and the northernmost row of a chunk is shaded like every other row. It also
 * means the look can change between builds without asking anybody to re-walk their world: the planes on disk
 * are measurements, not pictures.</p>
 *
 * <h2>What is applied, in order</h2>
 * <ol>
 *   <li><b>Relief.</b> A central-difference gradient, softened so a forty-block cliff and a five-block bank
 *       do not saturate to the same value, lit from the north-west at about 48 degrees. Flat ground comes
 *       out at exactly 1.0, so a plain reads as its own map colour and nothing else.</li>
 *   <li><b>Slope shading.</b> A small extra darkening proportional to steepness regardless of which way the
 *       slope faces, which is what gives valley walls and the foot of a cliff their weight.</li>
 *   <li><b>Water.</b> Depth drives a continuous ramp from a pale shallow to a dark deep, and the relief is
 *       taken from the <i>sea floor</i> -- surface height minus depth -- so drowned terrain reads through.
 *       A column with a land neighbour is brightened, which draws the coastline as a line.</li>
 *   <li><b>Elevation.</b> A deliberately weak hypsometric tint: high ground a shade warmer and lighter, low
 *       ground a shade cooler. Enough to tell a plateau from a basin of the same block, not enough to
 *       repaint the map.</li>
 *   <li><b>Contours.</b> Thin darkened lines where a column crosses a multiple of the interval, skipped
 *       across cliffs where several would land in one pixel and merge into a blot.</li>
 * </ol>
 *
 * <p>All of it happens in linear light. Multiplying sRGB bytes directly -- which is what
 * {@code MapColor.calculateARGBColor} does -- crushes shadows and washes out highlights; two lookup tables
 * cost a handful of nanoseconds a pixel and the difference is visible on any slope.</p>
 *
 * <p>Not thread-safe: an instance owns scratch buffers and is reused by one caller. Contains no Minecraft
 * types at all, on purpose -- it is arithmetic over two arrays, and it can be run anywhere.</p>
 */
public final class MapShading
{
    /**
     * Light direction, north-west at roughly 48 degrees above the horizon -- the convention every printed
     * relief map uses, because a human reads a lit-from-above-left surface as convex and a lit-from-below
     * one as concave. Unit length, so the dot product below needs no normalising on this side.
     */
    private static final float LIGHT_X = -0.473f;
    private static final float LIGHT_Z = -0.473f;
    private static final float LIGHT_Y = 0.743f;

    /**
     * The cosine flat ground makes with the light. Shading is expressed relative to it so that a plain comes
     * out at exactly its own colour and the whole effect vanishes where there is no relief to show.
     */
    private static final float FLAT_COSINE = LIGHT_Y;

    /**
     * Gradient softening. A raw central difference is unbounded -- a cliff face is a hundred blocks per
     * block -- and clamping it makes every cliff identical. Dividing by {@code 1 + |d| * K} instead
     * compresses smoothly: a one-block step keeps four fifths of its slope, an eight-block step keeps a
     * third, and nothing ever exceeds {@code 1 / K}.
     */
    private static final float GRADIENT_SOFTEN = 0.22f;

    /**
     * How dark the steepest ground gets from slope alone, before the light direction is considered.
     */
    private static final float SLOPE_DARKEN = 0.24f;

    /**
     * Relief is damped under water: the eye is looking through a body of it, and a sea floor rendered at
     * full strength competes with the land.
     */
    private static final float UNDERWATER_RELIEF = 0.55f;

    private static final float MIN_LIGHT = 0.38f;
    private static final float MAX_LIGHT = 1.34f;

    /**
     * Where flat, unlit ground sits on the light scale.
     *
     * <p>Vanilla multiplies an unshaded map colour by 220/255 -- its {@code NORMAL} brightness -- and this
     * sits deliberately close to it, for two reasons. It keeps the map at roughly the brightness players
     * know a filled map to be, and it keeps a chunk drawn from measurements next to a chunk still stored as
     * a finished pre-shaded pixel from being visibly two different maps. It also leaves the headroom the
     * hillshade needs: at 1.0 a sunlit dune or a snowfield clips all three channels and the slope
     * disappears into flat white.</p>
     */
    private static final float BASE_LEVEL = 0.90f;

    /**
     * Depth in blocks at which water is treated as fully deep. An exponential approach rather than a
     * straight ramp, so the first few blocks off a beach carry most of the change -- which is where a real
     * coastline has most of its.
     */
    private static final float WATER_FALLOFF = 9.0f;

    private static final float SHALLOW_R = 0.30f;
    private static final float SHALLOW_G = 0.72f;
    private static final float SHALLOW_B = 0.80f;

    private static final float DEEP_R = 0.015f;
    private static final float DEEP_G = 0.045f;
    private static final float DEEP_B = 0.200f;

    /**
     * How far towards the shallow tone water of no depth at all is taken. Small on purpose: a one-block
     * puddle is still water and must not come out white.
     */
    private static final float SHALLOW_MIX = 0.20f;

    /**
     * Brightening applied to a water column that touches land, which is what turns a coast from a fade into
     * a line. Also small on purpose -- an archipelago has a great deal of coast.
     */
    private static final float COAST_LIGHT = 0.10f;

    /**
     * Height above sea level at which the elevation tint reaches full strength.
     */
    private static final float ELEVATION_SPAN = 110.0f;

    /**
     * Widest height difference the softening table covers. Past it the gradient has long since saturated,
     * so the table is simply clamped.
     */
    private static final int SOFT_RANGE = 1024;

    /**
     * Steps per unit of squared slope in the two slope tables. The tables replace a square root and two
     * divisions per pixel with two array reads; a quantisation of one part in sixty-four is far below what
     * an eight-bit channel can show.
     */
    private static final int SLOPE_STEPS = 64;

    private static final int LINEAR_ONE = 1 << 16;
    private static final int ENCODE_BITS = 12;

    /**
     * The softened central difference for every whole-block height difference between two columns two apart.
     */
    private static final float[] SOFTENED = new float[SOFT_RANGE * 2 + 1];

    /**
     * {@code 1 / sqrt(slope^2 + 1)} -- the reciprocal length of the surface normal -- by quantised squared
     * slope.
     */
    private static final float[] NORMAL_LENGTH;

    /**
     * The slope-only darkening factor by the same index.
     */
    private static final float[] SLOPE_SHADE;

    /**
     * sRGB byte to linear light, scaled to {@link #LINEAR_ONE}.
     */
    private static final int[] DECODE = new int[256];

    /**
     * Linear light back to an sRGB byte, indexed by the linear value shifted down to
     * {@value #ENCODE_BITS} bits.
     */
    private static final byte[] ENCODE = new byte[1 << ENCODE_BITS];

    static
    {
        for (int i = 0; i < SOFTENED.length; i++)
        {
            final float d = (i - SOFT_RANGE) * 0.5f;
            SOFTENED[i] = d / (1.0f + Math.abs(d) * GRADIENT_SOFTEN);
        }

        final float widest = SOFTENED[SOFTENED.length - 1];
        final int slopeEntries = (int) (2.0f * widest * widest * SLOPE_STEPS) + 2;
        NORMAL_LENGTH = new float[slopeEntries];
        SLOPE_SHADE = new float[slopeEntries];
        for (int i = 0; i < slopeEntries; i++)
        {
            final double slopeSquared = (i + 0.5) / SLOPE_STEPS;
            NORMAL_LENGTH[i] = (float) (1.0 / Math.sqrt(slopeSquared + 1.0));
            final double slope = Math.sqrt(slopeSquared);
            SLOPE_SHADE[i] = (float) (slope / (slope + 2.2));
        }

        for (int i = 0; i < 256; i++)
        {
            final double s = i / 255.0;
            final double linear = s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
            DECODE[i] = (int) Math.round(linear * LINEAR_ONE);
        }
        for (int i = 0; i < ENCODE.length; i++)
        {
            final double linear = (i + 0.5) / ENCODE.length;
            final double s = linear <= 0.0031308 ? linear * 12.92 : 1.055 * Math.pow(linear, 1 / 2.4) - 0.055;
            ENCODE[i] = (byte) Math.round(Math.min(255.0, Math.max(0.0, s * 255.0)));
        }
    }

    /**
     * The knobs, all of them scaled so that 0 means "do not do this at all" and 1 means the tuned default.
     *
     * @param relief    strength of the hillshade and the slope darkening.
     * @param water     strength of the depth ramp and the coastline highlight.
     * @param elevation strength of the hypsometric tint.
     * @param contour   vertical spacing of contour lines in blocks; 0 draws none.
     * @param seaLevel  the y the elevation tint measures from.
     */
    public record Options(float relief, float water, float elevation, int contour, int seaLevel)
    {
        /**
         * What the map draws with unless the configuration says otherwise.
         */
        public static final Options DEFAULT = new Options(1.0f, 1.0f, 0.55f, 0, 63);

        /**
         * Every enhancement off: the base colours, flat. Useful as a reference and as what a column with no
         * height data falls back to anyway.
         */
        public static final Options NONE = new Options(0f, 0f, 0f, 0, 63);
    }

    /**
     * How far towards "deep" each depth in blocks is. The same for every tile and every setting -- the
     * strength knob scales it at the point of use -- so it is built once.
     */
    private static final float[] WATER_RAMP = waterRamp();

    private short[] relief = new short[0];
    private short[] landform = new short[0];
    private byte[] kind = new byte[0];
    private int[] rowSum = new int[0];
    private byte[] rowCount = new byte[0];

    /**
     * Shades one rectangle of stored planes into finished pixels.
     *
     * <p>The rectangle is shaded on its own, so its outermost column and row take a one-sided gradient
     * rather than a central one. Over a tile that is one pixel in five hundred and twelve, and the smoothing
     * pass below makes the error smaller still; the alternative -- reaching into whichever neighbouring
     * tiles happen to be resident -- costs a lookup per edge column and a good deal of plumbing for
     * something nobody can see.</p>
     *
     * @param base    the base plane, {@code width * height} long, row-major by z. See {@link MapTile} for
     *                the layout: RGB is the unshaded colour and the top byte says what the column is.
     * @param heights the height plane, laid out identically.
     * @param width   width of the rectangle in columns.
     * @param height  its height in columns.
     * @param options what to apply.
     * @param out     destination, the same length; unmapped columns are written as 0 so the caller can leave
     *                its own background showing through.
     */
    public void shade(
      final int[] base,
      final short[] heights,
      final int width,
      final int height,
      final Options options,
      final int[] out)
    {
        final int count = width * height;
        if (options.relief() <= 0f && options.water() <= 0f && options.elevation() <= 0f && options.contour() <= 0)
        {
            // Every enhancement off. There is nothing to derive, so neither of the two preparation passes
            // and none of the per-column work below has anything to do -- and a configuration that has
            // turned all of this off has presumably done so to get the cost back.
            for (int i = 0; i < count; i++)
            {
                out[i] = (base[i] >>> 24) == MapTile.UNMAPPED ? 0 : base[i] | 0xFF000000;
            }
            return;
        }

        if (this.relief.length < count)
        {
            this.relief = new short[count];
            this.landform = new short[count];
            this.kind = new byte[count];
            this.rowSum = new int[count];
            this.rowCount = new byte[count];
        }

        this.prepare(base, heights, count);
        this.smooth(width, height);

        for (int z = 0; z < height; z++)
        {
            final int row = z * width;
            final int above = (z > 0 ? z - 1 : 0) * width;
            final int below = (z < height - 1 ? z + 1 : height - 1) * width;

            for (int x = 0; x < width; x++)
            {
                final int i = row + x;
                final int what = this.kind[i] & 0xFF;

                if (what == MapTile.UNMAPPED)
                {
                    out[i] = 0;
                    continue;
                }
                if (what == MapTile.PRESHADED || this.landform[i] == MapTile.NO_HEIGHT)
                {
                    out[i] = base[i] | 0xFF000000;
                    continue;
                }

                final int west = x > 0 ? x - 1 : 0;
                final int east = x < width - 1 ? x + 1 : width - 1;
                out[i] = this.shadeColumn(
                  base[i], what, heights[i], i, row + west, row + east, above + x, below + x, options);
            }
        }
    }

    /**
     * First pass: split the packed kind byte out of the base plane and build the surface the relief is taken
     * from. For land that is the recorded surface; for water it is the sea floor, which is what makes a
     * shallow bank read as a bank rather than as flat blue.
     */
    private void prepare(final int[] base, final short[] heights, final int count)
    {
        for (int i = 0; i < count; i++)
        {
            final int what = base[i] >>> 24;
            this.kind[i] = (byte) what;
            if (what == MapTile.UNMAPPED || what == MapTile.PRESHADED || heights[i] == MapTile.NO_HEIGHT)
            {
                this.relief[i] = MapTile.NO_HEIGHT;
            }
            else if (what >= MapTile.WATER)
            {
                this.relief[i] = (short) (heights[i] - (what - MapTile.WATER));
            }
            else
            {
                this.relief[i] = heights[i];
            }
        }
    }

    /**
     * Second pass: a three by three mean of the relief, and the reason the map does not come out looking
     * like static.
     *
     * <p>The recorded surface is <b>the top of the column</b>, which over a forest is the canopy. Lighting
     * that directly shades every individual treetop -- a one-block-wide, seven-block-tall feature -- and the
     * landform underneath disappears under the noise. Averaging first pushes the relief down to the scale
     * that is actually terrain, while the block colours keep drawing the trees themselves, which is the
     * division of labour a printed map uses too: the tint says what is growing there, the shading says what
     * shape the ground is.</p>
     *
     * <p>Columns with no height are left out of the average rather than counted as zero, so the edge of
     * explored ground does not turn into a cliff. Separable, in two passes with a running count, because
     * that is six array reads a column instead of nine and no inner loop for the compiler to reason
     * about.</p>
     */
    private void smooth(final int width, final int height)
    {
        for (int z = 0; z < height; z++)
        {
            final int row = z * width;
            for (int x = 0; x < width; x++)
            {
                final int i = row + x;
                int sum = 0;
                int n = 0;
                short v = this.relief[i];
                if (v != MapTile.NO_HEIGHT)
                {
                    sum = v;
                    n = 1;
                }
                if (x > 0 && (v = this.relief[i - 1]) != MapTile.NO_HEIGHT)
                {
                    sum += v;
                    n++;
                }
                if (x < width - 1 && (v = this.relief[i + 1]) != MapTile.NO_HEIGHT)
                {
                    sum += v;
                    n++;
                }
                this.rowSum[i] = sum;
                this.rowCount[i] = (byte) n;
            }
        }

        for (int z = 0; z < height; z++)
        {
            final int row = z * width;
            final int above = z > 0 ? row - width : row;
            final int below = z < height - 1 ? row + width : row;
            for (int x = 0; x < width; x++)
            {
                final int i = row + x;
                if (this.relief[i] == MapTile.NO_HEIGHT)
                {
                    this.landform[i] = MapTile.NO_HEIGHT;
                    continue;
                }
                int sum = this.rowSum[i];
                int n = this.rowCount[i];
                if (above != row)
                {
                    sum += this.rowSum[above + x];
                    n += this.rowCount[above + x];
                }
                if (below != row)
                {
                    sum += this.rowSum[below + x];
                    n += this.rowCount[below + x];
                }
                this.landform[i] = (short) (sum / n);
            }
        }
    }

    private int shadeColumn(
      final int packed,
      final int what,
      final short surfaceY,
      final int here,
      final int west,
      final int east,
      final int north,
      final int south,
      final Options options)
    {
        final int hw = this.at(west, here);
        final int he = this.at(east, here);
        final int hn = this.at(north, here);
        final int hs = this.at(south, here);

        final float dx = soften(he - hw);
        final float dz = soften(hs - hn);

        final boolean water = what >= MapTile.WATER;
        final float strength = options.relief() * (water ? UNDERWATER_RELIEF : 1.0f);

        float light = 1.0f;
        if (strength > 0.0f)
        {
            final int q = Math.min(NORMAL_LENGTH.length - 1, (int) ((dx * dx + dz * dz) * SLOPE_STEPS));
            final float cosine = (-dx * LIGHT_X - dz * LIGHT_Z + LIGHT_Y) * NORMAL_LENGTH[q];
            light = 1.0f + strength * (cosine / FLAT_COSINE - 1.0f);
            light *= 1.0f - strength * SLOPE_DARKEN * SLOPE_SHADE[q];
            light = Math.min(MAX_LIGHT, Math.max(MIN_LIGHT, light));
        }

        int r = DECODE[packed >>> 16 & 0xFF];
        int g = DECODE[packed >>> 8 & 0xFF];
        int b = DECODE[packed & 0xFF];

        if (water)
        {
            final float t = WATER_RAMP[Math.min(WATER_RAMP.length - 1, what - MapTile.WATER)];
            final float deep = options.water() * t;
            final float shallow = options.water() * (1.0f - t) * SHALLOW_MIX;

            r = mix(r, DEEP_R, deep);
            g = mix(g, DEEP_G, deep);
            b = mix(b, DEEP_B, deep);
            r = mix(r, SHALLOW_R, shallow);
            g = mix(g, SHALLOW_G, shallow);
            b = mix(b, SHALLOW_B, shallow);

            light *= 1.0f + options.water() * (0.06f - 0.42f * t);
            if (options.water() > 0.0f
                  && ((this.kind[west] & 0xFF) == MapTile.LAND
                        || (this.kind[east] & 0xFF) == MapTile.LAND
                        || (this.kind[north] & 0xFF) == MapTile.LAND
                        || (this.kind[south] & 0xFF) == MapTile.LAND))
            {
                light *= 1.0f + COAST_LIGHT * options.water();
            }
        }
        else
        {
            if (options.elevation() > 0.0f)
            {
                float e = (surfaceY - options.seaLevel()) / ELEVATION_SPAN;
                e = Math.min(1.0f, Math.max(-0.45f, e));
                light *= 1.0f + options.elevation() * 0.11f * e;
                if (e > 0.0f)
                {
                    final float warm = options.elevation() * 0.13f * e * e;
                    r = mix(r, 0.98f, warm);
                    g = mix(g, 0.95f, warm);
                    b = mix(b, 0.87f, warm);
                }
            }
            if (options.contour() > 0)
            {
                final int interval = options.contour();
                final int level = this.landform[here];
                final int band = Math.floorDiv(level, interval);
                final boolean crossing =
                  (Math.abs(he - level) <= interval && Math.floorDiv(he, interval) != band)
                    || (Math.abs(hs - level) <= interval && Math.floorDiv(hs, interval) != band);
                if (crossing)
                {
                    light *= band % 5 == 0 ? 0.82f : 0.91f;
                }
            }
        }

        light *= BASE_LEVEL;
        return 0xFF000000
                 | (encode(r * light) << 16)
                 | (encode(g * light) << 8)
                 | encode(b * light);
    }

    /**
     * The smoothed relief at one index, falling back to the centre column where there is nothing to read.
     * Falling back rather than treating the gap as zero matters: zero would be a two-thousand-block cliff
     * along the edge of every unexplored region.
     */
    private int at(final int index, final int fallback)
    {
        final short value = this.landform[index];
        return value == MapTile.NO_HEIGHT ? this.landform[fallback] : value;
    }

    /**
     * How far towards "deep" each depth in blocks is, precomputed because the exponential is the same for
     * every column and there are only ever a couple of hundred distinct depths.
     */
    private static float[] waterRamp()
    {
        final float[] ramp = new float[MapTile.MAX_WATER_DEPTH + 1];
        for (int d = 0; d < ramp.length; d++)
        {
            ramp[d] = (float) (1.0 - Math.exp(-d / WATER_FALLOFF));
        }
        return ramp;
    }

    private static float soften(final int difference)
    {
        return SOFTENED[Math.min(SOFT_RANGE * 2, Math.max(0, difference + SOFT_RANGE))];
    }

    private static int mix(final int linear, final float towards, final float amount)
    {
        return linear + (int) ((towards * LINEAR_ONE - linear) * amount);
    }

    private static int encode(final float linear)
    {
        final int v = (int) linear;
        if (v <= 0)
        {
            return 0;
        }
        return ENCODE[Math.min(ENCODE.length - 1, v >> (16 - ENCODE_BITS))] & 0xFF;
    }

    /**
     * Reduces a finished tile to one pixel per {@code factor x factor} block, into a smaller buffer.
     *
     * <p>Zoomed out below one pixel per block a 512-pixel tile is drawn 256, 128 or as few as 16 pixels
     * wide, and the sampler is nearest with no mip chain -- both vanilla's own choices for a
     * {@code DynamicTexture} and neither reachable from here without a mixin. Left alone that samples one
     * column in four or one in a thousand and throws the rest away, which turns a coastline into a dotted
     * line and a forest into static.</p>
     *
     * <p>So the reduction happens here instead, and the texture is then created at the reduced size rather
     * than at 512: one texel per screen pixel exactly, which is where the picture is identical to a
     * full-size texture the sampler would have thrown away fifteen sixteenths of, and where a tile at the
     * widest zoom costs a kilobyte of video memory instead of a megabyte. {@code factor} is always a power
     * of two and always divides the width, so the tile stays a whole number of pixels wide and every blit
     * still lands on an exact pixel boundary.</p>
     *
     * <p>Unmapped columns are left out of the average and a block with nothing in it stays unmapped, so the
     * edge of explored ground does not bleed into a grey fringe.</p>
     *
     * @param pixels  the finished pixels, {@code width * width}.
     * @param width   their side length.
     * @param factor  how many pixels on a side collapse into one; 1 or less copies nothing and is a
     *                programming error, since the caller then has no reduction to do.
     * @param average true to average the block, false to take its first pixel -- which is the cheap, ugly
     *                sampling the {@code smoothZoomedOut} setting switches back on.
     * @param out     destination, at least {@code (width / factor)} squared.
     */
    public static void reduce(
      final int[] pixels,
      final int width,
      final int factor,
      final boolean average,
      final int[] out)
    {
        final int side = width / factor;
        for (int z = 0; z < side; z++)
        {
            for (int x = 0; x < side; x++)
            {
                if (!average)
                {
                    out[z * side + x] = pixels[z * factor * width + x * factor];
                    continue;
                }

                int r = 0;
                int g = 0;
                int b = 0;
                int n = 0;
                for (int dz = 0; dz < factor; dz++)
                {
                    final int row = (z * factor + dz) * width + x * factor;
                    for (int dx = 0; dx < factor; dx++)
                    {
                        final int argb = pixels[row + dx];
                        if (argb != 0)
                        {
                            r += argb >>> 16 & 0xFF;
                            g += argb >>> 8 & 0xFF;
                            b += argb & 0xFF;
                            n++;
                        }
                    }
                }
                out[z * side + x] = n == 0 ? 0 : 0xFF000000 | (r / n) << 16 | (g / n) << 8 | b / n;
            }
        }
    }
}
