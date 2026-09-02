package com.ldtteam.blockui;

import com.ldtteam.blockui.util.SafeError;
import com.ldtteam.blockui.views.View;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.resources.Identifier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.MutableComponent;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import org.jetbrains.annotations.Nullable;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Special parameters for the panes.
 */
public class PaneParams
{
    /**
     * Identifies a cached attribute value. The attribute name alone is not enough: one attribute may legitimately be
     * read through several accessors, and each of them parses it into a different type - see {@link #getProperty} for
     * what happened when the name was the whole key.
     *
     * @param name       the xml attribute name
     * @param parserKind what the value was parsed into, see {@link #getProperty(String, Object, Function, Object)}
     */
    private record PropertyKey(String name, Object parserKind)
    {
    }

    private final Map<PropertyKey, Object> propertyCache = new HashMap<>();
    private final List<PaneParams>    children;
    private final Node                node;
    private       View                parentView;
    private final Identifier          windowResLoc;

    /**
     * Instantiates the pane parameters.
     *
     * @param n the node.
     */
    public PaneParams(final Node n, final Identifier windowResLoc)
    {
        node = n;
        this.windowResLoc = windowResLoc;
        children = new ArrayList<>(node.getChildNodes().getLength());
    }

    /**
     * Get the node type.
     *
     * @return the name of the node.
     */
    public String getType()
    {
        return node.getNodeName();
    }

    /**
     * Get the parent for this pane.
     *
     * @return the parent.
     */
    public View getParentView()
    {
        return parentView;
    }

    /**
     * Set the parent for this pane.
     *
     * @param parent the new parent.
     */
    public void setParentView(final View parent)
    {
        parentView = parent;
    }

    /**
     * Get the width of the parent, if any. Defaults to 0 if no parent has been set.
     *
     * @return the width.
     */
    public int getParentWidth()
    {
        return parentView != null ? parentView.getInteriorWidth() : 0;
    }

    /**
     * Get the height of the parent, if any. Defaults to 0 if no parent has been set.
     *
     * @return the height.
     */
    public int getParentHeight()
    {
        return parentView != null ? parentView.getInteriorHeight() : 0;
    }

    /**
     * Get the left position of the parent, if any. Defaults to 0 if no parent has been set.
     *
     * @return the left position.
     */
    public int getParentLeft()
    {
        return parentView != null ? parentView.x : 0;
    }

    /**
     * Get the top position of the parent, if any. Defaults to 0 if no parent has been set.
     *
     * @return the top position.
     */
    public int getParentTop()
    {
        return parentView != null ? parentView.y : 0;
    }

    public List<PaneParams> getChildren()
    {
        if (!children.isEmpty()) return children;

        Node child = node.getFirstChild();
        while (child != null)
        {
            if (child.getNodeType() == Node.ELEMENT_NODE)
            {
                children.add(new PaneParams(child, windowResLoc));
            }
            child = child.getNextSibling();
        }

        return children;
    }

        public String getText()
    {
        return node.getTextContent().trim();
    }

    private Node getAttribute(final String name)
    {
        return node.getAttributes().getNamedItem(name);
    }

    public boolean hasAttribute(final String name)
    {
        return node.getAttributes().getNamedItem(name) != null;
    }

    /**
     * Finds an attribute by name from the XML node
     * and parses it using the provided parser method
     * @param name the attribute name to search for
     * @param parser the parser to convert the attribute to its property
     * @param def the default value if none can be found
     * @param <T> the type of value to work with
     * @return the parsed value
     */
    public <T> T getProperty(String name, Function<String, T> parser, T def)
    {
        // A stateless parser is fully described by its own class: Parsers.INT always produces an Integer,
        // Parsers.RESOURCE always an Identifier, String::toString always a String. Accessors whose parser is
        // parameterised by a type know better and pass that type themselves, see getEnum.
        return getProperty(name, parser.getClass(), parser, def);
    }

    /**
     * Finds an attribute by name from the XML node and parses it using the provided parser method, caching the result
     * per (name, parserKind) pair.
     * <p>
     * The cache used to be keyed by attribute name alone, which is what made every window holding an
     * {@code <image source="...">} fail to parse: {@code Image}'s constructor reads {@code source} through
     * {@link #getResource} for the texture and through {@link #getString} for the diagnostic message, and the second
     * read got the first read's {@link Identifier} back under the same key. The guard that was meant to catch this -
     * a {@code (T)} cast wrapped in {@code catch (ClassCastException)} - could never fire: {@code T} is erased, so
     * javac emits no {@code checkcast} here at all and puts one on the caller's side instead, where the mismatch blew
     * up as {@code Identifier cannot be cast to String} from inside {@code Image.<init>}. There is no runtime type
     * token in this method to check a cached value against, so rather than resurrect a check that cannot be written,
     * the situation is made unreachable: a value can only ever be read back through a parser of the kind that
     * produced it, and a different kind is simply a cache miss.
     * <p>
     * {@code parserKind} is the parser's <em>class</em> and not the parser instance, because {@code Parsers.ENUM} and
     * {@code Parsers.SCALED} are factories handing out a fresh lambda per call - keying on identity would never hit
     * for them and would grow this map once per window open, {@code PaneParams} instances living in {@link Loader}'s
     * xml cache for the whole session. One lambda class per call site is exactly the granularity wanted.
     * <p>
     * Known limit, unchanged by this: a parser parameterised by something other than its result type still shares one
     * entry per name, so reading one attribute with {@code Parsers.SCALED(a)} and then {@code Parsers.SCALED(b)}
     * returns the first scale's number. That is a wrong value rather than a wrong type and predates the type keying.
     *
     * @param name the attribute name to search for
     * @param parserKind identifies what the parser turns the attribute into; equal kinds must mean equal types
     * @param parser the parser to convert the attribute to its property
     * @param def the default value if none can be found
     * @param <T> the type of value to work with
     * @return the parsed value
     */
    @SuppressWarnings("unchecked") // safe by construction: the key carries the type the value was parsed into
    private <T> T getProperty(final String name, final Object parserKind, final Function<String, T> parser, final T def)
    {
        final PropertyKey key = new PropertyKey(name, parserKind);

        if (propertyCache.containsKey(key))
        {
            final T cached = (T) propertyCache.get(key);
            return cached != null ? cached : def;
        }

        T result = null;

        final Node attr = getAttribute(name);
        if (attr != null) result = parser.apply(attr.getNodeValue());

        propertyCache.put(key, result);
        return result != null ? result : def;
    }

    /**
     * Get the compoundTag attribute.
     *
     * @param name the name to search.
     * @return the attribute.
     */
    @Nullable
    public CompoundTag getCompoundTag(final String name)
    {
        return getCompoundTag(name, null);
    }

    /**
     * Get the compoundTag attribute from the name and revert to the default if not present.
     *
     * @param name      the name.
     * @param def the default value if none can be found
     * @return the String.
     */
    public CompoundTag getCompoundTag(final String name, final CompoundTag def)
    {
        final String data = getString(name, null);
        if (data == null)
        {
            return def;
        }
        CompoundTag tag;
        try
        {
            tag = TagParser.parseCompoundFully(data);
        }
        catch (CommandSyntaxException e)
        {
            SafeError.throwInDev(new IllegalArgumentException("Failed to parse compound at: " + getXmlRelatedId(), e));
            return def;
        }
        return tag;
    }

    /**
     * Get the string attribute.
     *
     * @param name the name to search.
     * @return the attribute.
     */
    @Nullable
    public String getString(final String name)
    {
        return getString(name, null);
    }

    /**
     * Get the String attribute from the name and revert to the default if not present.
     *
     * @param name      the name.
     * @param def the default value if none can be found
     * @return the String.
     */
    public String getString(final String name, final String def)
    {
        return getProperty(name, String::toString, def);
    }

    /**
     * Get the resource location from the name
     * @param name the attribute name
     * @return the parsed resource location
     */
    @Nullable
    public Identifier getResource(final String name)
    {
        return getResource(name, (Identifier) null);
    }

    /**
     * Get the resource location from the name
     * @param name the attribute name
     * @param def the default value to fallback to
     * @return the parsed resource location
     */
    public Identifier getResource(final String name, final Identifier def)
    {
        return getProperty(name, Parsers.RESOURCE, def);
    }

    /**
     * Get the resource location from the name and load it
     * @param name the attribute name
     * @param loader a method to act upon the resource if it is not blank or null
     * @return the parsed resource location (or null if it couldn't be parsed)
     */
    @Nullable
    public Identifier getResource(final String name, final Consumer<Identifier> loader)
    {
        final Identifier rl = getResource(name);
        if (rl != null && !rl.getPath().isEmpty())
        {
            loader.accept(rl);
            return rl;
        }
        return null;
    }

    /**
     * Get the text content with potential newlines from the name.
     *
     * @param name the name
     * @return the parsed and localized list
     */
    public List<MutableComponent> getMultilineText(final String name)
    {
        return getMultilineText(name, Collections.emptyList());
    }

    /**
     * Get the text content with potential newlines from the name and revert to the default if not present.
     *
     * @param name the name
     * @param def the default value if none can be found
     * @return the parsed and localized list
     */
    public List<MutableComponent> getMultilineText(final String name, List<MutableComponent> def)
    {
        return getProperty(name, Parsers.MULTILINE, def);
    }

    /**
     * Get the localized String attribute from the name and revert to the default if not present.
     *
     * @param name      the name.
     * @param def the default value if none can be found
     * @return the localized text component.
     */
    public MutableComponent getTextComponent(final String name, final MutableComponent def)
    {
        return getProperty(name, Parsers.TEXT, def);
    }

    /**
     * Get the integer attribute from name and revert to the default if not present.
     *
     * @param name     the name.
     * @param def the default value if none can be found
     * @return the int.
     */
    public int getInteger(final String name, final int def)
    {
        return getProperty(name, Parsers.INT, def);
    }

    /**
     * Get the float attribute from name and revert to the default if not present.
     *
     * @param name     the name.
     * @param def the default value if none can be found
     * @return the float.
     */
    public float getFloat(final String name, final float def)
    {
        return getProperty(name, Parsers.FLOAT, def);
    }

    /**
     * Get the double attribute from name and revert to the default if not present.
     *
     * @param name     the name.
     * @param def the default value if none can be found
     * @return the double.
     */
    public double getDouble(final String name, final double def)
    {
        return getProperty(name, Parsers.DOUBLE, def);
    }

    /**
     * Get the boolean attribute from name and revert to the default if not present.
     *
     * @param name     the name.
     * @param def the default value if none can be found
     * @return the boolean.
     */
    public boolean getBoolean(final String name, final boolean def)
    {
        return getProperty(name, Parsers.BOOLEAN, def);
    }

    /**
     * Get the boolean attribute from name and class and revert to the default if not present.
     *
     * @param name      the name.
     * @param clazz     the class.
     * @param def the default value if none can be found
     * @param <T>       the type of class.
     * @return the enum attribute.
     */
    public <T extends Enum<T>> T getEnum(final String name, final Class<T> clazz, final T def)
    {
        // every Parsers.ENUM(...) lambda shares one class, so the enum class itself is the cache kind - otherwise one
        // attribute read as two different enums would collide, which is the single case parser-class keying misses
        return getProperty(name, clazz, Parsers.ENUM(clazz), def);
    }

    /**
     * Get the scalable integer attribute from name and revert to the default if not present.
     *
     * @param name  the name
     * @param scale the total value to be a fraction of
     * @param def the default value if none can be found
     * @return the parsed value
     */
    public int getScaledInteger(String name, final int scale, final int def)
    {
        return getProperty(name, Parsers.SCALED(scale), def);
    }

    /**
     * Parses two scalable values and processes them through an applicant
     *
     * @param name the attribute name to search for
     * @param scaleX the first fraction total
     * @param scaleY the second fraction total
     * @param applier the method to utilise the result values
     */
    public void getScaledInteger(final String name, final int scaleX, final int scaleY, Consumer<List<Integer>> applier)
    {
        List<Integer> results = Parsers.SCALED(scaleX, scaleY).apply(getString(name));
        if (results != null) applier.accept(results);
    }

    /**
     * Get the color attribute from name and revert to the default if not present.
     *
     * @param name the name.
     * @param def  the default value if none can be found
     * @return int color value.
     */
    public int getColor(final String name, final int def)
    {
        return getProperty(name, Parsers.COLOR, def);
    }

    /**
     * Fetches a property and runs the result through a given method.
     * Commonly used for shorthand properties.
     * @param name the name of the attribute to retrieve
     * @param parser the parser applied to each part
     * @param parts the maximum number of parts to fill to if less are given
     * @param applier the method to utilise the parsed values
     * @param <T> the type of each part
     */
    public <T> void applyShorthand(String name, Function<String, T> parser, int parts, Consumer<List<T>> applier)
    {
        List<T> results = Parsers.shorthand(parser, parts).apply(getString(name));
        if (results != null) applier.accept(results);
    }

    /**
     * Checks if any of attribute names are present and return first found, else return default.
     *
     * @param def the default value if none can be found
     * @param attributes attributes names to check
     * @return first found attribute or default
     */
    public String hasAnyAttribute(final String def, final String... attributes)
    {
        final NamedNodeMap nodeMap = node.getAttributes();
        for (final String attr : attributes)
        {
            if (nodeMap.getNamedItem(attr) != null) // inlined hasAttribute
            {
                return attr;
            }
        }
        return def;
    }

    /**
     * @return string path from nearest parent with id
     */
    public String getXmlRelatedId()
    {
        return windowResLoc.toString() + "|" + Objects.requireNonNullElseGet(getString("id"), () -> pathToNearestIdParent(node));
    }

    private static String pathToNearestIdParent(final Node node)
    {
        if (node == null)
        {
            return "root";
        }

        final NamedNodeMap attributes = node.getAttributes();
        final Node idNode = attributes == null ? null : attributes.getNamedItem("id");
        final String id = idNode == null ? null : idNode.getNodeValue();
        return id != null ? id : pathToNearestIdParent(node.getParentNode()) + "/" + node.getLocalName();
    }
}
